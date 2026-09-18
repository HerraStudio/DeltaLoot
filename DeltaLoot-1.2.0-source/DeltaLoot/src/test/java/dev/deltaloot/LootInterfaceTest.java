package dev.deltaloot;

import dev.deltaloot.config.PluginSettings;
import dev.deltaloot.gui.LootSession;
import dev.deltaloot.loot.LootEntry;
import dev.deltaloot.loot.LootTable;
import dev.deltaloot.model.*;
import dev.deltaloot.storage.BoxStore;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises real manager/session logic; only the Paper server boundary and icon metadata are mocked. */
class LootInterfaceTest {
    @TempDir Path directory;
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ItemStack> icons;
    private LootManager manager;
    private LootBox box;
    private Player player;
    private Inventory inventory;
    private final Map<Integer, ItemStack> visible = new HashMap<>();
    private BoxStore store;

    @BeforeEach void setup() {
        UUID worldId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.CHEST);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("deltaloot.use")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 1.5, 64.5, 1.5));
        inventory = mock(Inventory.class);
        doAnswer(invocation -> {
            visible.put(invocation.getArgument(0), invocation.getArgument(1)); return null;
        }).when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
        bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                .thenAnswer(call -> {
                    when(inventory.getHolder(false)).thenReturn(call.getArgument(0));
                    when(inventory.getSize()).thenReturn(call.getArgument(1));
                    return inventory;
                });
        icons = mockConstruction(ItemStack.class, (item, context) -> {
            when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));
        });
        LootEntry entry = mock(LootEntry.class);
        when(entry.weight()).thenReturn(1.0);
        var table = new LootTable("test", "搜刮箱", 0, 0, 60, 0, List.of(entry));
        var settings = new PluginSettings(4, 5, true, Map.of("test", table));
        store = new BoxStore(directory.resolve("boxes.yml"));
        box = new LootBox("test", new BoxKey(worldId, 1, 64, 1), Material.CHEST, "test");
        box.refreshAt = Long.MAX_VALUE;
        box.slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) box.slots.add(new SlotData(null, 4, false));
        store.add(box);
        manager = new LootManager(mock(DeltaLootPlugin.class), store, settings);
    }
    @AfterEach void cleanup() {
        if (icons != null) icons.close();
        if (bukkit != null) bukkit.close();
    }
    private LootSession open() {
        manager.open(player, box);
        return (LootSession) inventory.getHolder(false);
    }

    @Test void openingAutomaticallySearchesEverySlotIncludingTheFormerCloseButton() {
        var session = open();
        assertEquals(36, inventory.getSize());
        assertEquals(36, visible.size());
        assertTrue(visible.values().stream().allMatch(Objects::nonNull));
        assertTrue(session.search.active());
        assertEquals(0, session.search.slot());
        for (int i = 0; i < 70; i++) manager.tick();
        assertEquals(35, session.search.slot());
        manager.tick(); manager.tick();
        assertTrue(session.finished);
        assertFalse(session.search.active());
        assertTrue(box.slots.stream().allMatch(SlotData::revealed));
        assertTrue(visible.values().stream().allMatch(Objects::isNull));
        verify(player, never()).closeInventory();
    }

    @Test void legacySaveGainsSearchableSlotsWithoutRerollingAndPersistsExpansion() throws Exception {
        box.slots = new ArrayList<>(box.slots.subList(0, 27));
        box.slots.get(0).revealed(true);
        var original = box.slots.get(0);
        open();
        assertEquals(36, box.slots.size());
        assertSame(original, box.slots.get(0));
        assertEquals(Long.MAX_VALUE, box.refreshAt);
        assertNull(visible.get(0));
        for (int i = 27; i < 36; i++) assertNotNull(visible.get(i));
        var restored = new BoxStore(directory.resolve("boxes.yml"));
        restored.load();
        assertEquals(36, restored.get("test").slots.size());
        assertTrue(restored.get("test").slots.get(0).revealed());
    }

    @Test void revealedEmptyOrAlreadyTakenSlotsAreActuallyEmptyAndCannotRestartSearch() {
        box.slots.forEach(slot -> slot.revealed(true));
        var session = open();
        assertEquals(36, visible.size());
        assertTrue(visible.values().stream().allMatch(Objects::isNull));
        manager.click(player, session, 0);
        assertFalse(session.search.active());
        verify(player, never()).showBossBar(any());
    }

    @Test void bossBarTracksProgressThenDisappearsAndCompletedEmptySlotClears() throws Exception {
        box.slots.forEach(slot -> slot.revealed(true));
        box.slots.get(0).revealed(false);
        var session = open();
        verify(player).showBossBar(session.progressBar);
        assertEquals(0f, session.progressBar.progress());
        manager.tick();
        assertEquals(0.5f, session.progressBar.progress());
        assertNotNull(visible.get(0));
        assertFalse(box.slots.get(0).revealed());
        manager.tick();
        assertFalse(session.search.active());
        assertTrue(box.slots.get(0).revealed());
        assertNull(visible.get(0));
        verify(player).hideBossBar(session.progressBar);
        var restored = new BoxStore(directory.resolve("boxes.yml"));
        restored.load();
        assertTrue(restored.get("test").slots.get(0).revealed());
    }

    @Test void interruptionPausesOneSecondThenAutomaticallyRestartsAtZero() {
        var session = open();
        manager.tick();
        manager.interrupt(player);
        assertFalse(session.search.active());
        assertFalse(box.slots.get(0).revealed());
        verify(player).hideBossBar(session.progressBar);
        for (int i = 0; i < 9; i++) manager.tick();
        assertFalse(session.search.active());
        manager.tick();
        assertTrue(session.search.active());
        assertEquals(0, session.search.slot());
        assertEquals(0f, session.progressBar.progress());
        verify(player, times(2)).showBossBar(session.progressBar);
    }

    @Test void closingReleasesSessionHidesBarAndStopsQueuedProgress() {
        var session = open();
        manager.close(session, true);
        manager.tick();
        manager.click(player, session, 0);
        assertFalse(manager.current(session));
        assertFalse(box.slots.get(0).revealed());
        verify(player).hideBossBar(session.progressBar);
        verify(player).closeInventory();
    }

    @Test void formerHiddenCloseIndexInPlayerInventoryDoesNothing() {
        var session = open();
        manager.click(player, session, 44);
        assertTrue(manager.current(session));
        assertEquals(0, session.search.slot());
        verify(player, never()).closeInventory();
    }

    @Test void completedSlotAutomaticallyAdvancesToTheNextUnrevealedSlot() {
        box.slots.get(1).revealed(true);
        var session = open();
        manager.tick(); manager.tick();
        assertTrue(box.slots.get(0).revealed());
        assertEquals(2, session.search.slot());
        assertEquals(0f, session.progressBar.progress());
        assertNull(visible.get(0));
        assertNull(visible.get(1));
        assertNotNull(visible.get(2));
    }

    @Test void clickingUnknownSlotsCannotReorderOrResetTheAutomaticScan() {
        var session = open();
        manager.tick();
        manager.click(player, session, 35);
        manager.click(player, session, 0);
        assertEquals(0, session.search.slot());
        assertEquals(0.5, session.search.fraction());
    }

    @Test void reopeningKeepsLootAndContinuesAtTheFirstUnrevealedSlot() {
        var originalSlots = box.slots;
        var session = open();
        manager.tick(); manager.tick(); manager.tick();
        assertEquals(1, session.search.slot());
        manager.close(session, true);
        var reopened = open();
        assertSame(originalSlots, box.slots);
        assertEquals(Long.MAX_VALUE, box.refreshAt);
        assertEquals(1, reopened.search.slot());
        assertEquals(0, reopened.search.fraction());
        assertTrue(box.slots.get(0).revealed());
    }

    @Test void expiredBoxGeneratesANewCycleOnOpenAndStartsAutomatically() {
        var previous = box.slots;
        box.refreshAt = 1;
        var session = open();
        assertNotSame(previous, box.slots);
        assertEquals(36, box.slots.size());
        assertTrue(box.refreshAt > System.currentTimeMillis());
        assertEquals(0, session.search.slot());
        assertEquals(20, box.slots.get(0).searchTicks());
    }

    @Test void expiryWhileSearchingDoesNotReplaceAnActiveCycle() {
        var session = open();
        var previous = box.slots;
        box.refreshAt = 1;
        manager.tick();
        manager.open(player, box);
        assertSame(previous, box.slots);
        assertTrue(manager.current(session));
        assertEquals(0.5, session.search.fraction());
    }

    @Test void takingDamageAgainRestartsTheRecoveryDelay() {
        var session = open();
        manager.interrupt(player);
        for (int i = 0; i < 9; i++) manager.tick();
        manager.interrupt(player);
        assertEquals(20, session.resumeDelayTicks);
        manager.tick();
        assertFalse(session.search.active());
        for (int i = 0; i < 9; i++) manager.tick();
        assertTrue(session.search.active());
        assertEquals(0, session.search.slot());
    }
}
