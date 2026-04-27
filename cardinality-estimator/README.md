# 面向云边端协同数据库的基数估计模型 —— 实验程序

本项目为论文《面向云边端协同数据库的基数估计模型研究》（李思成，2026）后期实验部分的原型实现。论文聚焦**云边端（CED）协同数据库**下边缘节点的**单表基数估计**问题，在资源受限的边侧环境中，针对合取型范围查询，构建一种兼顾**估计精度、推理效率与可部署性**的轻量级数据驱动直方图优化方法，为后续代价模型与查询迁移决策提供可靠输入。

---

## 1. 研究问题与方法概述

### 1.1 研究问题

在有限资源约束下对查询基数

$$
Card(T,q) = |T| \cdot Sel(q)
$$

进行快速、高精度估计（详见论文 2.1 节）。

### 1.2 方法主线：分层空间划分 —— 局部相关性解耦 —— 结果汇总

本实现严格遵循论文 2.2–2.6 节的四阶段技术路线：

| 论文章节 | 核心算法 | 代码文件 |
|----------|----------|----------|
| 2.3 两阶段宏观数据空间离散化 | GDS（网格密度策略）+ K-means 补充聚类（**算法 1**） | `algorithm/GridDensityStrategy.java`、`algorithm/KMeansPartitioner.java` |
| 2.4 MBB 与 HRDAG 构建 | 最小外接超矩形 + 层次有向无环图递归插入（**算法 2**） | `algorithm/Hrdag.java`、`model/HyperRectangle.java`、`model/MacroCluster.java` |
| 2.5 基于轴向二分的递归划分 | Welford 在线统计 + 协方差迹最小化（**算法 3**） | `algorithm/AxialBinarySplitter.java`、`model/RegionNode.java` |
| 2.6 叶子节点局部建模与基数推断 | 一维等深直方图连乘 + 命中叶子汇总（**公式 12–14**） | `model/Histogram.java`、`algorithm/ProposedEstimator.java` |

---

## 2. 环境要求

| 环境 | 版本要求 |
|------|----------|
| JDK | 17 或更高 |
| Maven | 3.8 或更高 |
| 操作系统 | macOS / Linux / Windows 均可 |

验证环境：

```bash
java -version
mvn -version
```

---

## 3. 项目结构

```
cardinality-estimator/
├── pom.xml
├── src/main/java/cn/lsc/cardinality/
│   ├── App.java                         # 实验主入口
│   ├── ExperimentRunner.java            # 实验调度与指标统计
│   ├── config/
│   │   └── ExperimentConfig.java        # 实验参数集中配置
│   ├── data/
│   │   ├── DataSet.java                 # 数据集抽象
│   │   ├── DataPoint.java               # 数据点
│   │   ├── RangeQuery.java              # 合取型范围查询
│   │   ├── CsvDataLoader.java           # CSV 读取工具
│   │   └── GaussianDataGenerator.java   # 高斯分布数据生成器
│   ├── model/
│   │   ├── HyperRectangle.java          # MBB / 超矩形
│   │   ├── MacroCluster.java            # 宏观空间簇
│   │   ├── RegionNode.java              # 轴向二分树节点
│   │   └── Histogram.java               # 一维等深直方图
│   ├── algorithm/
│   │   ├── GridDensityStrategy.java     # 算法 1：GDS 宏观离散化
│   │   ├── KMeansPartitioner.java       # 算法 1：稀疏区域补充聚类
│   │   ├── Hrdag.java                   # 算法 2：HRDAG 构建与查询路由
│   │   ├── AxialBinarySplitter.java     # 算法 3：轴向二分递归划分
│   │   └── ProposedEstimator.java       # 论文方法总装
│   └── baseline/
│       ├── CardinalityEstimator.java    # 估计器接口
│       ├── GlobalHistogramEstimator.java# 全局等宽/等深直方图基线
│       └── IndependenceEstimator.java   # 属性值独立（AVI）基线
└── src/main/resources/data/
    ├── sample_data.csv                  # 默认数据集（200 条，4 维，含噪声高斯）
    └── sample_queries.csv               # 默认查询集（5 条合取范围查询）
```

---

## 4. 数据与查询格式

### 4.1 数据集 CSV

- 第一行为属性表头；
- 其余每行为一条数据记录，属性值必须全部为数值。

示例（`sample_data.csv`）：

```csv
temperature,humidity,pressure,vibration
30.52,73.12,1022.55,0.79
24.46,82.10,1033.46,0.41
...
```

