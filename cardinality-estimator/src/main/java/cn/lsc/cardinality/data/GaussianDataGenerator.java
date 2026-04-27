package cn.lsc.cardinality.data;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class GaussianDataGenerator {
    private GaussianDataGenerator() {
    }

    public static void main(String[] args) throws IOException {
        DataSet d2 = generate(10000, 2, 5, 1.0, 100.0, 0.2, 0.2, 0.02, 30, 42L);
        writeToFile(d2, "src/main/resources/data/synthetic_D2.csv");
        System.out.println("已生成 synthetic_D2.csv, 样本数=" + d2.size());

        DataSet d5 = generate(10000, 5, 5, 1.0, 100.0, 0.2, 0.2, 0.02, 30, 42L);
        writeToFile(d5, "src/main/resources/data/synthetic_D5.csv");
        System.out.println("已生成 synthetic_D5.csv, 样本数=" + d5.size());
    }

    // 按 QDSPN 论文 V-A2 节设定生成多簇高斯数据：C 个簇、每簇 N/C 条，叠加白噪声。
    public static DataSet generate(
            int totalSamples,
            int dimensions,
            int clusterCount,
            double lb,
            double ub,
            double lambdaMu,
            double lambdaSigma,
            double lambdaNoise,
            double snrDb,
            long seed
    ) {
        if (dimensions <= 0 || clusterCount <= 0 || totalSamples <= 0) {
            throw new IllegalArgumentException("样本量/维度/簇数必须为正整数");
        }
        if (ub <= lb) {
            throw new IllegalArgumentException("上界必须大于下界");
        }

        Random random = new Random(seed);
        double range = ub - lb;
        double sigma0 = range / (6.0 * clusterCount);

        // 为每个簇采样中心 μ_c 与方差 σ_c（各维度独立、但同一簇共享方差基线波动）。
        double[][] clusterMeans = new double[clusterCount][dimensions];
        double[][] clusterStdDevs = new double[clusterCount][dimensions];
        for (int c = 0; c < clusterCount; c++) {
            for (int d = 0; d < dimensions; d++) {
                double meanLow = lb + range * lambdaMu;
                double meanHigh = ub - range * lambdaMu;
                clusterMeans[c][d] = meanLow + (meanHigh - meanLow) * random.nextDouble();
                double sigmaLow = (1.0 - lambdaSigma) * sigma0;
                double sigmaHigh = (1.0 + lambdaSigma) * sigma0;
                clusterStdDevs[c][d] = sigmaLow + (sigmaHigh - sigmaLow) * random.nextDouble();
            }
        }

        // 每个簇分到 N/C 条数据（尾部多出的余数均匀分配给前几个簇）。
        int basePerCluster = totalSamples / clusterCount;
        int remainder = totalSamples - basePerCluster * clusterCount;

        List<double[]> rawValues = new ArrayList<>(totalSamples);
        double sumOfSquares = 0.0;
        for (int c = 0; c < clusterCount; c++) {
            int size = basePerCluster + (c < remainder ? 1 : 0);
            for (int i = 0; i < size; i++) {
                double[] v = new double[dimensions];
                for (int d = 0; d < dimensions; d++) {
                    v[d] = clusterMeans[c][d] + clusterStdDevs[c][d] * random.nextGaussian();
                    sumOfSquares += v[d] * v[d];
                }
                rawValues.add(v);
            }
        }

        // 白噪声幅度：N(0, (ub-lb)·λnoise) · sqrt(Σxi²/N) · 10^(-snr/10)
        double rms = Math.sqrt(sumOfSquares / (totalSamples * dimensions));
        double noiseScale = range * lambdaNoise * rms * Math.pow(10.0, -snrDb / 10.0);

        List<DataPoint> points = new ArrayList<>(totalSamples);
        double[] minValues = new double[dimensions];
        double[] maxValues = new double[dimensions];
        for (int d = 0; d < dimensions; d++) {
            minValues[d] = Double.POSITIVE_INFINITY;
            maxValues[d] = Double.NEGATIVE_INFINITY;
        }

        for (double[] v : rawValues) {
            for (int d = 0; d < dimensions; d++) {
                v[d] += noiseScale * random.nextGaussian();
                if (v[d] < minValues[d]) {
                    minValues[d] = v[d];
                }
                if (v[d] > maxValues[d]) {
                    maxValues[d] = v[d];
                }
            }
            points.add(new DataPoint(v));
        }

        Collections.shuffle(points, random);
        return new DataSet(buildHeaders(dimensions), points, minValues, maxValues);
    }

    private static List<String> buildHeaders(int dimensions) {
        List<String> headers = new ArrayList<>(dimensions);
        for (int i = 0; i < dimensions; i++) {
            headers.add("attr" + (i + 1));
        }
        return headers;
    }

    public static void writeToFile(DataSet dataSet, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(String.join(",", dataSet.headers()));
            writer.newLine();

            for (DataPoint point : dataSet.points()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < point.dimensions(); i++) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    sb.append(String.format("%.4f", point.valueAt(i)));
                }
                writer.write(sb.toString());
                writer.newLine();
            }
        }
    }
}
