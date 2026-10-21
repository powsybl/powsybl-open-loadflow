# Code issues to solve before the split

This page lists the existing package dependencies that prevent the split into the modules of the
[target architecture](target_architecture.md). In a single module they compile, but once packages are spread over
modules each of them would be a dependency from a lower module to a higher one, i.e. a cyclic module dependency.

Dependencies on powsybl-core artifacts are not issues: any module, including `powsybl-na-commons` and
`powsybl-na-network`, may depend on powsybl-core, e.g. on `LoadFlowParameters` (loadflow API) or on
`BranchResult`/`BusResult` and limit violations (security analysis API). Only dependencies between packages of this
repository that would point from a lower module to a higher one are listed. Test classes are concerned too: the
tests of a module can only see that module, the modules below it and `powsybl-na-test`.

All the issues are fixed in the current single module, before any file is moved to a new module. Class names below
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

**Fix:** move `ReactiveRangeCheckMode`, `FictitiousGeneratorVoltageControlCheckMode` and
`SlackDistributionFailureBehavior` out of `OpenLoadFlowParameters` as top-level enums of the `network` package, like
the existing `network.SlackBusSelectionMode`. Move the two default value constants to `LfNetworkParameters`; the
`OpenLoadFlowParameters` constants reference them. The parameters JSON format and the parameter names are unchanged,
but Java code using `OpenLoadFlowParameters.<enum>` must import the enum from `network`.

### I2: network model depends on security analysis

| Class | Depends on |
|---|---|
| `network.LfBranch`, `network.impl.AbstractLfBranch`, `LfBranchImpl`, `LfBoundaryLineBranch`, `LfLegBranch`, `LfSwitch`, `LfTieLineBranch` | `sa.LimitScalingManager` |
| `network.impl.PropagatedContingencyCreationParameters` | `sa.OpenSecurityAnalysisParameters` |

**Why it blocks:** `powsybl-na-network` would depend on the security analysis code of `powsybl-open-loadflow`.

**Fix:** move `LimitScalingManager` and its test to `network`: limit scaling is a property of how branch limits are
read from IIDM, and the class only uses powsybl-core (`iidm.criteria`, `security.limitscaling`).
`PropagatedContingencyCreationParameters` owns `CONTINGENCY_PROPAGATION_DEFAULT_VALUE`, and
`OpenSecurityAnalysisParameters.CONTINGENCY_PROPAGATION_DEFAULT_VALUE` references it.

### I3: network model calls an AC equation term

`network.LfHvdc` calls the static `ac.equations.AbstractHvdcAcEmulationFlowEquationTerm.getAbsActivePowerWithLosses`.
In addition, a javadoc `{@link}` of `network.impl.LfVoltageSourceConverterImpl` points to
`ac.equations.dcnetwork.ConverterDroopEquationTerm`.

**Why it blocks:** `powsybl-na-network` would depend on `powsybl-na-equations`. A cross-module `{@link}` breaks the
javadoc generation of `powsybl-na-network`.

**Fix:** move the HVDC loss formula to a static method `LfHvdc.getAbsActivePowerWithLosses`, called by the two
`HvdcAcEmulationSide*ActiveFlowEquationTerm` classes. Turn the javadoc link into `{@code ConverterDroopEquationTerm}`.

### I4: network loader depends on the load flow network variant pool

`network.impl.LfNetworkList` uses the root class `NetworkVariantPool`.

**Fix:** move `NetworkVariantPool` and its test to `network.impl`: it is about IIDM variants, not about load flow.

### I5: equation system updaters depend on lf

| Class | Depends on |
|---|---|
| `ac.equations.AcEquationSystemCreator`, `AcEquationSystemUpdater` | `lf.AbstractEquationSystemUpdater` |
| `dc.equations.DcEquationSystemCreator`, `DcEquationSystemUpdater` | `lf.AbstractEquationSystemUpdater` |

**Why it blocks:** `powsybl-na-equations` would depend on `powsybl-open-loadflow`.

