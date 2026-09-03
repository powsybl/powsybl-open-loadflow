# Equation formula codegen

Single source of truth for the closed-form AC equation formulas, kept in sync by
generation. One **residual** source per family produces, by SymPy autodiff + Jinja,
the **two** backends that must never drift:

```
codegen/equations/<family>.py          # SINGLE SOURCE (hand-edited): the residuals only
        │
        │  python3 codegen/generate.py   (SymPy autodiff + Jinja)
        ├── src/main/java/.../<Class>.java   # SCALAR CPU API   — GENERATED, committed
        └── native/cuda/<family>.cuh         # FUSED CUDA kernel — GENERATED, committed
```

- The **scalar** `.java` is the precomputed-trig API (28 `static` methods) the
  equation terms and `AcNetworkVector` call — one method per value/derivative.
- The **fused** `.cuh` is one `evalClosedBranch` per element: it computes all 28
  outputs for a branch into Struct-of-Arrays at index `i`, with `sin`/`cos` of the
  branch angles shared via common-subexpression elimination — the device routine the
  GPU assembly kernel calls (one thread = one branch, inline trig, SoA — a fused
  per-branch kernel).

Derivatives are obtained by `sp.diff` — there is **no hand-derived Jacobian**.

## Why two shapes from one source

The CPU path keeps the granular scalar API (zero caller refactor; bit-stable). The
GPU path wants a fused per-element routine with shared trig. Both are the same
physics, so generating both from the one residual source means they cannot drift,
and the equivalence proof covers both. (We do **not** generate a fused `.java` or a
scalar `.cuh` — those shapes have no consumer.)

## Layout

| Path | Hand-edited? | Role |
|---|---|---|
| `codegen/equations/<family>.py` | **yes** | the residuals + branch angles + constants + canonical param order + the scatter `stamp` + emission metadata |
| `codegen/generate.py` | yes (rarely) | SymPy autodiff + Jinja render of the targets + scatter descriptor |
| `codegen/prove_equivalence.py` | yes (rarely) | signature-lock + 1e-12 equivalence (scalar + fused) + autodiff audit |
| `codegen/check_scatter.py` | yes (rarely) | validates the scatter stamp against OLF's real equation-system wiring |
| `codegen/extract_golden.py` | yes (rarely) | builds a family's golden from its ORIGINAL hand-written Java (run once) |
| `codegen/golden/<family>.golden.json` | **NO — snapshot** | frozen original bodies + signature manifest (equivalence reference) |
| `codegen/templates/*.j2` | yes | Jinja skeletons: scalar Java class, fused CUDA header |
| `codegen/regenerate.sh` | yes | convenience wrapper (prove → check scatter → render) |
| `codegen/requirements.txt` | yes | `sympy`, `jinja2` |
| `src/main/java/.../<Class>.java` | **NO — generated** | consumed by the equation terms + `AcNetworkVector` |
| `native/cuda/<family>.cuh` | **NO — generated** | the fused per-element GPU kernel body |
| `native/cuda/<family>.scatter.json` | **NO — generated** | scatter map: where each output lands in OLF's fixed-pattern Jacobian (GPU assembly) |
| `native/cuda/<family>_scatter.h` | **NO — generated** | C++ scatter table (per-output role + row/col endpoints) the fill kernel consumes |
| `native/cuda/jni/olf_gpu_nr_jni.cu` | yes (rarely) | **JNI binding** (in OLF, → libolfgpu) — the FULL GPU Newton-Raphson: fixed-pattern CSR from the scatter tables, all four fused kernels fill J+F on device, cuDSS refactorize/solve per iteration |
| `src/main/.../gpu/GpuAcNewtonSolver.java` + `.../solver/GpuNewtonRaphson{,Factory}.java` | yes (rarely) | Java seam (classpath loader + NVIDIA-chain preload) + the AcSolver SPI impl (`GPU_NEWTON_RAPHSON`) |
| `src/test/.../gpu/GpuFullNewtonTest.java`, `GpuLoadFlowTest.java` | yes (rarely) | **rungs 2+3**: device-resident Newton vs OLF's `JacobianMatrix` Newton (machine precision, per family) + plain `LoadFlow.run` GPU vs CPU. (Rung 1 — dense closed-branch-only `ClosedBranchGpuAssembler` — was removed once these subsumed it.) |
| `native/cuda/run_gpu_tests.sh` | yes | build + run the GPU Java suite (`GpuFullNewtonTest`/`GpuLoadFlowTest`) |

## How it works

`generate.py` builds the residuals + their `sp.diff` derivatives once (raw-angle,
`R2`/`A2` kept symbolic), then renders each target:

