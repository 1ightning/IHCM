package cn.lsc.cardinality.algorithm;

import cn.lsc.cardinality.data.DataPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class KMeansPartitioner {
    private KMeansPartitioner() {
    }

    public static List<Cluster> partition(List<DataPoint> points, int k, int maxIterations) {
        if (points.isEmpty() || k <= 0) {
            return List.of();
        }
        k = Math.min(k, points.size());
        Random random = new Random(42);
        // 先随机初始化 k 个中心点。
        List<double[]> centroids = initializeCentroids(points, k, random);
        Map<Integer, List<DataPoint>> clusters = new HashMap<>();
        for (int iter = 0; iter < maxIterations; iter++) {
            clusters.clear();
            for (int i = 0; i < k; i++) {
                clusters.put(i, new ArrayList<>());
            }
            boolean changed = false;
            // 把每个点分配到最近的中心。
            for (DataPoint point : points) {
                int nearest = findNearestCentroid(point, centroids);
                clusters.get(nearest).add(point);
            }
            // 根据当前聚类结果重新计算中心点。
            List<double[]> newCentroids = computeCentroids(clusters, centroids, points.get(0).dimensions());
            for (int i = 0; i < k; i++) {
                if (!centroidEquals(centroids.get(i), newCentroids.get(i))) {
                    changed = true;
                }
            }
            centroids = newCentroids;
            // 如果中心点没有变化，说明聚类已经稳定，可以提前结束。
            if (!changed) {
                break;
            }
        }
        List<Cluster> result = new ArrayList<>();
        for (Map.Entry<Integer, List<DataPoint>> entry : clusters.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(new Cluster(entry.getValue(), centroids.get(entry.getKey())));
            }
        }
        return result;
    }

    private static List<double[]> initializeCentroids(List<DataPoint> points, int k, Random random) {
        List<double[]> centroids = new ArrayList<>(k);
        centroids.add(points.get(random.nextInt(points.size())).values().clone());
        while (centroids.size() < k) {
            double[] distances = new double[points.size()];
            double sum = 0.0;
            for (int i = 0; i < points.size(); i++) {
                distances[i] = nearestDistance(points.get(i), centroids);
                sum += distances[i];
            }
            double threshold = random.nextDouble() * sum;
            double cumulative = 0.0;
            int selected = 0;
            for (int i = 0; i < points.size(); i++) {
                cumulative += distances[i];
                if (cumulative >= threshold) {
                    selected = i;
                    break;
                }
            }
            centroids.add(points.get(selected).values().clone());
        }
        return centroids;
    }

    private static double nearestDistance(DataPoint point, List<double[]> centroids) {
        double min = Double.POSITIVE_INFINITY;
        for (double[] centroid : centroids) {
            double dist = distance(point.values(), centroid);
            min = Math.min(min, dist);
        }
        return min;
    }

    private static double distance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += (a[i] - b[i]) * (a[i] - b[i]);
        }
        return sum;
    }

    private static int findNearestCentroid(DataPoint point, List<double[]> centroids) {
        int nearest = 0;
        double minDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < centroids.size(); i++) {
            double dist = distance(point.values(), centroids.get(i));
            if (dist < minDist) {
                minDist = dist;
                nearest = i;
            }
        }
        return nearest;
    }

    private static List<double[]> computeCentroids(Map<Integer, List<DataPoint>> clusters, List<double[]> previousCentroids, int dimensions) {
        List<double[]> centroids = new ArrayList<>();
        for (int i = 0; i < previousCentroids.size(); i++) {
            double[] centroid = new double[dimensions];
            List<DataPoint> cluster = clusters.get(i);
            // 如果某个簇暂时没有数据点，就保留上一次的中心。
            if (cluster == null || cluster.isEmpty()) {
                centroids.add(previousCentroids.get(i).clone());
                continue;
            }
            for (DataPoint point : cluster) {
                for (int d = 0; d < dimensions; d++) {
                    centroid[d] += point.valueAt(d);
                }
            }
            for (int d = 0; d < dimensions; d++) {
                centroid[d] /= cluster.size();
            }
            centroids.add(centroid);
        }
        return centroids;
    }

    private static boolean centroidEquals(double[] a, double[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1e-9) {
                return false;
            }
        }
        return true;
    }

    public record Cluster(List<DataPoint> points, double[] centroid) {
    }
}
