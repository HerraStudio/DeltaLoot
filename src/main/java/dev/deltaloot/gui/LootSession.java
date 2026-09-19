package dev.deltaloot.gui;

import dev.deltaloot.core.SearchProgress;
import dev.deltaloot.model.LootBox;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public final class LootSession implements InventoryHolder {
    public final UUID player;
    public final LootBox box;
    public final ItemStack searchMask;
    public final SearchProgress search = new SearchProgress();
    public int resumeDelayTicks;
    public int pulseTicks;
    public boolean finished;
    private final Inventory inventory;
    public LootSession(UUID player, LootBox box, Component title, ItemStack searchMask) {
        this.player = player; this.box = box;
        this.searchMask = searchMask;
        inventory = Bukkit.createInventory(this, box.slots.size(), title);
    }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
