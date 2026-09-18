package dev.deltaloot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import dev.deltaloot.loot.LootEntry;
import dev.deltaloot.loot.LootTable;
import java.util.List;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PluginSettingsTest {
    private static final String TABLE = "loot-tables.test";
    private static final String ENTRY = TABLE + ".entries.paper";
    private MockedStatic<Material> materialLookup;

    @BeforeEach
    void supplyOnlyMaterialRegistryMetadata() {
        Material paper = mock(Material.class);
        when(paper.isItem()).thenReturn(true);
        when(paper.getMaxStackSize()).thenReturn(64);
        Material air = mock(Material.class);
        when(air.isAir()).thenReturn(true);
        Material nonItem = mock(Material.class);
        materialLookup = mockStatic(Material.class);
        materialLookup.when(() -> Material.matchMaterial("PAPER")).thenReturn(paper);
        materialLookup.when(() -> Material.matchMaterial("AIR")).thenReturn(air);
        materialLookup.when(() -> Material.matchMaterial("WATER")).thenReturn(nonItem);
    }

    @AfterEach
    void restoreMaterialLookup() {
        if (materialLookup != null) {
            materialLookup.close();
        }
    }

    @Test
    void parsesRealYamlWithDefaultsAndImmutableResults() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                loot-tables:
                  test:
                    min-rolls: 2
                    max-rolls: 4
                    entries:
                      paper:
                        material: PAPER
                        name: 旧文件
                        lore:
                          - 封锁区的记录
                """);
        PluginSettings settings = PluginSettings.load(yaml);
        assertEquals(4, settings.guiRows());
        assertEquals(5.0, settings.maxDistance());
        assertTrue(settings.cancelOnDamage());
        LootTable table = settings.tables().get("test");
        assertEquals("test", table.title());
        assertEquals(2, table.minRolls());
        assertEquals(4, table.maxRolls());
        assertEquals(300, table.refreshSeconds());
        assertEquals(0.15, table.emptyChance());
        LootEntry entry = table.entries().getFirst();
        assertEquals(1, entry.minAmount());
        assertEquals(1, entry.maxAmount());
        assertEquals(1.0, entry.weight());
        assertEquals(40, entry.searchTicks());
        assertEquals("普通", entry.rarityName());
        assertEquals(NamedTextColor.WHITE, entry.rarityColor());
        assertEquals("旧文件", entry.name());
        assertEquals(List.of("封锁区的记录"), entry.lore());
        assertThrows(UnsupportedOperationException.class, settings.tables()::clear);
        assertThrows(UnsupportedOperationException.class, table.entries()::clear);
        assertThrows(UnsupportedOperationException.class, entry.lore()::clear);
    }

    @Test
    void appliesConfiguredValuesAndRoundsSecondsToTicks() {
        YamlConfiguration yaml = valid();
        yaml.set("gui.rows", 2);
        yaml.set("search.max-distance", 16.0);
        yaml.set("search.cancel-on-damage", false);
        yaml.set(TABLE + ".min-rolls", 0);
        yaml.set(TABLE + ".max-rolls", 9);
        yaml.set(TABLE + ".refresh-seconds", 604_800);
        yaml.set(TABLE + ".empty-chance", 1.0);
        yaml.set(ENTRY + ".min-amount", 2);
        yaml.set(ENTRY + ".max-amount", 64);
        yaml.set(ENTRY + ".rarity-name", "绝密");
        yaml.set(ENTRY + ".rarity-color", "RED");
        yaml.set(ENTRY + ".search-seconds", 0.125);
        PluginSettings settings = PluginSettings.load(yaml);
        assertEquals(2, settings.guiRows());
        assertEquals(16, settings.maxDistance());
        assertFalse(settings.cancelOnDamage());
        LootTable table = settings.tables().get("test");
        assertEquals(604_800, table.refreshSeconds());
        assertEquals(9, table.maxRolls());
        assertEquals(1.0, table.emptyChance());
        LootEntry entry = table.entries().getFirst();
        assertEquals(3, entry.searchTicks());
        assertEquals(64, entry.maxAmount());
        assertEquals("绝密", entry.rarityName());
        assertEquals(NamedTextColor.RED, entry.rarityColor());
    }

    @Test
    void rejectsMalformedSectionsInsteadOfSilentlyUsingDefaults() {
        rejects("gui", "wrong");
        rejects("search", "wrong");
        rejects("loot-tables", List.of("wrong"));
        rejects(TABLE + ".entries", "wrong");
    }

    @Test
    void rejectsNonIntegralOrOutOfRangeGuiSizes() {
        for (Object value : List.of(1, 7, 3.5, "4", Double.NaN)) {
            rejects("gui.rows", value);
        }
    }

    @Test
    void rejectsInvalidDistanceAndBooleanTypes() {
        for (Object value : List.of(0.9, 16.1, Double.POSITIVE_INFINITY, "5")) {
            rejects("search.max-distance", value);
        }
        rejects("search.cancel-on-damage", "true");
    }

    @Test
    void rejectsRollCountsBeyondTheAvailableSlotsOrInvalidRange() {
        YamlConfiguration yaml = valid();
        yaml.set("gui.rows", 2);
        yaml.set(TABLE + ".max-rolls", 19);
        assertInvalid(yaml, TABLE + ".max-rolls");
        rejects(TABLE + ".min-rolls", -1);
        rejects(TABLE + ".max-rolls", 1);
        rejects(TABLE + ".max-rolls", 4.5);
    }

    @Test
    void rejectsInvalidIntervalsAndProbabilities() {
        rejects(TABLE + ".refresh-seconds", 0);
        rejects(TABLE + ".refresh-seconds", 604_801);
        rejects(TABLE + ".refresh-seconds", 1.5);
        rejects(TABLE + ".empty-chance", -0.01);
        rejects(TABLE + ".empty-chance", 1.01);
        rejects(TABLE + ".empty-chance", Double.NaN);
        rejects(ENTRY + ".search-seconds", 0.09);
        rejects(ENTRY + ".search-seconds", 3600.1);
    }

    @Test
    void entireSixRowInventoryCanContainLootIncludingTheFormerToolbar() {
        YamlConfiguration yaml = valid();
        yaml.set("gui.rows", 6);
        yaml.set(TABLE + ".min-rolls", 54);
        yaml.set(TABLE + ".max-rolls", 54);
        PluginSettings settings = PluginSettings.load(yaml);
        assertEquals(54, settings.tables().get("test").maxRolls());
        assertEquals(54, settings.tables().get("test").roll(54, new java.util.Random(1)).size());
    }

    @Test
    void rejectsAirNonItemsUnknownMaterialsAndIllegalStackAmounts() {
        rejects(ENTRY + ".material", "AIR");
        rejects(ENTRY + ".material", "WATER");
        rejects(ENTRY + ".material", "DOES_NOT_EXIST");
        rejects(ENTRY + ".min-amount", 0);
        rejects(ENTRY + ".max-amount", 65);
        YamlConfiguration yaml = valid();
        yaml.set(ENTRY + ".min-amount", 5);
        yaml.set(ENTRY + ".max-amount", 4);
        assertInvalid(yaml, ENTRY + ".max-amount");
    }

    @Test
    void rejectsInvalidWeightsColorsAndLoreInsteadOfDroppingValues() {
        rejects(ENTRY + ".weight", 0);
        rejects(ENTRY + ".weight", -1);
        rejects(ENTRY + ".weight", Double.POSITIVE_INFINITY);
        rejects(ENTRY + ".rarity-color", "rainbow");
        rejects(ENTRY + ".rarity-name", " ");
        rejects(ENTRY + ".name", "");
        rejects(ENTRY + ".lore", "single string");
        rejects(ENTRY + ".lore", List.of("valid", 12));
    }

    @Test
    void requiresAtLeastOneTableAndEntryAndRejectsMalformedIds() {
        YamlConfiguration noTables = new YamlConfiguration();
        noTables.createSection("loot-tables");
        assertInvalid(noTables, "loot-tables");
        YamlConfiguration noEntries = valid();
        noEntries.set(ENTRY, null);
        assertInvalid(noEntries, TABLE);
        YamlConfiguration badTable = valid();
        badTable.createSection("loot-tables.BadId");
        assertInvalid(badTable, "loot-tables.BadId");
        YamlConfiguration badEntry = valid();
        badEntry.createSection(TABLE + ".entries.BadId");
        assertInvalid(badEntry, TABLE + ".entries.BadId");
    }

    private static YamlConfiguration valid() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(TABLE + ".min-rolls", 2);
        yaml.set(TABLE + ".max-rolls", 4);
        yaml.set(ENTRY + ".material", "PAPER");
        return yaml;
    }

    private static void rejects(String path, Object value) {
        YamlConfiguration yaml = valid();
        yaml.set(path, value);
        assertInvalid(yaml, path);
    }

    private static void assertInvalid(YamlConfiguration yaml, String expectedPath) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PluginSettings.load(yaml));
        assertTrue(error.getMessage().contains(expectedPath), error::getMessage);
    }
}
