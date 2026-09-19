package dev.deltaloot.integration;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/** Optional CraftEngine bridge. The definition is resolved when a GUI opens, after item loading. */
public final class CraftEngineSearchMask {
    public static final String ITEM_ID = "default:search_mask";
    private final Logger logger;
    private final ItemLookup lookup;
    private boolean warned;

    public CraftEngineSearchMask(JavaPlugin plugin) {
        this(plugin.getLogger(), () -> {
            var engine = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
            if (engine == null || !engine.isEnabled()) throw new IllegalStateException("CraftEngine 未启用");
            ClassLoader loader = engine.getClass().getClassLoader();
            Class<?> api = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems", true, loader);
            Class<?> keyType = Class.forName("net.momirealms.craftengine.core.util.Key", true, loader);
            Object key = keyType.getMethod("of", String.class).invoke(null, ITEM_ID);
            Object definition = api.getMethod("byId", keyType).invoke(null, key);
            if (definition == null) throw new IllegalStateException("物品未注册：" + ITEM_ID);
            return buildItem(definition);
        });
    }

    CraftEngineSearchMask(Logger logger, ItemLookup lookup) {
        this.logger = logger;
        this.lookup = lookup;
    }

    /** The returned stack is a private copy. Never edit CraftEngine's source item or strip model data. */
    public ItemStack create() {
        try {
            ItemStack original = lookup.load();
            if (original == null || original.getType().isAir() || original.getAmount() < 1)
                throw new IllegalStateException("CraftEngine 返回了空物品：" + ITEM_ID);
            ItemStack copy = original.clone();
            copy.setAmount(1);
            warned = false;
            return copy;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            if (!warned) {
                Throwable reason = ex.getCause() == null ? ex : ex.getCause();
                logger.warning("无法载入搜索图标 " + ITEM_ID + "：" + reason
                        + "；暂用黑色玻璃板。修复 CraftEngine 配置后重新打开搜刮箱即可重试。");
                warned = true;
            }
            return new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        }
    }

    // CraftEngine's modern BukkitItemDefinition and legacy CustomItem expose different stack builders.
    static ItemStack buildItem(Object definition) throws ReflectiveOperationException {
        Method build;
        try { build = definition.getClass().getMethod("buildBukkitItem"); }
        catch (NoSuchMethodException legacy) { build = definition.getClass().getMethod("buildItemStack"); }
        Object result = build.invoke(definition);
        if (!(result instanceof ItemStack item)) throw new IllegalStateException("CraftEngine 未返回 Bukkit ItemStack");
        return item;
    }

    @FunctionalInterface
    interface ItemLookup { ItemStack load() throws ReflectiveOperationException; }
}
