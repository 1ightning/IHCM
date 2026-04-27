package cn.lsc.cardinality.model;

import cn.lsc.cardinality.data.DataPoint;

import java.util.ArrayList;
import java.util.List;

public final class RegionNode {
    private final HyperRectangle bounds;
    private final List<DataPoint> points;
    private final int depth;
    private List<Histogram> histograms = List.of();
    private RegionNode left;
    private RegionNode right;

    public RegionNode(HyperRectangle bounds, List<DataPoint> points, int depth) {
        this.bounds = bounds;
        this.points = new ArrayList<>(points);
        this.depth = depth;
    }

    public HyperRectangle bounds() {
        return bounds;
    }

    public List<DataPoint> points() {
        return points;
    }

    public int depth() {
        return depth;
    }

    public boolean isLeaf() {
        return left == null && right == null;
    }

    public void setChildren(RegionNode left, RegionNode right) {
        this.left = left;
        this.right = right;
    }

    public RegionNode left() {
        return left;
    }

    public RegionNode right() {
        return right;
    }

    public void setHistograms(List<Histogram> histograms) {
        this.histograms = histograms;
    }

    public List<Histogram> histograms() {
        return histograms;
    }
}
