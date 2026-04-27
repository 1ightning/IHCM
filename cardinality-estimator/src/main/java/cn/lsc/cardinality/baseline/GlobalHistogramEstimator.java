package cn.lsc.cardinality.baseline;

import cn.lsc.cardinality.data.DataPoint;
import cn.lsc.cardinality.data.DataSet;
import cn.lsc.cardinality.data.RangeQuery;
import cn.lsc.cardinality.model.HyperRectangle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GlobalHistogramEstimator implements CardinalityEstimator {
    private final String name;
    private final int bucketCount;
    private final boolean equiDepth;
    private DataSet dataSet;
    // 每一维都保存一组桶，形成全局直方图。
    private List<List<Bucket>> bucketsByDimension;

    public GlobalHistogramEstimator(String name, int bucketCount, boolean equiDepth) {
        this.name = name;
        this.bucketCount = bucketCount;
        this.equiDepth = equiDepth;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void fit(DataSet dataSet) {
        this.dataSet = dataSet;
        this.bucketsByDimension = new ArrayList<>();
        for (int d = 0; d < dataSet.dimensions(); d++) {
            bucketsByDimension.add(equiDepth ? buildEquiDepth(dataSet.points(), d) : buildEquiWidth(dataSet, d));
        }
    }

    @Override
    public double estimate(RangeQuery query) {
        double selectivity = 1.0;
        // 这里仍然采用“各维独立”的近似思想，把每一维选择率相乘。
        for (int d = 0; d < dataSet.dimensions(); d++) {
            selectivity *= estimateSelectivity(bucketsByDimension.get(d), query.lowerBounds()[d], query.upperBounds()[d], dataSet.size());
        }
        return Math.max(0.0, selectivity * dataSet.size());
    }

    private List<Bucket> buildEquiDepth(List<DataPoint> points, int dimension) {
        List<DataPoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(point -> point.valueAt(dimension)));
        int actualBuckets = Math.max(1, Math.min(bucketCount, sorted.size()));
        List<Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < actualBuckets; i++) {
            int start = i * sorted.size() / actualBuckets;
            int end = ((i + 1) * sorted.size() / actualBuckets) - 1;
            buckets.add(new Bucket(new HyperRectangle(new double[]{sorted.get(start).valueAt(dimension)}, new double[]{sorted.get(end).valueAt(dimension)}), end - start + 1));
        }
        return buckets;
    }

    private List<Bucket> buildEquiWidth(DataSet dataSet, int dimension) {
        double min = dataSet.minValues()[dimension];
        double max = dataSet.maxValues()[dimension];
        double width = (max - min) / bucketCount;
        if (width <= 0.0) {
            return List.of(new Bucket(new HyperRectangle(new double[]{min}, new double[]{max}), dataSet.size()));
        }
        int[] counts = new int[bucketCount];
        for (DataPoint point : dataSet.points()) {
            int index = (int) ((point.valueAt(dimension) - min) / width);
            index = Math.max(0, Math.min(bucketCount - 1, index));
            counts[index]++;
        }
        List<Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            double lower = min + i * width;
            double upper = i == bucketCount - 1 ? max : lower + width;
            buckets.add(new Bucket(new HyperRectangle(new double[]{lower}, new double[]{upper}), counts[i]));
        }
        return buckets;
    }

    private double estimateSelectivity(List<Bucket> buckets, double queryLower, double queryUpper, int totalCount) {
        double matched = 0.0;
        for (Bucket bucket : buckets) {
            matched += bucket.overlapRatio(queryLower, queryUpper) * bucket.count();
        }
        return totalCount == 0 ? 0.0 : Math.min(1.0, Math.max(0.0, matched / totalCount));
    }

    private record Bucket(HyperRectangle bounds, int count) {
        private double overlapRatio(double queryLower, double queryUpper) {
            return bounds.intersectionRatio(0, queryLower, queryUpper);
        }
    }
}
