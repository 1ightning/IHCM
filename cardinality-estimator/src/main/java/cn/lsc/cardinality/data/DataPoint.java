package cn.lsc.cardinality.data;

import java.util.Arrays;

public record DataPoint(double[] values) {
    public int dimensions() {
        return values.length;
    }

    public double valueAt(int index) {
        return values[index];
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
