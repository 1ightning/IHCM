# 主准确性对比实验操作手册

> 对齐 QDSPN 论文（《Cardinality estimation based on QDSPN for embedded databases under dynamic workload》）第 V 章 B 节的「主准确性对比实验」。
> 本手册告诉你：**怎么从零跑到结果文件、怎么读懂输出、怎么把结果搬进论文第三章**。

---

## 1. 前置环境

| 要求 | 版本 |
|------|------|
| JDK | 17 或更高 |
| Maven | 3.8 或更高 |
| 操作系统 | macOS / Linux / Windows 均可 |

核对：

```bash
java -version
mvn -version
```

## 2. 实验一键跑通（最短路径）

```bash
cd "/Users/lsc/Desktop/claude work/cardinality-estimator"
mvn clean compile exec:java
```

一次执行会自动完成：

1. 如果 `src/main/resources/data/synthetic_D2.csv` 和 `synthetic_D5.csv` 不存在，则按 QDSPN 参数（N=10000、C=5 个高斯簇、范围 [1.0, 100.0]）现场生成；存在则复用。
2. 为每个数据集生成 `narrow` / `medium` / `wide` 三组查询，每组 200 条。
3. 依次跑 4 个估计器（论文方法 + 等宽 + 等深 + 独立性假设），统计 5 项 Q-Error 指标（median / mean / p90 / p95 / max）。
4. 同时记录构建耗时、单查询推理耗时、推理吞吐量、JVM 堆内存近似增量。
5. 输出控制台日志 + 两份结果文件到 `target/exp-results/`。

> 注意：当前 `RangeQueryGenerator` 为了避免生成过程卡死，会在目标选择率难以命中时放宽区间。因此 `narrow` / `medium` / `wide` 现在只适合作为查询组标签，不能直接写成严格选择率区间。主准确性对比仍然可用，因为所有方法面对同一批查询；选择率敏感性结论需要等查询生成逻辑修正后再写。

如果你想得到一份干净的新结果，不要在旧 CSV 后继续追加，直接运行：

```bash
rm -rf target/exp-results
mvn clean compile exec:java
```

---

## 3. 结果文件在哪里、分别给什么用

```
target/exp-results/
├── results_detail.csv      # 所有查询的逐条明细（4800 行数据 + 1 行表头）
└── results_summary.md      # 按「数据集 × 分组」的方法对比汇总表
```

实验数据的来源关系很简单：

- 原始数据：`src/main/resources/data/synthetic_D2.csv`、`synthetic_D5.csv`
- 查询数据：`src/main/resources/data/queries_synthetic_*.csv`
- 明细结果：`target/exp-results/results_detail.csv`
- 汇总结果：`target/exp-results/results_summary.md`
- 论文初稿：`experiment_chapter_draft.tex`

### 3.1 `results_detail.csv` 列说明

| 列 | 含义 |
|----|------|
| `dataset` | `synthetic_D2` 或 `synthetic_D5` |
| `group` | `narrow` / `medium` / `wide` |
| `queryName` | 查询唯一 ID |
| `method` | 估计器名字 |
| `actual` | 真实基数（oracle 精确扫描得到） |
| `estimated` | 估计基数 |
| `relativeError` | 相对误差 |
| `qError` | Q-Error，值越接近 1 越好；`inf` 代表真实或估计为 0 |
| `fitTimeMs` | 该方法在当前数据集上的训练耗时（毫秒） |
| `inferTotalMs` | 该方法在当前数据集 600 条查询上的推理总耗时（毫秒） |
| `inferPerQueryUs` | 平均单查询推理耗时（微秒/查询） |
| `throughputQps` | 推理吞吐量（queries per second） |
| `heapDeltaBytes` | `fit` 前后 JVM 已用堆内存近似增量（字节） |

这个 CSV **直接用于画箱线图、散点图、推理开销图和内存占用图**（推荐 Python pandas + seaborn）。

### 3.2 `results_summary.md` 版面

文件包含：

- 数据集 `synthetic_D2` 的 3 张分组表 + 1 张总体表
- 数据集 `synthetic_D5` 的 3 张分组表 + 1 张总体表

共 8 张表，每张表的列是 `median / mean / p90 / p95 / max / inf 数 / fit(ms) / infer(us/q) / qps / heap`，行是 4 个方法。**总体表可直接作为论文第三章主对比表的原始数据。**

### 3.3 论文里怎么取数

