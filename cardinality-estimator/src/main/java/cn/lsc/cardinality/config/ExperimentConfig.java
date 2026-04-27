package cn.lsc.cardinality.config;

public record ExperimentConfig(
        int gridCellsPerDimension,
        int denseCellThreshold,
        int sparseClusterCount,
        int leafSizeThreshold,
        int maxDepth,
        int histogramBuckets,
        double minSplitGain,
        String outputDir
) {
    public static ExperimentConfig defaultConfig() {
        return new ExperimentConfig(4, 3, 2, 6, 4, 4, 1e-6, "target/exp-results");
    }
}
