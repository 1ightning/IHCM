package cn.lsc.cardinality.baseline;

import cn.lsc.cardinality.data.DataSet;
import cn.lsc.cardinality.data.RangeQuery;

public final class IndependenceEstimator implements CardinalityEstimator {
    private DataSet dataSet;

    @Override
    public String name() {
        return "IndependenceEstimator";
    }

    @Override
    public void fit(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    @Override
    public double estimate(RangeQuery query) {
        double selectivity = 1.0;
        // 这是最简单的基线：默认各维之间互相独立。
        for (int d = 0; d < dataSet.dimensions(); d++) {
            double min = dataSet.minValues()[d];
            double max = dataSet.maxValues()[d];
            double range = max - min;
            if (range <= 0.0) {
                continue;
            }
            double covered = Math.max(0.0, Math.min(max, query.upperBounds()[d]) - Math.max(min, query.lowerBounds()[d]));
            selectivity *= Math.max(0.0, Math.min(1.0, covered / range));
        }
        return selectivity * dataSet.size();
    }
}
