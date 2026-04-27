package cn.lsc.cardinality.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CsvDataLoader {
    private CsvDataLoader() {
    }

    public static DataSet loadDataSet(String resourcePath) throws IOException {
        try (BufferedReader reader = open(resourcePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IOException("数据文件为空: " + resourcePath);
            }
            // 读取表头，得到每一维属性的名字。
            List<String> headers = Arrays.stream(headerLine.split(","))
                    .map(String::trim)
                    .toList();
            List<DataPoint> points = new ArrayList<>();
            double[] minValues = new double[headers.size()];
            double[] maxValues = new double[headers.size()];
            Arrays.fill(minValues, Double.POSITIVE_INFINITY);
            Arrays.fill(maxValues, Double.NEGATIVE_INFINITY);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",");
                double[] values = new double[headers.size()];
                for (int i = 0; i < headers.size(); i++) {
                    values[i] = Double.parseDouble(parts[i].trim());
                    // 在读取数据的同时，顺便统计每一维的最小值和最大值。
                    minValues[i] = Math.min(minValues[i], values[i]);
                    maxValues[i] = Math.max(maxValues[i], values[i]);
                }
                points.add(new DataPoint(values));
            }
            return new DataSet(headers, points, minValues, maxValues);
        }
    }

    public static List<RangeQuery> loadQueries(String resourcePath, List<String> headers) throws IOException {
        try (BufferedReader reader = open(resourcePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IOException("查询文件为空: " + resourcePath);
            }
            String[] columns = Arrays.stream(headerLine.split(",")).map(String::trim).toArray(String[]::new);
            List<RangeQuery> queries = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",");
                String name = parts[0].trim();
                double[] lowerBounds = new double[headers.size()];
                double[] upperBounds = new double[headers.size()];
                for (int i = 0; i < headers.size(); i++) {
                    String lowerColumn = headers.get(i) + "_min";
                    String upperColumn = headers.get(i) + "_max";
                    lowerBounds[i] = Double.parseDouble(parts[indexOf(columns, lowerColumn)].trim());
                    upperBounds[i] = Double.parseDouble(parts[indexOf(columns, upperColumn)].trim());
                }
                queries.add(new RangeQuery(name, lowerBounds, upperBounds));
            }
            return queries;
        }
    }

    private static int indexOf(String[] columns, String target) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(target)) {
                return i;
            }
        }
        throw new IllegalArgumentException("查询文件缺少列: " + target);
    }

    private static BufferedReader open(String resourcePath) throws IOException {
        // 从 resources 目录中读取文件。
        InputStream inputStream = CsvDataLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("找不到资源文件: " + resourcePath);
        }
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }
}
