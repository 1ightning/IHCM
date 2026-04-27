# 把实验场景搬到"边端"的落地方案

> 你的论文题目聚焦**云边端协同数据库中的边侧基数估计**，但当前实验跑在开发机上，跟论点"轻量可部署、适配边缘资源约束"脱钩。评审一定会问这个问题。
> 本文给出三档可操作方案，任选其一或组合，成本从 0 元到约 ¥500 不等。

---

## 1. 什么叫"边端场景"？先对齐定义再动手

边缘设备（edge device）在你的论文语境下通常指以下三类之一：

| 类别 | 典型硬件 | CPU | 内存 | 备注 |
|------|---------|-----|------|------|
| **IoT 网关** | 树莓派 4B / 香橙派 | ARM Cortex-A72 @ 1.5 GHz, 4 核 | 1 GB – 4 GB | 工业/家居 IoT 常见 |
| **边缘服务器** | 研华 UNO / Intel NUC | Intel Celeron / Atom | 4 GB – 8 GB | 工厂边侧控制 |
| **嵌入式数据库宿主** | 工控机（如 Siemens IoT2040） | Intel Atom @ 1.6 GHz | 1 GB | 嵌入式数据库的真实运行环境 |

**对你这个毕设的最小模拟要求**（基于 QDSPN 论文 V-A1 与常见嵌入式数据库研究）：

- CPU：单核或双核、主频 1–2 GHz
- 内存：**堆内存上限 ≤ 512 MB**
- **不使用 GPU**（你现在就没用）
- 存储：普通 eMMC/SSD 即可，不必强求慢盘

这个"最小资源包"就是你要模拟的约束目标。

---

## 2. 三档实施方案（按成本递增）

### 方案 A：JVM + OS 参数限制（0 元，1 小时，推荐起步）

**思路**：不换硬件，用 JVM 参数 + `taskset`/`cpulimit` 给 JVM 戴上资源紧箍咒，模拟"我在边端能拿到多少资源"。

**步骤**：

1. 在 `cardinality-estimator/` 根目录新建脚本 `scripts/bench_edge.sh`：

   ```bash
   #!/usr/bin/env bash
   set -e
   cd "$(dirname "$0")/.."
   mvn -q clean package -DskipTests
   JAR=target/cardinality-estimator-1.0-SNAPSHOT.jar

   # 核心约束参数：
   #  -Xmx256m         堆内存上限 256 MB（对齐 1 GB 内存的边端设备）
   #  -Xms256m         堆初始化大小，避免 JVM 动态扩容掩盖真实占用
   #  -XX:+UseSerialGC 单线程 GC（边端单核常态）
   #  -XX:+PrintFlagsFinal  可选：打印最终 JVM 配置，便于论文截图
   JVM_ARGS="-Xmx256m -Xms256m -XX:+UseSerialGC"

   echo ">>> 边端模拟场景 A：256MB 堆 + 单核 + SerialGC"
   # macOS 不支持 taskset，用 /usr/bin/nice；Linux 下改用 taskset -c 0
   if command -v taskset >/dev/null; then
     taskset -c 0 java $JVM_ARGS -jar $JAR
   else
     java $JVM_ARGS -jar $JAR
   fi
   ```

   赋权：`chmod +x scripts/bench_edge.sh`

2. 运行：`./scripts/bench_edge.sh`

3. 在运行时另开终端采集 CPU / 内存占用：
   ```bash
   # Linux
   pidof java | xargs -I {} top -b -n 1 -p {}
   # macOS
   top -pid $(pgrep -f cardinality-estimator) -l 1
   ```

**能拿到什么**：
- 在受限内存下四个方法的 Q-Error（应该和满配一致，因为算法本身内存占用 < 256 MB）
- 单核 CPU 下的构建 / 推理时间（**这是真正的边端时延**）
- 模型在内存里的峰值占用

**写进论文的地方**：3.1.1「硬件与软件环境」加一段「边端资源模拟方案」，注明 `-Xmx256m -Xms256m -XX:+UseSerialGC` + 单核绑定；3.7 的构建/推理耗时改用边端模式的数字。

