from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


DATA_DIR = Path("/Users/lsc/Desktop/claude work/cardinality-estimator/src/main/resources/data")
OUTPUT_DIR = Path("/Users/lsc/Desktop/claude work/cloud_experiment_outputs_clean/figures")


def density_values(x: np.ndarray, y: np.ndarray, bins: int = 80) -> np.ndarray:
    counts, x_edges, y_edges = np.histogram2d(x, y, bins=bins)
    xi = np.clip(np.searchsorted(x_edges, x, side="right") - 1, 0, counts.shape[0] - 1)
    yi = np.clip(np.searchsorted(y_edges, y, side="right") - 1, 0, counts.shape[1] - 1)
    return counts[xi, yi]


def pca_2d(df: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
    x = df.to_numpy(dtype=float)
    z = (x - x.mean(axis=0)) / x.std(axis=0, ddof=0)
    _, singular_values, vt = np.linalg.svd(z, full_matrices=False)
    projected = z @ vt[:2].T
    explained = singular_values**2 / np.sum(singular_values**2)
    return projected, explained[:2]


def style_scatter_axes(ax: plt.Axes) -> None:
    ax.grid(True, linestyle="--", linewidth=0.55, alpha=0.30)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.tick_params(axis="both", labelsize=9)


def save(fig: plt.Figure, name: str) -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for suffix in ("pdf", "png"):
        fig.savefig(OUTPUT_DIR / f"{name}.{suffix}", bbox_inches="tight", dpi=300)
    plt.close(fig)


def plot_individual_d2(d2: pd.DataFrame) -> None:
    x = d2["attr1"].to_numpy()
    y = d2["attr2"].to_numpy()
    c = density_values(x, y)

    fig, ax = plt.subplots(figsize=(5.4, 4.5))
    sc = ax.scatter(x, y, c=c, s=9, cmap="viridis", alpha=0.75, linewidths=0)
    ax.set_title("Distribution of D2", fontsize=12, pad=10)
    ax.set_xlabel("attr1")
    ax.set_ylabel("attr2")
    style_scatter_axes(ax)
    cb = fig.colorbar(sc, ax=ax, fraction=0.046, pad=0.04)
    cb.set_label("Local point density", fontsize=9)
    save(fig, "synthetic_D2_scatter_cloud")


def plot_individual_d5(d5: pd.DataFrame) -> None:
    projected, explained = pca_2d(d5)
    x = projected[:, 0]
    y = projected[:, 1]
    c = density_values(x, y)

    fig, ax = plt.subplots(figsize=(5.4, 4.5))
    sc = ax.scatter(x, y, c=c, s=9, cmap="viridis", alpha=0.75, linewidths=0)
    ax.set_title("Distribution of D5 (PCA Projection)", fontsize=12, pad=10)
    ax.set_xlabel(f"PC1 ({explained[0] * 100:.1f}%)")
    ax.set_ylabel(f"PC2 ({explained[1] * 100:.1f}%)")
    style_scatter_axes(ax)
    cb = fig.colorbar(sc, ax=ax, fraction=0.046, pad=0.04)
    cb.set_label("Local point density", fontsize=9)
    save(fig, "synthetic_D5_pca_scatter_cloud")


def plot_combined(d2: pd.DataFrame, d5: pd.DataFrame) -> None:
    d2_x = d2["attr1"].to_numpy()
    d2_y = d2["attr2"].to_numpy()
    d2_c = density_values(d2_x, d2_y)

    d5_projected, d5_explained = pca_2d(d5)
    d5_x = d5_projected[:, 0]
    d5_y = d5_projected[:, 1]
    d5_c = density_values(d5_x, d5_y)

    fig, axes = plt.subplots(1, 2, figsize=(10.8, 4.5))
    fig.subplots_adjust(wspace=0.28, top=0.86)

    sc0 = axes[0].scatter(d2_x, d2_y, c=d2_c, s=8, cmap="viridis", alpha=0.75, linewidths=0)
    axes[0].set_title("D2 Original Space", fontsize=12, pad=9)
    axes[0].set_xlabel("attr1")
    axes[0].set_ylabel("attr2")
    style_scatter_axes(axes[0])

    sc1 = axes[1].scatter(d5_x, d5_y, c=d5_c, s=8, cmap="viridis", alpha=0.75, linewidths=0)
    axes[1].set_title("D5 PCA Projection", fontsize=12, pad=9)
    axes[1].set_xlabel(f"PC1 ({d5_explained[0] * 100:.1f}%)")
    axes[1].set_ylabel(f"PC2 ({d5_explained[1] * 100:.1f}%)")
    style_scatter_axes(axes[1])

    cbar = fig.colorbar(sc1, ax=axes.ravel().tolist(), fraction=0.025, pad=0.03)
    cbar.set_label("Local point density", fontsize=9)

    save(fig, "dataset_distribution_cloud")


def main() -> None:
    d2 = pd.read_csv(DATA_DIR / "synthetic_D2.csv")
    d5 = pd.read_csv(DATA_DIR / "synthetic_D5.csv")
    plot_individual_d2(d2)
    plot_individual_d5(d5)
    plot_combined(d2, d5)
    print(f"Generated dataset scatter plots in: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
