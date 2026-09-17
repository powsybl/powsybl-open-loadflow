#!/usr/bin/env bash
# Copyright (c) 2026, RTE (http://www.rte-france.com) - SPDX-License-Identifier: MPL-2.0
#
# Build the OLF GPU native library (CMake -> target/classes/natives/<arch>/libolfgpu.so)
# and run the in-process GPU tests:
#   - GpuFullNewtonTest: the full device-resident Newton-Raphson per equation family,
#     vs OLF's own JacobianMatrix Newton on the SAME equation system (machine precision)
#   - GpuLoadFlowTest: a plain LoadFlow.run selecting the GPU through the AcSolver SPI,
#     vs the default CPU Newton-Raphson
# The tests load the library from the CLASSPATH via scijava NativeLoader (the Java
# loader preloads the NVIDIA chain) — i.e. this also exercises the real packaging path.
# Needs maven + cmake + the CUDA toolkit + cuDSS + a GPU + a JDK.
#
#   CUDA_TOOLKIT  default /usr/local/cuda
#   CUDSS_ROOT    default ../cudss next to this repo
#   CUDA_ARCH     default 86
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
[ -f "$ROOT/native/local-env.sh" ] && . "$ROOT/native/local-env.sh"   # optional local machine paths (gitignored)

echo "[1/2] build libolfgpu.so (CMake, static cudart, dynamic cuDSS)"
"$ROOT/native/build-gpu.sh"

echo "[2/2] run the in-process GPU tests (classpath-loaded)"
( cd "$ROOT" && mvn -o test -Dtest='GpuFullNewtonTest,GpuLoadFlowTest' \
    -DfailIfNoTests=false 2>&1 \
    | grep -iE "GPU full Newton|LoadFlow.run GPU|Tests run:|BUILD" | grep -v "Version:" | tail -12 )