建议优先使用 `results_summary.md` 中的“总体（跨分组汇总）”表：

- 精度主表：取 `median / mean / p90 / p95 / max`
- 构建开销：取 `fit(ms)`
- 在线推理开销：取 `infer(us/q)` 或 `qps`
- 资源占用：取 `heap`

如果要画箱线图或计算更多统计量，再读取 `results_detail.csv`。明细 CSV 每条查询都会重复写入同一个方法的 `fitTimeMs / inferPerQueryUs / throughputQps / heapDeltaBytes`，这是为了让画图脚本可以只读一张表。

---

## 4. 画箱线图（对齐 QDSPN Figure 10–15）

QDSPN 的主图就是「四方法 × 五指标」的箱线图。用现成的 CSV 画法如下（示例 Python，不是必须）：

```python
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

df = pd.read_csv("target/exp-results/results_detail.csv")
df = df[df["qError"].apply(lambda s: s != "inf")]
df["qError"] = df["qError"].astype(float)

for ds in ["synthetic_D2", "synthetic_D5"]:
    for grp in ["narrow", "medium", "wide"]:
        sub = df[(df["dataset"] == ds) & (df["group"] == grp)]
        sns.boxplot(data=sub, x="method", y="qError")
        plt.yscale("log")
        plt.title(f"{ds} - {grp}")
        plt.savefig(f"fig_{ds}_{grp}.png", dpi=150)
        plt.clf()
```

## 5. 常见调优与变体

### 5.1 改查询规模

`App.java` 里直接改数字：

```java
groups.put("narrow", RangeQueryGenerator.generate(dataSet, datasetName + "_narrow", 200, 0.0, 0.05, 1001L));
```

把 `200` 调大（例如 500）即可对齐 QDSPN 原论文的查询批次规模；重跑 `mvn exec:java` 即可。

### 5.2 改数据集维度 / 样本量

`App.java` 中：

```java
runDataset("synthetic_D2", 2, config);
runDataset("synthetic_D5", 5, config);
```

想加一个 10 维版本就再加一行 `runDataset("synthetic_D10", 10, config);`，并把新数据集在 `GaussianDataGenerator.generate(...)` 里也按同样参数生成（程序会自动做）。

> **注意**：现已生成的 CSV 会被优先复用。如果你想用新参数重跑，请先删除 `src/main/resources/data/synthetic_D*.csv` 再运行。

### 5.3 改论文方法的超参数

`config/ExperimentConfig.java` → `defaultConfig()`：

```java
return new ExperimentConfig(4, 3, 2, 6, 4, 4, 1e-6, "target/exp-results");
//                          ^  ^  ^  ^  ^  ^   ^         ^
//                          |  |  |  |  |  |   |         输出目录
//                          |  |  |  |  |  |   最小增益
//                          |  |  |  |  |  直方图桶数
//                          |  |  |  |  最大深度
//                          |  |  |  叶子最小样本
//                          |  |  稀疏聚类数 K
//                          |  稠密阈值 ρ_min（样本数）
//                          网格每维划分数
```

改完再 `mvn exec:java`，直接得到新配置下的 8 张表 + CSV。

### 5.4 参数敏感性扫描（论文 3.x 节「参数影响」小节）

如需做网格扫描，例如扫 `histogramBuckets`：

1. 新建一个 Java 程序（或在 `App.main` 里加一个循环），每次用不同 `ExperimentConfig` 跑一次 `runDataset`。
2. 把 `config.outputDir()` 每次设成不同子目录（如 `target/exp-results/buckets_4`、`target/exp-results/buckets_8`），避免 CSV 被追加覆盖。
3. 扫描结束后用 Python 读入每个目录的 `results_detail.csv`，聚合成一张「桶数 × Q-Error」折线图。

---

## 6. 结果校验（实验是否可信）

跑完一次后，手动核对以下三点：

1. `results_detail.csv` 的数据行数 = `2 (数据集) × 3 (组) × 200 (查询) × 4 (方法) = 4800`，加表头后 `wc -l` 应为 4801。
2. 随便抽 3 条记录，用 Python 或 Java 重算一次 oracle 基数：
   ```python
   import pandas as pd
   data = pd.read_csv("src/main/resources/data/synthetic_D2.csv")
   # 找一条 actual = 478 的查询，在对应 CSV 里查 queryName，读取 lower/upper 后重算：
   mask = (data["attr1"] >= lb1) & (data["attr1"] <= ub1) & (data["attr2"] >= lb2) & (data["attr2"] <= ub2)
   assert mask.sum() == 478
   ```
