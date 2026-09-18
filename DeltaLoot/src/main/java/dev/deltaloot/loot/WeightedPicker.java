package dev.deltaloot.loot;

import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

/** Immutable weighted selection with replacement, independent of Bukkit. */
public final class WeightedPicker<T> {
    private final List<T> values;
    private final double[] cumulativeWeights;
    private final double totalWeight;

    public WeightedPicker(List<T> values, ToDoubleFunction<? super T> weightFunction) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(weightFunction, "weightFunction");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("At least one weighted value is required");
        }
        this.values = List.copyOf(values);
        this.cumulativeWeights = new double[values.size()];
        double total = 0;
        for (int i = 0; i < values.size(); i++) {
            double weight = weightFunction.applyAsDouble(this.values.get(i));
            if (!Double.isFinite(weight) || weight <= 0) {
                throw new IllegalArgumentException("Weight at index " + i + " must be finite and positive");
            }
            double next = total + weight;
            if (!Double.isFinite(next) || next <= total) {
                throw new IllegalArgumentException("Weights exceed usable numeric precision at index " + i);
            }
            total = next;
            cumulativeWeights[i] = total;
        }
        this.totalWeight = total;
    }

    public T pick(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        double target = random.nextDouble(totalWeight);
        int low = 0;
        int high = cumulativeWeights.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (target < cumulativeWeights[mid]) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return values.get(low);
    }
}
