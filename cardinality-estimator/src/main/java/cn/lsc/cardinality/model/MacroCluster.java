package cn.lsc.cardinality.model;

import cn.lsc.cardinality.data.DataPoint;

import java.util.List;

/**
 * 一个宏观空间簇：由稠密网格连通分量或 K-means 簇形成。
 * 每个簇绑定它的 MBB 与后续 axial binary split 生成的局部树根。
 */
public final class MacroCluster {
    private final List<DataPoint> points;
    private final HyperRectangle mbb;
    private final boolean fromDense;
    private RegionNode axialRoot;

    public MacroCluster(List<DataPoint> points, HyperRectangle mbb, boolean fromDense) {
        this.points = points;
        this.mbb = mbb;
        this.fromDense = fromDense;
    }

    public List<DataPoint> points() {
        return points;
    }

    public HyperRectangle mbb() {
        return mbb;
    }

    public boolean fromDense() {
        return fromDense;
    }

    public RegionNode axialRoot() {
        return axialRoot;
    }

    public void setAxialRoot(RegionNode axialRoot) {
        this.axialRoot = axialRoot;
    }
}
