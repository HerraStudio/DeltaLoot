package dev.deltaloot.listener;

import dev.deltaloot.LootManager;
import dev.deltaloot.model.LootBox;
import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/** Keeps bound physical containers inaccessible outside the search interface. */
public final class BoxProtectionListener implements Listener {
    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private final LootManager manager;

    public BoxProtectionListener(LootManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        LootBox box = manager.at(clicked);
        if (box == null) return;

        // Read the previous block result before denying vanilla interaction.
        // A protection plugin's DENY must also prevent our custom interface.
        boolean allowed = event.useInteractedBlock() != Event.Result.DENY;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (allowed && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getHand() == EquipmentSlot.HAND) {
            manager.open(event.getPlayer(), box);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (manager.isBound(event.getInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Also covers shift-clicks, number keys, creative clones and collecting
        // from a physical chest view that was already open when it was bound.
        if (manager.isBound(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (manager.isBound(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        LootBox box = manager.at(event.getBlock());
        if (box == null) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text(
                "请先使用 /dl remove " + box.id + " 解除绑定，再破坏搜刮箱。", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (bound(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::bound);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::bound);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesBox(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesBox(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonTouchesBox(Block piston, List<Block> moved, BlockFace movement) {
        if (bound(piston)) return true;
        // The head position is independent of the moved list (including an
        // empty retract). Read its facing instead of guessing from movement.
        if (piston.getBlockData() instanceof Directional directional
                && bound(piston.getRelative(directional.getFacing()))) return true;
        for (Block block : moved) {
            // Paper supplies actual movement direction for both event types.
            if (bound(block) || bound(block.getRelative(movement))) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (bound(event.getBlockPlaced())) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof BlockMultiPlaceEvent multiPlace) {
            for (BlockState replaced : multiPlace.getReplacedBlockStates()) {
                if (bound(replaced.getBlock())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        Block placed = event.getBlockPlaced();
        Material type = placed.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) return;
        for (BlockFace face : HORIZONTAL) {
            Block neighbor = placed.getRelative(face);
            if (neighbor.getType() == type && bound(neighbor)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text(
                        "搜刮箱旁不能放置同类箱子，以免合并为大箱子。", NamedTextColor.RED));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent event) {
        if (manager.isBound(event.getSource()) || manager.isBound(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupItem(InventoryPickupItemEvent event) {
        if (manager.isBound(event.getInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopperSearch(HopperInventorySearchEvent event) {
        if (bound(event.getSearchBlock())
                || (event.getInventory() != null && manager.isBound(event.getInventory()))) {
            event.setInventory(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTransportTarget(ItemTransportingEntityValidateTargetEvent event) {
        if (bound(event.getBlock())) event.setAllowed(false);
    }

    private boolean bound(Block block) {
        return manager.at(block) != null;
    }
}
