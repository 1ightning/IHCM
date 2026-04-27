package cn.lsc.cardinality.algorithm;

import cn.lsc.cardinality.config.ExperimentConfig;
import cn.lsc.cardinality.data.DataPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GridDensityStrategy {
    private GridDensityStrategy() {
    }

    public static GridView gridDiscretization(List<DataPoint> points,
                                              double[] minValues,
                                              double[] maxValues,
                                              ExperimentConfig config) {
        int gridCells = config.gridCellsPerDimension();
        int dimensions = minValues.length;

        // 每一维的网格宽度（对应算法 1 第 1 步）。
        double[] widths = new double[dimensions];
        for (int d = 0; d < dimensions; d++) {
            double range = maxValues[d] - minValues[d];
            widths[d] = range <= 0 ? 1.0 : range / gridCells;
        }

        // 用哈希表按多维索引保存非空网格，避免枚举 g^d 个网格单元。
        Map<Long, Cell> cellByKey = new HashMap<>();
        // 同时建立“数据点 → 所在网格”的唯一归属映射，后续稠密/稀疏分区均以此为准。
        Map<DataPoint, Cell> pointToCell = new HashMap<>(points.size() * 2);

        for (DataPoint point : points) {
            int[] index = new int[dimensions];
            double[] lower = new double[dimensions];
            double[] upper = new double[dimensions];
            for (int d = 0; d < dimensions; d++) {
                int idx = (int) Math.floor((point.valueAt(d) - minValues[d]) / widths[d]);
                if (idx < 0) idx = 0;
                if (idx >= gridCells) idx = gridCells - 1;
                index[d] = idx;
                lower[d] = minValues[d] + idx * widths[d];
                upper[d] = idx == gridCells - 1 ? maxValues[d] : lower[d] + widths[d];
            }
            long key = encodeIndex(index, gridCells);
            Cell cell = cellByKey.computeIfAbsent(key, k -> new Cell(lower, upper, index.clone()));
            cell.addPoint(point);
            pointToCell.put(point, cell);
        }

        return new GridView(cellByKey, pointToCell, gridCells, dimensions);
    }

    public static List<Cell> extractDenseCells(GridView view, int totalPoints, ExperimentConfig config) {
        List<Cell> denseCells = new ArrayList<>();
        double minDensity = (double) config.denseCellThreshold() / Math.max(1, totalPoints);
        for (Cell cell : view.allCells()) {
            double density = cell.count() / (double) Math.max(1, totalPoints);
            if (density >= minDensity) {
                denseCells.add(cell);
            }
        }
        return denseCells;
    }

    /**
     * 按多维 6-邻接（共享一个 d-1 维面）提取稠密网格的连通分量，对应算法 1 第 6-8 步。
     */
    public static List<List<Cell>> connectedComponents(List<Cell> denseCells, GridView view) {
        Map<Long, Cell> denseByKey = new HashMap<>();
        for (Cell cell : denseCells) {
            denseByKey.put(encodeIndex(cell.index(), view.gridCells()), cell);
        }
        List<List<Cell>> components = new ArrayList<>();
        Set<Cell> visited = new HashSet<>();
        for (Cell seed : denseCells) {
            if (visited.contains(seed)) continue;
            List<Cell> component = new ArrayList<>();
            Deque<Cell> queue = new ArrayDeque<>();
            queue.add(seed);
            visited.add(seed);
            while (!queue.isEmpty()) {
                Cell current = queue.poll();
                component.add(current);
                int[] idx = current.index();
                for (int d = 0; d < view.dimensions(); d++) {
                    for (int delta : new int[]{-1, 1}) {
                        int ni = idx[d] + delta;
                        if (ni < 0 || ni >= view.gridCells()) continue;
                        int[] neighborIdx = idx.clone();
                        neighborIdx[d] = ni;
                        Cell neighbor = denseByKey.get(encodeIndex(neighborIdx, view.gridCells()));
                        if (neighbor != null && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    public static List<DataPoint> sparsePoints(GridView view, Set<Cell> covered) {
        List<DataPoint> sparse = new ArrayList<>();
        for (Map.Entry<DataPoint, Cell> entry : view.pointToCell().entrySet()) {
            if (!covered.contains(entry.getValue())) {
                sparse.add(entry.getKey());
            }
        }
        return sparse;
    }

    private static long encodeIndex(int[] index, int gridCells) {
        long code = 0L;
        for (int d = index.length - 1; d >= 0; d--) {
            code = code * gridCells + index[d];
        }
        return code;
    }

    public static final class Cell {
        private final double[] lowerBounds;
        private final double[] upperBounds;
        private final int[] index;
        private final List<DataPoint> points = new ArrayList<>();

        public Cell(double[] lowerBounds, double[] upperBounds, int[] index) {
            this.lowerBounds = lowerBounds;
            this.upperBounds = upperBounds;
            this.index = index;
        }

        public void addPoint(DataPoint point) {
            points.add(point);
        }

        public int count() {
            return points.size();
        }

        public List<DataPoint> points() {
            return points;
        }

        public double[] lowerBounds() {
            return lowerBounds;
        }

        public double[] upperBounds() {
            return upperBounds;
        }

        public int[] index() {
            return index;
        }

        @Override
        public String toString() {
            return "Cell(index=" + Arrays.toString(index) + ", count=" + count() + ")";
        }
    }

    public static final class GridView {
        private final Map<Long, Cell> cellByKey;
        private final Map<DataPoint, Cell> pointToCell;
        private final int gridCells;
        private final int dimensions;

        public GridView(Map<Long, Cell> cellByKey, Map<DataPoint, Cell> pointToCell, int gridCells, int dimensions) {
            this.cellByKey = cellByKey;
            this.pointToCell = pointToCell;
            this.gridCells = gridCells;
            this.dimensions = dimensions;
        }

        public java.util.Collection<Cell> allCells() {
            return cellByKey.values();
        }

        public Map<DataPoint, Cell> pointToCell() {
            return pointToCell;
        }

        public int gridCells() {
            return gridCells;
        }

        public int dimensions() {
            return dimensions;
        }
    }
}
