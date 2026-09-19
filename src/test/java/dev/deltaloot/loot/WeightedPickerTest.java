package dev.deltaloot.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WeightedPickerTest {
    private record Value(String name, double weight) {}

    @Test
    void boundariesUseHalfOpenWeightIntervals() {
        WeightedPicker<Value> picker = picker(1, 2, 1);
        assertEquals("0", picker.pick(atFraction(0)).name());
        assertEquals("0", picker.pick(atFraction(Math.nextDown(0.25))).name());
        assertEquals("1", picker.pick(atFraction(0.25)).name());
        assertEquals("1", picker.pick(atFraction(Math.nextDown(0.75))).name());
        assertEquals("2", picker.pick(atFraction(0.75)).name());
        assertEquals("2", picker.pick(atFraction(Math.nextDown(1.0))).name());
    }

    @Test
    void seededSampleMatchesRelativeWeights() {
        WeightedPicker<Value> picker = picker(1, 3, 6);
        Random random = new Random(20260917L);
        int[] counts = new int[3];
        int samples = 100_000;
        for (int i = 0; i < samples; i++) {
            counts[Integer.parseInt(picker.pick(random).name())]++;
        }
        // Wide deterministic tolerance detects unweighted selection without a flaky statistical test.
        assertEquals(0.1, counts[0] / (double) samples, 0.01);
        assertEquals(0.3, counts[1] / (double) samples, 0.01);
        assertEquals(0.6, counts[2] / (double) samples, 0.01);
    }

    @Test
    void relativeScaleDoesNotChangeSelections() {
        WeightedPicker<Value> original = picker(1, 3, 6);
        WeightedPicker<Value> scaled = picker(10, 30, 60);
        Random random = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            double fraction = random.nextDouble();
            assertEquals(original.pick(atFraction(fraction)).name(), scaled.pick(atFraction(fraction)).name());
        }
    }

    @Test
    void singletonAlwaysWinsAndSelectionUsesReplacement() {
        Value value = new Value("only", 0.125);
        WeightedPicker<Value> picker = new WeightedPicker<>(List.of(value), Value::weight);
        Random random = new Random(7);
        for (int i = 0; i < 100; i++) {
            assertEquals(value, picker.pick(random));
        }
    }

    @Test
    void sourceMutationCannotChangePicker() {
        List<Value> values = new ArrayList<>(List.of(new Value("kept", 1)));
        WeightedPicker<Value> picker = new WeightedPicker<>(values, Value::weight);
        values.clear();
        values.add(new Value("replacement", 100));
        assertEquals("kept", picker.pick(new Random(1)).name());
    }

    @Test
    void rejectsEmptyInputsAndInvalidWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedPicker<Value>(List.of(), Value::weight));
        for (double weight : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> picker(weight));
            assertTrue(exception.getMessage().contains("finite and positive"));
        }
    }

    @Test
    void rejectsOverflowAndWeightsLostToPrecision() {
        assertThrows(IllegalArgumentException.class, () -> picker(Double.MAX_VALUE, Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> picker(1e100, 1));
    }

    @Test
    void rejectsNullValuesAndRandom() {
        assertThrows(NullPointerException.class, () -> new WeightedPicker<Value>(null, Value::weight));
        assertThrows(NullPointerException.class, () -> new WeightedPicker<>(List.of(new Value("a", 1)), null));
        assertThrows(NullPointerException.class, () -> picker(1).pick(null));
    }

    private static WeightedPicker<Value> picker(double... weights) {
        List<Value> values = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            values.add(new Value(Integer.toString(i), weights[i]));
        }
        return new WeightedPicker<>(values, Value::weight);
    }

    private static Random atFraction(double fraction) {
        return new Random(0) {
            @Override
            public double nextDouble(double bound) {
                return fraction * bound;
            }
        };
    }
}
