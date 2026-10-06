#!/usr/bin/env bash
# Copyright (c) 2026, RTE (http://www.rte-france.com) - SPDX-License-Identifier: MPL-2.0
#
# Build OLF's GPU native library (libolfgpu — the full GPU Newton-Raphson) via the
# root CMakeLists into target/classes/natives/<arch>/, where the classpath loader
# (GpuAcNewtonSolver / scijava NativeLoader) finds it and `mvn package` bundles it
# into the jar — same packaging pattern as powsybl-math-native.
#
# Opt-in by construction: the normal Maven build never runs this; without it the
# GPU code paths just report isAvailable() == false.
#
#   CUDA_TOOLKIT  default /usr/local/cuda
#   CUDA_ARCH     default 86 (CMAKE_CUDA_ARCHITECTURES)
#   CUDSS_ROOT    default ../cudss next to this repo (REQUIRED: libolfgpu links
#                 cuDSS dynamically — never bundled)
#   EMBED_RPATH   set to 1 to bake a machine-local RPATH into libolfgpu (then the
#                 .so loads outside the JVM without any env). Default OFF: the bundled
#                 artifact stays location-independent (DISTRIBUTABLE — only NVIDIA's
#                 own .so may not ship in the jar, never our wrapper), and the Java
#                 loader (GpuAcNewtonSolver) preloads libcublasLt/libcublas/libcudss
#                 from olf.cudss.path/CUDSS_ROOT + olf.cuda.path/CUDA_HOME instead.
#
# The cmake cache lives in native/build/ (survives `mvn clean`); the .so output
# lands under target/classes/, so after a `mvn clean` just re-run this script
# (incremental: it only re-links).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
[ -f "$HERE/local-env.sh" ] && . "$HERE/local-env.sh"   # optional local machine paths (gitignored)
TK="${CUDA_TOOLKIT:-/usr/local/cuda}"
ARCH="${CUDA_ARCH:-86}"
BUILD="$ROOT/native/build"
CUDSS="${CUDSS_ROOT:-$ROOT/../cudss}"
RPATH="$([ "${EMBED_RPATH:-0}" = "1" ] && echo ON || echo OFF)"

if [ ! -e "$CUDSS/include/cudss.h" ]; then
    echo "error: cuDSS not found at $CUDSS — set CUDSS_ROOT to your cuDSS install" >&2
    exit 1
fi
echo "cuDSS at $CUDSS, CUDA toolkit at $TK (arch $ARCH, RPATH embed: $RPATH)"

# cuDSS include/lib are resolved by find_path/find_library and CACHED in the CMake cache. If
# CUDSS_ROOT changed since the last configure, the stale cache keeps the OLD headers — silently
# compiling against a 0.8 header while linking/loading a 0.7.x runtime (or vice versa), which the
# CUDSS_VERSION shim then expands wrong (e.g. the 3-type vs 2-type BatchCsr signature) → a cuDSS
# INVALID_VALUE at runtime. Wipe the build dir on a CUDSS_ROOT change to force a clean re-resolve.
STAMP="$BUILD/.cudss_root"
if [ -f "$STAMP" ] && [ "$(cat "$STAMP")" != "$CUDSS" ]; then
    echo "CUDSS_ROOT changed ($(cat "$STAMP") -> $CUDSS) — wiping $BUILD for a clean re-resolve"
    rm -rf "$BUILD"
fi

cmake -S "$ROOT" -B "$BUILD" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_CUDA_COMPILER="$TK/bin/nvcc" \
    -DCMAKE_CUDA_ARCHITECTURES="$ARCH" \
    -DCUDAToolkit_ROOT="$TK" \
    -DCUDSS_ROOT="$CUDSS" \
    -DCUDSS_EMBED_RPATH="$RPATH"
mkdir -p "$BUILD"; printf '%s' "$CUDSS" > "$STAMP"
cmake --build "$BUILD" -j