- **scalar Java** — substitute `sin(ksi)→sinKsi`, `cos(ksi)→cosKsi`, and (by side)
  `sin(theta)→sinTheta`, `cos(theta)→cosTheta`, rewrite integer powers to products
  (no `Math.pow`), derive each signature as the subset of the **canonical parameter
  order** appearing in the body — which reproduces OLF's exact granular signatures, so
  **no caller changes**. The frozen `golden/<family>.golden.json` manifest locks this.
- **fused CUDA** — `sp.cse` across all 28 outputs (so `sin`/`cos` of `ksi`/`theta1`/
  `theta2` are computed once), `sp.ccode` per fragment, Jinja loops emit the temps and
  the SoA writes. `R2`/`A2` are `constexpr` (fold to 1/0 at compile time).

**Structured magnitude families** (current magnitude, `i = hypot(reI, imI)`) declare
`magnitudes` in the spec instead of relying on a flat inlined derivative. The generator
then emits the chain-rule shape OLF originally hand-wrote: **private** component helpers
`reI`/`imI` and their per-variable derivatives `dreId<var>`/`dimId<var>`, with the public
`i = hypot(reI(), imI())` and `di/d<var> = (reI()*dreId<var>() + imI()*dimId<var>())/i()`
**assembled by calling** them — not one giant closed form per output. Only the outer
chain-rule skeleton is templated; the component derivatives are still `sp.diff` (no
hand-derived Jacobian), and a zero component-derivative drops its term (and, if both are
zero, the whole `di/d<var>`, e.g. open-branch's rotation invariance in `ph`).

`'*'` is spaced for checkstyle's `WhitespaceAround`; SymPy never emits `**` here
(powers are rewritten / absent), so the spacing is a plain replace.

### The proof (`prove_equivalence.py`)

Formatting-robust (compares numbers, never text). Five guards:

- **A. signature-lock** — every scalar `(name, params)` matches the golden manifest.
- **B. equivalence** — every scalar body equals the *original* hand-written body
  (snapshotted in `golden/`) to 1e-12 over random inputs. For magnitude families a
  **B2. rendered assembly** check additionally compiles the emitted helper-based methods
  (component helpers + the assembled value/derivatives) and confirms each public method
  equals the golden to 1e-12 — so a templating bug in the chain-rule assembly is caught,
  not just the underlying math.
- **C. autodiff audit** — every Jacobian entry matches a central finite difference of
  the closed-form residual to ~1e-7 (confirms the derivatives are genuine).
- **D. fused target** — `evalClosedBranch`'s 28 outputs also equal the golden to 1e-12
  (proves the second artifact, not just the scalar one).
- **E. java == cuda** — the GOLDEN-INDEPENDENT cross-check: the rendered scalar `.java`
  (compiled) and the rendered fused `.cuh` (CSE temps + ccode, evaluated) agree with EACH
  OTHER to 1e-12 over random inputs — both are rendered from the one residual source, so
  they cannot drift. Because it does not involve the golden, it holds even after you EDIT an
  equation (both regenerate from the edit and still agree; guards B/D separately flag the
  intended math change). It evaluates the rendered artifacts, so a render bug in either
  backend is caught, not just the SymPy behind them. Runs for every family with a `.cuh`.

## SymPy + Jinja are in the render path — the diff guard is an alert

`generate.py` runs SymPy and Jinja, so the committed `.java`/`.cuh` are not the output
of a stdlib-only renderer. The CI `render-up-to-date` job is therefore an **alert**,
not a determinism contract: if a SymPy/Jinja version bump reformats a body, the job
shows a clear diff and you simply **re-commit** the new render. Correctness never rests
on that textual guard — it rests on the numeric proof (`formulas-proven`, robust to
formatting). The pins in `requirements.txt` are a courtesy.

## Workflow when a formula changes or an equation is added

1. Edit the **residuals** in `codegen/equations/<family>.py` (only place physics lives).
2. `codegen/regenerate.sh` (prove → render). Needs SymPy + Jinja:
   `pip install -r codegen/requirements.txt`.
3. Build + validate + test (offline shown; drop `-o` if deps not yet cached):
   ```
   mvn -o validate            # runs the real checkstyle (validate phase)
   mvn -o -q -DskipTests test-compile
   mvn -o -q test -Dtest='EquationsTest,EquationArrayTest,AcLoadFlow*Test'
   ```
4. Commit the spec **and** the regenerated `.java` / `.cuh` together.

> Changing a residual intentionally changes the math, so guard **B** (equivalence vs
> the frozen `golden/`) will then fire — that is the point. Re-snapshot the golden only
> as a deliberate, reviewed act once the new math is the intended one.

## Adding a new equation family (recipe)

The closed-branch family (`equations/closed_branch.py` → `ClosedBranchFormulas.java` +
`native/cuda/closed_branch.cuh`) is the worked example. To port another:

1. Read the family's existing `static` formula methods + the branch angles they use.
2. Create `codegen/equations/<family>.py` defining `MODULE` (copy `closed_branch.py`):
   emission metadata (`java_class/package/imports/path`, `cuda_path/namespace/consts`,
   `doc`) then the residual authoring (`constants`, `variables`, `thetas`,
   `canonical_order`, `residuals`). You write **only the residuals**.
3. Register it in `MODULES` in `generate.py` (and `prove_equivalence.py`).
4. Snapshot the family's current hand-written bodies to `codegen/golden/<family>.golden.json`
   **before** deleting them, with `codegen/extract_golden.py` (parses
   `public static double NAME(...) { return EXPR; }` from the ORIGINAL Java):
   ```
   python3 codegen/extract_golden.py --class <Class> \
       --out codegen/golden/<family>.golden.json <OriginalTerm1.java> <OriginalTerm2.java> ...
   ```
   Point it at the **original** hand-written source, never a file the codegen already
   produces (that would make the proof circular). The golden is frozen — extract once.
5. Replace the hand-written bodies in the term class(es) with delegations to the
   generated scalar class; drop now-unused imports.
6. Regenerate (prove → render), then build/validate/test.

## Not covered yet

Control / outer-loop equations and the ZIP load (variable-length sum) are not clean
codegen targets and stay hand-written; only the closed-form per-element flows are
generated.

## Scatter descriptor (Strategy 2 — GPU Jacobian assembly)

Alongside the formulas, each family declares a **scatter `stamp`**: where every output
lands in OLF's equation system. `generate.py` turns it into
`native/cuda/<family>.scatter.json` — the fixed-pattern-J structural data:

- each **residual** → the bus power-balance equation it feeds (the `F` row), e.g.
  `p1 → BUS_TARGET_P @ bus1`;
- each **Jacobian entry** `d<res>d<var>` → `J[row = res's equation, col = var's variable]`,
  e.g. `dp1dph1 → J[(BUS_TARGET_P, bus1), (BUS_PHI, bus1)]`.

Endpoints (`bus1`/`bus2`/`branch`) are symbolic; the host resolves them per branch to
flat row/col indices (exactly as `AcNetworkVector` does today). This is the data to
assemble OLF's **fixed-pattern** Jacobian once and then ride the value-only-patch +
batched-cuDSS + low-rank-N-1 machinery — the chosen GPU strategy (see below).

`check_scatter.py` validates the stamp against OLF's **real** wiring, so it can't drift
from the code the kernel must reproduce:

- **columns** vs the vector evaluator's `getDerivatives()` — each `J` column's
  `(AcVariableType, endpoint)`;
- **equations** vs `AcEquationSystemCreator.createImpedantBranchEquations()` — each
  `addTerm(...)` into `getEquation(bus, AcEquationType.…)`.

The authoritative Java files are named in the spec's `stamp.sources`, so the check is
spec-driven and generalises per family.

The generated scatter descriptor is consumed directly by the GPU path:
`native/cuda/jni/olf_gpu_nr_jni.cu` builds the fixed-pattern CSR **once** from the
scatter tables and the topology (an N-1 branch trip removes only that branch's
contributions — `supp(J^(c)) ⊆ supp(J)`), then the four fused kernels fill `J`/`F` on
device each iteration while cuDSS refactorizes and solves `J·Δx = -g`. That is the full
device Newton-Raphson (`libolfgpu`), validated against OLF's own `JacobianMatrix` Newton
by `GpuFullNewtonTest` and end to end by `GpuLoadFlowTest`. So the chain is complete and
validated: **residuals → SymPy/Jinja → fused `.cuh` + scatter descriptor → fixed-pattern
CSR `J`/`g` → cuDSS Newton solve on the GPU**.

## Relationship to the GPU work

The fused `.cuh` is the per-element physics for the GPU assembly kernel. The chosen
GPU strategy is to keep OLF's per-branch formulation and borrow the proven
structural machinery (fixed-pattern Jacobian assembled once + value-only patches +
batched cuDSS), which is where its speedup lives — rather than converging onto the
Ybus formulation. The full analysis (and a fused-vs-scalar comparison + an
`AcNetworkVector` refactor sketch) is kept off this branch, on
`claude/codegen-experimental` (`codegen/experimental/`). A differential test (compile
the `.cuh` host-side and compare numerically to the Java) is the planned cross-backend
guarantee on top of the proof above.