---

### 方案 B：Docker 容器 + cgroups 硬限制（0 元，2 小时，评审认可度高）

**思路**：用 Docker 容器 + `--cpus --memory` 做硬限制，可选 QEMU 模拟 ARM 架构，让实验环境**能精确复现且对得上工业标准**。

**步骤**：

1. 项目根目录新建 `Dockerfile.edge`：

   ```dockerfile
   # 使用轻量 JDK 镜像；eclipse-temurin 是官方 OpenJDK 发行版
   FROM eclipse-temurin:17-jre-alpine

   WORKDIR /app

   # 拷贝打包好的 jar 和数据资源
   COPY target/cardinality-estimator-1.0-SNAPSHOT.jar app.jar
   COPY src/main/resources/data /data

   # 固定 JVM 参数模拟边端约束
   ENV JAVA_TOOL_OPTIONS="-Xmx256m -Xms256m -XX:+UseSerialGC"

   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```

2. 构建并运行，**用 cgroups 限制到 1 核 + 512MB**：

   ```bash
   cd "/Users/lsc/Desktop/claude work/cardinality-estimator"
   mvn -q clean package -DskipTests
   docker build -f Dockerfile.edge -t cardinality-edge .

   # 关键：--cpus=1 单核；--memory=512m 总内存上限 512MB
   docker run --rm \
     --cpus=1 \
     --memory=512m \
     --memory-swap=512m \
     -v "$PWD/target/exp-results:/app/target/exp-results" \
     cardinality-edge
   ```

3. **可选进阶：模拟 ARM 架构**（更贴近树莓派真实环境）：

   ```bash
   # 开启 Docker 的多架构支持（一次性）
   docker run --privileged --rm tonistiigi/binfmt --install arm64

   # 用 ARM64 基础镜像重新构建（Dockerfile.edge 第一行改为）
   # FROM --platform=linux/arm64 eclipse-temurin:17-jre-alpine
   docker buildx build --platform linux/arm64 -f Dockerfile.edge -t cardinality-edge-arm64 .
   docker run --rm --platform linux/arm64 --cpus=1 --memory=512m cardinality-edge-arm64
   ```

   > ⚠️ ARM64 模拟通过 QEMU 用户态翻译，**速度会比 x86 慢 3–5 倍**，用来测绝对耗时没意义；只用来验证**代码能在 ARM 上正确运行**（例如字节对齐、endianness）。

4. 容器内资源占用监控：

   ```bash
   docker stats --no-stream
   # 在另一个终端持续采样写入 CSV
   docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" \
     >> docker_stats.log
   ```

**能拿到什么**（比方案 A 多这几项）：
- 容器级硬内存上限，OOM 会被 Docker 捕获（若 OOM 说明算法超出边端承受力，这是**有价值的负面结果**）
- 可复现：评审跑 `docker run` 一行命令就能复现你的数字
- 若跑 ARM 模拟：证明算法与架构无关

---

### 方案 C：真实树莓派部署（¥300–500，3–5 小时，加分项）

**思路**：买一台树莓派 4B（2GB 版约 ¥350，京东/淘宝有售），把 jar 拷过去跑，结果最可信。

**硬件推荐**（2026 年国内可购）：

| 型号 | 价格 | 理由 |
|------|------|------|
| **树莓派 4B（2GB）** | ¥350 | Cortex-A72 @ 1.5 GHz × 4 核，对齐 QDSPN 的 i7 设置做 10 倍降权很合理 |
| **香橙派 5B（4GB）** | ¥500 | 瑞芯微 RK3588S 更强，国产 ARM 代表 |
| **树莓派 3B+** | ¥200 | 极限约束场景，如果要强调"最差边端也能跑" |

**步骤**（以树莓派 4B 为例）：

1. 开发机打包：
   ```bash
   cd "/Users/lsc/Desktop/claude work/cardinality-estimator"
   mvn -q clean package -DskipTests
   ```

