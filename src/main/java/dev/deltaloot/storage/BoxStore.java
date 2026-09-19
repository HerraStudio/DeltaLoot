package dev.deltaloot.storage;

import dev.deltaloot.model.*;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Fail closed on corrupt state; write to a sibling temporary file before replacing. */
public final class BoxStore {
    private final Path file;
    private final Map<String, LootBox> boxes = new LinkedHashMap<>();
    private final Map<BoxKey, LootBox> locations = new HashMap<>();
    public BoxStore(Path file) { this.file = file; }
    public Collection<LootBox> all() { return Collections.unmodifiableCollection(boxes.values()); }
    public LootBox get(String id) { return boxes.get(id); }
    public LootBox at(BoxKey key) { return locations.get(key); }
    public void add(LootBox box) { boxes.put(box.id, box); locations.put(box.key, box); }
    public void remove(LootBox box) { boxes.remove(box.id); locations.remove(box.key); }

    public void load() throws IOException, InvalidConfigurationException {
        if (!Files.exists(file)) return;
        var yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        if (yaml.getInt("schema", -1) != 1 || !yaml.isConfigurationSection("boxes"))
            throw new IOException("boxes.yml: unsupported or incomplete schema");
        try {
            for (String id : yaml.getConfigurationSection("boxes").getKeys(false)) {
                if (!id.matches("[a-z0-9_-]{1,40}")) throw new IllegalArgumentException("invalid box ID");
                var s = Objects.requireNonNull(yaml.getConfigurationSection("boxes." + id));
                for (String key : List.of("world", "x", "y", "z", "material", "table", "refresh-at", "slot-count"))
                    if (!s.contains(key)) throw new IllegalArgumentException(id + " missing " + key);
                for (String key : List.of("x", "y", "z", "slot-count"))
                    if (!s.isInt(key)) throw new IllegalArgumentException(id + ": " + key + " must be integer");
                if (!(s.get("refresh-at") instanceof Number)) throw new IllegalArgumentException("invalid refresh-at");
                var key = new BoxKey(UUID.fromString(s.getString("world")), s.getInt("x"), s.getInt("y"), s.getInt("z"));
                var type = Material.valueOf(s.getString("material"));
                if (!Set.of(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL).contains(type))
                    throw new IllegalArgumentException("unsupported block " + type);
                String table = Objects.requireNonNull(s.getString("table"));
                if (!table.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("invalid table ID");
                if (locations.containsKey(key)) throw new IllegalArgumentException("duplicate location");
                var box = new LootBox(id, key, type, table);
                box.refreshAt = s.getLong("refresh-at");
                int count = s.getInt("slot-count");
                if (count < 0 || count > 54 || count % 9 != 0 || box.refreshAt < 0
                        || (count == 0) != (box.refreshAt == 0)) throw new IllegalArgumentException("invalid cycle");
                var slots = new ArrayList<SlotData>();
                for (int i = 0; i < count; i++) {
                    var slot = Objects.requireNonNull(s.getConfigurationSection("slots." + i), "missing slot " + i);
                    if (!slot.isBoolean("revealed") || !(slot.get("ticks") instanceof Number) || !slot.isString("item"))
                        throw new IllegalArgumentException("incomplete slot " + i);
                    long ticks = slot.getLong("ticks");
                    if (ticks < 1 || ticks > 72000) throw new IllegalArgumentException("invalid search ticks");
                    String encoded = slot.getString("item");
                    ItemStack item = encoded.equals("empty") ? null : ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
                    if (item != null && (item.getType().isAir() || item.getAmount() < 1 || item.getAmount() > item.getMaxStackSize()))
                        throw new IllegalArgumentException("invalid saved item");
                    slots.add(new SlotData(item, ticks, slot.getBoolean("revealed")));
                }
                box.slots = slots;
                add(box);
            }
        } catch (RuntimeException ex) {
            boxes.clear(); locations.clear();
            throw new IOException("boxes.yml is invalid; refusing to regenerate loot", ex);
        }
    }

    public void save() throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.createSection("boxes");
        for (var box : boxes.values()) {
            String p = "boxes." + box.id + ".";
            yaml.set(p + "world", box.key.world().toString());
            yaml.set(p + "x", box.key.x()); yaml.set(p + "y", box.key.y()); yaml.set(p + "z", box.key.z());
            yaml.set(p + "material", box.blockType.name()); yaml.set(p + "table", box.tableId);
            yaml.set(p + "refresh-at", box.refreshAt); yaml.set(p + "slot-count", box.slots.size());
            for (int i = 0; i < box.slots.size(); i++) {
                var slot = box.slots.get(i);
                String q = p + "slots." + i + ".";
                yaml.set(q + "revealed", slot.revealed()); yaml.set(q + "ticks", slot.searchTicks());
                yaml.set(q + "item", slot.item() == null ? "empty" : Base64.getEncoder().encodeToString(slot.item().serializeAsBytes()));
            }
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}
