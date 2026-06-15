# Smooth Reactive Limits

## Goal

The `smoothReactiveLimits` option is an experimental alternative to the usual
generator reactive limit outer loop.

The classical formulation keeps a generator voltage-controlled bus as PV with:

```text
V = Vtarget
```

and uses `ReactiveLimitsOuterLoop` to switch buses between PV and PQ when a
generator reaches its reactive limits. The objective of the smooth formulation
is to replace that discontinuous PV/PQ switching with a continuous equation that
keeps reactive power inside its limits while staying as close as possible to the
voltage target.

When `smoothReactiveLimits=true`, the `ReactiveLimitsOuterLoop` is disabled.

## Implemented Equation

For each eligible generator voltage control, the usual voltage target equation
is replaced by:

```text
Q - Qmid - Qhalf * s((Vtarget - V) / Vscale) = 0
```

with:

```text
Qmid  = (Qmin + Qmax) / 2
Qhalf = (Qmax - Qmin) / 2
```

and:

```text
s(x) = tanh(x) / tanh(Xsat), for -Xsat < x < Xsat
s(x) =  1,                 for x >= Xsat
s(x) = -1,                 for x <= -Xsat
```

Current constants:

```text
Vscale = 0.0075 pu
Xsat   = 2.0
```

So the equation enforces:

```text
Q in [Qmin, Qmax]
```

at any root, because `s(x)` is clipped to `[-1, 1]`.

## Interpretation

The equation defines a smooth Q/V characteristic:

```text
V close to Vtarget  -> behaves like a steep voltage control equation
V far from Vtarget  -> behaves like a fixed Q limit equation
```

If the voltage target can be reached without violating reactive limits, the
solution should remain near `Vtarget`.

If reaching `Vtarget` would require Q outside `[Qmin, Qmax]`, the equation lets
voltage move away from the target and pushes Q toward the corresponding limit.

This is only an approximation of the original PV/PQ diagram. It is not exactly
equivalent to PV/PQ switching.

## Eligibility

The smooth equation is only created when all required controller buses have
finite and plausible reactive limits.

For local generator voltage control:

```text
smooth equation is used only if:
- the controlled bus has no generator slope
- Qmin/Qmax are finite and plausible
```

For remote generator voltage control:

```text
smooth equation is used only if:
- all enabled controller buses have finite and plausible Qmin/Qmax
```

Otherwise the standard voltage control equation is kept.

## Remote Controls

For remote voltage control, the existing reactive power distribution equations
are still created. The smooth voltage equation uses the distributed controller
reactive powers to compute the controlled Q value.

For several controllers, the equivalent reactive range is derived from each
controller bus range and its remote-control reactive percentage. If that cannot
produce a finite consistent range, the fallback is the sum of individual
controller ranges.

## Activation

User-facing parameter:

```text
smoothReactiveLimits
```

When enabled:

```text
OpenLoadFlowParameters.create(parameters)
    .setSmoothReactiveLimits(true);
```

Effects:

```text
- creates BUS_TARGET_SMOOTH_V equations where eligible
- disables ReactiveLimitsOuterLoop
- keeps other outer loops unchanged
```

## Validation So Far

Small IEEE14 tests:

```text
testAcWithSmoothReactiveLimits
  status: CONVERGED
  Newton iterations: 10
  stateVectorScalingMode: NONE

testAcWithSmoothReactiveLimitsAtGeneratorLimit
  status: CONVERGED
  Newton iterations: 11
  stateVectorScalingMode: NONE
  checked that generator Q remains inside configured limits
```

Tuning observations on IEEE14:

```text
Vscale = 0.02   -> converged, about 14 iterations
Vscale = 0.01   -> converged, about 11 iterations
Vscale = 0.0075 -> converged, about 10 iterations
Vscale = 0.005  -> oscillated / max iterations reached
```

Stanway test:

```text
case: ecct-situ-20250325-1900-20250324-1315-stanway.xml.bz2
smoothReactiveLimits = true
maxNewtonRaphsonIterations = 100
stateVectorScalingMode = NONE

result: FAILED
solverIterations: 1
reason: first AC Jacobian factorization failed with KLU_SINGULAR
```

Baseline on the same Stanway case:

```text
smoothReactiveLimits = false
ReactiveLimitsOuterLoop enabled

result: CONVERGED
solverIterations: 15
outerLoopIterations: 8
```

## Known Limits

This method is currently experimental and not robust enough for large real
cases.

Main limitations:

```text
- IEEE14 needs 10 Newton iterations, which is high for such a small case.
- The tuning is sensitive to Vscale.
- Too small Vscale makes the tanh characteristic too steep and can oscillate.
- Stanway fails immediately with a singular Jacobian.
- The method removes the discrete PV/PQ switching logic, so it can lose useful
  stabilizing behavior from ReactiveLimitsOuterLoop.
- Exact equality to Qmin or Qmax should not be expected unless the characteristic
  is in its clipped saturation branch.
```

The current result suggests that a simple tanh replacement of `V = Vtarget` is
not sufficient for production use on large grids.

## Possible Next Directions

Potential improvements to investigate:

```text
- add a continuation strategy on Vscale
- only activate smooth reactive limits for selected controls
- keep ReactiveLimitsOuterLoop as a fallback when the smooth system is singular
- use a better-conditioned complementarity smoothing formulation
- add diagnostics to identify which smooth equations make the Jacobian singular
- introduce a dedicated parameter for Vscale instead of a hard-coded constant
```

Any future version should be validated first on small cases with known PV/PQ
switching behavior, then on Stanway with no state-vector scaling, before trying
line search or other damping strategies.
