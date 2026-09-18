package dev.deltaloot.core;

import org.bukkit.inventory.ItemStack;
import java.util.Optional;

/** Plans a full-stack transfer without modifying either input; armor/offhand are excluded by caller. */
public final class InventoryTransfer {
    private InventoryTransfer() { }
    public static Optional<ItemStack[]> plan(ItemStack[] storage, ItemStack reward, int inventoryMax) {
        if (reward == null || reward.getType().isAir() || reward.getAmount() <= 0 || inventoryMax <= 0)
            throw new IllegalArgumentException("Invalid reward or inventory maximum");
        ItemStack[] result = new ItemStack[storage.length];
        for (int i = 0; i < storage.length; i++) result[i] = storage[i] == null ? null : storage[i].clone();
        int remaining = reward.getAmount();
        int max = Math.min(inventoryMax, reward.getMaxStackSize());
        for (var existing : result) {
            if (existing == null || !existing.isSimilar(reward)) continue;
            int space = Math.max(0, Math.min(max, existing.getMaxStackSize()) - existing.getAmount());
            int moved = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + moved); remaining -= moved;
            if (remaining == 0) return Optional.of(result);
        }
        for (int i = 0; i < result.length; i++) {
            if (result[i] != null && !result[i].getType().isAir()) continue;
            var part = reward.clone(); int moved = Math.min(max, remaining);
            part.setAmount(moved); result[i] = part; remaining -= moved;
            if (remaining == 0) return Optional.of(result);
        }
        return Optional.empty();
    }
}
