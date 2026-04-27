from __future__ import annotations

import math
from pathlib import Path

import numpy as np
import pandas as pd

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Patch


SOURCE_DIR = Path("/Users/lsc/Desktop/毕业设计/exp-results")
DETAIL_CSV = SOURCE_DIR / "results_detail.csv"
OUTPUT_DIR = Path("/Users/lsc/Desktop/claude work/cloud_experiment_outputs_clean")
FIG_DIR = OUTPUT_DIR / "figures"
TABLE_DIR = OUTPUT_DIR / "tables"

METHOD_ORDER = [
    "ProposedCEDEstimator",
    "GlobalEquiWidthHistogram",
    "GlobalEquiDepthHistogram",
    "IndependenceEstimator",
]

METHOD_LABELS = {
    "ProposedCEDEstimator": "Proposed",
    "GlobalEquiWidthHistogram": "EW-Hist",
    "GlobalEquiDepthHistogram": "ED-Hist",
    "IndependenceEstimator": "Indep.",
}

DATASET_LABELS = {
    "synthetic_D2": "D2",
    "synthetic_D5": "D5",
}

COLORS = {
    "ProposedCEDEstimator": "#c7537d",
    "GlobalEquiWidthHistogram": "#8fb6d8",
    "GlobalEquiDepthHistogram": "#4f6faf",
    "IndependenceEstimator": "#26285f",
}

HATCHES = {
    "ProposedCEDEstimator": "//",
    "GlobalEquiWidthHistogram": "++",
    "GlobalEquiDepthHistogram": "oo",
    "IndependenceEstimator": "**",
}


def ensure_dirs() -> None:
    FIG_DIR.mkdir(parents=True, exist_ok=True)
    TABLE_DIR.mkdir(parents=True, exist_ok=True)


def load_detail() -> pd.DataFrame:
    df = pd.read_csv(DETAIL_CSV)
    df["dataset_label"] = df["dataset"].map(DATASET_LABELS)
    df["method_label"] = df["method"].map(METHOD_LABELS)
    df["qError"] = pd.to_numeric(df["qError"], errors="coerce")
    df = df[df["method"].isin(METHOD_ORDER)].copy()
    return df


def percentile(values: pd.Series, q: float) -> float:
    finite = values[np.isfinite(values)]
    if finite.empty:
        return math.inf
    return float(np.percentile(finite.to_numpy(), q))


def build_overall_summary(df: pd.DataFrame) -> pd.DataFrame:
    rows = []
    for dataset in DATASET_LABELS:
        for method in METHOD_ORDER:
            sub = df[(df["dataset"] == dataset) & (df["method"] == method)]
            finite = sub["qError"][np.isfinite(sub["qError"])]
            metrics = sub.iloc[0]
            rows.append(
                {
                    "dataset": dataset,
                    "dataset_label": DATASET_LABELS[dataset],
                    "method": method,
                    "method_label": METHOD_LABELS[method],
                    "median": percentile(sub["qError"], 50),
                    "mean": float(finite.mean()) if not finite.empty else math.inf,
                    "p90": percentile(sub["qError"], 90),
                    "p95": percentile(sub["qError"], 95),
                    "max": float(finite.max()) if not finite.empty else math.inf,
                    "inf_count": int(np.isinf(sub["qError"]).sum()),
                    "fit_ms": float(metrics["fitTimeMs"]),
                    "infer_us_q": float(metrics["inferPerQueryUs"]),
                    "qps": float(metrics["throughputQps"]),
                    "heap_bytes": int(metrics["heapDeltaBytes"]),
                }
            )
    return pd.DataFrame(rows)


def build_group_summary(df: pd.DataFrame) -> pd.DataFrame:
    rows = []
    for dataset in DATASET_LABELS:
        for group in ["narrow", "medium", "wide"]:
            for method in METHOD_ORDER:
                sub = df[
                    (df["dataset"] == dataset)
                    & (df["group"] == group)
                    & (df["method"] == method)
                ]
                rows.append(
                    {
                        "dataset": dataset,
                        "dataset_label": DATASET_LABELS[dataset],
                        "group": group,
                        "method": method,
                        "method_label": METHOD_LABELS[method],
                        "median": percentile(sub["qError"], 50),
                    }
                )
    return pd.DataFrame(rows)


def human_heap(num_bytes: int) -> str:
    if abs(num_bytes) < 1024:
        return f"{num_bytes} B"
    kb = num_bytes / 1024
    if abs(kb) < 1024:
        return f"{kb:.1f} KB"
    return f"{kb / 1024:.2f} MB"


