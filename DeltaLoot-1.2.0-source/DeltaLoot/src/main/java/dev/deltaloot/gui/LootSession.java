package dev.deltaloot.gui;

import dev.deltaloot.core.SearchProgress;
import dev.deltaloot.model.LootBox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public final class LootSession implements InventoryHolder {
    public final UUID player;
    public final LootBox box;
    public final SearchProgress search = new SearchProgress();
    public int resumeDelayTicks;
    public int pulseTicks;
    public boolean finished;
    public final BossBar progressBar = BossBar.bossBar(Component.text("搜索中"), 0,
            BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private final Inventory inventory;
    public LootSession(UUID player, LootBox box, Component title) {
        this.player = player; this.box = box;
        inventory = Bukkit.createInventory(this, box.slots.size(), title);
    }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
