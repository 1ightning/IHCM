package cn.lsc.cardinality.algorithm;

import cn.lsc.cardinality.config.ExperimentConfig;
import cn.lsc.cardinality.data.DataPoint;
import cn.lsc.cardinality.model.HyperRectangle;
import cn.lsc.cardinality.model.RegionNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 基于轴向二分法的区域递归划分（论文算法 3）。
 * 使用 Welford 在线统计量对每个候选切分点做 O(1) 递推，
 * 切分目标为最小化切分后两个子区域的“协方差矩阵的迹”加权和。
 */
public final class AxialBinarySplitter {
    private AxialBinarySplitter() {
    }

    public static RegionNode buildTree(List<DataPoint> points, double[] minValues, double[] maxValues, ExperimentConfig config) {
        HyperRectangle bounds = new HyperRectangle(minValues.clone(), maxValues.clone());
        RegionNode root = new RegionNode(bounds, points, 0);
        splitRecursively(root, config);
        return root;
    }

    private static void splitRecursively(RegionNode node, ExperimentConfig config) {
        if (shouldStop(node, config)) {
            return;
        }
        SplitResult result = findBestSplit(node.points(), node.bounds(), config);
        if (result == null || !result.isWorthwhile(config.minSplitGain())) {
            return;
        }
        List<DataPoint> leftPoints = new ArrayList<>();
        List<DataPoint> rightPoints = new ArrayList<>();
        for (DataPoint p : node.points()) {
            if (p.valueAt(result.dimension()) <= result.threshold()) {
                leftPoints.add(p);
            } else {
                rightPoints.add(p);
            }
        }
        if (leftPoints.isEmpty() || rightPoints.isEmpty()) {
            return;
        }

        double[] lowerLeft = node.bounds().lowerBounds().clone();
        double[] upperLeft = node.bounds().upperBounds().clone();
        double[] lowerRight = node.bounds().lowerBounds().clone();
        double[] upperRight = node.bounds().upperBounds().clone();
        upperLeft[result.dimension()] = result.threshold();
        lowerRight[result.dimension()] = result.threshold();

        RegionNode leftChild = new RegionNode(new HyperRectangle(lowerLeft, upperLeft), leftPoints, node.depth() + 1);
        RegionNode rightChild = new RegionNode(new HyperRectangle(lowerRight, upperRight), rightPoints, node.depth() + 1);
        node.setChildren(leftChild, rightChild);
        splitRecursively(leftChild, config);
        splitRecursively(rightChild, config);
    }

    private static boolean shouldStop(RegionNode node, ExperimentConfig config) {
        return node.points().size() <= config.leafSizeThreshold() || node.depth() >= config.maxDepth();
    }

    private static SplitResult findBestSplit(List<DataPoint> points, HyperRectangle bounds, ExperimentConfig config) {
        int n = points.size();
        if (n < 2) {
            return null;
        }
        int dimensions = bounds.dimensions();

        // 使用 Welford 统计整体节点的方差，作为划分增益比较基线。
        WelfordStats parentStats = new WelfordStats(dimensions);
        for (DataPoint p : points) {
            parentStats.push(p);
        }
        double parentVariance = parentStats.traceOfCovariance();

        double bestScore = Double.POSITIVE_INFINITY;
        int bestDimension = -1;
        double bestThreshold = Double.NaN;

        int candidateBuckets = Math.max(2, Math.min(config.histogramBuckets(), n));

        for (int d = 0; d < dimensions; d++) {
            if (bounds.upperBounds()[d] - bounds.lowerBounds()[d] <= 0) {
                continue;
            }
            int dim = d;
            List<DataPoint> sorted = new ArrayList<>(points);
            sorted.sort(Comparator.comparingDouble(p -> p.valueAt(dim)));

            WelfordStats left = new WelfordStats(dimensions);
            WelfordStats right = new WelfordStats(dimensions);
            for (DataPoint p : sorted) {
                right.push(p);
            }

            // 按等深分位 k*n/buckets 依次作为候选切点。
            int cursor = 0;
            for (int k = 1; k < candidateBuckets; k++) {
                int targetIndex = (int) ((long) k * n / candidateBuckets);
                if (targetIndex <= cursor || targetIndex >= n) continue;
                while (cursor < targetIndex) {
                    DataPoint moving = sorted.get(cursor);
                    left.push(moving);
                    right.pop(moving);
                    cursor++;
                }
                if (left.size() == 0 || right.size() == 0) continue;

                double leftVar = left.traceOfCovariance();
                double rightVar = right.traceOfCovariance();
                double weighted = (left.size() * leftVar + right.size() * rightVar) / (double) n;

                double threshold = sorted.get(targetIndex - 1).valueAt(dim);
                if (threshold <= bounds.lowerBounds()[dim] || threshold >= bounds.upperBounds()[dim]) {
                    continue;
                }
                if (weighted < bestScore) {
                    bestScore = weighted;
                    bestDimension = dim;
                    bestThreshold = threshold;
                }
            }
        }

        if (bestDimension < 0) {
            return null;
        }
        return new SplitResult(bestDimension, bestThreshold, bestScore, parentVariance);
    }

    /** Welford 在线方差统计，支持正向插入与反向剔除，复杂度 O(1) 每步。 */
    private static final class WelfordStats {
        private final int dimensions;
        private final double[] mean;
        private final double[] m2;
        private int n;

        WelfordStats(int dimensions) {
            this.dimensions = dimensions;
            this.mean = new double[dimensions];
            this.m2 = new double[dimensions];
        }

        void push(DataPoint point) {
            n++;
            for (int d = 0; d < dimensions; d++) {
                double x = point.valueAt(d);
                double delta = x - mean[d];
                mean[d] += delta / n;
                double delta2 = x - mean[d];
                m2[d] += delta * delta2;
            }
        }

        void pop(DataPoint point) {
            if (n == 0) return;
            if (n == 1) {
                n = 0;
                for (int d = 0; d < dimensions; d++) {
                    mean[d] = 0.0;
                    m2[d] = 0.0;
                }
                return;
            }
            for (int d = 0; d < dimensions; d++) {
                double x = point.valueAt(d);
                double oldMean = mean[d];
                double newMean = (oldMean * n - x) / (n - 1);
                m2[d] -= (x - oldMean) * (x - newMean);
                if (m2[d] < 0) m2[d] = 0.0;
                mean[d] = newMean;
            }
            n--;
        }

        int size() {
            return n;
        }

        double traceOfCovariance() {
            if (n <= 1) return 0.0;
            double sum = 0.0;
            for (int d = 0; d < dimensions; d++) {
                sum += m2[d] / n;
            }
            return sum;
        }
    }

    private record SplitResult(int dimension, double threshold, double score, double originalVariance) {
        public boolean isWorthwhile(double minGain) {
            return originalVariance - score >= minGain;
        }
    }
}
