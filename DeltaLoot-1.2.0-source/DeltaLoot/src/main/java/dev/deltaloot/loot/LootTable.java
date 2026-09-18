package dev.deltaloot.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** A box's generation rules. A null result represents an empty searchable slot. */
public record LootTable(
        String id,
        String title,
        int minRolls,
        int maxRolls,
        long refreshSeconds,
        double emptyChance,
        List<LootEntry> entries) {

    public LootTable {
        if (id == null || !id.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid loot table id: " + id);
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Loot table title cannot be blank: " + id);
        }
        if (minRolls < 0 || maxRolls < minRolls || maxRolls > 54) {
            throw new IllegalArgumentException("Loot rolls must satisfy 0 <= min <= max <= 54: " + id);
        }
        if (refreshSeconds < 1 || refreshSeconds > 604_800) {
            throw new IllegalArgumentException("Refresh interval must be 1..604800 seconds: " + id);
        }
        if (!Double.isFinite(emptyChance) || emptyChance < 0 || emptyChance > 1) {
            throw new IllegalArgumentException("Empty chance must be 0..1: " + id);
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        // Validate the complete weight range when loading, before a player opens a box.
        new WeightedPicker<>(entries, LootEntry::weight);
    }

    /** Returns exactly {@code slots} immutable entries, with null for empty slots. */
    public List<LootEntry> roll(int slots, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        if (slots < 1 || slots > 54 || maxRolls > slots) {
            throw new IllegalArgumentException("Slots must be 1..54 and accommodate max-rolls for " + id);
        }
        int rolls = minRolls == maxRolls ? minRolls : random.nextInt(minRolls, maxRolls + 1);
        List<LootEntry> result = new ArrayList<>(Collections.nCopies(slots, null));
        WeightedPicker<LootEntry> picker = new WeightedPicker<>(entries, LootEntry::weight);
        for (int i = 0; i < rolls; i++) {
            if (random.nextDouble() >= emptyChance) {
                result.set(i, picker.pick(random));
            }
        }
        for (int i = result.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            LootEntry previous = result.set(i, result.get(j));
            result.set(j, previous);
        }
        // List.copyOf rejects null; use an unmodifiable wrapper for searchable empty slots.
        return Collections.unmodifiableList(result);
    }
}
