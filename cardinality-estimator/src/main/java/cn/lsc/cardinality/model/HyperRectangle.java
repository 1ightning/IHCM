package cn.lsc.cardinality.model;

import java.util.Arrays;

public record HyperRectangle(double[] lowerBounds, double[] upperBounds) {
    public int dimensions() {
        return lowerBounds.length;
    }

    public boolean intersects(HyperRectangle other) {
        for (int i = 0; i < lowerBounds.length; i++) {
            if (upperBounds[i] < other.lowerBounds[i] || lowerBounds[i] > other.upperBounds[i]) {
                return false;
            }
        }
        return true;
    }

    public boolean disjoint(HyperRectangle other) {
        return !intersects(other);
    }

    /** 判定 this 是否完整包含 other。 */
    public boolean contains(HyperRectangle other) {
        for (int i = 0; i < lowerBounds.length; i++) {
            if (other.lowerBounds[i] < lowerBounds[i] || other.upperBounds[i] > upperBounds[i]) {
                return false;
            }
        }
        return true;
    }

    /** 计算两个矩形的交集。若不相交则返回 null。 */
    public HyperRectangle intersection(HyperRectangle other) {
        int d = lowerBounds.length;
        double[] lo = new double[d];
        double[] hi = new double[d];
        for (int i = 0; i < d; i++) {
            lo[i] = Math.max(lowerBounds[i], other.lowerBounds[i]);
            hi[i] = Math.min(upperBounds[i], other.upperBounds[i]);
            if (hi[i] < lo[i]) {
                return null;
            }
        }
        return new HyperRectangle(lo, hi);
    }

    public double intersectionRatio(int dimension, double queryLower, double queryUpper) {
        double lower = Math.max(lowerBounds[dimension], queryLower);
        double upper = Math.min(upperBounds[dimension], queryUpper);
        if (upper < lower) {
            return 0.0;
        }
        double width = upperBounds[dimension] - lowerBounds[dimension];
        if (width <= 0.0) {
            return (queryLower <= lowerBounds[dimension] && queryUpper >= upperBounds[dimension]) ? 1.0 : 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (upper - lower) / width));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HyperRectangle other)) return false;
        return Arrays.equals(lowerBounds, other.lowerBounds) && Arrays.equals(upperBounds, other.upperBounds);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(lowerBounds) + Arrays.hashCode(upperBounds);
    }

    @Override
    public String toString() {
        return "HR(lo=" + Arrays.toString(lowerBounds) + ", hi=" + Arrays.toString(upperBounds) + ")";
    }
}