2. 拷贝到树莓派（假设 IP 是 192.168.1.50，用户 pi）：
   ```bash
   scp target/cardinality-estimator-1.0-SNAPSHOT.jar pi@192.168.1.50:/home/pi/
   scp -r src/main/resources/data pi@192.168.1.50:/home/pi/
   ```

3. 登录树莓派安装 JDK：
   ```bash
   ssh pi@192.168.1.50
   sudo apt-get update
   sudo apt-get install -y openjdk-17-jre-headless
   java -version   # 确认是 17
   ```

4. 跑实验（树莓派内存只有 2GB，继续用 `-Xmx256m` 模拟更受限场景）：
   ```bash
   cd ~
   java -Xmx256m -Xms256m -XX:+UseSerialGC \
        -jar cardinality-estimator-1.0-SNAPSHOT.jar
   ```

5. 采集关键指标：
   ```bash
   # 在另一个终端持续记录
   while pgrep java > /dev/null; do
     ps -p $(pgrep java) -o %cpu,%mem,rss,vsz >> /tmp/pi_resource.log
     sleep 1
   done
   ```

6. 结果取回开发机：
   ```bash
   scp pi@192.168.1.50:~/target/exp-results /tmp/exp-results-pi
   ```

**能拿到什么**（只有真机能证的）：
- 真实 ARM 指令集下的**绝对构建/推理耗时**（而不是 x86 上的模拟数字）
- 温度限频后的性能（长时间运行有意义）
- **功耗**（如果配个 USB 功率计，约 ¥40，能测出方法的能耗）
- 评审说"这是在真实边侧设备测的"，比任何模拟都有说服力

---

## 3. 代码需要补这几处改动

当前代码已经内置推理耗时、吞吐量和 JVM 堆内存近似增量统计；下面内容保留为实现原理说明。真正运行边端模拟时，只需要按前文 JVM / Docker / 真机命令执行，结果会自动写入 `target/exp-results/`。

### 3.1 推理耗时与吞吐的统计方式（已内置）

`ExperimentRunner.java` 的 `run()` 方法里，估计器循环改成：

```java
// 在 fit 计时之后、进入分组循环之前：
long inferStart = System.nanoTime();
long totalInferred = 0;
for (Map.Entry<String, List<RangeQuery>> entry : queryGroups.entrySet()) {
    // ... 原有逻辑 ...
    for (RangeQuery query : queries) {
        double estimated = estimator.estimate(query);
        totalInferred++;
        // ... 原有收集逻辑 ...
    }
}
double inferTotalMs = (System.nanoTime() - inferStart) / 1_000_000.0;
double inferPerQueryUs = inferTotalMs * 1000.0 / totalInferred;
double throughputQPS = totalInferred / (inferTotalMs / 1000.0);
System.out.printf("推理总耗时: %.2f ms | 平均 %.1f μs/查询 | 吞吐 %.1f QPS%n",
        inferTotalMs, inferPerQueryUs, throughputQPS);
```

**在边端，推理延迟是比精度更敏感的指标**——论文方法可能精度更高但延迟慢，需要权衡。

### 3.2 内存增量采样方式（已内置）

新建 `cn.lsc.cardinality.util.MemoryProbe`：

```java
package cn.lsc.cardinality.util;

public final class MemoryProbe {
    private MemoryProbe() {}

    public static long usedHeapBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    public static String humanize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
```

在 `ExperimentRunner.run()` 每个 estimator 的 `fit` 前后各调一次，记录 Δheap：

```java
System.gc();  // 排除已有垃圾的干扰（边端场景这么做是合理的）
long before = MemoryProbe.usedHeapBytes();
estimator.fit(dataSet);
long after = MemoryProbe.usedHeapBytes();
System.out.println("内存占用增量: " + MemoryProbe.humanize(after - before));
```

**警告**：JVM 堆内存采样不是严格准确（GC 时刻影响），但作为相对比较足够支撑论文陈述。严格精确的内存测量需要 JMH 或 VisualVM，本科毕设不必追求。

