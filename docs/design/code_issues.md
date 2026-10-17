# Code issues to solve before the split

This page lists the existing package dependencies that prevent the split into the modules of the
[target architecture](target_architecture.md). In a single module they compile, but once packages are spread over
modules each of them would be a dependency from a lower module to a higher one, i.e. a cyclic module dependency.

Dependencies on powsybl-core artifacts are not issues: any module, including `powsybl-na-commons` and
`powsybl-na-network`, may depend on powsybl-core, e.g. on `LoadFlowParameters` (loadflow API) or on
`BranchResult`/`BusResult` and limit violations (security analysis API). Only dependencies between packages of this
repository that would point from a lower module to a higher one are listed.

All the issues can be fixed in the current single module, before any file is moved to a new module. Class names below
are relative to `com.powsybl.openloadflow`.

```{raw} html
<likec4-view view-id="currentViolations"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

## Blocking issues

### I1: network model depends on OpenLoadFlowParameters

| Class | Depends on |
|---|---|
| `network.LfNetworkParameters` | `OpenLoadFlowParameters.ReactiveRangeCheckMode`, `FictitiousGeneratorVoltageControlCheckMode`, `AREA_INTERCHANGE_CONTROL_DEFAULT_VALUE`, `FORCE_TARGET_Q_IN_REACTIVE_LIMITS_DEFAULT_VALUE` |
| `network.impl.LfGeneratorImpl` | `OpenLoadFlowParameters.FictitiousGeneratorVoltageControlCheckMode` |
| `network.util.ActivePowerDistribution` | `OpenLoadFlowParameters.SlackDistributionFailureBehavior` |

**Why it blocks:** `powsybl-na-network` would depend on `powsybl-open-loadflow`. The uses of `LoadFlowParameters`
in the same classes are fine, it is a powsybl-core class.

**Fix:** move the three enums into the network package (e.g. as nested enums of `LfNetworkParameters`) and make
`OpenLoadFlowParameters` reuse them, keeping the parameters JSON format unchanged. Move the two default value
constants to `LfNetworkParameters`.

### I2: network model depends on security analysis

| Class | Depends on |
|---|---|
| `network.LfBranch`, `network.impl.AbstractLfBranch`, `LfBranchImpl`, `LfBoundaryLineBranch`, `LfLegBranch`, `LfSwitch`, `LfTieLineBranch` | `sa.LimitScalingManager` |
| `network.impl.PropagatedContingencyCreationParameters` | `sa.OpenSecurityAnalysisParameters` |

**Why it blocks:** `powsybl-na-network` would depend on the security analysis code of `powsybl-open-loadflow`.

**Fix:** move `LimitScalingManager` (limit scaling is a property of how branch limits are read from IIDM) to
`network`, or pass an interface defined in `network` that `sa` implements. Replace the use of
`OpenSecurityAnalysisParameters.CONTINGENCY_PROPAGATION_DEFAULT_VALUE` with a constant owned by the network package.

### I3: network model calls an AC equation term

`network.LfHvdc` calls the static `ac.equations.AbstractHvdcAcEmulationFlowEquationTerm.getAbsActivePowerWithLosses`.

**Why it blocks:** `powsybl-na-network` would depend on `powsybl-na-equations`.

**Fix:** move the HVDC loss formula into `network` (e.g. a small `HvdcUtils` class) and call it from the equation
term.

### I4: network loader depends on the load flow network variant pool

`network.impl.LfNetworkList` uses the root class `NetworkVariantPool`.

**Fix:** move `NetworkVariantPool` to `network.impl`: it is about IIDM variants, not about load flow.

### I5: equation terms depend on load flow equation system updaters

| Class | Depends on |
|---|---|
| `ac.equations.AcEquationSystemCreator`, `AcEquationSystemUpdater` | `lf.AbstractEquationSystemUpdater` |
| `dc.equations.DcEquationSystemCreator`, `DcEquationSystemUpdater` | `lf.AbstractEquationSystemUpdater` |

**Why it blocks:** the creators and updaters build the load flow equation system (voltage controls, slack,
remote controls) and belong to the load flow. Left in `ac.equations`/`dc.equations` they would pull `lf` into
`powsybl-na-equations`.

**Fix:** move the creators, updaters and their parameters to load flow packages (e.g. `ac`, `dc`), keeping the
equation terms and `AcEquationType`/`AcVariableType` (and DC equivalents) in `ac.equations`/`dc.equations`.

### I6: utilities depend on network, equations and load flow

| Class | Depends on |
|---|---|
| `util.Reports` | `network.LfBus`, `network.LfGenerator`, `lf.outerloop.DiscreteControllerChange`, `OpenLoadFlowReportConstants` |
| `util.Derivable` | `equations.Quantity`, `equations.Variable` |

**Why it blocks:** `powsybl-na-commons` would depend on all the other modules.

**Fix:** split `Reports` (used by almost every package) by module: the network loading reports go to `network`,
the load flow, security and sensitivity reports to `powsybl-open-loadflow`. Each module gets its own report resource
bundle and `reports*.properties`, which today are a single `util.report.PowsyblOpenLoadFlowReportResourceBundle`.
Move `Derivable` to `equations`, where it is used (with `sensi`).

### I7: multi-threading helper depends on network loader and load flow

`util.mt.ContingencyMultiThreadHelper` depends on `network.impl` (`LfNetworkList`, `PropagatedContingency`...) and
`lf.AbstractLoadFlowParameters`.

**Fix:** move `util.mt` to `powsybl-open-loadflow`; only security and sensitivity analysis use it.

### I8: test utilities depend on Open Load Flow

The test-jar is replaced by the `powsybl-na-test` module (see the [target architecture](target_architecture.md#test-utilities)),
which must only depend on `powsybl-na-commons` and powsybl-core:

| Class | Depends on |
|---|---|
| Test network factories (`network/**/*Factory*`) | `util.PerUnit` only: no issue |
| `util.LoadFlowAssert`, `util.LoadFlowResultBuilder` | powsybl-core only: no issue |
| `CommonTestConfig`, `ServiceParameterResolver` | powsybl-core and JUnit only: no issue |
| `sa.AbstractOpenSecurityAnalysisTest` | `OpenLoadFlowProvider`, `OpenLoadFlowParameters`, `graph.*ConnectivityFactory`, `network.LfBus`, `LfBranch`, `SlackBusSelectionMode` |

In addition, 5 test classes of the `network` packages run a load flow, which `powsybl-na-network` tests cannot do.

**Why it blocks:** `powsybl-na-test` would depend on `powsybl-open-loadflow`, whose tests depend on
`powsybl-na-test`: a cycle in the Maven reactor.

**Fix:** split `AbstractOpenSecurityAnalysisTest` into a generic base class using only powsybl-core APIs (moved to
`powsybl-na-test`) and an Open Load Flow specific subclass kept in the `powsybl-open-loadflow` tests. Move the tests of
`network` that run a load flow to the `powsybl-open-loadflow` tests, or keep only their network part.

### I9: test utilities are in packages owned by other modules

| Classes | Current package | Package owned by | Used by |
|---|---|---|---|
| `AbstractLoadFlowNetworkFactory` and 25 network factories | `network` | `powsybl-na-network` | 94 test classes |
| `LoadFlowAssert`, `LoadFlowResultBuilder` | `util` | `powsybl-na-commons` | 64 test classes |
| `CommonTestConfig`, `ServiceParameterResolver` | root package | `powsybl-open-loadflow` | 78 and 64 test classes |
| `AbstractOpenSecurityAnalysisTest` (generic part, see I8) | `sa` | `powsybl-open-loadflow` | 4 test classes |

**Why it blocks:** once in `powsybl-na-test`, these classes would put the root, `network`, `util` and `sa` packages in two
modules each. This contradicts the "one package, one module" rule of the
[target architecture](target_architecture.md#compatibility-for-users).

**Fix:** move them to `com.powsybl.openloadflow.test` and update the imports of the test classes. The 17 test classes
of package `network` use the factories without importing them, so they now need imports. The factory helpers
are `protected static` methods of `AbstractLoadFlowNetworkFactory`, and the 6 test classes outside the factories that
use them do it by extending it, so the move keeps them accessible. Downstream test code must update its imports.

## Non-blocking cycles

The following cycles stay inside a single target module. They do not prevent the split and are listed for
information only; fixing them is optional.

| Cycle | Module | Cause |
|---|---|---|
| `network` ↔ `network.impl` | `powsybl-na-network` | `LfCopyable` → `LfNetworkCopier`, `LfGenerator` → `LfNetworkLoadingReport`, `PowerShift` → `LfLoadImpl` |
| `equations` ↔ `ac.equations` | `powsybl-na-equations` | `equations.JacobianMatrixFastDecoupled` uses AC equation types and terms; it could move to `ac.equations.fastdecoupled` |
| `graph` ↔ `graph.dtree` | `powsybl-na-graph` | `ModificationsContext` → `AbstractSetView` |
| `lf` ↔ `lf.outerloop` | `powsybl-open-loadflow` | `AbstractLoadFlowResult` → `OuterLoopResult` |
| `ac` ↔ `ac.solver`, `ac` ↔ `ac.outerloop` | `powsybl-open-loadflow` | engine and outer loops/solvers reference each other |
| root ↔ `lf`, `lf.outerloop.config`, `ac.solver`, `ac.outerloop`, `dc` | `powsybl-open-loadflow` | `OpenLoadFlowParameters` and `VoltageTargetChecker` are used by and use these packages |
