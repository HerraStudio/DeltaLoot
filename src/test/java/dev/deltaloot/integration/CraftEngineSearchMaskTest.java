package dev.deltaloot.integration;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CraftEngineSearchMaskTest {
    @Test void copiesTheCompleteStackAndOnlyChangesCopyAmount() {
        ItemStack original = validStack();
        ItemStack copy = mock(ItemStack.class);
        when(original.clone()).thenReturn(copy);
        var provider = new CraftEngineSearchMask(mock(Logger.class), () -> original);
        assertSame(copy, provider.create());
        verify(copy).setAmount(1);
        verify(original, never()).setAmount(anyInt());
        verify(original, never()).setItemMeta(any());
        verify(copy, never()).setItemMeta(any());
    }

    @Test void modernApiBuildsBukkitItem() throws Exception {
        ItemStack stack = mock(ItemStack.class);
        assertSame(stack, CraftEngineSearchMask.buildItem(new ModernDefinition(stack)));
    }

    @Test void legacyApiBuildsItemStack() throws Exception {
        ItemStack stack = mock(ItemStack.class);
        assertSame(stack, CraftEngineSearchMask.buildItem(new LegacyDefinition(stack)));
    }

    @Test void missingItemUsesBlackGlassAndWarnsOnlyOnceWhileUnavailable() {
        Logger logger = mock(Logger.class);
        var provider = new CraftEngineSearchMask(logger, () -> { throw new IllegalStateException("missing"); });
        try (var construction = mockConstruction(ItemStack.class, (item, context) ->
                assertEquals(Material.BLACK_STAINED_GLASS_PANE, context.arguments().getFirst()))) {
            provider.create(); provider.create();
            assertEquals(2, construction.constructed().size());
            verify(logger, times(1)).warning(contains("default:search_mask"));
        }
    }

    @Test void aLaterOpenRetriesAfterCraftEngineReloadInsteadOfCachingFailure() {
        Logger logger = mock(Logger.class);
        ItemStack original = validStack();
        ItemStack copy = mock(ItemStack.class);
        when(original.clone()).thenReturn(copy);
        AtomicInteger attempts = new AtomicInteger();
        var provider = new CraftEngineSearchMask(logger, () -> attempts.incrementAndGet() == 1 ? null : original);
        try (var fallback = mockConstruction(ItemStack.class)) {
            provider.create();
            assertEquals(1, fallback.constructed().size());
            assertSame(copy, provider.create());
            assertEquals(2, attempts.get());
        }
    }

    @Test void unsupportedApiFallsBackWithoutBreakingTheSearchSession() {
        Logger logger = mock(Logger.class);
        var provider = new CraftEngineSearchMask(logger, () -> CraftEngineSearchMask.buildItem(new Object()));
        try (var fallback = mockConstruction(ItemStack.class)) {
            assertNotNull(provider.create());
            assertEquals(1, fallback.constructed().size());
            verify(logger).warning(contains("default:search_mask"));
        }
    }

    @Test void anInvalidBuilderResultIsRejected() {
        assertThrows(IllegalStateException.class, () -> CraftEngineSearchMask.buildItem(new InvalidDefinition()));
    }

    private static ItemStack validStack() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(mock(Material.class));
        when(item.getAmount()).thenReturn(64);
        return item;
    }
    public record ModernDefinition(ItemStack item) {
        public ItemStack buildBukkitItem() { return item; }
    }
    public record LegacyDefinition(ItemStack item) {
        public ItemStack buildItemStack() { return item; }
    }
    public static class InvalidDefinition {
        public Object buildBukkitItem() { return "not an item"; }
    }
}