**Fix:** move `lf.AbstractEquationSystemUpdater` to `equations`. It only depends on `equations` and `network`
(including `LoadFlowModel`), so it is not load flow specific. The AC and DC creators and updaters stay in
`ac.equations`/`dc.equations` and go to `powsybl-na-equations` with the equation terms.

### I6: utilities depend on network, equations and load flow

| Class | Depends on |
|---|---|
| `util.Reports` | `network.LfBus`, `network.LfGenerator`, `lf.outerloop.DiscreteControllerChange`, `OpenLoadFlowReportConstants` |
| `util.Derivable` | `equations.Quantity`, `equations.Variable` |

**Why it blocks:** `powsybl-na-commons` would depend on all the other modules.

**Fix:**
- Split `Reports`, used by almost every package, by module:
  - the 51 report methods used by the network packages move to a new `network.NetworkReports`. Their 51 message keys
    move to `network/reports*.properties`, registered by a new `network.NetworkReportResourceBundle`;
  - the load flow, security and sensitivity reports move to `lf.Reports`, and the bundle class to
    `lf.PowsyblOpenLoadFlowReportResourceBundle`. Its `reports*.properties` keep the remaining 76 keys.
- Remove `OpenLoadFlowReportConstants`: its only constant becomes `NetworkReports.MISMATCH_TYPED_VALUE`.
- Move `Derivable` to `equations`, where it is used (with `sensi`).

Network reports can be added under a root report node built by the caller with only the Open Load Flow bundle, and
load flow reports can be added under network report nodes. So every node created by `NetworkReports` uses all the
report bundles found on the classpath, like the `LfNetwork` root report nodes already do
(`withAllResourceBundlesFromClasspath()`). Without that, messages such as `olf.componentsWithoutGenerators` would no
longer be found.

### I7: multi-threading helper depends on network loader and load flow

`util.mt.ContingencyMultiThreadHelper` depends on `network.impl` (`LfNetworkList`, `PropagatedContingency`...) and
`lf.AbstractLoadFlowParameters`. It is only used by `sa.AbstractSecurityAnalysis` and `sensi.AcSensitivityAnalysis`.

**Fix:** no code change. `util.mt` is a different Java package from `util`, so it goes to `powsybl-open-loadflow`
without splitting a package.

### I8: tests and test utilities depend on Open Load Flow

