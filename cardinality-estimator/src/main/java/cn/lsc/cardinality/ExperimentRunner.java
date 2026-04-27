package cn.lsc.cardinality;

import cn.lsc.cardinality.algorithm.ProposedEstimator;
import cn.lsc.cardinality.baseline.CardinalityEstimator;
import cn.lsc.cardinality.baseline.GlobalHistogramEstimator;
import cn.lsc.cardinality.baseline.IndependenceEstimator;
import cn.lsc.cardinality.config.ExperimentConfig;
import cn.lsc.cardinality.data.DataSet;
import cn.lsc.cardinality.data.RangeQuery;
import cn.lsc.cardinality.util.MemoryProbe;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class ExperimentRunner {
    private static final String OVERALL_KEY = "__overall__";

    private final String datasetName;
    private final DataSet dataSet;
    private final Map<String, List<RangeQuery>> queryGroups;
    private final List<EstimatorSpec> estimatorSpecs;
    private final ExperimentConfig config;
    private final Map<String, Double> actualCardinalities;

    public ExperimentRunner(String datasetName, DataSet dataSet, Map<String, List<RangeQuery>> queryGroups, ExperimentConfig config) {
        this.datasetName = datasetName;
        this.dataSet = dataSet;
        this.queryGroups = new LinkedHashMap<>(queryGroups);
        this.config = config;
        this.estimatorSpecs = List.of(
                new EstimatorSpec("ProposedCEDEstimator", () -> new ProposedEstimator(config)),
                new EstimatorSpec("GlobalEquiWidthHistogram", () -> new GlobalHistogramEstimator("GlobalEquiWidthHistogram", 8, false)),
                new EstimatorSpec("GlobalEquiDepthHistogram", () -> new GlobalHistogramEstimator("GlobalEquiDepthHistogram", 8, true)),
                new EstimatorSpec("IndependenceEstimator", IndependenceEstimator::new)
        );
        this.actualCardinalities = computeActualCardinalities(allQueries());
    }

    // 兼容老入口：如果只给扁平查询列表，视作单组 "all"。
    public ExperimentRunner(DataSet dataSet, List<RangeQuery> queries, ExperimentConfig config) {
        this("default", dataSet, Map.of("all", queries), config);
    }

    public List<RunRecord> run() {
        List<RunRecord> allRecords = new ArrayList<>();
        Map<String, RunMetrics> metricsByMethod = new LinkedHashMap<>();
        int totalQueryCount = totalQueryCount();

        for (EstimatorSpec spec : estimatorSpecs) {
            // 每个估计器都重新创建，避免已经训练好的模型常驻内存影响下一组 heap delta。
            CardinalityEstimator estimator = spec.create();
            // 这里的堆内存增量是 JVM 级近似值，用于论文中的轻量性对比，不等同于精确对象大小。
            long heapBeforeFit = MemoryProbe.usedHeapAfterGc();
            long start = System.nanoTime();
            estimator.fit(dataSet);
            double fitTime = (System.nanoTime() - start) / 1_000_000.0;
            long heapAfterFit = MemoryProbe.usedHeapAfterGc();
            long heapDelta = heapAfterFit - heapBeforeFit;

            System.out.println("================================================================================");
            System.out.println("数据集: " + datasetName + " | 估计器: " + estimator.name());
            System.out.println("训练耗时: " + String.format(Locale.ROOT, "%.2f", fitTime) + " ms");

            List<PartialRunRecord> estimatorRecords = new ArrayList<>();
            long inferTotalNanos = 0L;
            for (Map.Entry<String, List<RangeQuery>> entry : queryGroups.entrySet()) {
                String group = entry.getKey();
                List<RangeQuery> queries = entry.getValue();
                System.out.println("--------------------------------------------------------------------------------");
                System.out.println("分组: " + group + "  (" + queries.size() + " 条查询)");
                List<Double> qErrors = new ArrayList<>();
                int infCount = 0;
                for (RangeQuery query : queries) {
                    double actual = actualCardinalities.get(query.name());
                    // 推理计时只覆盖在线 estimate 调用，Q-Error 统计和日志输出不计入查询延迟。
                    long inferStart = System.nanoTime();
                    double estimated = estimator.estimate(query);
                    inferTotalNanos += System.nanoTime() - inferStart;
                    double qError = computeQError(actual, estimated);
                    double relErr = computeRelativeError(actual, estimated);
                    if (Double.isInfinite(qError)) {
                        infCount++;
                    }
                    qErrors.add(qError);
                    estimatorRecords.add(new PartialRunRecord(datasetName, group, query.name(), estimator.name(), actual, estimated, relErr, qError));
                }
                QErrorStats stats = QErrorStats.from(qErrors);
                System.out.printf(Locale.ROOT, "median=%.3f  mean=%.3f  p90=%.3f  p95=%.3f  max=%s  inf=%d%n",
                        stats.median(), stats.mean(), stats.p90(), stats.p95(),
                        Double.isInfinite(stats.max()) ? "inf" : String.format(Locale.ROOT, "%.3f", stats.max()),
                        infCount);
            }
            double inferTotalMs = inferTotalNanos / 1_000_000.0;
            RunMetrics metrics = RunMetrics.from(fitTime, inferTotalMs, totalQueryCount, heapDelta);
            metricsByMethod.put(estimator.name(), metrics);
            for (PartialRunRecord r : estimatorRecords) {
                allRecords.add(new RunRecord(r.dataset(), r.group(), r.queryName(), r.method(),
                        r.actual(), r.estimated(), r.relativeError(), r.qError(), metrics));
            }
            System.out.printf(Locale.ROOT,
                    "推理总耗时: %.2f ms | 平均: %.2f us/query | 吞吐: %.2f QPS | JVM堆增量: %s%n",
                    metrics.inferTotalMs(), metrics.inferPerQueryUs(), metrics.throughputQps(),
                    MemoryProbe.humanize(metrics.heapDeltaBytes()));
        }
        System.out.println("================================================================================");

        try {
            exportDetailCsv(allRecords);
            exportSummaryMarkdown(allRecords, metricsByMethod);
        } catch (IOException e) {
            throw new RuntimeException("导出结果文件失败", e);
        }
        return allRecords;
    }

    private Map<String, Double> computeActualCardinalities(List<RangeQuery> queries) {
        Map<String, Double> actuals = new LinkedHashMap<>();
        for (RangeQuery query : queries) {
            double actual = dataSet.points().stream().filter(query::contains).count();
            actuals.put(query.name(), actual);
        }
        return actuals;
    }

    private List<RangeQuery> allQueries() {
        List<RangeQuery> all = new ArrayList<>();
        for (List<RangeQuery> group : queryGroups.values()) {
            all.addAll(group);
        }
        return all;
    }

    private int totalQueryCount() {
        int total = 0;
        for (List<RangeQuery> group : queryGroups.values()) {
            total += group.size();
        }
        return total;
    }

    private double computeQError(double actual, double estimated) {
        if (actual == 0.0 && estimated == 0.0) {
            return 1.0;
        }
        if (actual == 0.0 || estimated == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(estimated / actual, actual / estimated);
    }

    private double computeRelativeError(double actual, double estimated) {
        if (actual == 0.0) {
            return estimated == 0.0 ? 0.0 : 1.0;
        }
        return Math.abs(estimated - actual) / actual;
    }

    private void exportDetailCsv(List<RunRecord> records) throws IOException {
        Path outputDir = Path.of(config.outputDir());
        Files.createDirectories(outputDir);
        Path csvPath = outputDir.resolve("results_detail.csv");
        boolean newFile = !Files.exists(csvPath);
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8,
                newFile ? java.nio.file.StandardOpenOption.CREATE : java.nio.file.StandardOpenOption.APPEND,
                java.nio.file.StandardOpenOption.WRITE)) {
            if (newFile) {
                writer.write("dataset,group,queryName,method,actual,estimated,relativeError,qError,fitTimeMs,inferTotalMs,inferPerQueryUs,throughputQps,heapDeltaBytes");
                writer.newLine();
            }
            for (RunRecord r : records) {
                writer.write(String.format(Locale.ROOT, "%s,%s,%s,%s,%.1f,%.4f,%.6f,%s,%.3f,%.3f,%.3f,%.3f,%d",
                        r.dataset(), r.group(), r.queryName(), r.method(),
                        r.actual(), r.estimated(), r.relativeError(),
                        Double.isInfinite(r.qError()) ? "inf" : String.format(Locale.ROOT, "%.6f", r.qError()),
                        r.metrics().fitTimeMs(), r.metrics().inferTotalMs(),
                        r.metrics().inferPerQueryUs(), r.metrics().throughputQps(),
                        r.metrics().heapDeltaBytes()));
                writer.newLine();
            }
        }
        System.out.println("明细结果已写入: " + csvPath.toAbsolutePath());
    }

    private void exportSummaryMarkdown(List<RunRecord> records, Map<String, RunMetrics> metricsByMethod) throws IOException {
        Path outputDir = Path.of(config.outputDir());
        Files.createDirectories(outputDir);
        Path mdPath = outputDir.resolve("results_summary.md");
        boolean newFile = !Files.exists(mdPath);
        try (BufferedWriter writer = Files.newBufferedWriter(mdPath, StandardCharsets.UTF_8,
                newFile ? java.nio.file.StandardOpenOption.CREATE : java.nio.file.StandardOpenOption.APPEND,
                java.nio.file.StandardOpenOption.WRITE)) {
            if (newFile) {
                writer.write("# 准确性对比实验汇总（对齐 QDSPN V-B 节）\n\n");
                writer.write("Q-Error 定义：`max(A,F) / min(A,F)`，越接近 1 越好。`inf` 表示真实或估计基数为 0。\n\n");
            }
            writer.write("## 数据集: " + datasetName + "\n\n");

            // 按组输出一张表 + 最后一张总体表。
            List<String> groups = new ArrayList<>(queryGroups.keySet());
            for (String group : groups) {
                writer.write("### 分组: " + group + "\n\n");
                writeMarkdownTable(writer, records, group, metricsByMethod);
            }
            writer.write("### 总体（跨分组汇总）\n\n");
            writeMarkdownTable(writer, records, OVERALL_KEY, metricsByMethod);
        }
        System.out.println("汇总结果已写入: " + mdPath.toAbsolutePath());
    }

    private void writeMarkdownTable(BufferedWriter writer, List<RunRecord> records, String group, Map<String, RunMetrics> metricsByMethod) throws IOException {
        List<String> methodOrder = new ArrayList<>();
        for (EstimatorSpec e : estimatorSpecs) {
            methodOrder.add(e.name());
        }
        writer.write("| 方法 | median | mean | p90 | p95 | max | inf 数 | fit(ms) | infer(us/q) | qps | heap |\n");
        writer.write("|------|--------|------|-----|-----|-----|--------|---------|-------------|-----|------|\n");
        for (String method : methodOrder) {
            List<Double> qErrors = new ArrayList<>();
            int infCount = 0;
            for (RunRecord r : records) {
                if (!r.method().equals(method)) continue;
                if (!OVERALL_KEY.equals(group) && !r.group().equals(group)) continue;
                qErrors.add(r.qError());
                if (Double.isInfinite(r.qError())) {
                    infCount++;
                }
            }
            QErrorStats stats = QErrorStats.from(qErrors);
            RunMetrics metrics = metricsByMethod.getOrDefault(method, RunMetrics.empty());
            writer.write(String.format(Locale.ROOT, "| %s | %.3f | %.3f | %.3f | %.3f | %s | %d | %.2f | %.2f | %.2f | %s |%n",
                    method,
                    stats.median(), stats.mean(), stats.p90(), stats.p95(),
                    Double.isInfinite(stats.max()) ? "inf" : String.format(Locale.ROOT, "%.3f", stats.max()),
                    infCount,
                    metrics.fitTimeMs(), metrics.inferPerQueryUs(), metrics.throughputQps(),
                    MemoryProbe.humanize(metrics.heapDeltaBytes())));
        }
        writer.write("\n");
    }

    private record EstimatorSpec(String name, Supplier<CardinalityEstimator> factory) {
        CardinalityEstimator create() {
            return factory.get();
        }
    }

    // 推理耗时和 heap delta 是方法级指标；为了输出逐查询 CSV，后续会复制到每条 RunRecord 上。
    private record PartialRunRecord(String dataset, String group, String queryName, String method,
                                    double actual, double estimated, double relativeError, double qError) {
    }

    public record RunRecord(String dataset, String group, String queryName, String method,
                            double actual, double estimated, double relativeError, double qError, RunMetrics metrics) {
    }

    public record RunMetrics(double fitTimeMs, double inferTotalMs, double inferPerQueryUs,
                             double throughputQps, long heapDeltaBytes) {
        public static RunMetrics from(double fitTimeMs, double inferTotalMs, int totalQueries, long heapDeltaBytes) {
            double perQueryUs = totalQueries == 0 ? 0.0 : inferTotalMs * 1000.0 / totalQueries;
            double qps = inferTotalMs <= 0.0 ? 0.0 : totalQueries / (inferTotalMs / 1000.0);
            return new RunMetrics(fitTimeMs, inferTotalMs, perQueryUs, qps, heapDeltaBytes);
        }

        public static RunMetrics empty() {
            return new RunMetrics(0.0, 0.0, 0.0, 0.0, 0L);
        }
    }

    // Q-Error 五项百分位统计：median / mean / p90 / p95 / max。
    // inf 参与排序后落在最尾，因此 max 可能为 inf；中位数和 p90/p95 由非空有限数据保证合理。
    public record QErrorStats(double median, double mean, double p90, double p95, double max) {
        public static QErrorStats from(List<Double> qErrors) {
            if (qErrors == null || qErrors.isEmpty()) {
                return new QErrorStats(0.0, 0.0, 0.0, 0.0, 0.0);
            }
            List<Double> sorted = new ArrayList<>(qErrors);
            Collections.sort(sorted);
            double median = percentile(sorted, 0.50);
            double p90 = percentile(sorted, 0.90);
            double p95 = percentile(sorted, 0.95);
            double max = sorted.get(sorted.size() - 1);
            double sum = 0.0;
            int finiteCount = 0;
            for (double q : qErrors) {
                if (Double.isFinite(q)) {
                    sum += q;
                    finiteCount++;
                }
            }
            double mean = finiteCount == 0 ? Double.POSITIVE_INFINITY : sum / finiteCount;
            return new QErrorStats(median, mean, p90, p95, max);
        }

        // 线性插值法计算百分位数，和 numpy 默认行为一致。
        private static double percentile(List<Double> sorted, double p) {
            int n = sorted.size();
            if (n == 1) {
                return sorted.get(0);
            }
            double rank = p * (n - 1);
            int lo = (int) Math.floor(rank);
            int hi = (int) Math.ceil(rank);
            if (lo == hi) {
                return sorted.get(lo);
            }
            double loVal = sorted.get(lo);
            double hiVal = sorted.get(hi);
            if (Double.isInfinite(loVal) || Double.isInfinite(hiVal)) {
                return Double.isInfinite(hiVal) ? hiVal : loVal;
            }
            return loVal + (hiVal - loVal) * (rank - lo);
        }
    }
}