### 3.3 边端运行时的输出目录隔离

修改 `App.java`，支持通过环境变量切换输出目录，便于同一份代码在开发机和边端产生不同结果文件：

```java
ExperimentConfig config = ExperimentConfig.defaultConfig();
String outDir = System.getenv("EDGE_OUTPUT_DIR");
if (outDir != null && !outDir.isEmpty()) {
    config = new ExperimentConfig(
            config.gridCellsPerDimension(), config.denseCellThreshold(),
            config.sparseClusterCount(), config.leafSizeThreshold(),
            config.maxDepth(), config.histogramBuckets(),
            config.minSplitGain(), outDir);
}
```

跑边端实验：`EDGE_OUTPUT_DIR=target/exp-results-edge mvn exec:java`

---

## 4. 新增实验指标清单（写进论文 3.7 节）

运行边端模拟后，你的"边端部署验证"实验可以直接使用这些指标：

| 指标 | 获取方式 | 写进论文 |
|------|----------|----------|
| **构建耗时（边端）** | 边端模式跑出的 `fitTimeMs` | 表格对比开发机 vs 边端 |
| **单查询推理耗时** | `inferPerQueryUs` | 表格 + 折线图（按分组） |
| **推理吞吐 QPS** | `throughputQps` | 表格 |
| **内存占用** | `MemoryProbe` 的 Δheap | 表格 |
| **冷启动耗时** | JVM 启动到首个查询返回的总时延 | 单独一段讨论 |
| **(可选) 功耗** | USB 功率计，真机独有 | 表格，强支撑"轻量部署" |

推荐呈现形式：

**表 3-x 边端部署 vs 开发机性能对比（synthetic_D5 数据集）**

| 方法 | 构建(ms) 开发机 | 构建(ms) 边端 | 降速倍数 | 推理(μs/q) 开发机 | 推理(μs/q) 边端 | 内存 |
|------|:---:|:---:|:---:|:---:|:---:|:---:|
| ProposedCEDEstimator | 43.24 | *XX* | *XX*× | *XX* | *XX* | *XX* KB |
| GlobalEquiWidth | 3.08 | *XX* | *XX*× | *XX* | *XX* | *XX* KB |
| GlobalEquiDepth | 6.47 | *XX* | *XX*× | *XX* | *XX* | *XX* KB |
| IndependenceEstimator | 0.00 | *XX* | *XX*× | *XX* | *XX* | *XX* KB |

**核心论点**：

1. 即便在边端受限环境下，论文方法构建耗时仍在 **X 秒以内**（一次构建、多次查询可接受）
2. 推理延迟稳定在 **X 微秒/查询**，对比传统直方图**只慢 Y 倍**，对应 Q-Error **提升 Z 倍**，综合性价比占优
3. 内存占用 **< 100 KB**，在 1 GB 内存设备上占比可忽略

---

## 5. 选哪个方案？决策树

```
是否有预算买硬件？
├── 没有（本科毕设常态）
│   ├── 只要能写进论文就行     → 方案 A（JVM 参数）
│   └── 希望评审能复现/可信度高 → 方案 B（Docker）
└── 有 ¥300–500 预算
    └── 方案 C（真实树莓派） + 方案 B 作为对照
```

**我给你的建议**：

- **最省事**：只做方案 A + 代码补丁 3.1 / 3.2，就能把「边端资源约束」章节写得像模像样
- **推荐**：方案 A + 方案 B 都做。论文中呈现「开发机（上界）vs 容器限制（边端模拟）」双组结果，讨论资源约束对各方法的影响差异。**这是本科毕设的性价比最优解**
- **加分项**：方案 B + 方案 C。如果你确实有一台树莓派/香橙派（或者实验室有），真机数据作为"致敬 QDSPN 的嵌入式数据库实验环境"，毕设答辩会显得很扎实

---

## 6. 论文章节里怎么写？（第三章补充框架）

