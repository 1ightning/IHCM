package cn.lsc.cardinality;

import cn.lsc.cardinality.config.ExperimentConfig;
import cn.lsc.cardinality.data.CsvDataLoader;
import cn.lsc.cardinality.data.DataSet;
import cn.lsc.cardinality.data.GaussianDataGenerator;
import cn.lsc.cardinality.data.RangeQuery;
import cn.lsc.cardinality.data.RangeQueryGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class App {
    // 主准确性对比实验：对齐 QDSPN V-B 节。
    // 数据集: D=2/D=5 两个合成数据；每个数据集按选择率分 3 组、每组 200 条查询。
    public static void main(String[] args) throws IOException {
        System.out.println("面向云边端协同数据库的基数估计模型 - 主准确性对比实验");
        System.out.println("=======================================");
        System.out.println();

        ExperimentConfig config = ExperimentConfig.defaultConfig();

        runDataset("synthetic_D2", 2, config);
        runDataset("synthetic_D5", 5, config);

        System.out.println();
        System.out.println("全部实验完成。");
        System.out.println("明细 CSV: " + Path.of(config.outputDir(), "results_detail.csv").toAbsolutePath());
        System.out.println("汇总 MD : " + Path.of(config.outputDir(), "results_summary.md").toAbsolutePath());
    }

    private static void runDataset(String datasetName, int dims, ExperimentConfig config) throws IOException {
        DataSet dataSet = loadOrGenerate(datasetName, dims);
        System.out.println(">>> 数据集 " + datasetName + ": " + dataSet.size() + " 条, " + dataSet.dimensions() + " 维");

        Map<String, List<RangeQuery>> groups = new LinkedHashMap<>();
        groups.put("narrow", RangeQueryGenerator.generate(dataSet, datasetName + "_narrow", 200, 0.0, 0.05, 1001L));
        groups.put("medium", RangeQueryGenerator.generate(dataSet, datasetName + "_medium", 200, 0.10, 0.30, 1002L));
        groups.put("wide",   RangeQueryGenerator.generate(dataSet, datasetName + "_wide",   200, 0.50, 1.00, 1003L));

        for (Map.Entry<String, List<RangeQuery>> e : groups.entrySet()) {
            String csvPath = "src/main/resources/data/queries_" + datasetName + "_" + e.getKey() + ".csv";
            RangeQueryGenerator.writeQueriesToFile(e.getValue(), dataSet.headers(), csvPath);
        }

        ExperimentRunner runner = new ExperimentRunner(datasetName, dataSet, groups, config);
        runner.run();
        System.out.println();
    }

    // 数据集存在时复用 CSV（保证跨次运行可复现），否则按 QDSPN 参数现场生成并落盘。
    private static DataSet loadOrGenerate(String datasetName, int dims) throws IOException {
        String resourcePath = "data/" + datasetName + ".csv";
        Path fileOnDisk = Path.of("src/main/resources/", resourcePath);
        if (Files.exists(fileOnDisk)) {
            return CsvDataLoader.loadDataSet(resourcePath);
        }
        DataSet generated = GaussianDataGenerator.generate(
                10000, dims, 5, 1.0, 100.0,
                0.2, 0.2, 0.02, 30,
                42L + dims
        );
        Files.createDirectories(fileOnDisk.getParent());
        GaussianDataGenerator.writeToFile(generated, fileOnDisk.toString());
        return generated;
    }
}
