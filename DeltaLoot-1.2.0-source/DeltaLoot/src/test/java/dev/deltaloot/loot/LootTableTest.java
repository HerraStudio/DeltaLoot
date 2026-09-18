package dev.deltaloot.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class LootTableTest {
    @Test
    void allEmptyResultsRetainEverySearchableSlotAndAreImmutable() {
        LootTable table = table(9, 9, 1.0, List.of(entry("paper", 1)));
        List<LootEntry> result = table.roll(9, new Random(1));
        assertEquals(9, result.size());
        assertTrue(result.stream().allMatch(value -> value == null));
        assertThrows(UnsupportedOperationException.class, () -> result.set(0, entry("other", 1)));
    }

    @Test
    void zeroRollsProduceOnlyEmptySlots() {
        LootTable table = table(0, 0, 0, List.of(entry("paper", 1)));
        assertEquals(27, table.roll(27, new Random(2)).stream().filter(value -> value == null).count());
    }

    @Test
    void everyRollCountIsReachableAndAlwaysWithinConfiguredBounds() {
        LootTable table = table(3, 7, 0, List.of(entry("paper", 1)));
        Random random = new Random(42);
        Set<Long> seenCounts = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            List<LootEntry> result = table.roll(9, random);
            assertEquals(9, result.size());
            long count = result.stream().filter(value -> value != null).count();
            assertTrue(count >= 3 && count <= 7);
            seenCounts.add(count);
        }
        assertEquals(Set.of(3L, 4L, 5L, 6L, 7L), seenCounts);
    }

    @Test
    void generatedItemsCanAppearInEverySlotInsteadOfOnlyAtTheBeginning() {
        LootTable table = table(1, 1, 0, List.of(entry("paper", 1)));
        Random random = new Random(77);
        Set<Integer> seenPositions = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            List<LootEntry> result = table.roll(9, random);
            for (int slot = 0; slot < result.size(); slot++) {
                if (result.get(slot) != null) {
                    seenPositions.add(slot);
                }
            }
        }
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8), seenPositions);
    }

    @Test
    void selectionUsesReplacementAndPreservesEntryIdentity() {
        LootEntry only = entry("paper", 1);
        LootTable table = table(9, 9, 0, List.of(only));
        for (LootEntry result : table.roll(9, new Random(3))) {
            assertSame(only, result);
        }
    }

    @Test
    void entryWeightsAndEmptyChanceBothAffectGeneratedResults() {
        LootEntry common = entry("common", 3);
        LootEntry rare = entry("rare", 1);
        LootTable table = table(9, 9, 0.2, List.of(common, rare));
        Random random = new Random(2026);
        int empty = 0;
        int commonCount = 0;
        int rareCount = 0;
        for (int i = 0; i < 5_000; i++) {
            for (LootEntry result : table.roll(9, random)) {
                if (result == null) {
                    empty++;
                } else if (result == common) {
                    commonCount++;
                } else if (result == rare) {
                    rareCount++;
                }
            }
        }
        assertEquals(0.2, empty / 45_000.0, 0.02);
        assertEquals(0.75, commonCount / (double) (commonCount + rareCount), 0.02);
        assertEquals(45_000, empty + commonCount + rareCount);
    }

    @Test
    void sameSeedReproducesTheWholeSlotLayout() {
        LootTable table = table(3, 9, 0.25, List.of(entry("a", 1), entry("b", 2)));
        assertEquals(table.roll(9, new Random(10)), table.roll(9, new Random(10)));
    }

    @Test
    void invalidSlotCountsCannotSilentlyTruncateLoot() {
        LootTable table = table(1, 10, 0, List.of(entry("paper", 1)));
        assertThrows(IllegalArgumentException.class, () -> table.roll(9, new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> table.roll(0, new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> table.roll(55, new Random(0)));
    }

    @Test
    void invalidTableRulesFailBeforeAnyPlayerRollsLoot() {
        LootEntry valid = entry("paper", 1);
        assertThrows(IllegalArgumentException.class, () -> table(2, 1, 0, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () -> table(-1, 1, 0, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () -> table(1, 55, 0, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () -> table(1, 1, Double.NaN, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () -> table(1, 1, 1.01, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () -> table(1, 1, -0.01, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () -> table(1, 1, 0, List.of()));
    }

    private static LootTable table(int minRolls, int maxRolls, double emptyChance, List<LootEntry> entries) {
        return new LootTable("test", "测试箱", minRolls, maxRolls, 60, emptyChance, entries);
    }

    private static LootEntry entry(String id, double weight) {
        // Material item properties access Paper's server registry; replace only that external dependency.
        Material material = mock(Material.class);
        when(material.isItem()).thenReturn(true);
        when(material.getMaxStackSize()).thenReturn(64);
        return new LootEntry(id, material, 1, 5, weight, "普通", NamedTextColor.WHITE,
                20, "测试物品", List.of());
    }
}
