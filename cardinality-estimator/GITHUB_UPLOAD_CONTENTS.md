# GitHub Upload Contents

This folder contains the source code, experiment outputs, plotting scripts, and appendix materials that are suitable for uploading to GitHub.

## Recommended repository contents

- `src/`: Java source code and resource files for the cardinality estimation experiments.
- `pom.xml`: Maven project configuration.
- `README.md`, `EXPERIMENT_GUIDE.md`, `EDGE_SIMULATION_GUIDE.md`: usage and experiment documentation.
- `results/`: Tencent Cloud experimental outputs used in the thesis.
- `figures/`: clean PDF figures generated from the Tencent Cloud results.
- `tables/`: LaTeX tables and compact summary files for the thesis.
- `scripts/`: Python plotting scripts.
- `appendix/`: appendix draft and appendix material notes.

## Do not upload

- `target/`: Maven build output.
- `.DS_Store`: macOS metadata files.
- Local LaTeX auxiliary files such as `.aux`, `.log`, `.out`, and `.synctex.gz`.

The `.gitignore` file in this folder excludes these generated or local-only files.
