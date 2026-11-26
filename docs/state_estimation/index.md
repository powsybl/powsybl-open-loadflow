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
| `WlsParameters`, holding the tolerance, the iteration cap and the two safeguards of the iteration | an `Extension<StateEstimationParameters>` contributed by this provider, since none of them changes what the answer means |
| `LfMeasurement`, a reading keyed on the `AcEquationType` of what it measures | built from the IIDM measurement extensions a network already carries |
| `WlsResult`, already shaped after `ComponentResult` and carrying `BusResult` and `BranchResult` in IIDM units | one entry in `StateEstimationResult.getComponentResults()`, with its bus and branch results merged into the network-wide lists |
| `WlsResult.Status` and `getStatusText()` | `ComponentResult.Status` and its own status text, with the same four values so nothing is translated |
| the standard deviations on `WlsResult.BusResult` | `StateEstimationResult.QuantityAccuracy`, for `Quantity.VOLTAGE`. The accuracy of an estimated flow needs the same propagation applied to a branch term and is not reported yet |
| `WlsResult.MeasurementResult`, one per reading, carrying its residual, its normalized residual and the IIDM measurements it came from | `StateEstimationResult.MeasurementResult`, which names a reading by `getMeasurementId()`, and the input a `BadDataResult` is decided from |
| nothing yet | `ObservabilityResult`, from a measurement-graph traversal not implemented here |
| nothing yet | `BadDataResult` itself: the residuals are reported, the test that acts on them is not |

## What is implemented

Voltage magnitude, active and reactive injection, and active and reactive flow measurements,
each at a bus or at one side of a branch, alongside current magnitude. Gauss-Newton
iteration on the normal equations, from a flat start unless a state is supplied. One angle per
synchronous component is held fixed as the reference, and it gets no column in the gain
matrix. A branch open at one end still carries its own charging, and a reading at its connected
end is modeled with the load flow's open-branch terms. One without shunt admittance carries
nothing, so a current reading at its connected end cannot move the state and stays whole in its
residual.

The measurement model is an `EquationSystem` keyed on `AcEquationType`, holding one equation
per measured quantity, built out of this repository's existing AC flow terms. The
measurement Jacobian comes from a `JacobianMatrix` over that system, so the estimator and
the load flow differentiate a network with the same code they interpret it with. Every
matrix is created through the `MatrixFactory` the estimator is given, and the default is
`SparseMatrixFactory`.

## The solver

`WlsStateEstimator` builds the measurement model and reports the result. The minimization
itself sits behind `WlsSolver`, which the estimator gets from a `WlsSolverFactory`, the way
the AC load flow gets its Newton-Raphson, Newton-Krylov or fast decoupled solver from an
`AcSolverFactory`. `GaussNewtonFactory` is the default and the only implementation so far.

A solver works on a `WlsProblem`: the readings, their weights, the unknowns, the residuals at
the current state, and `W^(1/2) H^T` over the unknowns, which is the one matrix every
formulation of the normal equations starts from. What a solver does with that matrix is its
own business. `GaussNewton` hands it to a `GainFactorization`, and `LuGainFactorization` forms
`G` and decomposes it by LU. A different factorization, such as a sparse Cholesky one working
from the scaled Jacobian, is another `GainFactorization` given to `GaussNewtonFactory`. An
algorithm that iterates differently, such as Levenberg-Marquardt, is another `WlsSolver`.

The accuracy of an estimate is worked out with the factorization of the solver that produced
it, through `WlsSolver.factorizeGain`, so the two never disagree about the linear algebra.

Two safeguards of the iteration are settings of `WlsParameters`, with pandapower's values as
defaults:

- the first iteration leaves current magnitude readings out. The derivative of a current
  magnitude is undefined where the current is zero, which is where a flat start puts every
  branch without a shunt admittance. The other readings therefore have to determine the state
  on their own, as they do for pandapower;
- a step whose largest component exceeds 0.35, per unit or radians, is scaled down to it. The
  direction is kept, so an iteration started far from the answer does not overshoot into
  divergence.

## How a run can end

`WlsResult.Status` carries the four values of `ComponentResult.Status` in the Core API.
`ESTIMATED` means the iteration met the tolerance. The other three are distinguished rather
than lumped together, because they call for different things from whoever reads them:

- `UNOBSERVABLE` when the gain matrix cannot be decomposed, which is what a measurement set
  that does not determine the state looks like numerically. Two cases are caught before any
  decomposition, because a numerical one does not reliably refuse them: readings that reach no
  state variable at all, and buses whose readings tie their angles to each other but to no
  reference bus. Nothing is reported about the accuracy of such a run, no residual of it can
  be tested, and the redundancy of every reading is `NaN`;
