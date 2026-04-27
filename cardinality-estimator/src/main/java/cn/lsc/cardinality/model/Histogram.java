package cn.lsc.cardinality.model;

import cn.lsc.cardinality.data.DataPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Histogram {
    private final List<Bucket> buckets;

    private Histogram(List<Bucket> buckets) {
        this.buckets = buckets;
    }

    public static Histogram build(List<DataPoint> points, int dimension, int bucketCount) {
        List<DataPoint> sorted = new ArrayList<>(points);
        // 先按某一维的取值排序，便于做等深分桶。
        sorted.sort(Comparator.comparingDouble(point -> point.valueAt(dimension)));
        if (sorted.isEmpty()) {
            return new Histogram(List.of());
        }
        int actualBucketCount = Math.max(1, Math.min(bucketCount, sorted.size()));
        List<Bucket> buckets = new ArrayList<>(actualBucketCount);
        for (int i = 0; i < actualBucketCount; i++) {
            int start = i * sorted.size() / actualBucketCount;
            int end = ((i + 1) * sorted.size() / actualBucketCount) - 1;
            double lower = sorted.get(start).valueAt(dimension);
            double upper = sorted.get(end).valueAt(dimension);
            int count = end - start + 1;
            buckets.add(new Bucket(new HyperRectangle(new double[]{lower}, new double[]{upper}), count));
        }
        return new Histogram(buckets);
    }

    public double estimateSelectivity(double queryLower, double queryUpper, int totalCount) {
        if (buckets.isEmpty() || totalCount == 0) {
            return 0.0;
        }
        double matched = 0.0;
        for (Bucket bucket : buckets) {
            matched += bucket.overlapRatio(queryLower, queryUpper) * bucket.count();
        }
        // 选择率 = 命中样本数 / 总样本数。
        return Math.max(0.0, Math.min(1.0, matched / totalCount));
    }

    private record Bucket(HyperRectangle bounds, int count) {
        private double overlapRatio(double queryLower, double queryUpper) {
            return bounds.intersectionRatio(0, queryLower, queryUpper);
        }
    }
}
