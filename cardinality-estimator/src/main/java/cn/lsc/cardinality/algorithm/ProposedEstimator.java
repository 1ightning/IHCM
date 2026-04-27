package cn.lsc.cardinality.algorithm;

import cn.lsc.cardinality.baseline.CardinalityEstimator;
import cn.lsc.cardinality.config.ExperimentConfig;
import cn.lsc.cardinality.data.DataPoint;
import cn.lsc.cardinality.data.DataSet;
import cn.lsc.cardinality.data.RangeQuery;
import cn.lsc.cardinality.model.Histogram;
import cn.lsc.cardinality.model.HyperRectangle;
import cn.lsc.cardinality.model.MacroCluster;
import cn.lsc.cardinality.model.RegionNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProposedEstimator implements CardinalityEstimator {
    private final ExperimentConfig config;
    private DataSet dataSet;
    private final List<MacroCluster> clusters = new ArrayList<>();
    private Hrdag hrdag;

    public ProposedEstimator(ExperimentConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "ProposedCEDEstimator";
    }

    @Override
    public void fit(DataSet dataSet) {
        this.dataSet = dataSet;
        this.clusters.clear();

        // --- 阶段 1：两阶段宏观数据空间离散化（对应算法 1） ---
        GridDensityStrategy.GridView view = GridDensityStrategy.gridDiscretization(
                dataSet.points(), dataSet.minValues(), dataSet.maxValues(), config);
        List<GridDensityStrategy.Cell> denseCells =
                GridDensityStrategy.extractDenseCells(view, dataSet.size(), config);

        List<List<GridDensityStrategy.Cell>> components =
                GridDensityStrategy.connectedComponents(denseCells, view);

        // 每个稠密连通分量 → 一个宏观簇（点由其所属网格唯一归属，避免重复计数）。
        for (List<GridDensityStrategy.Cell> component : components) {
            List<DataPoint> componentPoints = new ArrayList<>();
            for (GridDensityStrategy.Cell cell : component) {
                componentPoints.addAll(cell.points());
            }
            if (componentPoints.isEmpty()) continue;
            HyperRectangle mbb = computeMbb(componentPoints, dataSet.dimensions());
            clusters.add(new MacroCluster(componentPoints, mbb, true));
        }

        // K-means 对未被稠密网格覆盖的稀疏样本做补充划分。
        Set<GridDensityStrategy.Cell> covered = new HashSet<>(denseCells);
        List<DataPoint> sparsePoints = GridDensityStrategy.sparsePoints(view, covered);
        if (!sparsePoints.isEmpty() && config.sparseClusterCount() > 0) {
            List<KMeansPartitioner.Cluster> kmeansClusters =
                    KMeansPartitioner.partition(sparsePoints, config.sparseClusterCount(), 20);
            for (KMeansPartitioner.Cluster c : kmeansClusters) {
                if (c.points().isEmpty()) continue;
                HyperRectangle mbb = computeMbb(c.points(), dataSet.dimensions());
                clusters.add(new MacroCluster(c.points(), mbb, false));
            }
        }

        // 退化兜底：如果没有形成任何宏观簇，就把全量数据视为一个簇。
        if (clusters.isEmpty()) {
            HyperRectangle mbb = new HyperRectangle(dataSet.minValues().clone(), dataSet.maxValues().clone());
            clusters.add(new MacroCluster(new ArrayList<>(dataSet.points()), mbb, false));
        }

        // --- 阶段 2：构建 HRDAG（对应算法 2） ---
        HyperRectangle universe = new HyperRectangle(dataSet.minValues().clone(), dataSet.maxValues().clone());
        this.hrdag = Hrdag.build(clusters, universe);

        // --- 阶段 3：在每个宏观簇的 MBB 内做轴向二分递归划分（算法 3），并在叶子建立 1D 直方图。 ---
        for (MacroCluster cluster : clusters) {
            RegionNode root = AxialBinarySplitter.buildTree(
                    cluster.points(),
                    cluster.mbb().lowerBounds(),
                    cluster.mbb().upperBounds(),
                    config);
            attachHistograms(root);
            cluster.setAxialRoot(root);
        }
    }

    @Override
    public double estimate(RangeQuery query) {
        if (hrdag == null) return 0.0;
        // 通过 HRDAG 路由剪枝，命中与查询矩形相交的原始宏观簇。
        List<MacroCluster> hit = hrdag.route(query);
        double estimate = 0.0;
        for (MacroCluster cluster : hit) {
            RegionNode root = cluster.axialRoot();
            if (root != null) {
                estimate += estimateNode(root, query);
            }
        }
        return estimate;
    }

    private double estimateNode(RegionNode node, RangeQuery query) {
        HyperRectangle queryRect = new HyperRectangle(query.lowerBounds(), query.upperBounds());
        if (!node.bounds().intersects(queryRect)) {
            return 0.0;
        }
        if (node.isLeaf()) {
            int leafSize = node.points().size();
            if (leafSize == 0) return 0.0;
            // 局部相关性已被削弱，叶内采用一维直方图连乘近似局部联合选择率（公式 12-14）。
            double selectivity = 1.0;
            for (int d = 0; d < query.dimensions(); d++) {
                selectivity *= node.histograms().get(d)
                        .estimateSelectivity(query.lowerBounds()[d], query.upperBounds()[d], leafSize);
            }
            return selectivity * leafSize;
        }
        return estimateNode(node.left(), query) + estimateNode(node.right(), query);
    }

    private void attachHistograms(RegionNode node) {
        if (node.isLeaf()) {
            List<Histogram> histograms = new ArrayList<>();
            for (int d = 0; d < dataSet.dimensions(); d++) {
                histograms.add(Histogram.build(node.points(), d, config.histogramBuckets()));
            }
            node.setHistograms(histograms);
            return;
        }
        attachHistograms(node.left());
        attachHistograms(node.right());
    }

    private HyperRectangle computeMbb(List<DataPoint> points, int dimensions) {
        double[] min = new double[dimensions];
        double[] max = new double[dimensions];
        for (int d = 0; d < dimensions; d++) {
            min[d] = Double.POSITIVE_INFINITY;
            max[d] = Double.NEGATIVE_INFINITY;
        }
        for (DataPoint point : points) {
            for (int d = 0; d < dimensions; d++) {
                min[d] = Math.min(min[d], point.valueAt(d));
                max[d] = Math.max(max[d], point.valueAt(d));
            }
        }
        return new HyperRectangle(min, max);
    }

    public List<MacroCluster> clusters() {
        return clusters;
    }

    public Hrdag hrdag() {
        return hrdag;
    }
}