### 4.2 查询集 CSV

- 第一列为查询名称；
- 其余列按 `属性名_min / 属性名_max` 成对出现，顺序与列名必须与数据集表头一致。

示例（`sample_queries.csv`）：

```csv
name,temperature_min,temperature_max,humidity_min,humidity_max,pressure_min,pressure_max,vibration_min,vibration_max
q1,20,30,50,80,1010,1030,0.2,0.8
q2,25,35,40,70,1000,1025,0.1,0.6
...
```

---

## 5. 编译与运行

### 5.1 编译

```bash
cd "/Users/lsc/Desktop/claude work/cardinality-estimator"
mvn clean compile
```

### 5.2 运行默认实验（示例数据 + 示例查询）

```bash
mvn exec:java
```

### 5.3 运行自定义数据

当前主入口 `App.java` 固定运行 `synthetic_D2` 和 `synthetic_D5` 两组合成数据，用于论文第三章的主准确性对比实验。若要接入自定义数据，需要在 `App.runDataset(...)` 或 `CsvDataLoader.loadDataSet(...)` 的调用处增加新的数据集入口；现阶段不要使用 `-Dexec.args`，因为主程序尚未解析该参数。

### 5.4 生成新的高斯分布数据

```bash
mvn compile
java -cp target/classes cn.lsc.cardinality.data.GaussianDataGenerator
```

默认生成 200 条 4 维（temperature / humidity / pressure / vibration）含 10% 噪声的高斯样本到 `sample_data.csv`。若需修改样本量、维度、均值、标准差或噪声比例，直接编辑 `GaussianDataGenerator.main` 中的参数：

```java
DataSet dataSet = generate(
    1000,                                         // 样本量
    new String[]{"attr1","attr2","attr3"},       // 属性名
    new double[]{100.0, 50.0, 25.0},             // 均值
    new double[]{10.0,  5.0,  2.5},              // 标准差
    0.15                                          // 噪声比例
);
```

### 5.5 打包后独立运行

```bash
mvn package
java -jar target/cardinality-estimator-1.0-SNAPSHOT.jar
```

---

## 6. 实验参数配置

所有关键参数集中在 `config/ExperimentConfig.java`，与论文各算法一一对应：

| 参数 | 默认值 | 对应论文符号 | 说明 |
|------|--------|--------------|------|
| `gridCellsPerDimension` | 4 | $g_i$（算法 1） | 每维等宽网格划分数 |
| `denseCellThreshold` | 3 | $\rho_{min}$（定义 2） | 稠密网格最小样本数阈值 |
| `sparseClusterCount` | 2 | $K$（算法 1） | 稀疏区域 K-means 簇数 |
| `leafSizeThreshold` | 6 | —— | 叶子节点停止划分的最小样本数 |
| `maxDepth` | 4 | —— | 轴向二分最大递归深度 |
| `histogramBuckets` | 4 | —— | 叶子节点一维等深直方图桶数 |
| `minSplitGain` | 1e-6 | —— | 划分增益阈值（避免无效切分） |

修改默认配置示例：

```java
public static ExperimentConfig defaultConfig() {
    return new ExperimentConfig(4, 3, 2, 6, 4, 4, 1e-6);
}
```

---

## 7. 对比方法

`ExperimentRunner` 默认同时运行论文方法与三条经典基线（覆盖论文第三章“对比实验”拟选定的传统方法）：

| 方法 | 类名 | 说明 |
|------|------|------|
| 论文方法 | `ProposedCEDEstimator` | GDS + K-means + HRDAG + 轴向二分 + 叶子一维等深直方图 |
| 等宽直方图 | `GlobalEquiWidthHistogram` | 全局一维等宽直方图 + AVI 假设 |
| 等深直方图 | `GlobalEquiDepthHistogram` | 全局一维等深直方图 + AVI 假设 |
| 纯独立性假设 | `IndependenceEstimator` | 不使用任何统计结构，仅凭区间比例相乘 |

三条基线对应论文 2.3 节对 AVI 假设失效问题的讨论，用于量化"先划分、后建模"方案相对于传统直方图的收益。

---

## 8. 输出结果与评价指标

控制台输出示例：