The test-jar is replaced by the `powsybl-na-test` module (see the [target architecture](target_architecture.md#test-utilities)),
which must only depend on `powsybl-na-commons` and powsybl-core:

| Class | Depends on |
|---|---|
| Test network factories (`network/**/*Factory*`) | `util.PerUnit` only: no issue |
| `util.LoadFlowAssert`, `util.LoadFlowResultBuilder` | powsybl-core only: no issue |
| `CommonTestConfig`, `ServiceParameterResolver` | powsybl-core and JUnit only: no issue |
| `sa.AbstractOpenSecurityAnalysisTest` | `OpenLoadFlowProvider`, `OpenLoadFlowParameters`, `graph.*ConnectivityFactory`, `network.LfBus`, `LfBranch`, `SlackBusSelectionMode` |

In addition, 8 test classes of the `network` and `equations` packages run a load flow, which the tests of
`powsybl-na-network` and `powsybl-na-equations` cannot do:

| Test classes | Load flow code used |
|---|---|
| `network.LfNetworkTest`, `network.MostMeshedSlackBusSelectorTest` | `OpenLoadFlowProvider`, `OpenLoadFlowParameters` |
| `network.impl.LfSwitchTest`, `LfNetworkCopierTest`, `LfNetworkCopyReflectionGuardTest`, `LfNetworkJsonStateTest` | `ac.AcLoadFlowParameters`, `AcLoadFlowContext`, `AcloadFlowEngine` |
| `equations.EquationArrayTest`, `EquationSystemTest` | AC load flow context, Newton-Raphson solver |

**Why it blocks:** `powsybl-na-test` would depend on `powsybl-open-loadflow`, whose tests depend on
`powsybl-na-test`: a cycle in the Maven reactor. The 8 test classes would not compile in their module.

**Fix:**
- Split `AbstractOpenSecurityAnalysisTest`:
  - a generic `test.AbstractSecurityAnalysisTest` goes to `powsybl-na-test`. It holds the network, monitor and
    contingency helpers, the result assertions and the `runSecurityAnalysis`/`runLoadFlow` methods, written against
    the `SecurityAnalysisProvider`/`LoadFlowProvider` interfaces with two abstract provider creation methods;
  - `sa.AbstractOpenSecurityAnalysisTest` extends it, creates the Open Load Flow providers and keeps
    `setSlackBusId`. It stays in the `powsybl-open-loadflow` tests and is no longer published.
- Move the 8 test classes unchanged to load flow test packages: the root package for the first two, `ac` for the
  others. `EquationArrayTest` used the protected `JacobianMatrix.initDer()` and `matrix`: it now calls the public
  `getMatrix()`, which runs the same `initDer()` on a fresh context.

### I9: test utilities are in packages owned by other modules

| Classes | Current package | Package owned by | Used by |
|---|---|---|---|
| `AbstractLoadFlowNetworkFactory` and 24 network factories | `network` | `powsybl-na-network` | 94 test classes |
| `LoadFlowAssert`, `LoadFlowResultBuilder` | `util` | `powsybl-na-commons` | 64 test classes |
| `CommonTestConfig`, `ServiceParameterResolver` | root package | `powsybl-open-loadflow` | 78 and 64 test classes |
| `AbstractOpenSecurityAnalysisTest` (generic part, see I8) | `sa` | `powsybl-open-loadflow` | 3 test classes |

**Why it blocks:** once in `powsybl-na-test`, these classes would put the root, `network`, `util` and `sa` packages in two
modules each. This contradicts the "one package, one module" rule of the
[target architecture](target_architecture.md#compatibility-for-users).

**Fix:** move them to `com.powsybl.openloadflow.test` and update the imports of the test classes. The 17 test classes
of package `network` use the factories without importing them, so they now need imports. The factory helpers
are `protected static` methods of `AbstractLoadFlowNetworkFactory`, and the 6 test classes outside the factories that
use them do it by extending it, so the move keeps them accessible. Downstream test code must update its imports.

### I10: graph tests depend on the network model

`graph.BridgesTest` and `graph.NetworkConnectivityTest` build an `LfNetwork` from IIDM test networks to exercise the
connectivity algorithms. The tests of `powsybl-na-graph` cannot see `powsybl-na-network`.

**Why it blocks:** the two test classes would not compile in `powsybl-na-graph`.

**Fix:** move both classes to the `network` test package, except the tests that need package-private or protected
members of `graph`. Those are rewritten in `graph` on small synthetic graphs of integer vertices that have the same
topology:
- `testFindBridges` tests the package-private `BridgesFinder`. It becomes `graph.BridgesFinderTest`, on the topology
  of the Eurostag tutorial network: NGEN–NHV1, a doubled NHV1–NHV2 line, NHV2–NLOAD.
- `testNonConnectedComponents` tests the protected `AbstractGraphConnectivity.getNonConnectedVertices`. It becomes
  `graph.GraphConnectivityNonConnectedVerticesTest`, on three components linked by the two edges that are cut. It
  covers the same three connectivity implementations and the null-vertex check.

`BridgesFinder` itself is used by no main code, only by its test.

## Enforcing the layering until the split

As long as all packages are in a single module, nothing prevents new back-dependencies. A layering test based on
[ArchUnit](https://www.archunit.org/) is added together with the fixes. It declares one layer per target module
with its packages, allows dependencies only towards the lower modules, and checks main and test classes together.
I10 was found that way.

| Layer | Packages | May be used by |
|---|---|---|
| `powsybl-na-commons` | `util` (not `util.mt`) | all the other layers |
| `powsybl-na-graph` | `graph..` | network, equations, open-loadflow |
| `powsybl-na-network` | `network..` | equations, open-loadflow |
| `powsybl-na-equations` | `equations..`, `ac.equations..`, `dc.equations..`, `adm..` | open-loadflow |
| `powsybl-na-test` | `test` | all the other layers; it may only use commons |
| `powsybl-open-loadflow` | all the other packages | no other layer |

The test is removed by the split: the Maven module dependencies then enforce the same rules at compile time.

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
