#!/usr/bin/env bash
# Copyright (c) 2026, RTE (http://www.rte-france.com) - SPDX-License-Identifier: MPL-2.0
#
# Regenerate the committed generated equation sources from the residual specs in
# codegen/equations/. One stage: SymPy autodiff + Jinja render, producing both
# targets per family:
#   - the scalar CPU API   src/main/java/.../<Class>.java
#   - the fused CUDA kernel native/cuda/<family>.cuh
#
# Needs sympy + jinja2 (pip install -r codegen/requirements.txt). Run after editing
# a residual spec, then commit the regenerated files.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

echo "[1/3] prove equivalence (signatures + 1e-12 numeric, scalar + fused + autodiff)"
python3 codegen/prove_equivalence.py

echo "[2/3] check scatter stamp vs OLF equation-system wiring"
python3 codegen/check_scatter.py

echo "[3/3] render (SymPy + Jinja): residuals -> .java + .cuh + .scatter.json"
python3 codegen/generate.py

echo
echo "Done. Review with: git diff"
