package dev.deltaloot;

import dev.deltaloot.config.PluginSettings;
import dev.deltaloot.gui.LootSession;
import dev.deltaloot.loot.LootEntry;
import dev.deltaloot.loot.LootTable;
import dev.deltaloot.model.*;
import dev.deltaloot.storage.BoxStore;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises real manager/session logic; only the Paper server boundary and icon metadata are mocked. */
class LootInterfaceTest {
    @TempDir Path directory;
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ItemStack> icons;
    private MockedStatic<ItemStack> itemCodec;
    private LootManager manager;
    private LootBox box;
    private Player player;
    private Inventory inventory;
    private final Map<Integer, ItemStack> visible = new HashMap<>();
    private BoxStore store;

    @BeforeEach void setup() {
        UUID worldId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.CHEST);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("deltaloot.use")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 1.5, 64.5, 1.5));
        inventory = mock(Inventory.class);
        doAnswer(invocation -> {
            visible.put(invocation.getArgument(0), invocation.getArgument(1)); return null;
        }).when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
        bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                .thenAnswer(call -> {
                    when(inventory.getHolder(false)).thenReturn(call.getArgument(0));
                    when(inventory.getSize()).thenReturn(call.getArgument(1));
                    return inventory;
                });
        icons = mockConstruction(ItemStack.class, (item, context) -> {
            when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));
            when(item.clone()).thenAnswer(ignored -> new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        });
        LootEntry entry = mock(LootEntry.class);
        when(entry.weight()).thenReturn(1.0);
        var table = new LootTable("test", "搜刮箱", 0, 0, 60, 0, List.of(entry));
        var settings = new PluginSettings(4, 5, true, Map.of("test", table));
        store = new BoxStore(directory.resolve("boxes.yml"));
        box = new LootBox("test", new BoxKey(worldId, 1, 64, 1), Material.CHEST, "test");
        box.refreshAt = Long.MAX_VALUE;
        box.slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) box.slots.add(new SlotData(reward(), 4, false));
        ItemStack decoded = reward();
        itemCodec = mockStatic(ItemStack.class);
        itemCodec.when(() -> ItemStack.deserializeBytes(any(byte[].class))).thenReturn(decoded);
        store.add(box);
        var plugin = mock(DeltaLootPlugin.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        manager = new LootManager(plugin, store, settings);
    }
    @AfterEach void cleanup() {
        try {
            if (player != null) {
                verify(player, never()).showBossBar(any());
                verify(player, never()).hideBossBar(any());
                verify(player, never()).sendActionBar(any(Component.class));
            }
        } finally {
            if (itemCodec != null) itemCodec.close();
            if (icons != null) icons.close();
            if (bukkit != null) bukkit.close();
        }
    }
    private LootSession open() {
        manager.open(player, box);
        return (LootSession) inventory.getHolder(false);
    }
    private ItemStack reward() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.displayName()).thenReturn(Component.text("测试物资"));
        when(item.getItemMeta()).thenReturn(meta);
        when(item.getType()).thenReturn(mock(Material.class));
        when(item.getAmount()).thenReturn(1);
        when(item.getMaxStackSize()).thenReturn(64);
        when(item.serializeAsBytes()).thenReturn(new byte[] {1});
        when(item.clone()).thenAnswer(ignored -> new ItemStack(Material.PAPER));
        return item;
    }

    @Test void openingAutomaticallySearchesEverySlotIncludingTheFormerCloseButton() {
        var session = open();
        assertEquals(36, inventory.getSize());
        assertEquals(36, visible.size());
        assertTrue(visible.values().stream().allMatch(Objects::nonNull));
        assertTrue(session.search.active());
        assertEquals(0, session.search.slot());
        for (int i = 0; i < 70; i++) manager.tick();
        assertEquals(35, session.search.slot());
        manager.tick(); manager.tick();
        assertTrue(session.finished);
        assertFalse(session.search.active());
        assertTrue(box.slots.stream().allMatch(SlotData::revealed));
        assertTrue(visible.values().stream().allMatch(Objects::nonNull));
        verify(player, never()).closeInventory();
    }

    @Test void legacySaveExpansionKeepsLootAndAddsOnlyBlankSlots() throws Exception {
        box.slots = new ArrayList<>(box.slots.subList(0, 27));
        box.slots.get(0).revealed(true);
        var original = box.slots.get(0);
        open();
        assertEquals(36, box.slots.size());
        assertSame(original, box.slots.get(0));
        assertEquals(Long.MAX_VALUE, box.refreshAt);
        assertNotNull(visible.get(0));
        for (int i = 27; i < 36; i++) assertNull(visible.get(i));
        var restored = new BoxStore(directory.resolve("boxes.yml"));
        restored.load();
        assertEquals(36, restored.get("test").slots.size());
        assertTrue(restored.get("test").slots.get(0).revealed());
    }

    @Test void revealedEmptyOrAlreadyTakenSlotsAreActuallyEmptyAndCannotRestartSearch() {
        box.slots.forEach(slot -> { slot.revealed(true); slot.item(null); });
        var session = open();
        assertEquals(36, visible.size());
        assertTrue(visible.values().stream().allMatch(Objects::isNull));
        manager.click(player, session, 0);
        assertFalse(session.search.active());
        verify(player, never()).showBossBar(any());
    }

    @Test void searchStillRevealsLootOnTimeWithNoVisibleProgress() throws Exception {
        box.slots.forEach(slot -> slot.revealed(true));
        box.slots.get(0).revealed(false);
        var session = open();
        var maskMeta = visible.get(0).getItemMeta();
        verify(maskMeta).displayName(Component.text("正在搜索", net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        verify(maskMeta).lore(List.of());
        assertEquals(0, session.search.fraction());
        manager.tick();
        assertEquals(0.5, session.search.fraction());
        assertNotNull(visible.get(0));
        assertFalse(box.slots.get(0).revealed());
        manager.tick();
        assertFalse(session.search.active());
        assertTrue(box.slots.get(0).revealed());
        assertNotNull(visible.get(0));
        var restored = new BoxStore(directory.resolve("boxes.yml"));
        restored.load();
        assertTrue(restored.get("test").slots.get(0).revealed());
    }

    @Test void interruptionPausesOneSecondThenAutomaticallyRestartsAtZero() {
        var session = open();
        manager.tick();
        manager.interrupt(player);
        assertFalse(session.search.active());
        assertFalse(box.slots.get(0).revealed());
        for (int i = 0; i < 9; i++) manager.tick();
        assertFalse(session.search.active());
        manager.tick();
        assertTrue(session.search.active());
        assertEquals(0, session.search.slot());
        assertEquals(0, session.search.fraction());
    }

    @Test void closingReleasesSessionAndStopsQueuedSearch() {
        var session = open();
        manager.close(session, true);
        manager.tick();
        manager.click(player, session, 0);
        assertFalse(manager.current(session));
        assertFalse(box.slots.get(0).revealed());
        verify(player).closeInventory();
    }

    @Test void formerHiddenCloseIndexInPlayerInventoryDoesNothing() {
        var session = open();
        manager.click(player, session, 44);
        assertTrue(manager.current(session));
        assertEquals(0, session.search.slot());
        verify(player, never()).closeInventory();
    }

    @Test void completedSlotAutomaticallyAdvancesToTheNextUnrevealedSlot() {
        box.slots.get(1).revealed(true);
        var session = open();
        manager.tick(); manager.tick();
        assertTrue(box.slots.get(0).revealed());
        assertEquals(2, session.search.slot());
        assertEquals(0, session.search.fraction());
        assertNotNull(visible.get(0));
        assertNotNull(visible.get(1));
        assertNotNull(visible.get(2));
    }

    @Test void clickingUnknownSlotsCannotReorderOrResetTheAutomaticScan() {
        var session = open();
        manager.tick();
        manager.click(player, session, 35);
        manager.click(player, session, 0);
        assertEquals(0, session.search.slot());
        assertEquals(0.5, session.search.fraction());
    }

    @Test void reopeningKeepsLootAndContinuesAtTheFirstUnrevealedSlot() {
        var originalSlots = box.slots;
        var session = open();
        manager.tick(); manager.tick(); manager.tick();
        assertEquals(1, session.search.slot());
        manager.close(session, true);
        var reopened = open();
        assertSame(originalSlots, box.slots);
        assertEquals(Long.MAX_VALUE, box.refreshAt);
        assertEquals(1, reopened.search.slot());
        assertEquals(0, reopened.search.fraction());
        assertTrue(box.slots.get(0).revealed());
    }

    @Test void anEmptyRolledCycleRemainsBlankAndDoesNotStartAFakeSearch() {
        var previous = box.slots;
        box.refreshAt = 1;
        var session = open();
        assertNotSame(previous, box.slots);
        assertEquals(36, box.slots.size());
        assertTrue(box.refreshAt > System.currentTimeMillis());
        assertEquals(-1, session.search.slot());
        assertTrue(session.finished);
        assertTrue(visible.values().stream().allMatch(Objects::isNull));
        verify(player, never()).showBossBar(any());
    }

    @Test void expiryWhileSearchingDoesNotReplaceAnActiveCycle() {
        var session = open();
        var previous = box.slots;
        box.refreshAt = 1;
        manager.tick();
        manager.open(player, box);
        assertSame(previous, box.slots);
        assertTrue(manager.current(session));
        assertEquals(0.5, session.search.fraction());
    }

    @Test void takingDamageAgainRestartsTheRecoveryDelay() {
        var session = open();
        manager.interrupt(player);
        for (int i = 0; i < 9; i++) manager.tick();
        manager.interrupt(player);
        assertEquals(20, session.resumeDelayTicks);
        manager.tick();
        assertFalse(session.search.active());
        for (int i = 0; i < 9; i++) manager.tick();
        assertTrue(session.search.active());
        assertEquals(0, session.search.slot());
    }

    @Test void legacyUnrevealedEmptySlotsAreInvisibleAndSkippedImmediately() {
        box.slots.forEach(slot -> slot.item(null));
        var session = open();
        assertFalse(session.search.active());
        assertTrue(session.finished);
        assertTrue(visible.values().stream().allMatch(Objects::isNull));
        verify(player, never()).showBossBar(any());
    }

    @Test void sparseLootSearchesOnlyRealItemsAndSkipsAllEmptyGaps() {
        for (int i = 0; i < box.slots.size(); i++) if (i != 5 && i != 30) box.slots.get(i).item(null);
        var session = open();
        assertEquals(5, session.search.slot());
        assertEquals(2, visible.values().stream().filter(Objects::nonNull).count());
        manager.tick(); manager.tick();
        assertEquals(30, session.search.slot());
        assertTrue(box.slots.get(5).revealed());
        manager.tick(); manager.tick();
        assertTrue(session.finished);
        assertTrue(box.slots.get(30).revealed());
        assertEquals(2, visible.values().stream().filter(Objects::nonNull).count());
    }

    @Test void claimingRevealedLootClearsItsSlotWithoutInterruptingSearch() {
        for (int i = 0; i < box.slots.size(); i++) if (i != 5 && i != 30) box.slots.get(i).item(null);
        PlayerInventory backpack = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(backpack);
        when(backpack.getStorageContents()).thenReturn(new ItemStack[36]);
        when(backpack.getMaxStackSize()).thenReturn(64);
        var session = open();
        manager.tick(); manager.tick();
        manager.click(player, session, 5);
        assertNull(box.slots.get(5).item());
        assertNull(visible.get(5));
        assertEquals(30, session.search.slot());
        assertTrue(box.slots.get(5).revealed());
        assertFalse(box.slots.get(30).revealed());
        verify(backpack).setStorageContents(any(ItemStack[].class));
    }
}