def save_fig(fig: plt.Figure, name: str) -> None:
    for suffix in ["pdf", "png"]:
        fig.savefig(FIG_DIR / f"{name}.{suffix}", bbox_inches="tight", dpi=300)
    plt.close(fig)


def style_axes(ax: plt.Axes) -> None:
    ax.grid(axis="y", linestyle="--", linewidth=0.6, alpha=0.35)
    ax.grid(axis="x", linestyle=":", linewidth=0.45, alpha=0.18)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.tick_params(axis="both", labelsize=9)


def grouped_bar(
    summary: pd.DataFrame,
    value_col: str,
    ylabel: str,
    title: str,
    filename: str,
    log_scale: bool = False,
) -> None:
    fig, ax = plt.subplots(figsize=(7.4, 4.2))
    fig.subplots_adjust(right=0.78, top=0.88, bottom=0.16)
    datasets = ["synthetic_D2", "synthetic_D5"]
    x = np.arange(len(datasets))
    width = 0.18
    offsets = np.linspace(-1.5 * width, 1.5 * width, len(METHOD_ORDER))

    for offset, method in zip(offsets, METHOD_ORDER):
        values = []
        for dataset in datasets:
            row = summary[(summary["dataset"] == dataset) & (summary["method"] == method)].iloc[0]
            values.append(float(row[value_col]))
        positive_values = [v for v in values if v > 0]
        floor_value = min(positive_values) / 10.0 if positive_values else 0.001
        plot_values = [max(v, floor_value) if log_scale else v for v in values]
        bars = ax.bar(
            x + offset,
            plot_values,
            width=width,
            label=METHOD_LABELS[method],
            color=COLORS[method],
            edgecolor="#202020",
            linewidth=0.65,
            hatch=HATCHES[method],
            alpha=0.92,
        )

    ax.set_xticks(x)
    ax.set_xticklabels([DATASET_LABELS[d] for d in datasets])
    ax.set_ylabel(ylabel)
    ax.set_title(title, fontsize=11, pad=10)
    if log_scale:
        ax.set_yscale("log")
        all_positive = []
        for method in METHOD_ORDER:
            for dataset in datasets:
                row = summary[(summary["dataset"] == dataset) & (summary["method"] == method)].iloc[0]
                value = float(row[value_col])
                if value > 0:
                    all_positive.append(value)
        if all_positive:
            ax.set_ylim(bottom=min(all_positive) / 10.0, top=max(all_positive) * 2.5)
        else:
            ax.set_ylim(bottom=0.001)
    else:
        finite_values = []
        for method in METHOD_ORDER:
            for dataset in datasets:
                row = summary[(summary["dataset"] == dataset) & (summary["method"] == method)].iloc[0]
                finite_values.append(float(row[value_col]))
        ax.set_ylim(bottom=0, top=max(finite_values) * 1.18 if finite_values else 1.0)
    style_axes(ax)
    ax.legend(frameon=False, fontsize=9, loc="center left", bbox_to_anchor=(1.01, 0.5))
    save_fig(fig, filename)


def boxplot_qerror(df: pd.DataFrame, dataset: str) -> None:
    sub = df[(df["dataset"] == dataset) & np.isfinite(df["qError"])].copy()
    data = [sub[sub["method"] == method]["qError"].to_numpy() for method in METHOD_ORDER]
    labels = [METHOD_LABELS[m] for m in METHOD_ORDER]

    fig, ax = plt.subplots(figsize=(8.2, 4.4))
    fig.subplots_adjust(right=0.80, top=0.88, bottom=0.18)
    bp = ax.boxplot(
        data,
        tick_labels=labels,
        patch_artist=True,
        showfliers=True,
        showmeans=True,
        meanprops={
            "marker": "D",
            "markerfacecolor": "#f4d27a",
            "markeredgecolor": "#4b3b00",
            "markersize": 4,
        },
        medianprops={"color": "#a33a1f", "linewidth": 1.2},
        whiskerprops={"color": "#555555", "linewidth": 0.8},
        capprops={"color": "#555555", "linewidth": 0.8},
        flierprops={
            "marker": "o",
            "markerfacecolor": "white",
            "markeredgecolor": "#333333",
            "markersize": 3,
            "alpha": 0.75,
        },
    )
    for patch, method in zip(bp["boxes"], METHOD_ORDER):
        patch.set_facecolor(COLORS[method])
        patch.set_edgecolor("#202020")
        patch.set_linewidth(0.8)
        patch.set_hatch(HATCHES[method])
        patch.set_alpha(0.9)

    ax.set_yscale("log")
    ax.set_ylim(bottom=0.9)
    ax.set_ylabel("Q-Error")
    ax.set_title(f"Q-Error Distribution on {DATASET_LABELS[dataset]} (Tencent Cloud)", fontsize=11, pad=10)
    style_axes(ax)
    legend_handles = [
        Patch(facecolor=COLORS[m], edgecolor="#202020", hatch=HATCHES[m], label=METHOD_LABELS[m], alpha=0.9)
        for m in METHOD_ORDER
    ]
    ax.legend(handles=legend_handles, frameon=False, fontsize=9, loc="center left", bbox_to_anchor=(1.01, 0.5))
    save_fig(fig, f"{dataset}_qerror_boxplot_qdspn_style_cloud")