3. `ProposedCEDEstimator` 的 median Q-Error 在任意（数据集 × 分组）下都应 ≤ `IndependenceEstimator` 的 median。这是分层划分的最低保底。
4. `inferPerQueryUs` 与 `throughputQps` 应为正数；`heapDeltaBytes` 是 JVM 堆内存近似增量，可能受 GC 影响，不要当作精确对象大小。

快速检查命令：

```bash
wc -l target/exp-results/results_detail.csv
head -1 target/exp-results/results_detail.csv
python3 - <<'PY'
import csv
rows = list(csv.DictReader(open("target/exp-results/results_detail.csv")))
print("records =", len(rows))
print("bad latency =", sum(1 for r in rows if float(r["inferPerQueryUs"]) <= 0))
print("bad qps =", sum(1 for r in rows if float(r["throughputQps"]) <= 0))
PY
```

---

## 7. 已跑出的参考结果（可直接对论文有感知）

### synthetic_D2（2 维，10000 条）

| 分组 | 方法 | median Q-Error | max Q-Error |
|------|------|---------------|------------|
| narrow | **ProposedCEDEstimator** | **1.055** | **1.510** |
| narrow | GlobalEquiWidth | 1.266 | 5.045 |
| narrow | IndependenceEstimator | 1.816 | 3.822 |
| wide | **ProposedCEDEstimator** | **1.036** | **1.119** |
| wide | GlobalEquiWidth | 1.067 | 1.311 |

### synthetic_D5（5 维，10000 条）

| 分组 | 方法 | median Q-Error | max Q-Error |
|------|------|---------------|------------|
| narrow | **ProposedCEDEstimator** | **1.103** | **1.897** |
| narrow | GlobalEquiWidth | 10.397 | 345.930 |
| narrow | GlobalEquiDepth | 12.239 | 424.695 |
| narrow | IndependenceEstimator | 20.712 | 126.766 |

**结论**：维度升高后，传统直方图基线在 AVI 假设下迅速退化（总体中位数从 D=2 的 ~1.2 上升到 D=5 的 ~6），而论文方法始终稳定在 median ≈ 1.1、p95 ≈ 1.4 的区间，验证了「分层划分 → 局部建模」路线的有效性。当前 `narrow` 标签不应直接解释为严格低选择率，选择率敏感性需要等查询生成逻辑修正后再写。

---

## 8. 常见问题

| 现象 | 原因 / 排查 |
|------|-------------|
| `找不到资源文件: data/synthetic_D2.csv` | 第一次跑还没落盘，`App.loadOrGenerate` 会自动生成；若你手动 `mvn clean` 而又改了 classpath，删除 `target/` 重新 `compile` 即可 |
| `Maven 编译失败，符号找不到 RangeQueryGenerator` | `mvn clean compile` 重新生成 `target/classes` |
| CSV 结果行数是预期的 2 倍 | `results_detail.csv` 是**追加写**；多次运行想要干净覆盖，先 `rm -rf target/exp-results` 再跑 |
| 某个方法某组出现 `inf` | 查询的真实基数为 0 或估计为 0。在论文里单独注释说明，不计入均值但计入 max；本工具已在表里单列「inf 数」 |
| 想加真实数据（Forest） | 实现 `data/ForestDataLoader.java` → 返回 `DataSet` 即可无缝接入 `ExperimentRunner`；现有数据接口已足够通用 |

---

## 9. 建议的论文章节映射

| 论文小节 | 原始数据来源 |
|----------|-------------|
| 3.x「精度对比主表」 | `results_summary.md` 里的总体汇总表 |
| 3.x「选择率敏感性」 | 当前先不要正式使用；待修正查询选择率生成后再填 |
| 3.x「维度敏感性」 | `synthetic_D2` vs `synthetic_D5` 两组汇总表对比 |
| 3.x「分布箱线图」 | `results_detail.csv` → 自行用 Python/Excel 画箱线图 |
| 3.x「构建/推理开销」 | `results_summary.md` 的 `fit(ms)`、`infer(us/q)`、`qps`、`heap` 列 |
| 3.x「资源占用」 | `results_summary.md` 的 `heap` 列；严格表述为 JVM 堆内存近似增量 |
