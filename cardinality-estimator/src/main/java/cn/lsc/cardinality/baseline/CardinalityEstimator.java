package cn.lsc.cardinality.baseline;

import cn.lsc.cardinality.data.DataSet;
import cn.lsc.cardinality.data.RangeQuery;

public interface CardinalityEstimator {
    String name();

    void fit(DataSet dataSet);

    double estimate(RangeQuery query);
}
