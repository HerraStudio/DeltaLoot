package dev.deltaloot.model;

import org.bukkit.inventory.ItemStack;

/** Actual items never enter the GUI until the slot has been revealed. */
public final class SlotData {
    private ItemStack item;
    private final long searchTicks;
    private boolean revealed;
    public SlotData(ItemStack item, long searchTicks, boolean revealed) {
        this.item = item;
        this.searchTicks = searchTicks;
        this.revealed = revealed;
    }
    public ItemStack item() { return item; }
    public void item(ItemStack item) { this.item = item; }
    public long searchTicks() { return searchTicks; }
    public boolean revealed() { return revealed; }
    public void revealed(boolean value) { revealed = value; }
}