- `FAILED` when the weighted sum of squared residuals grows in two successive iterations.
  Gauss-Newton on a non-linear model can overshoot once and recover, so a single worse
  iteration says nothing, but a run walking away from the answer is worth stopping and worth
  telling apart from one that merely ran slowly. A derivative, a residual or a state update
  that is not a finite number ends the run the same way;
- `DID_NOT_CONVERGE` when the iteration cap arrives with the fit still improving. The
  accuracy of the estimate survives that, since it follows from where the meters are rather
  than from how far the iteration got.

`getStatusText()` says why a run ended `UNOBSERVABLE` or `FAILED` and is empty otherwise.

## What is not supported yet

- A branch without impedance, which includes every switch of a network loaded at breaker
  level. The AC flow terms refuse to be built for one, and the estimator throws;
- A current magnitude reading on a closed branch that carries no current at the estimated
  state. Its derivative is as undefined there as at a flat start, and the run ends `FAILED`.

## What the estimate comes with

Beyond the state itself, each run reports what the measurement configuration allows:

- the standard deviation of every estimated bus voltage and angle, which is the diagonal of
  the inverse of the gain matrix. It follows from where the meters are and how precise each
  one is, and not at all from what they read;
- per reading, the residual against the estimate, the share of its own variance that
  residual kept, and the residual divided by its own standard deviation. That last one is
  what a bad data detection compares against a threshold, and the share is what tells a
  reading whose error would show up from one the estimate reproduces no matter what it says;
- the identifiers of the IIDM measurements each reading was built from, which is how the
  Core API names a reading and therefore how a bad data result names a culprit. There can be
  several, where device readings were summed into a bus injection or where two meters on one
  quantity were merged;
- the weighted sum of squared residuals and the readings in excess of the unknowns, which
  are the two arguments of a chi-square test.

The inverse of the gain matrix is never formed. A variance is one
triangular solve against an already factorized gain matrix, which costs a solve per unknown
and a solve per measured quantity once the iteration has converged. The gain matrix itself is
reachable through `getGainMatrix()` for anything that needs more than that.

## What it reuses, and what it does not

Reused as published API: the AC flow terms and `AcEquationType` of `ac.equations`,
`EquationSystem`, `JacobianMatrix`, `Variable`, `VariableSet` and `StateVector` from
`equations`, `LfNetwork` and its elements, `Networks` for loading and terminal lookup,
`PerUnit`, and `Matrix` with its factories for every piece of linear algebra.

Three things had to give before the equation system could carry a measurement model.
[#1559](https://github.com/powsybl/powsybl-open-loadflow/pull/1559) did two of them.
`JacobianMatrix` used to refuse a non-square system outright, although only its LU
decomposition needs one, and a measurement Jacobian is taller than it is wide. And the branch
power control equations shared one equation type across both ends, so the two sides of a
branch could not be two measured quantities. The third is here: `AcEquationType` gained
`BRANCH_TARGET_I1` and `BRANCH_TARGET_I2`, because the load flow builds current magnitude
terms but hangs them off the branch as `Evaluable`s rather than keying an equation on them,
so a current reading had no type to be held under. None of the three is specific to state
estimation.

What is assembled here rather than taken from the load flow is the gain matrix `G = H^T W H`.
`JacobianMatrix` keeps a factorization of itself, which is the right thing for a load flow
and the wrong one here, because what gets solved repeatedly is `G`, not `H`. Since `W` is
diagonal, its square root folds into both factors, so `G` is one `Matrix.times` of a scaled
copy of `H` with that copy's own transpose. The scaled copy is rebuilt each iteration rather
than refilled, because a dense matrix iterates the values that are not zero rather than the
structure holding them, and a derivative that is exactly zero at a flat start stops being one
a step later.

`MeasurementAggregator` sorts equipment by how `LfNetwork` carries it, which is not always how
IIDM classes it. A static var compensator is a generator there, so its reading sums into the bus
injection like any other device's. A shunt compensator sits in the network admittance, so the
injection equation already holds its term and its reading is left out. A boundary line is an
injection in IIDM and a branch in `LfNetwork`, alone or as half of a tie line, so its reading is
a branch flow.

A bus with no device injects nothing, and by default that enters as a zero-valued reading with
a small standard deviation. The star bus of a three winding transformer is such a bus by
construction, and it is the one pandapower always treats this way.

Two meters reading the same quantity at the same place share an equation and a row of `H`,
and differ in what they read and how far each is trusted. `MeasurementAggregator` merges them
by inverse variance before that point, which is what pandapower does and what keeps the two
implementations comparable, and `DuplicateMeasurementPolicy` can be set to keep both instead.
Either way each reading keeps its own residual, and a merged one names every meter behind it.
