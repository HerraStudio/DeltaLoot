package dev.deltaloot.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** One possible item stack, including its visible quality and search duration. */
public record LootEntry(
        String id,
        Material material,
        int minAmount,
        int maxAmount,
        double weight,
        String rarityName,
        NamedTextColor rarityColor,
        long searchTicks,
        String name,
        List<String> lore) {

    public LootEntry {
        if (id == null || !id.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid loot entry id: " + id);
        }
        Objects.requireNonNull(material, "material");
        if (material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException("Material must be a non-air item: " + material);
        }
        if (minAmount < 1 || maxAmount < minAmount || maxAmount > material.getMaxStackSize()) {
            throw new IllegalArgumentException("Invalid stack amount for " + id);
        }
        if (!Double.isFinite(weight) || weight <= 0) {
            throw new IllegalArgumentException("Weight must be finite and positive for " + id);
        }
        if (rarityName == null || rarityName.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Loot name and rarity name cannot be blank for " + id);
        }
        Objects.requireNonNull(rarityColor, "rarityColor");
        if (searchTicks < 2 || searchTicks > 72_000) {
            throw new IllegalArgumentException("Search duration must be 2..72000 ticks for " + id);
        }
        lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
    }

    public ItemStack createItem(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        int amount = minAmount == maxAmount ? minAmount : random.nextInt(minAmount, maxAmount + 1);
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, rarityColor).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>(lore.size() + 2);
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        if (!lines.isEmpty()) {
            lines.add(Component.empty());
        }
        lines.add(Component.text("品质：", NamedTextColor.GRAY)
                .append(Component.text(rarityName, rarityColor))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }
}