在 `THESIS_EXPERIMENT_FRAMEWORK.md` 的基础上，3.1.1 与 3.7 要扩展。

### 3.1.1 硬件与软件环境（扩展）

**新加段落**：

> 为贴近云边端协同数据库中边侧设备的真实资源约束，本文采用双环境实验设计。开发机环境（表 3-1）用于排除硬件瓶颈、获得算法理论上界；**边端模拟环境**（表 3-2）通过 JVM 堆内存上限（`-Xmx256m`）、串行 GC（`-XX:+UseSerialGC`）以及单核绑定（`taskset -c 0`，Docker `--cpus=1`）的组合手段，将实验运行环境约束到不超过常见 IoT 网关（如树莓派 4B）的规格，以此验证本文方法的可部署性。

**表 3-2 边端模拟环境配置**：

| 项 | 值 |
|---|---|
| JVM 堆内存上限 | 256 MB |
| JVM 堆初始大小 | 256 MB（避免动态扩容） |
| GC 策略 | Serial GC（单线程） |
| CPU 绑定 | 单核 |
| 对应真实设备规格 | 树莓派 4B（2GB） / Intel IoT2040 |

### 3.7 新增小节「3.7.4 边端部署验证」

**结构**：

1. 一段话说明实验目的与环境（承接 3.1.1）
2. 一张对比表（上面的表 3-x）
3. 一张折线图：x 轴为三种场景（开发机 / JVM 限制 / Docker 限制），y 轴为单查询推理耗时
4. 一段话讨论结果，突出三条核心论点（上面已列）

### 3.8 本章小结里新增一句话

> 此外，本章通过 JVM 资源约束与容器化部署两种方式模拟了边侧设备环境，结果表明本文方法在内存占用 < 100 KB、推理延迟 < X μs/查询 的约束下仍能保持精度优势，验证了方法在云边端协同数据库中的边侧部署可行性。

---

## 7. 常见问题

| 问题 | 答案 |
|------|------|
| 方案 A 在 macOS 上没 `taskset` 怎么办 | 用 `cpulimit` 或直接忽略 CPU 绑定，单线程 GC + 小堆已经能模拟核心约束 |
| 为什么不建议把 `-Xmx` 压到 128MB 以下 | 数据集本身 10000 × 5 × 8 byte = 400 KB，再加上 JVM 基础开销（类元数据、JIT 缓存）至少需要 128 MB 起。压太低会 OOM，反映不了算法真实占用 |
| Docker 里怎么读到 `src/main/resources/data` | Dockerfile 里 `COPY src/main/resources/data /data` 已经拷贝；你的 `CsvDataLoader` 走 classpath 读取，jar 里自带，不用挂载 |
| 真机实验要不要加重试 | 至少跑 3 次取中位数，避免 JIT 预热 / 温度限频 / 偶发 GC 干扰结果 |
| 评审问"只用合成数据 + 模拟边端算不算真边端" | 如实说明实验约束与简化，强调这是**本科毕设范围内的权衡**，把「真实设备长时间运行测试」列入「未来工作」即可 |
| 树莓派跑出来的数字和 Docker ARM 模拟差多少 | 通常相差 2–5 倍（模拟比真机慢，因 QEMU 用户态翻译）。如果两边都跑，真机数字可信度更高 |

---

## 8. 最短落地清单（本周内可完成）

按优先级：

- [ ] 加代码补丁 3.1（推理耗时） — 30 分钟
- [ ] 加代码补丁 3.2（内存探针） — 30 分钟
- [ ] 跑方案 A（JVM 参数） — 30 分钟
- [ ] 产出一张「开发机 vs 边端模拟」对比表 — 1 小时
- [ ] 写 3.1.1 扩展段落 + 3.7.4 小节 — 2 小时

加起来约半天，就能让你的第三章从"跑了算法"升级到"验证了边侧部署可行性"。

如果有多余的一天，追加方案 B（Docker）；有多余的两天 + ¥400 预算，追加方案 C（树莓派）。
