package cn.lsc.cardinality.data;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// 按目标选择率区间批量生成合取范围查询：以随机数据点为中心张矩形，oracle 核实选择率后接受/拒绝。
public final class RangeQueryGenerator {
    private static final int MAX_ATTEMPTS_PER_QUERY = 50;

    private RangeQueryGenerator() {
    }

    public static List<RangeQuery> generate(
            DataSet dataSet,
            String namePrefix,
            int count,
            double selLow,
            double selHigh,
            long seed
    ) {
        if (selLow < 0 || selHigh <= selLow || selHigh > 1.0) {
            throw new IllegalArgumentException("选择率区间非法: [" + selLow + ", " + selHigh + "]");
        }

        Random random = new Random(seed);
        List<RangeQuery> accepted = new ArrayList<>(count);
        int dims = dataSet.dimensions();
        List<DataPoint> points = dataSet.points();
        int total = points.size();
        double[] minValues = dataSet.minValues();
        double[] maxValues = dataSet.maxValues();

        double currentLow = selLow;
        double currentHigh = selHigh;

        while (accepted.size() < count) {
            boolean matched = false;
            for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_QUERY; attempt++) {
                double[] lower = new double[dims];
                double[] upper = new double[dims];
                // 每维独立采样中心 + 半宽。半宽从 5% 到 40% 维度幅度之间抽，配合拒绝采样命中目标选择率。
                DataPoint center = points.get(random.nextInt(total));
                for (int d = 0; d < dims; d++) {
                    double range = maxValues[d] - minValues[d];
                    double halfWidthRatio = 0.05 + 0.35 * random.nextDouble();
                    double half = range * halfWidthRatio;
                    lower[d] = Math.max(minValues[d], center.valueAt(d) - half);
                    upper[d] = Math.min(maxValues[d], center.valueAt(d) + half);
                }

                String tempName = namePrefix + "_" + accepted.size();
                RangeQuery candidate = new RangeQuery(tempName, lower, upper);
                long hits = points.stream().filter(candidate::contains).count();
                double sel = (double) hits / total;
                if (sel > currentLow && sel <= currentHigh) {
                    accepted.add(candidate);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                // 放宽区间再试：下界减半、上界向 1 扩张 20%，避免极端数据集下永远拒绝。
                currentLow = Math.max(0.0, currentLow / 2.0);
                currentHigh = Math.min(1.0, currentHigh + (1.0 - currentHigh) * 0.2);
                if (currentHigh - currentLow >= 1.0 - 1e-9) {
                    // 已放宽到全域，避免死循环：直接按当前半宽生成一个。
                    double[] lower = new double[dims];
                    double[] upper = new double[dims];
                    DataPoint center = points.get(random.nextInt(total));
                    for (int d = 0; d < dims; d++) {
                        double range = maxValues[d] - minValues[d];
                        double half = range * (0.05 + 0.35 * random.nextDouble());
                        lower[d] = Math.max(minValues[d], center.valueAt(d) - half);
                        upper[d] = Math.min(maxValues[d], center.valueAt(d) + half);
                    }
                    accepted.add(new RangeQuery(namePrefix + "_" + accepted.size(), lower, upper));
                }
            }
        }
        return accepted;
    }

    // 把生成的查询写到 data/ 目录，格式与 CsvDataLoader.loadQueries 对齐。
    public static void writeQueriesToFile(List<RangeQuery> queries, List<String> headers, String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            StringBuilder header = new StringBuilder("name");
            for (String attr : headers) {
                header.append(",").append(attr).append("_min").append(",").append(attr).append("_max");
            }
            writer.write(header.toString());
            writer.newLine();

            for (RangeQuery q : queries) {
                StringBuilder sb = new StringBuilder(q.name());
                for (int i = 0; i < headers.size(); i++) {
                    sb.append(",").append(String.format("%.4f", q.lowerBounds()[i]));
                    sb.append(",").append(String.format("%.4f", q.upperBounds()[i]));
                }
                writer.write(sb.toString());
                writer.newLine();
            }
        }
    }
}
