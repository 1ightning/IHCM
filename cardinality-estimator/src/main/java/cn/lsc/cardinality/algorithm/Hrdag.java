package cn.lsc.cardinality.algorithm;

import cn.lsc.cardinality.data.RangeQuery;
import cn.lsc.cardinality.model.HyperRectangle;
import cn.lsc.cardinality.model.MacroCluster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 层次区域有向无环图（HRDAG），对应论文算法 2。
 * <ul>
 *   <li>虚拟根下挂载全部宏观簇 MBB 节点</li>
 *   <li>节点间按 Disjoint / Contains / Intersects 三种几何关系组织</li>
 *   <li>相交时引入交集节点 Ω 并复用，避免重复构建</li>
 *   <li>查询时从根向下剪枝，仅收集"原始簇节点"以避免交集区域引入重复计数</li>
 * </ul>
 */
public final class Hrdag {
    private final Node root;

    public Hrdag(HyperRectangle universeBounds) {
        this.root = new Node(universeBounds, null, true);
    }

    public static Hrdag build(List<MacroCluster> clusters, HyperRectangle universeBounds) {
        Hrdag dag = new Hrdag(universeBounds);
        Map<HyperRectangle, Node> seen = new HashMap<>();
        Set<Long> compared = new HashSet<>();
        for (MacroCluster cluster : clusters) {
            Node node = new Node(cluster.mbb(), cluster, false);
            dag.insert(dag.root, node, seen, compared);
        }
        return dag;
    }

    /** 对应算法 2 的递归插入过程。 */
    private void insert(Node parent, Node n, Map<HyperRectangle, Node> seen, Set<Long> compared) {
        boolean contained = false;
        List<Node> snapshot = new ArrayList<>(parent.children);
        for (Node child : snapshot) {
            long pairKey = pairKey(child, n);
            if (compared.contains(pairKey)) {
                continue;
            }
            compared.add(pairKey);

            if (child.bounds.disjoint(n.bounds)) {
                continue;
            }
            if (child.bounds.contains(n.bounds)) {
                contained = true;
                insert(child, n, seen, compared);
            } else if (n.bounds.contains(child.bounds)) {
                if (!parent.children.contains(n)) {
                    parent.children.add(n);
                }
                parent.children.remove(child);
                if (!n.children.contains(child)) {
                    n.children.add(child);
                }
            } else {
                HyperRectangle omega = child.bounds.intersection(n.bounds);
                if (omega == null) continue;
                Node m = seen.get(omega);
                if (m == null) {
                    m = new Node(omega, null, false);
                    m.isIntersection = true;
                    seen.put(omega, m);
                    insert(child, m, seen, compared);
                }
                if (!hasCrossGenerationConflict(n, m) && !n.children.contains(m)) {
                    n.children.add(m);
                }
            }
        }
        if (!contained && !parent.children.contains(n)) {
            parent.children.add(n);
        }
    }

    /** 避免把 n 连到它本身的祖先 / 后代，防止环。 */
    private boolean hasCrossGenerationConflict(Node n, Node m) {
        if (n == m) return true;
        return isDescendant(n, m) || isDescendant(m, n);
    }

    private boolean isDescendant(Node root, Node target) {
        Set<Node> visited = new HashSet<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Node cur = stack.pop();
            if (!visited.add(cur)) continue;
            for (Node c : cur.children) {
                if (c == target) return true;
                stack.push(c);
            }
        }
        return false;
    }

    /** 剪枝查询：返回所有 MBB 与查询矩形相交的原始簇。 */
    public List<MacroCluster> route(RangeQuery query) {
        HyperRectangle queryRect = new HyperRectangle(query.lowerBounds(), query.upperBounds());
        Set<MacroCluster> hit = new HashSet<>();
        Set<Node> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        traverse(root, queryRect, visited, hit);
        return new ArrayList<>(hit);
    }

    private void traverse(Node node, HyperRectangle queryRect, Set<Node> visited, Set<MacroCluster> hit) {
        if (!visited.add(node)) return;
        if (node.cluster != null && node.bounds.intersects(queryRect)) {
            hit.add(node.cluster);
        }
        for (Node child : node.children) {
            if (child.bounds.intersects(queryRect)) {
                traverse(child, queryRect, visited, hit);
            }
        }
    }

    private long pairKey(Node a, Node b) {
        int hashA = System.identityHashCode(a);
        int hashB = System.identityHashCode(b);
        long lo = Math.min(hashA, hashB) & 0xffffffffL;
        long hi = Math.max(hashA, hashB) & 0xffffffffL;
        return (hi << 32) | lo;
    }

    public Node root() {
        return root;
    }

    public static final class Node {
        private final HyperRectangle bounds;
        private final MacroCluster cluster;
        private final boolean virtualRoot;
        private boolean isIntersection;
        private final List<Node> children = new ArrayList<>();

        public Node(HyperRectangle bounds, MacroCluster cluster, boolean virtualRoot) {
            this.bounds = bounds;
            this.cluster = cluster;
            this.virtualRoot = virtualRoot;
        }

        public HyperRectangle bounds() { return bounds; }
        public MacroCluster cluster() { return cluster; }
        public boolean virtualRoot() { return virtualRoot; }
        public boolean isIntersection() { return isIntersection; }
        public List<Node> children() { return children; }
    }
}