def group_median_plot(group_summary: pd.DataFrame, dataset: str) -> None:
    sub = group_summary[group_summary["dataset"] == dataset]
    fig, ax = plt.subplots(figsize=(7.4, 4.2))
    fig.subplots_adjust(right=0.78, top=0.88, bottom=0.16)
    groups = ["narrow", "medium", "wide"]
    x = np.arange(len(groups))
    width = 0.18
    offsets = np.linspace(-1.5 * width, 1.5 * width, len(METHOD_ORDER))
    for offset, method in zip(offsets, METHOD_ORDER):
        values = [
            float(sub[(sub["group"] == group) & (sub["method"] == method)]["median"].iloc[0])
            for group in groups
        ]
        ax.bar(
            x + offset,
            values,
            width=width,
            label=METHOD_LABELS[method],
            color=COLORS[method],
            edgecolor="#202020",
            linewidth=0.65,
            hatch=HATCHES[method],
            alpha=0.92,
        )

    ax.set_xticks(x)
    ax.set_xticklabels(groups)
    ax.set_ylabel("Median Q-Error")
    ax.set_title(f"Grouped Median Q-Error on {DATASET_LABELS[dataset]}", fontsize=11, pad=10)
    ax.set_yscale("log")
    ax.set_ylim(bottom=0.9)
    style_axes(ax)
    ax.legend(frameon=False, fontsize=9, loc="center left", bbox_to_anchor=(1.01, 0.5))
    save_fig(fig, f"{dataset}_group_median_qerror_cloud")


def write_tex_tables(summary: pd.DataFrame) -> None:
    qerror_lines = [
        r"\begin{table}[htbp]",
        r"    \centering",
        r"    \caption{Overall Q-Error comparison on \texttt{D2} and \texttt{D5}}",
        r"    \label{tab:overall_qerror_comparison}",
        r"    \small",
        r"    \setlength{\tabcolsep}{3pt}",
        r"    \begin{tabular}{@{}llrrrrr@{}}",
        r"        \toprule",
        r"        Dataset & Method & Median & Mean & P90 & P95 & Max \\",
        r"        \midrule",
    ]
    for dataset in ["synthetic_D2", "synthetic_D5"]:
        if dataset == "synthetic_D5":
            qerror_lines.append(r"        \midrule")
        for method in METHOD_ORDER:
            row = summary[(summary["dataset"] == dataset) & (summary["method"] == method)].iloc[0]
            qerror_lines.append(
                "        "
                + rf"\texttt{{{row['dataset_label']}}} & \texttt{{{row['method_label']}}} "
                + rf"& {row['median']:.3f} & {row['mean']:.3f} & {row['p90']:.3f} "
                + rf"& {row['p95']:.3f} & {row['max']:.3f} \\"
            )
    qerror_lines.extend(
        [
            r"        \bottomrule",
            r"    \end{tabular}",
            r"\end{table}",
            "",
        ]
    )
    (TABLE_DIR / "cloud_overall_qerror_table.tex").write_text("\n".join(qerror_lines), encoding="utf-8")

    efficiency_lines = [
        r"\begin{table}[htbp]",
        r"    \centering",
        r"    \caption{Efficiency comparison on \texttt{D2} and \texttt{D5}}",
        r"    \label{tab:efficiency_comparison}",
        r"    \small",
        r"    \setlength{\tabcolsep}{3pt}",
        r"    \begin{tabular}{@{}llrrrr@{}}",
        r"        \toprule",
        r"        Dataset & Method & Fit(ms) & Infer($\mu$s/q) & QPS & Heap \\",
        r"        \midrule",
    ]
    for dataset in ["synthetic_D2", "synthetic_D5"]:
        if dataset == "synthetic_D5":
            efficiency_lines.append(r"        \midrule")
        for method in METHOD_ORDER:
            row = summary[(summary["dataset"] == dataset) & (summary["method"] == method)].iloc[0]
            efficiency_lines.append(
                "        "
                + rf"\texttt{{{row['dataset_label']}}} & \texttt{{{row['method_label']}}} "
                + rf"& {row['fit_ms']:.2f} & {row['infer_us_q']:.2f} "
                + rf"& {row['qps']:.2f} & {human_heap(int(row['heap_bytes']))} \\"
            )
    efficiency_lines.extend(
        [
            r"        \bottomrule",
            r"    \end{tabular}",
            r"\end{table}",
            "",
        ]
    )
    (TABLE_DIR / "cloud_efficiency_table.tex").write_text("\n".join(efficiency_lines), encoding="utf-8")


