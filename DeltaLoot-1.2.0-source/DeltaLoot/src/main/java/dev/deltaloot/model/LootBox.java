package dev.deltaloot.model;

import org.bukkit.Material;
import java.util.List;

public final class LootBox {
    public final String id;
    public final BoxKey key;
    public final Material blockType;
    public final String tableId;
    public long refreshAt;
    public List<SlotData> slots = List.of();
    public LootBox(String id, BoxKey key, Material blockType, String tableId) {
        this.id = id;
        this.key = key;
        this.blockType = blockType;
        this.tableId = tableId;
    }
}
