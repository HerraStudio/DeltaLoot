package dev.deltaloot.config;

import dev.deltaloot.loot.LootEntry;
import dev.deltaloot.loot.LootTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Fully validated immutable settings. Invalid reloads can retain the previous settings. */
public record PluginSettings(
        int guiRows,
        double maxDistance,
        boolean cancelOnDamage,
        Map<String, LootTable> tables) {

    public PluginSettings {
        if (guiRows < 2 || guiRows > 6) {
            throw invalid("gui.rows", "必须为 2..6 的整数");
        }
        if (!Double.isFinite(maxDistance) || maxDistance < 1 || maxDistance > 16) {
            throw invalid("search.max-distance", "必须为 1..16");
        }
        if (tables == null || tables.isEmpty()) {
            throw invalid("loot-tables", "至少需要一个战利品表");
        }
        tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
        for (Map.Entry<String, LootTable> entry : tables.entrySet()) {
            if (entry.getValue() == null || !entry.getKey().equals(entry.getValue().id())) {
                throw invalid("loot-tables", "表 ID 与键名不一致");
            }
            if (entry.getValue().maxRolls() > guiRows * 9) {
                throw invalid("loot-tables." + entry.getKey() + ".max-rolls", "不能超过可搜索格数");
            }
        }
    }

    public static PluginSettings load(FileConfiguration config) {
        optionalSection(config, "gui");
        optionalSection(config, "search");
        int rows = integer(config, "gui.rows", 4, 2, 6);
        int slots = rows * 9;
        double maxDistance = number(config, "search.max-distance", 5.0, 1, 16);
        boolean cancelOnDamage = bool(config, "search.cancel-on-damage", true);
        ConfigurationSection tableSection = section(config, "loot-tables");
        Map<String, LootTable> tables = new LinkedHashMap<>();
        for (String tableId : tableSection.getKeys(false)) {
            validateId(tableSection, tableId);
            ConfigurationSection table = section(tableSection, tableId);
            String title = string(table, "title", tableId);
            int minRolls = integer(table, "min-rolls", 1, 0, slots);
            int maxRolls = integer(table, "max-rolls", slots, minRolls, slots);
            long refreshSeconds = integer(table, "refresh-seconds", 300, 1, 604_800);
            double emptyChance = number(table, "empty-chance", 0.15, 0, 1);
            ConfigurationSection entrySection = section(table, "entries");
            List<LootEntry> entries = new ArrayList<>();
            for (String entryId : entrySection.getKeys(false)) {
                validateId(entrySection, entryId);
                entries.add(readEntry(entrySection, entryId));
            }
            try {
                tables.put(tableId, new LootTable(tableId, title, minRolls, maxRolls,
                        refreshSeconds, emptyChance, entries));
            } catch (IllegalArgumentException exception) {
                throw invalid(table.getCurrentPath(), exception.getMessage());
            }
        }
        return new PluginSettings(rows, maxDistance, cancelOnDamage, tables);
    }

    private static LootEntry readEntry(ConfigurationSection parent, String id) {
        ConfigurationSection entry = section(parent, id);
        String materialName = string(entry, "material", null);
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir() || !material.isItem()) {
            throw invalid(path(entry, "material"), "必须是有效的非空气物品材质：" + materialName);
        }
        int minAmount = integer(entry, "min-amount", 1, 1, material.getMaxStackSize());
        int maxAmount = integer(entry, "max-amount", minAmount, minAmount, material.getMaxStackSize());
        double weight = number(entry, "weight", 1.0, 0, Double.MAX_VALUE);
        if (weight <= 0) {
            throw invalid(path(entry, "weight"), "必须大于 0");
        }
        String rarityName = string(entry, "rarity-name", "普通");
        String colorName = string(entry, "rarity-color", "white").toLowerCase(Locale.ROOT);
        NamedTextColor color = NamedTextColor.NAMES.value(colorName);
        if (color == null) {
            throw invalid(path(entry, "rarity-color"), "不是有效颜色名称：" + colorName);
        }
        double searchSeconds = number(entry, "search-seconds", 2.0, 0.1, 3600);
        long searchTicks = Math.round(searchSeconds * 20.0);
        String name = string(entry, "name", id);
        List<String> lore = stringList(entry, "lore");
        return new LootEntry(id, material, minAmount, maxAmount, weight,
                rarityName, color, searchTicks, name, lore);
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection value = parent.getConfigurationSection(key);
        if (value == null) {
            throw invalid(path(parent, key), "必须是配置节");
        }
        return value;
    }

    private static void optionalSection(ConfigurationSection parent, String key) {
        if (parent.contains(key) && !parent.isConfigurationSection(key)) {
            throw invalid(path(parent, key), "必须是配置节");
        }
    }

    private static int integer(ConfigurationSection section, String key, int fallback, int min, int max) {
        double value = number(section, key, fallback, min, max);
        if (value != Math.rint(value)) {
            throw invalid(path(section, key), "必须是整数");
        }
        return (int) value;
    }

    private static double number(ConfigurationSection section, String key, double fallback, double min, double max) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number numeric)) {
            throw invalid(path(section, key), "必须为数字");
        }
        double value = numeric.doubleValue();
        if (!Double.isFinite(value) || value < min || value > max) {
            throw invalid(path(section, key), "必须在 " + min + ".." + max + " 范围内");
        }
        return value;
    }

    private static boolean bool(ConfigurationSection section, String key, boolean fallback) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Boolean value)) {
            throw invalid(path(section, key), "必须为 true 或 false");
        }
        return value;
    }

    private static String string(ConfigurationSection section, String key, String fallback) {
        Object raw = section.get(key);
        if (raw == null && fallback != null) {
            return fallback;
        }
        if (!(raw instanceof String value) || value.isBlank()) {
            throw invalid(path(section, key), "必须为非空字符串");
        }
        return value;
    }

    private static List<String> stringList(ConfigurationSection section, String key) {
        Object raw = section.get(key);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> values)) {
            throw invalid(path(section, key), "必须为字符串列表");
        }
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String line)) {
                throw invalid(path(section, key), "每一项都必须是字符串");
            }
            result.add(line);
        }
        return List.copyOf(result);
    }

    private static void validateId(ConfigurationSection section, String id) {
        if (!id.matches("[a-z0-9_-]+")) {
            throw invalid(path(section, id), "ID 仅允许小写英文字母、数字、下划线和短横线");
        }
    }

    private static String path(ConfigurationSection section, String key) {
        String base = section.getCurrentPath();
        return base == null || base.isEmpty() ? key : base + "." + key;
    }

    private static IllegalArgumentException invalid(String path, String message) {
        return new IllegalArgumentException(path + ": " + message);
    }
}
