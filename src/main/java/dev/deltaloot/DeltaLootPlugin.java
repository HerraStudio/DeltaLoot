package dev.deltaloot;

import dev.deltaloot.command.LootCommand;
import dev.deltaloot.config.PluginSettings;
import dev.deltaloot.listener.BoxProtectionListener;
import dev.deltaloot.listener.SessionListener;
import dev.deltaloot.storage.BoxStore;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

public final class DeltaLootPlugin extends JavaPlugin {
    private LootManager manager;
    public PluginSettings readSettings() throws IOException, InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        yaml.load(new File(getDataFolder(), "config.yml"));
        return PluginSettings.load(yaml);
    }
    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            var settings = readSettings();
            var store = new BoxStore(getDataFolder().toPath().resolve("boxes.yml"));
            store.load();
            for (var box : store.all()) if (!settings.tables().containsKey(box.tableId))
                throw new IllegalArgumentException("箱子 " + box.id + " 引用了不存在的掉落表 " + box.tableId);
            manager = new LootManager(this, store, settings);
            var executor = new LootCommand(this, manager);
            var command = Objects.requireNonNull(getCommand("deltaloot"), "Missing deltaloot command in plugin.yml");
            command.setExecutor(executor); command.setTabCompleter(executor);
            getServer().getPluginManager().registerEvents(new SessionListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new BoxProtectionListener(manager), this);
            getServer().getScheduler().runTaskTimer(this, manager::tick, 2L, 2L);
            getLogger().info("DeltaLoot enabled for Paper 1.21.11; loaded " + store.all().size() + " boxes.");
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "DeltaLoot 启用失败；请修复配置/存档后重启。原存档未覆盖。", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        if (manager != null) manager.closeAll();
    }
}
