package dev.deltaloot.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SearchProgressTest {
    @Test
    void idleProgressCannotEmitACompletion() {
        SearchProgress progress = new SearchProgress();
        assertFalse(progress.active());
        assertEquals(-1, progress.slot());
        assertEquals(0, progress.fraction());
        assertTrue(progress.advance(Long.MAX_VALUE).isEmpty());
        progress.cancel();
        assertTrue(progress.advance(1).isEmpty());
    }

    @Test
    void secondStartCannotReplaceAnActiveSlotOrItsDuration() {
        SearchProgress progress = new SearchProgress();
        assertTrue(progress.start(4, 20));
        assertTrue(progress.advance(5).isEmpty());
        assertFalse(progress.start(8, 1));
        assertEquals(4, progress.slot());
        assertEquals(0.25, progress.fraction());
        assertTrue(progress.advance(14).isEmpty());
        assertEquals(OptionalInt.of(4), progress.advance(1));
    }

    @Test
    void reachingDurationEmitsOneCompletionAndClearsAllProgress() {
        SearchProgress progress = new SearchProgress();
        assertTrue(progress.start(0, 10));
        assertEquals(0, progress.fraction());
        assertTrue(progress.advance(3).isEmpty());
        assertEquals(0.3, progress.fraction(), 0.00001);
        assertEquals(OptionalInt.of(0), progress.advance(7));
        assertFalse(progress.active());
        assertEquals(-1, progress.slot());
        assertEquals(0, progress.fraction());
        assertTrue(progress.advance(10).isEmpty());
        assertTrue(progress.start(5, 1));
        assertEquals(OptionalInt.of(5), progress.advance(1));
    }

    @Test
    void overshootingDurationStillCompletesOnlyCurrentSlot() {
        SearchProgress progress = new SearchProgress();
        assertTrue(progress.start(7, 5));
        assertEquals(OptionalInt.of(7), progress.advance(100));
        assertTrue(progress.advance(100).isEmpty());
    }

    @Test
    void cancellationDiscardsAnAlmostCompleteSearch() {
        SearchProgress progress = new SearchProgress();
        assertTrue(progress.start(2, 10));
        assertTrue(progress.advance(9).isEmpty());
        progress.cancel();
        assertFalse(progress.active());
        assertEquals(-1, progress.slot());
        assertEquals(0, progress.fraction());
        assertTrue(progress.advance(1).isEmpty());
        assertTrue(progress.advance(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void cancelledSlotCannotCompleteAfterStartingADifferentSearch() {
        SearchProgress progress = new SearchProgress();
        assertTrue(progress.start(2, 10));
        progress.advance(9);
        progress.cancel();
        assertTrue(progress.start(6, 50));
        assertEquals(0, progress.fraction());
        assertTrue(progress.advance(1).isEmpty());
        assertEquals(6, progress.slot());
        assertEquals(0.02, progress.fraction(), 0.00001);
        assertEquals(OptionalInt.of(6), progress.advance(49));
    }

    @Test
    void restartingSameSlotRequiresItsEntireDurationAgain() {
        SearchProgress progress = new SearchProgress();
        progress.start(3, 20);
        progress.advance(19);
        progress.cancel();
        progress.start(3, 20);
        assertTrue(progress.advance(19).isEmpty());
        assertEquals(OptionalInt.of(3), progress.advance(1));
    }

    @Test
    void enormousElapsedAndIncrementValuesCannotOverflow() {
        SearchProgress progress = new SearchProgress();
        progress.start(9, Long.MAX_VALUE);
        assertTrue(progress.advance(Long.MAX_VALUE - 2).isEmpty());
        assertTrue(progress.active());
        assertEquals(OptionalInt.of(9), progress.advance(Long.MAX_VALUE));
        assertFalse(progress.active());
    }

    @Test
    void enormousDurationCanCompleteExactlyWithoutOverflow() {
        SearchProgress progress = new SearchProgress();
        progress.start(1, Long.MAX_VALUE);
        assertTrue(progress.advance(Long.MAX_VALUE - 1).isEmpty());
        assertEquals(OptionalInt.of(1), progress.advance(1));
    }

    @Test
    void invalidStartsLeaveIdleOrActiveStateUnchanged() {
        SearchProgress progress = new SearchProgress();
        assertThrows(IllegalArgumentException.class, () -> progress.start(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> progress.start(0, 0));
        assertThrows(IllegalArgumentException.class, () -> progress.start(0, -1));
        assertFalse(progress.active());
        progress.start(4, 10);
        progress.advance(2);
        assertThrows(IllegalArgumentException.class, () -> progress.start(-1, 1));
        assertEquals(4, progress.slot());
        assertEquals(0.2, progress.fraction(), 0.00001);
    }

    @Test
    void invalidAdvancesLeaveIdleOrActiveStateUnchanged() {
        SearchProgress progress = new SearchProgress();
        assertThrows(IllegalArgumentException.class, () -> progress.advance(0));
        assertThrows(IllegalArgumentException.class, () -> progress.advance(-1));
        progress.start(4, 10);
        assertThrows(IllegalArgumentException.class, () -> progress.advance(Long.MIN_VALUE));
        assertEquals(4, progress.slot());
        assertEquals(0, progress.fraction());
        assertEquals(OptionalInt.of(4), progress.advance(10));
    }
}