def write_markdown_summary(summary: pd.DataFrame) -> None:
    out = summary.copy()
    out["heap"] = out["heap_bytes"].map(lambda v: human_heap(int(v)))
    headers = [
        "Dataset",
        "Method",
        "Median",
        "Mean",
        "P90",
        "P95",
        "Max",
        "Fit(ms)",
        "Infer(us/q)",
        "QPS",
        "Heap",
    ]
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join(["---"] * len(headers)) + " |",
    ]
    for _, row in out.iterrows():
        values = [
            row["dataset_label"],
            row["method_label"],
            f"{row['median']:.3f}",
            f"{row['mean']:.3f}",
            f"{row['p90']:.3f}",
            f"{row['p95']:.3f}",
            f"{row['max']:.3f}",
            f"{row['fit_ms']:.2f}",
            f"{row['infer_us_q']:.2f}",
            f"{row['qps']:.2f}",
            row["heap"],
        ]
        lines.append("| " + " | ".join(values) + " |")
    (TABLE_DIR / "cloud_overall_and_efficiency_summary.md").write_text(
        "\n".join(lines) + "\n", encoding="utf-8"
    )
    out.to_csv(TABLE_DIR / "cloud_overall_and_efficiency_summary.csv", index=False)


def main() -> None:
    ensure_dirs()
    df = load_detail()
    summary = build_overall_summary(df)
    group_summary = build_group_summary(df)

    summary.to_csv(TABLE_DIR / "cloud_overall_metrics_raw.csv", index=False)
    group_summary.to_csv(TABLE_DIR / "cloud_group_median_metrics_raw.csv", index=False)
    write_tex_tables(summary)
    write_markdown_summary(summary)

    grouped_bar(
        summary,
        "median",
        "Median Q-Error",
        "Overall Median Q-Error (Tencent Cloud)",
        "overall_median_qerror_cloud",
        log_scale=True,
    )
    grouped_bar(
        summary,
        "mean",
        "Mean Q-Error",
        "Overall Mean Q-Error (Tencent Cloud)",
        "overall_mean_qerror_cloud",
        log_scale=True,
    )
    grouped_bar(
        summary,
        "p95",
        "P95 Q-Error",
        "Overall P95 Q-Error (Tencent Cloud)",
        "overall_p95_qerror_cloud",
        log_scale=True,
    )
    grouped_bar(
        summary,
        "fit_ms",
        "Fit Time (ms)",
        "Model Construction Time (Tencent Cloud)",
        "fit_time_cloud",
        log_scale=True,
    )
    grouped_bar(
        summary,
        "infer_us_q",
        "Inference Time (us/query)",
        "Average Inference Time (Tencent Cloud)",
        "infer_time_cloud",
        log_scale=True,
    )
    summary = summary.assign(heap_kb=summary["heap_bytes"] / 1024.0)
    grouped_bar(
        summary,
        "heap_kb",
        "JVM Heap Delta (KB)",
        "Approximate JVM Heap Increment (Tencent Cloud)",
        "heap_overhead_cloud",
        log_scale=True,
    )

    for dataset in DATASET_LABELS:
        boxplot_qerror(df, dataset)
        group_median_plot(group_summary, dataset)

    print(f"Generated figures in: {FIG_DIR}")
    print(f"Generated tables in: {TABLE_DIR}")


if __name__ == "__main__":
    main()
