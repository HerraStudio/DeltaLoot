package dev.deltaloot.listener;

import dev.deltaloot.DeltaLootPlugin;
import dev.deltaloot.LootManager;
import dev.deltaloot.gui.LootSession;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

public final class SessionListener implements Listener {
    private final DeltaLootPlugin plugin;
    private final LootManager manager;
    public SessionListener(DeltaLootPlugin plugin, LootManager manager) { this.plugin = plugin; this.manager = manager; }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof LootSession session)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        // Event-safe transfer: next tick; manager verifies ownership and the still-open session again.
        plugin.getServer().getScheduler().runTask(plugin, () -> manager.click(player, session, slot));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof LootSession) event.setCancelled(true);
    }
    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof LootSession session) manager.close(session, false);
    }
    @EventHandler
    public void quit(PlayerQuitEvent event) { manager.closePlayer(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) { manager.closePlayer(event.getPlayer()); }
    @EventHandler
    public void death(PlayerDeathEvent event) { manager.closePlayer(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0 && manager.settings().cancelOnDamage())
            manager.interrupt(player);
    }
}
