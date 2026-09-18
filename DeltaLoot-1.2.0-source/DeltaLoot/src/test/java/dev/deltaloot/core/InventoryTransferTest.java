package dev.deltaloot.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.IdentityHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class InventoryTransferTest {
    private final Map<ItemStack, StackState> states = new IdentityHashMap<>();
    private final Material itemMaterial = mock(Material.class);

    @Test
    void mergesPartialStackBeforeUsingEmptySpaceAndLeavesInputsUntouched() {
        ItemStack partial = stack("common", 60, 64);
        ItemStack unrelated = stack("other", 64, 64);
        ItemStack reward = stack("common", 10, 64);
        ItemStack[] storage = {partial, unrelated, null};

        ItemStack[] planned = InventoryTransfer.plan(storage, reward, 64).orElseThrow();

        assertEquals(3, planned.length);
        assertEquals(64, planned[0].getAmount());
        assertEquals(64, planned[1].getAmount());
        assertEquals(6, planned[2].getAmount());
        assertTrue(planned[2].isSimilar(reward));
        assertNotSame(partial, planned[0]);
        assertNotSame(unrelated, planned[1]);
        assertNotSame(reward, planned[2]);
        assertEquals(60, partial.getAmount());
        assertEquals(64, unrelated.getAmount());
        assertEquals(10, reward.getAmount());
        assertEquals(null, storage[2]);
    }

    @Test
    void succeedsWithNoEmptySlotsWhenExistingStacksHaveEnoughCombinedSpace() {
        ItemStack first = stack("common", 58, 64);
        ItemStack second = stack("common", 60, 64);
        ItemStack reward = stack("common", 10, 64);

        ItemStack[] planned = InventoryTransfer.plan(new ItemStack[] {first, second}, reward, 64)
                .orElseThrow();

        assertEquals(64, planned[0].getAmount());
        assertEquals(64, planned[1].getAmount());
        assertEquals(58, first.getAmount());
        assertEquals(60, second.getAmount());
        assertEquals(10, reward.getAmount());
    }

    @Test
    void insufficientSpaceDoesNotPartiallyChangeInventoryOrReward() {
        ItemStack partial = stack("common", 63, 64);
        ItemStack unrelated = stack("other", 64, 64);
        ItemStack reward = stack("common", 2, 64);
        ItemStack[] storage = {partial, unrelated};

        assertTrue(InventoryTransfer.plan(storage, reward, 64).isEmpty());

        assertEquals(63, partial.getAmount());
        assertEquals(64, unrelated.getAmount());
        assertEquals(2, reward.getAmount());
        assertTrue(storage[0] == partial && storage[1] == unrelated);
    }

    @Test
    void nonStackableItemsUseSeparateEmptySlots() {
        ItemStack existing = stack("tool", 1, 1);
        ItemStack reward = stack("tool", 2, 1);

        ItemStack[] planned = InventoryTransfer.plan(new ItemStack[] {existing, null, null}, reward, 64)
                .orElseThrow();

        assertEquals(1, planned[0].getAmount());
        assertEquals(1, planned[1].getAmount());
        assertEquals(1, planned[2].getAmount());
        assertNotSame(planned[1], planned[2]);
        assertEquals(1, existing.getAmount());
        assertEquals(2, reward.getAmount());
    }

    @Test
    void sameMaterialWithDifferentMetadataCannotMerge() {
        ItemStack ordinary = stack("ordinary-quality", 30, 64);
        ItemStack reward = stack("rare-quality", 5, 64);
        assertFalse(ordinary.isSimilar(reward));

        ItemStack[] planned = InventoryTransfer.plan(new ItemStack[] {ordinary, null}, reward, 64)
                .orElseThrow();

        assertEquals(30, planned[0].getAmount());
        assertEquals(5, planned[1].getAmount());
        assertTrue(planned[0].isSimilar(ordinary));
        assertTrue(planned[1].isSimilar(reward));
        assertTrue(InventoryTransfer.plan(new ItemStack[] {ordinary}, reward, 64).isEmpty());
        assertEquals(30, ordinary.getAmount());
        assertEquals(5, reward.getAmount());
    }

    @Test
    void lowerInventoryMaximumLimitsBothMergedAndNewStacks() {
        ItemStack partial = stack("common", 12, 64);
        ItemStack reward = stack("common", 20, 64);

        ItemStack[] planned = InventoryTransfer.plan(new ItemStack[] {partial, null}, reward, 16)
                .orElseThrow();

        assertEquals(16, planned[0].getAmount());
        assertEquals(16, planned[1].getAmount());
        assertEquals(12, partial.getAmount());
        assertEquals(20, reward.getAmount());
    }

    @Test
    void airStacksAreUsableAsEmptySlots() {
        Material airMaterial = mock(Material.class);
        when(airMaterial.isAir()).thenReturn(true);
        ItemStack air = stack("air", 0, 64, airMaterial);
        ItemStack reward = stack("common", 4, 64);

        ItemStack[] planned = InventoryTransfer.plan(new ItemStack[] {air}, reward, 64).orElseThrow();

        assertEquals(4, planned[0].getAmount());
        assertTrue(planned[0].isSimilar(reward));
        assertEquals(0, air.getAmount());
        assertEquals(4, reward.getAmount());
    }

    @Test
    void returnedStacksAreIndependentOfInputsAndOfEachOther() {
        ItemStack reward = stack("common", 20, 64);
        ItemStack[] planned = InventoryTransfer.plan(new ItemStack[] {null, null, null}, reward, 8)
                .orElseThrow();
        assertEquals(8, planned[0].getAmount());
        assertEquals(8, planned[1].getAmount());
        assertEquals(4, planned[2].getAmount());

        planned[0].setAmount(1);

        assertEquals(20, reward.getAmount());
        assertEquals(8, planned[1].getAmount());
        assertEquals(4, planned[2].getAmount());
    }

    private ItemStack stack(String metadata, int amount, int maxStackSize) {
        return stack(metadata, amount, maxStackSize, itemMaterial);
    }

    /** Every clone owns its quantity; only immutable metadata and material identity are shared. */
    private ItemStack stack(String metadata, int amount, int maxStackSize, Material material) {
        StackState state = new StackState(metadata, amount, maxStackSize, material);
        ItemStack item = mock(ItemStack.class, invocation -> switch (invocation.getMethod().getName()) {
            case "getType" -> state.material;
            case "getAmount" -> state.amount;
            case "getMaxStackSize" -> state.maxStackSize;
            case "setAmount" -> {
                state.amount = invocation.getArgument(0, Integer.class);
                yield null;
            }
            case "clone" -> stack(state.metadata, state.amount, state.maxStackSize, state.material);
            case "isSimilar" -> {
                StackState other = states.get(invocation.getArgument(0));
                yield other != null && state.material == other.material
                        && state.maxStackSize == other.maxStackSize
                        && state.metadata.equals(other.metadata);
            }
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        states.put(item, state);
        return item;
    }

    private static final class StackState {
        private final String metadata;
        private int amount;
        private final int maxStackSize;
        private final Material material;

        private StackState(String metadata, int amount, int maxStackSize, Material material) {
            this.metadata = metadata;
            this.amount = amount;
            this.maxStackSize = maxStackSize;
            this.material = material;
        }
    }
}
