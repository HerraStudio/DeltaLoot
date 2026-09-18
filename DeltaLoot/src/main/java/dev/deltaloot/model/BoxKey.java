package dev.deltaloot.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import java.util.UUID;

public record BoxKey(UUID world, int x, int y, int z) {
    public static BoxKey of(Block block) {
        return new BoxKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
    public Location location() {
        var w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }
}
