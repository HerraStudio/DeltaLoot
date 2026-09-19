package dev.deltaloot.command;

import dev.deltaloot.DeltaLootPlugin;
import dev.deltaloot.LootManager;
import dev.deltaloot.model.LootBox;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.logging.Level;
import static dev.deltaloot.LootManager.message;

public final class LootCommand implements CommandExecutor, TabCompleter {
    private final DeltaLootPlugin plugin;
    private final LootManager manager;
    public LootCommand(DeltaLootPlugin plugin, LootManager manager) { this.plugin = plugin; this.manager = manager; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("deltaloot.admin")) { message(sender, "你没有管理搜刮箱的权限。"); return true; }
        if (args.length == 0) { help(sender); return true; }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> {
                    if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("用法：/dl create <箱子ID> [掉落表]");
                    if (!(sender instanceof Player player)) throw new IllegalArgumentException("请在游戏内看向空箱执行。");
                    if (args[1].equals("all")) throw new IllegalArgumentException("all 是保留名称，请换一个箱子 ID。");
                    String table = args.length == 3 ? args[2] : "civilian";
                    manager.create(player, args[1], table); message(sender, "已绑定 " + args[1] + "，掉落表：" + table + "。右键即可搜刮。");
                }
                case "remove" -> {
                    if (args.length > 2) throw new IllegalArgumentException("用法：/dl remove [箱子ID]");
                    var box = resolve(sender, args); manager.remove(box);
                    message(sender, "已解绑 " + box.id + "，该箱本轮剩余虚拟战利品已删除。");
                }
                case "reset" -> {
                    if (args.length > 2) throw new IllegalArgumentException("用法：/dl reset [箱子ID|all]");
                    var boxes = args.length == 2 && args[1].equalsIgnoreCase("all") ? manager.store().all() : List.of(resolve(sender, args));
                    int count = boxes.size(); manager.reset(boxes);
                    message(sender, "已重置 " + count + " 个箱子，下次打开时生成新战利品。");
                }
                case "info" -> {
                    if (args.length > 2) throw new IllegalArgumentException("用法：/dl info [箱子ID]");
                    var box = resolve(sender, args);
                    long seconds = Math.max(0, (box.refreshAt - System.currentTimeMillis() + 999) / 1000);
                    message(sender, box.id + " | " + box.tableId + " | " + box.blockType);
                    message(sender, "最早刷新剩余 " + seconds + " 秒。");
                }
                case "list" -> {
                    message(sender, "已绑定 " + manager.store().all().size() + " 个箱子：");
                    manager.store().all().stream().limit(50).forEach(box -> message(sender,
                            box.id + " → " + box.tableId + " @ " + box.key.x() + ", " + box.key.y() + ", " + box.key.z()));
                    if (manager.store().all().size() > 50) message(sender, "仅显示前 50 个，完整列表见 boxes.yml。");
                }
                case "tables" -> message(sender, "可用掉落表：" + String.join(", ", manager.settings().tables().keySet()));
                case "reload" -> {
                    manager.reload(plugin.readSettings()); message(sender, "配置已重载，现有战利品保留至刷新；打开的搜刮界面已关闭。");
                }
                default -> help(sender);
            }
        } catch (IllegalArgumentException ex) { message(sender, ex.getMessage()); }
        catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Loot command failed", ex);
            message(sender, "操作失败，请检查控制台；配置重载失败时仍保留旧配置。");
        }
        return true;
    }
    private LootBox resolve(CommandSender sender, String[] args) {
        LootBox box;
        if (args.length > 1) box = manager.store().get(args[1]);
        else if (sender instanceof Player player) box = manager.at(player.getTargetBlockExact(6));
        else throw new IllegalArgumentException("控制台执行时必须指定箱子 ID。");
        if (box == null) throw new IllegalArgumentException("没有找到搜刮箱，请指定 ID 或看向已绑定箱子。");
        return box;
    }
    private void help(CommandSender sender) {
        message(sender, "/dl create <箱子ID> [civilian|military|medical] — 绑定所看空箱");
        message(sender, "/dl remove [ID] — 解绑；/dl reset [ID|all] — 重置");
        message(sender, "/dl info [ID]；/dl list；/dl tables；/dl reload");
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                         @NotNull String alias, String @NotNull [] args) {
        if (!sender.hasPermission("deltaloot.admin")) return List.of();
        List<String> choices = new ArrayList<>();
        if (args.length == 1) choices.addAll(List.of("create", "remove", "reset", "info", "list", "tables", "reload"));
        else if (args.length == 2 && Set.of("remove", "reset", "info").contains(args[0].toLowerCase(Locale.ROOT))) {
            manager.store().all().forEach(b -> choices.add(b.id));
            if (args[0].equalsIgnoreCase("reset")) choices.add("all");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) choices.addAll(manager.settings().tables().keySet());
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.startsWith(prefix)).sorted().toList();
    }
}
