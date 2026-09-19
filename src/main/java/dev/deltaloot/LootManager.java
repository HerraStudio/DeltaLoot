package dev.deltaloot;

import dev.deltaloot.config.PluginSettings;
import dev.deltaloot.core.InventoryTransfer;
import dev.deltaloot.gui.LootSession;
import dev.deltaloot.integration.CraftEngineSearchMask;
import dev.deltaloot.model.*;
import dev.deltaloot.storage.BoxStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.Lootable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class LootManager {
    private final DeltaLootPlugin plugin;
    private final BoxStore store;
    private final CraftEngineSearchMask searchMask;
    private PluginSettings settings;
    private final Map<UUID, LootSession> players = new HashMap<>();
    private final Map<String, LootSession> locks = new HashMap<>();
    public LootManager(DeltaLootPlugin plugin, BoxStore store, PluginSettings settings) {
        this.plugin = plugin; this.store = store; this.settings = settings;
        this.searchMask = new CraftEngineSearchMask(plugin);
    }
    public PluginSettings settings() { return settings; }
    public BoxStore store() { return store; }
    public LootBox at(Block block) { return block == null ? null : store.at(BoxKey.of(block)); }
    public boolean isBound(Inventory inventory) {
        if (inventory == null) return false;
        var holder = inventory.getHolder(false);
        if (holder instanceof Container container) return at(container.getBlock()) != null;
        if (holder instanceof DoubleChest chest) {
            return (chest.getLeftSide() instanceof Container left && at(left.getBlock()) != null)
                    || (chest.getRightSide() instanceof Container right && at(right.getBlock()) != null);
        }
        return false;
    }
    public boolean current(LootSession session) {
        return players.get(session.player) == session && locks.get(session.box.id) == session;
    }
    public static void message(org.bukkit.command.CommandSender player, String text) {
        player.sendMessage(Component.text("[搜刮箱] ", NamedTextColor.GOLD).append(Component.text(text, NamedTextColor.GRAY)));
    }
    private void transaction(Runnable change, Runnable rollback) throws IOException {
        change.run();
        try { store.save(); }
        catch (IOException | RuntimeException ex) {
            rollback.run();
            if (ex instanceof IOException io) throw io;
            throw new IOException("Cannot serialize box state", ex);
        }
    }
    private void storageError(Player player, IOException ex) {
        plugin.getLogger().log(Level.SEVERE, "Cannot save loot state; operation refused", ex);
        message(player, "存档写入失败，本次操作已取消，请联系管理员。");
    }
    public void create(Player player, String id, String table) throws IOException {
        if (!id.matches("[a-z0-9_-]{1,40}")) throw new IllegalArgumentException("箱子 ID 仅支持 1–40 位小写字母、数字、下划线和连字符。");
        if (store.get(id) != null) throw new IllegalArgumentException("箱子 ID 已存在。");
        if (!settings.tables().containsKey(table)) throw new IllegalArgumentException("未知掉落表，使用 /dl tables 查看。");
        Block block = player.getTargetBlockExact(6);
        if (block == null || !Set.of(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL).contains(block.getType()))
            throw new IllegalArgumentException("请看向 6 格内的单箱、陷阱箱或木桶。");
        if (at(block) != null) throw new IllegalArgumentException("该位置已绑定。");
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Chest chest
                && chest.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE)
            throw new IllegalArgumentException("请使用单箱，不支持大箱子。");
        Container container = (Container) block.getState();
        if (!container.getInventory().isEmpty()) throw new IllegalArgumentException("绑定前请清空实体容器。");
        if (!container.getInventory().getViewers().isEmpty()) throw new IllegalArgumentException("有人正在打开实体容器，请先关闭。");
        if (container instanceof Lootable lootable && lootable.getLootTable() != null)
            throw new IllegalArgumentException("请使用没有原版战利品表的普通空箱。");
        var box = new LootBox(id, BoxKey.of(block), block.getType(), table);
        transaction(() -> store.add(box), () -> store.remove(box));
    }
    public void remove(LootBox box) throws IOException {
        transaction(() -> store.remove(box), () -> store.add(box));
        closeBox(box);
    }
    public void reset(Collection<LootBox> boxes) throws IOException {
        var copy = List.copyOf(boxes);
        Map<LootBox, List<SlotData>> oldSlots = new HashMap<>();
        Map<LootBox, Long> oldTimes = new HashMap<>();
        copy.forEach(b -> { oldSlots.put(b, b.slots); oldTimes.put(b, b.refreshAt); });
        transaction(() -> copy.forEach(b -> { b.slots = List.of(); b.refreshAt = 0; }),
                () -> copy.forEach(b -> { b.slots = oldSlots.get(b); b.refreshAt = oldTimes.get(b); }));
        copy.forEach(this::closeBox);
    }
    public void reload(PluginSettings replacement) {
        for (var box : store.all()) if (!replacement.tables().containsKey(box.tableId))
            throw new IllegalArgumentException("掉落表 " + box.tableId + " 仍被箱子 " + box.id + " 引用，不能移除。");
        closeAll(); settings = replacement;
    }
    private void generate(LootBox box) throws IOException {
        var table = settings.tables().get(box.tableId);
        var random = ThreadLocalRandom.current();
        var rolled = table.roll(settings.guiRows() * 9, random);
        var slots = new ArrayList<SlotData>();
        for (var entry : rolled) slots.add(new SlotData(entry == null ? null : entry.createItem(random),
                entry == null ? 20 : entry.searchTicks(), entry == null));
        var previousSlots = box.slots; long previousTime = box.refreshAt;
        transaction(() -> { box.slots = slots; box.refreshAt = System.currentTimeMillis() + table.refreshSeconds() * 1000; },
                () -> { box.slots = previousSlots; box.refreshAt = previousTime; });
    }
    public void open(Player player, LootBox box) {
        if (!player.hasPermission("deltaloot.use")) { message(player, "你没有使用搜刮箱的权限。"); return; }
        if (store.get(box.id) != box || !near(player, box)) return;
        if (locks.containsKey(box.id)) { message(player, "有人正在搜刮这个箱子。"); return; }
        if (players.containsKey(player.getUniqueId())) close(players.get(player.getUniqueId()), true);
        try {
            if (box.slots.isEmpty() || System.currentTimeMillis() >= box.refreshAt) generate(box);
            // Preserve existing loot when expanding; added empty slots are never searched.
            if (box.slots.size() < settings.guiRows() * 9) {
                var previous = box.slots;
                var expanded = new ArrayList<>(previous);
                while (expanded.size() < settings.guiRows() * 9) expanded.add(new SlotData(null, 20, true));
                transaction(() -> box.slots = expanded, () -> box.slots = previous);
            }
        } catch (IOException ex) { storageError(player, ex); return; }
        var session = new LootSession(player.getUniqueId(), box,
                Component.text(settings.tables().get(box.tableId).title(), NamedTextColor.DARK_GRAY), searchMask.create());
        render(session);
        players.put(session.player, session); locks.put(box.id, session);
        try {
            player.openInventory(session.getInventory());
            if (player.getOpenInventory().getTopInventory().getHolder(false) != session) close(session, false);
            else { startNext(player, session); render(session); }
        } catch (RuntimeException ex) {
            close(session, false);
            plugin.getLogger().log(Level.SEVERE, "Could not open loot GUI", ex);
        }
    }
    private boolean near(Player player, LootBox box) {
        Location center = box.key.location();
        return player.isOnline() && !player.isDead() && center != null
                && player.getWorld().equals(center.getWorld())
                && player.getLocation().distanceSquared(center) <= settings.maxDistance() * settings.maxDistance()
                && center.getBlock().getType() == box.blockType;
    }
    public void click(Player player, LootSession session, int slot) {
        if (!current(session) || !session.player.equals(player.getUniqueId())
                || player.getOpenInventory().getTopInventory().getHolder(false) != session) return;
        if (!near(player, session.box)) { close(session, true); return; }
        int size = session.box.slots.size();
        if (slot < 0 || slot >= size) return;
        var data = session.box.slots.get(slot);
        if (!data.revealed()) return; // The automatic scan owns the search order.
        if (data.item() == null) return;
        ItemStack item = data.item();
        var destination = InventoryTransfer.plan(player.getInventory().getStorageContents(), item, player.getInventory().getMaxStackSize());
        if (destination.isEmpty()) { message(player, "背包空间不足，物品仍保留在箱内。"); return; }
        try { transaction(() -> data.item(null), () -> data.item(item)); }
        catch (IOException ex) { storageError(player, ex); return; }
        // Persistent consumption comes first. Abrupt process loss here can lose loot, but cannot reissue it.
        player.getInventory().setStorageContents(destination.get());
        player.saveData();
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", 0.6f, 1.2f);
        render(session);
    }
    public void tick() {
        for (var session : List.copyOf(players.values())) {
            Player player = Bukkit.getPlayer(session.player);
            if (player == null || !near(player, session.box) || player.getOpenInventory().getTopInventory().getHolder(false) != session) {
                close(session, true); continue;
            }
            if (session.resumeDelayTicks > 0) {
                session.resumeDelayTicks = Math.max(0, session.resumeDelayTicks - 2);
                if (session.resumeDelayTicks == 0) { startNext(player, session); render(session); }
                continue;
            }
            if (!session.search.active()) continue;
            var complete = session.search.advance(2);
            if (complete.isPresent()) {
                var slot = session.box.slots.get(complete.getAsInt());
                try { transaction(() -> slot.revealed(true), () -> slot.revealed(false)); }
                catch (IOException ex) { storageError(player, ex); close(session, true); continue; }
                revealFeedback(player, slot);
                startNext(player, session);
                render(session);
            } else {
                session.pulseTicks += 2;
                if (session.pulseTicks >= 10) {
                    session.pulseTicks = 0;
                    player.playSound(player.getLocation(), "minecraft:block.note_block.hat", 0.18f, 1.6f);
                }
            }
        }
    }
    public void interrupt(Player player) {
        var session = players.get(player.getUniqueId());
        if (session != null && (session.search.active() || session.resumeDelayTicks > 0)) {
            session.resumeDelayTicks = 20;
            session.search.cancel(); render(session);
        }
    }
    public void closePlayer(Player player) { var s = players.get(player.getUniqueId()); if (s != null) close(s, true); }
    public void close(LootSession session, boolean window) {
        session.resumeDelayTicks = 0;
        session.search.cancel(); players.remove(session.player, session); locks.remove(session.box.id, session);
        var player = Bukkit.getPlayer(session.player);
        if (player != null && window && player.getOpenInventory().getTopInventory().getHolder(false) == session) player.closeInventory();
    }
    private void closeBox(LootBox box) { var session = locks.get(box.id); if (session != null) close(session, true); }
    public void closeAll() { List.copyOf(players.values()).forEach(s -> close(s, true)); }
    private void startNext(Player player, LootSession session) {
        if (!current(session) || session.search.active() || session.finished || session.resumeDelayTicks > 0) return;
        for (int i = 0; i < session.box.slots.size(); i++) {
            var slot = session.box.slots.get(i);
            if (slot.revealed() || slot.item() == null) continue;
            session.search.start(i, slot.searchTicks());
            session.pulseTicks = 0;
            player.playSound(player.getLocation(), "minecraft:block.gravel.hit", 0.35f, 1.3f);
            return;
        }
        session.finished = true;
        player.playSound(player.getLocation(), "minecraft:block.note_block.chime", 0.45f, 1.2f);
    }
    private void revealFeedback(Player player, SlotData slot) {
        if (slot.item() == null) {
            player.playSound(player.getLocation(), "minecraft:block.note_block.hat", 0.25f, 0.8f);
            return;
        }
        var meta = slot.item().getItemMeta();
        Component name = meta != null ? meta.displayName() : null;
        var color = name == null ? null : name.color();
        boolean rare = NamedTextColor.RED.equals(color) || NamedTextColor.GOLD.equals(color);
        player.playSound(player.getLocation(), rare ? "minecraft:entity.player.levelup" : "minecraft:block.note_block.pling",
                rare ? 0.65f : 0.45f, rare ? 1.1f : 1.5f);
    }
    private static ItemStack icon(Material material, String name, NamedTextColor color, String... lore) {
        return icon(new ItemStack(material), name, color, lore);
    }
    private static ItemStack icon(ItemStack item, String name, NamedTextColor color, String... lore) {
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(Arrays.stream(lore).map(s -> Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta); return item;
    }
    private void render(LootSession session) {
        var inv = session.getInventory(); int count = session.box.slots.size();
        for (int i = 0; i < count; i++) {
            var slot = session.box.slots.get(i);
            ItemStack display;
            if (slot.item() == null) display = null;
            else if (session.search.slot() == i) display = icon(session.searchMask.clone(), "正在搜索", NamedTextColor.YELLOW);
            else if (!slot.revealed()) display = icon(Material.BLACK_STAINED_GLASS_PANE, "未搜索", NamedTextColor.GRAY,
                    session.resumeDelayTicks > 0 ? "搜索暂时中断，即将自动继续" : "等待自动搜索", "物资将在识别完成后显示");
            else {
                display = slot.item().clone(); var meta = display.getItemMeta();
                var lines = new ArrayList<Component>(); if (meta.lore() != null) lines.addAll(meta.lore());
                lines.add(Component.text("点击领取整组物品", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                meta.lore(lines); display.setItemMeta(meta);
            }
            inv.setItem(i, display);
        }
    }
}
