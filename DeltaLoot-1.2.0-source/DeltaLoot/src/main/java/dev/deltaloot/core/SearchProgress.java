package dev.deltaloot.core;

import java.util.OptionalInt;

/**
 * Progress for one player's currently active slot. All access must be on the server thread.
 * Completion is emitted exactly once; cancellation discards the previous slot completely.
 */
public final class SearchProgress {
    private int slot = -1;
    private long totalTicks;
    private long elapsedTicks;

    public boolean start(int slot, long totalTicks) {
        if (slot < 0) {
            throw new IllegalArgumentException("Slot must be nonnegative");
        }
        if (totalTicks < 1) {
            throw new IllegalArgumentException("Search duration must be at least one tick");
        }
        if (active()) {
            return false;
        }
        this.slot = slot;
        this.totalTicks = totalTicks;
        this.elapsedTicks = 0;
        return true;
    }

    public OptionalInt advance(long ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("Advance must be at least one tick");
        }
        if (!active()) {
            return OptionalInt.empty();
        }
        // Subtract before comparing so even very large increments cannot overflow.
        if (ticks >= totalTicks - elapsedTicks) {
            int completedSlot = slot;
            cancel();
            return OptionalInt.of(completedSlot);
        }
        elapsedTicks += ticks;
        return OptionalInt.empty();
    }

    public void cancel() {
        slot = -1;
        totalTicks = 0;
        elapsedTicks = 0;
    }

    public boolean active() {
        return slot >= 0;
    }

    public int slot() {
        return slot;
    }

    public double fraction() {
        return active() ? elapsedTicks / (double) totalTicks : 0;
    }
}
