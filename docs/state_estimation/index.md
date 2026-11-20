# State estimation

An AC weighted least squares state estimator built on the same network model, equation
terms and linear algebra the load flow uses.

The code is experimental and has no provider. It exists to show which parts of this
repository a state estimator reuses, ahead of the module split described in
[#1525](https://github.com/powsybl/powsybl-open-loadflow/pull/1525).

## Relationship to the PowSyBl Core API

A solver-agnostic `state-estimation-api` module is proposed for PowSyBl Core in
[powsybl-core#4144](https://github.com/powsybl/powsybl-core/pull/4144). It defines three
operations behind one provider, `analyseObservability`, `estimate` and `detectBadData`,
along with the parameters and results they exchange.

The types here are already shaped after that API. What is missing is the provider itself:
`@AutoService` and `ServiceLoader` need the real interface on the classpath, and
`state-estimation-api` exists only at Core 7.5.0-SNAPSHOT, built from the fork behind that
pull request and published nowhere. Taking the dependency would mean nobody else could build
this branch. Once a Core release carries the module, `WlsStateEstimator` becomes the engine
behind a `StateEstimationProvider` with no change to the types below.

| Here | In the API |
|---|---|
| `WlsParameters`, holding the tolerance and the iteration cap | an `Extension<StateEstimationParameters>` contributed by this provider, since neither setting changes what the answer means |
| `Measurement`, `MeasuredQuantity` | built from the IIDM measurement extensions a network already carries |
| `WlsResult`, already shaped after `ComponentResult` and carrying `BusResult` and `BranchResult` in IIDM units | one entry in `StateEstimationResult.getComponentResults()`, with its bus and branch results merged into the network-wide lists |
| nothing yet | `ObservabilityResult`, from a measurement-graph traversal not implemented here |
| nothing yet | `BadDataResult`, from normalized residuals the kernel already computes and discards |

## What is implemented

Voltage magnitude, active and reactive injection, and active and reactive flow
measurements, each at a bus or at one side of a branch. Gauss-Newton iteration on the
normal equations, from a flat start or from a supplied state. One angle per synchronous
component is held fixed as the reference, and its column is never assembled.

The measurement functions are this repository's existing AC flow terms, wrapped in a small
`MeasurementEquation` abstraction, so the estimator and the load flow interpret a network
identically. Current magnitude measurements are supported alongside voltage, injection and
flow.

## What it reuses, and what it does not

Reused as published API: the AC flow terms of `ac.equations`, `Variable`, `VariableSet`
and `StateVector` from `equations`, `LfNetwork` and its elements, `Networks` for loading
and terminal lookup, and `PerUnit`.

Not reused, deliberately. `EquationSystem` keys equations by element and type and
`createEquation` is get-or-create, so two redundant meters of the same quantity at the same
place would collapse into one row; redundancy is what a state estimation is built on.
`JacobianMatrix` requires a square system and maintains a factorization of itself, while a
measurement Jacobian is taller than it is wide and what gets factorized is the gain matrix
`G = H' W H`, not the Jacobian. The estimator accumulates `G` one measurement at a time
without ever forming `H`, and decomposes it itself.