```
================================================================================
估计器: ProposedCEDEstimator
训练耗时: 12.45 ms
--------------------------------------------------------------------------------
查询    真实基数    估计基数    绝对误差    相对误差      Q-Error
q1      15.0        16.2        1.2         8.00%        1.08
q2      12.0        11.5        0.5         4.17%        1.04
...
--------------------------------------------------------------------------------
平均相对误差: 8.50%
平均 Q-Error: 1.12
最大 Q-Error: 1.45
推理总耗时: 4.60 ms | 平均: 7.66 us/query | 吞吐: 130514.14 QPS | JVM堆增量: 395.9 KB
================================================================================
```

| 指标 | 计算方式 | 论文用途 |
|------|----------|----------|
| 真实基数 | 对原数据集精确扫描计数 | Ground truth |
| 绝对误差 | \|估计值 − 真实值\| | 辅助分析 |
| 相对误差 | 绝对误差 / 真实值 | **精度对比主指标之一** |
| Q-Error | max(估计值/真实值, 真实值/估计值) | **精度对比主指标之一**（越接近 1 越好） |
| 训练耗时（ms） | `fit` 阶段纳秒计时 | **构建开销指标** |
| 推理总耗时（ms） | 累加全部 `estimate` 调用耗时 | 在线推理开销 |
| 单查询推理耗时（us/query） | 推理总耗时 / 查询数 | 边侧低延迟指标 |
| 推理吞吐（QPS） | 查询数 / 推理总耗时 | 批量负载处理能力 |
| JVM 堆增量 | `fit` 前后 JVM 已用堆内存差值 | 资源占用近似指标 |

结果文件位于 `target/exp-results/`：

- `results_detail.csv`：逐查询明细，含 `fitTimeMs`、`inferTotalMs`、`inferPerQueryUs`、`throughputQps`、`heapDeltaBytes`。
- `results_summary.md`：按数据集和查询组汇总，论文表格建议优先从这里取数。

---

## 9. 实验结果如何对应到论文实验部分

论文第三章已明确，实验验证将围绕**估计精度、推理开销、资源消耗**三方面展开，并分析**多属性相关、数据分布不均、查询范围变化**三类场景。下表给出本程序输出与论文拟撰写实验小节的对照关系，实验结果可直接按此映射填入论文图表。

### 9.1 精度对比实验（论文 3.x 节 · 方法对比主表）

- **做法**：运行 `mvn clean compile exec:java`，按 `target/exp-results/results_summary.md` 采集各估计器在 `synthetic_D2` 与 `synthetic_D5` 上的 **median / mean / p90 / p95 / max Q-Error**。
- **论文用途**：
  - 绘制**方法 × 指标**的主对比表，论证 `ProposedCEDEstimator` 在平均与最差情况均优于三条基线；
  - 针对 D=5 场景下传统基线的尾部误差放大现象，结合论文 2.3 节关于 AVI 假设失效的定性分析；
  - 将 `IndependenceEstimator` 的退化表现作为"无任何统计结构"的下界参照。

### 9.2 训练与推理开销（论文 3.x 节 · 效率与资源分析）

- **做法**：运行 `mvn clean compile exec:java` 后，读取 `target/exp-results/results_summary.md` 中的 `fit(ms)`、`infer(us/q)`、`qps`、`heap` 四列。
- **论文用途**：
  - 训练耗时对应论文所述"构建阶段开销"；
  - `infer(us/q)` 对应"在线推断开销"；
  - `qps` 用于说明批量查询处理能力；
  - `heap` 是 JVM 堆内存近似增量，可用于论证方法在边侧**轻量可部署**。

> 注意：`heap` 受 JVM 垃圾回收时机影响，论文中建议写作“JVM 堆内存近似增量”，不要表述为严格模型大小。

### 9.3 参数敏感性分析（论文 3.x 节 · 参数影响）

对应论文第三章"结合实验结果分析网格划分粒度、稀疏区域聚类数、叶子节点划分停止条件以及局部直方图桶数等参数对估计性能的影响"。

建议按如下组合做网格扫描实验，并将结果绘制为折线图：

| 被扫描参数 | 推荐取值 | 固定其它参数 | 观测指标 |
|------------|----------|---------------|----------|
| `gridCellsPerDimension` | 2, 3, 4, 6, 8 | 其它用默认值 | 平均 Q-Error、训练耗时 |
| `sparseClusterCount` | 0, 1, 2, 4, 8 | 其它用默认值 | 平均 Q-Error、叶子数量 |
| `leafSizeThreshold` | 2, 4, 6, 10, 20 | 其它用默认值 | 平均 Q-Error、最大 Q-Error |
| `histogramBuckets` | 2, 4, 8, 16 | 其它用默认值 | 平均相对误差 |

