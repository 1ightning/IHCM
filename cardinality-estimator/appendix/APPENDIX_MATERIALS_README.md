# 附录材料说明

这个文件夹用于整理论文附录和实验复现材料。所有结果均来自腾讯云 2 核 2G 环境下的实验输出；没有包含旧的本地图像。

## 文件夹结构

- `appendix_draft.tex`：英文 LaTeX 附录草稿，可复制到论文项目的附录文件中再按模板调整。
- `figures/`：论文可用的云端实验 PDF 图像。
- `tables/`：可直接引用或复制的 LaTeX 表格、汇总 CSV/MD。
- `data/`：云服务器实验原始结果文件。
- `scripts/`：生成图表的 Python 脚本。

## 建议放入正文的材料

正文实验章优先使用这些文件：

- `figures/dataset_distribution_cloud.pdf`：放在 `Datasets` 小节，展示 D2 原始二维分布和 D5 PCA 投影。
- `tables/cloud_overall_qerror_table.tex`：放在 `Overall Accuracy Comparison`。
- `figures/overall_p95_qerror_cloud.pdf`：放在 `Overall Accuracy Comparison`，突出尾部误差。
- `figures/synthetic_D5_qerror_boxplot_qdspn_style_cloud.pdf`：放在 `Error Distribution Analysis`，展示高维场景误差分布。
- `tables/cloud_efficiency_table.tex`：放在 `Performance and Resource Overhead`。
- `figures/infer_time_cloud.pdf`：放在 `Performance and Resource Overhead`，展示在线推理耗时。
- `figures/heap_overhead_cloud.pdf`：放在 `Performance and Resource Overhead`，展示 JVM 堆内存近似增量。

## 建议放入附录的材料

附录可以放这些补充材料：

- 实验环境、运行命令和输出文件说明。
- `tables/cloud_overall_and_efficiency_summary.md` 或对应 CSV 的汇总说明。
- `figures/overall_median_qerror_cloud.pdf` 与 `figures/overall_mean_qerror_cloud.pdf`。
- `figures/synthetic_D2_qerror_boxplot_qdspn_style_cloud.pdf`。
- `figures/synthetic_D2_group_median_qerror_cloud.pdf` 与 `figures/synthetic_D5_group_median_qerror_cloud.pdf`。
- `figures/fit_time_cloud.pdf`。
- `data/results_summary.md` 与 `data/results_detail.csv` 作为复现实验材料，不建议把完整 CSV 粘贴进论文。

## 重要注意事项

- `narrow / medium / wide` 只能写作 nominal workload groups，不能写成严格选择率分区。
- `heap` 是 JVM 堆内存近似增量，受 GC 影响，不能写成精确模型大小。
- 当前没有真实消融实验数据，不要在附录中编造 ablation study 结果。
- 如果把 `appendix_draft.tex` 并入论文项目，需要确认图片路径是否仍为 `figures/...`；若图片放到论文项目的其他目录，需要同步修改 `\includegraphics` 路径。

## 复现图像脚本

- `scripts/make_cloud_experiment_artifacts.py`：根据 `results_detail.csv` 生成准确性、效率和资源开销图。
- `scripts/make_dataset_scatter_plots.py`：根据 `synthetic_D2.csv` 和 `synthetic_D5.csv` 生成数据集散点图与 PCA 投影图。

脚本中的输入路径是当前电脑上的绝对路径。如果将项目迁移到其他机器，需要先修改脚本开头的路径常量。
