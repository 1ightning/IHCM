package cn.lsc.cardinality.data;

import java.util.Arrays;

public record RangeQuery(String name, double[] lowerBounds, double[] upperBounds) {
    public boolean contains(DataPoint point) {
        for (int i = 0; i < lowerBounds.length; i++) {
            double value = point.valueAt(i);
            if (value < lowerBounds[i] || value > upperBounds[i]) {
                return false;
            }
        }
        return true;
    }

    public int dimensions() {
        return lowerBounds.length;
    }

    @Override
    public String toString() {
        return name + " lower=" + Arrays.toString(lowerBounds) + ", upper=" + Arrays.toString(upperBounds);
    }
}