只需修改 `ExperimentConfig.defaultConfig()` 对应字段并重新执行 `mvn exec:java`，即可得到每组曲线数据。

### 9.4 数据分布与查询范围敏感性（论文 3.x 节 · 场景鲁棒性）

对应论文"多属性相关、数据分布不均以及查询范围变化等场景"。

1. **变化相关性强度**：使用 `GaussianDataGenerator` 时调节不同属性的均值与方差，人工制造相关/不相关对照组（目前生成器按维度独立采样，如需强相关可在后续扩展中对协方差做线性组合）。
2. **变化数据规模**：将 `generate` 第一个参数由 200 增加至 2 000、10 000、50 000，观察**精度与训练/推理耗时**随规模变化的趋势。
3. **变化查询选择率**：在 `sample_queries.csv` 中同时加入窄范围（选择率 < 5%）、中等范围（10%–30%）、宽范围（> 50%）三类查询，分别统计三组 Q-Error 平均值，考察方法在**高选择率与低选择率**下的鲁棒性。

### 9.5 消融实验（论文 3.x 节 · 模块贡献分析）

验证论文四大模块（GDS、K-means 补充、HRDAG、轴向二分）各自的贡献：

| 变体 | 如何开启 | 预期对比 |
|------|----------|----------|
| 仅 GDS（关闭 K-means） | `sparseClusterCount = 0` | 验证稀疏区域补充的必要性 |
| 仅叶子直方图（关闭轴向二分） | `maxDepth = 0` | 验证轴向二分对 AVI 假设的修正效果 |
| 完整论文方法 | 默认配置 | 作为上界 |

以上三组结果差值即为各模块对"平均 Q-Error 下降幅度"的定量贡献，可作为论文消融表直接引用。

---

## 10. 最小可复现实验

```bash
cd "/Users/lsc/Desktop/claude work/cardinality-estimator"
mvn clean compile exec:java
```

预期输出：四个估计器（论文方法 + 等宽直方图 + 等深直方图 + 独立性假设）在两组合成数据、三组查询上的 Q-Error 汇总，同时给出构建耗时、单查询推理耗时、吞吐量与 JVM 堆内存近似增量。该结果即可作为论文"准确性 + 可部署性"实验表的原始数据。

---

## 11. 常见问题

| 现象 | 排查方式 |
|------|----------|
| `找不到数据文件` | 确认 CSV 位于 `src/main/resources/data/`，`-Dexec.args` 使用相对路径以 `data/` 开头 |
| `属性名不匹配` | 查询文件必须包含数据集**全部属性**的 `_min` 与 `_max` 列 |
| `Maven 编译失败` | `java -version` 确认 JDK ≥ 17 |
| `某个估计器 Q-Error 为 ∞` | 该查询真实基数为 0 或估计基数为 0，属定义上界，可在论文中单独注释说明 |
| `平均误差与参数调整不敏感` | 数据量过小或查询选择率过高，建议先将数据量扩大到 ≥ 1 000 再重做扫描 |

---

## 12. 技术要点回顾

1. **网格密度策略（GDS）**：将 $d$ 维空间离散为等宽网格，保留密度 $\ge \rho_{min}$ 的稠密网格，构建无向邻接图后提取连通分量形成宏观空间簇。
2. **K-means 补充覆盖**：对 GDS 未覆盖的稀疏样本做轻量级聚类，确保建模覆盖率，避免低密度样本被直接忽略。
3. **MBB + HRDAG**：用最小外接超矩形抽象每个宏观簇，并以"包含 / 相交 / 不相交"三类几何关系递归构建层次有向无环图，支持查询时的空间剪枝与路由。
4. **轴向二分递归划分**：在每个宏观簇 MBB 内沿最优轴分裂，借助 Welford 在线统计增量维护协方差矩阵的迹，$O(\gamma \cdot d)$ 时间完成单节点划分，逐步削弱属性相关性。
5. **叶子节点局部建模**：在残余相关性足够低的叶子上对每一维构建等深直方图，查询时将各维边缘选择率连乘得局部选择率。
6. **全局基数汇总**：$Card(T,q)=\sum_{L_k \in \mathcal{L}_{hit}} |L_k|\cdot \prod_{i=1}^{d} sel_i^{(L_k)}(q)$，对所有命中叶子的贡献相加，即为最终估计基数。
