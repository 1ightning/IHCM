package cn.lsc.cardinality.data;

import java.util.List;

public record DataSet(List<String> headers, List<DataPoint> points, double[] minValues, double[] maxValues) {
    public DataSet {
        headers = List.copyOf(headers);
        points = List.copyOf(points);
    }

    public int dimensions() {
        return headers.size();
    }

    public int size() {
        return points.size();
    }
}
