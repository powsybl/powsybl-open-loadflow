# Target architecture

## Landscape

The repository is renamed `powsybl-network-analysis`. It still sits between powsybl-core and the users of the load
flow, security analysis and sensitivity analysis providers. It will also host the state estimation, short circuit and
network reduction modules.

```{raw} html
<likec4-view view-id="index"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

## Modules

The parent POM `com.powsybl:powsybl-network-analysis` (packaging `pom`, parent `powsybl-parent`) aggregates the
following modules, all sharing the same version. Module directories have short names, artifactIds carry the
`powsybl-` prefix, like in powsybl-core.

```{raw} html
<likec4-view view-id="target"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

| Directory | Artifact | Content (packages of `com.powsybl.openloadflow`) | Depends on |
|---|---|---|---|
| `commons` | `powsybl-na-commons` | `util` (after `Reports` is split and `Derivable` moved, see issue I6), the `PowsyblOpenLoadFlowVersion` template | powsybl-core |
| `graph` | `powsybl-na-graph` | `graph`, `graph.dtree` | `powsybl-na-commons`, powsybl-core, jgrapht |
| `network` | `powsybl-na-network` | `network`, `network.impl`, `network.util`, `network.action`, network report messages | `powsybl-na-graph`, powsybl-core (IIDM, loadflow, security analysis, contingency and action APIs) |
| `equations` | `powsybl-na-equations` | `equations`, `ac.equations`, `dc.equations`, `adm` | `powsybl-na-network`, powsybl-core |
| `open-loadflow` | `powsybl-open-loadflow` | root package, `lf`, `ac`, `ac.outerloop`, `ac.solver`, `dc`, `dc.fastdc`, `sa`, `sensi`, `util.mt`, load flow report messages | all the modules above, powsybl-core |
| `test` | `powsybl-na-test` | `test`: test networks and test utilities (see [test utilities](#test-utilities)) | `powsybl-na-commons`, powsybl-core, JUnit, Mockito |
| `distribution` | `powsybl-na-distribution` | no code: aggregates all the modules for the code coverage report | all the modules |

The reusable modules only depend on lower modules: `powsybl-na-equations` → `powsybl-na-network` →
`powsybl-na-graph` → `powsybl-na-commons`. A dependency in the other direction fails the build, which is what makes
them really reusable. The analyses depend on the reusable modules, and on `powsybl-open-loadflow` when they need a
load flow, e.g. short circuit for its pre-fault state, or state estimation for a DC voltage initialization (see
[analyses](#analyses)). Nothing depends on an analysis module, except `distribution`.

Every module may depend on any powsybl-core artifact, including the loadflow and security analysis APIs. Each module
declares the powsybl-core artifacts it uses: today the contingency, action, `iidm.criteria` and computation APIs are
only obtained transitively.

`PowsyblOpenLoadFlowVersion` is generated from `src/main/java-templates` and registered with
`@AutoService(Version.class)`. It is in package `util`, so it stays in `powsybl-na-commons`, together with the
buildnumber and templating plugins and the `powsybl-tools` dependency.

### Compatibility for users

- The `powsybl-open-loadflow` artifactId is kept and still contains the three providers and `OpenLoadFlowParameters`.
  pypowsybl and other users keep the same Maven dependency and the same provider names; the reusable modules come
  in transitively. The parameters JSON format and the parameter names are unchanged.
- Java package names are kept (`com.powsybl.openloadflow.*`, `com.powsybl.sc.*`). `LfNetworkLoaderPostProcessor`
  plugins, external `AcSolver` implementations and outer loop configurations keep compiling.
- A Java package lives in exactly one module (no split packages). This is why the test utilities move to
  `com.powsybl.openloadflow.test`.
- The code issue fixes move a few public classes, so Java code using them must update its imports:

| Before | After |
|---|---|
| `OpenLoadFlowParameters.ReactiveRangeCheckMode`, `FictitiousGeneratorVoltageControlCheckMode`, `SlackDistributionFailureBehavior` | `network.ReactiveRangeCheckMode`, `network.FictitiousGeneratorVoltageControlCheckMode`, `network.SlackDistributionFailureBehavior` |
| `sa.LimitScalingManager` | `network.LimitScalingManager` |
| `NetworkVariantPool` | `network.impl.NetworkVariantPool` |
| `lf.AbstractEquationSystemUpdater` | `equations.AbstractEquationSystemUpdater` |
| `util.Reports` | `lf.Reports` and `network.NetworkReports` |
| `util.report.PowsyblOpenLoadFlowReportResourceBundle` | `lf.PowsyblOpenLoadFlowReportResourceBundle` |
| `OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE` | `network.NetworkReports.MISMATCH_TYPED_VALUE` |
| `util.Derivable` | `equations.Derivable` |

- Test code of downstream projects using the test-jar must switch to `powsybl-na-test` and update its imports. This
  only affects test code.

### Analyses

State estimation, short circuit and network reduction are meant to be modules of `powsybl-network-analysis`. They
depend on the reusable modules and, when they need a load flow, on `powsybl-open-loadflow`. Short circuit and network
reduction can stay outside the repository first, depending on the published `powsybl-na-*` artifacts, and join it
later.

```{raw} html
<likec4-view view-id="targetReuse"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

| Analysis | Status | Joins the repository | Reuses |
|---|---|---|---|
| State estimation | developed in the repository (module `powsybl-na-state-estimation`, proposed name) | by the end of 2026 | network model, AC/DC equation terms for measurement equations, equation system and Jacobian, possibly Open Load Flow for a DC voltage initialization |
| Short circuit | exists outside: [powsybl-open-sc](https://github.com/powsybl/powsybl-open-sc), see [short circuit](#short-circuit) | to be decided by the powsybl-open-sc maintainers | network model, equation system, Open Load Flow AC engine for the pre-fault state |
| Network reduction | proof of concept outside: [powsybl-incubator](https://github.com/powsybl/powsybl-incubator/tree/main/simulator/network-reduction) | timeline to be decided | network model, admittance matrix, graph connectivity to split internal and external areas |

State estimation will be added by the end of 2026, so at least the code issues blocking `powsybl-na-commons`,
`powsybl-na-graph`, `powsybl-na-network` and `powsybl-na-equations` should be fixed before then.

An analysis that needs a load flow, such as state estimation for a DC voltage initialization, can use it either
through the powsybl-core `LoadFlow` API or by calling Open Load Flow directly (e.g. its DC engine). Only the second
way makes the module depend on `powsybl-open-loadflow`. The choice is left to each analysis implementation and is
not part of the repository rename and reorganization.

### Short circuit

The following is based on early testing with the current main branch of
[powsybl-open-sc](https://github.com/powsybl/powsybl-open-sc). Whether, when and how short circuit joins the
repository is to be decided by the powsybl-open-sc maintainers when they decide to join.

- powsybl-open-sc fits in as a `short-circuit` group of modules, keeping its Java packages `com.powsybl.sc.*`.
- Its engine needs `powsybl-open-loadflow`: it computes the pre-fault state with the Open Load Flow AC engine and
  works on the network model and the equation system.
- No code migration was needed: the code compiled and its tests passed with the current powsybl-core and Open Load
  Flow, the code issue fixes included. Only formatting changes were required by the stricter checkstyle of the newer
  `powsybl-parent`.

## SPI, resources and test utilities

| Item | Target module |
|---|---|
| `OpenLoadFlowProvider`, `OpenSecurityAnalysisProvider`, `OpenSensitivityAnalysisProvider` | `powsybl-open-loadflow` |
| `AcSolverFactory` implementations, `ContingencyActivePowerLossDistribution` | `powsybl-open-loadflow` |
| `LfNetworkLoaderPostProcessor` SPI | `powsybl-na-network` |
| `PowsyblOpenLoadFlowVersion` (`Version` SPI) | `powsybl-na-commons` |
| `NetworkReportResourceBundle` and `network/reports*.properties` | `powsybl-na-network` |
| `PowsyblOpenLoadFlowReportResourceBundle` and `reports*.properties` | `powsybl-open-loadflow` |

`@AutoService` keeps generating the `META-INF/services` files of each module.

### Test utilities

The test-jar of `powsybl-open-loadflow` is removed. The test networks and the test utilities become the main code of
the `powsybl-na-test` module, in the dedicated package `com.powsybl.openloadflow.test`. Today they are in
the root package and `com.powsybl.openloadflow.network`, `util` and `sa`, packages owned by other modules: keeping
them there would split these packages over two modules (see issue I9).

| Item | Target |
|---|---|
| `AbstractLoadFlowNetworkFactory` and the test network factories (`network/**/*Factory*`) | `powsybl-na-test` |
| `LoadFlowAssert`, `LoadFlowResultBuilder` | `powsybl-na-test` |
| `CommonTestConfig` (matrix factory used by the tests), `ServiceParameterResolver` (JUnit parameter resolver) | `powsybl-na-test` |
| `AbstractSecurityAnalysisTest`, the generic part of `AbstractOpenSecurityAnalysisTest` (see issue I8) | `powsybl-na-test` |
| `AbstractOpenSecurityAnalysisTest` (Open Load Flow providers, `setSlackBusId`), `OLFDefaultParametersLoaderMock` | test sources of `powsybl-open-loadflow`, not published |

Despite their names, `LoadFlowAssert`, `LoadFlowResultBuilder` and the network factories are not load flow specific:
they build IIDM networks and check IIDM values with powsybl-core APIs only, so any analysis can use them.

`powsybl-na-test` cannot depend on `powsybl-open-loadflow`, whose tests use it: the classes that need the load flow
provider or the internal network model stay in the test sources of the module that owns them (see issue I8).
Downstream projects replace their dependency on the test-jar (`<type>test-jar</type>`) with a `test` scope
dependency on `powsybl-na-test`, and update their imports to `com.powsybl.openloadflow.test`.

The powsybl-core BOM manages `junit-jupiter`, `mockito-core` and `powsybl-commons-test` with the `test` scope. As
they are used by the main code of `powsybl-na-test`, its POM declares them with the `compile` scope.

## Build, CI and documentation

- `powsybl-parent` remains the parent of the parent POM, so checkstyle and spotbugs settings are unchanged. The
  parent POM holds the module list, the surefire configuration, the powsybl-core BOM and the versions of the other
  third-party dependencies (asciitable, jafama, slf4j-toys).
- The CI builds the whole reactor with the same command (`./mvnw -Pjacoco install`). Most of the code of the
  reusable modules is only exercised by the load flow tests, so per-module coverage reports would underestimate it.
  As in powsybl-core, the `distribution` module runs the JaCoCo `report-aggregate` goal in the `jacoco` profile, and
  the parent POM points SonarCloud to it (`sonar.coverage.jacoco.xmlReportPaths` set to
  `../distribution/target/site/jacoco-aggregate/jacoco.xml`).
- Each module running tests has its own `logback-test.xml`. The other test resources stay with the
  `powsybl-open-loadflow` tests, which are the only ones using them.
- The documentation stays at the repository root, in `docs`, as a single ReadTheDocs project documenting all the
  modules. `docs/conf.py` reads the version from the parent POM (`module_name` set to `powsybl-network-analysis`).

## Open decisions

| Decision | Recommendation |
|---|---|
| Rename Java packages to `com.powsybl.networkanalysis.*` (or similar) for the reusable modules | Not in the first step: it breaks every downstream plugin for no functional gain. Can be done later, module by module. |
| Split security analysis and sensitivity analysis into their own modules | Not in the first step: both depend heavily on the load flow engines and on `OpenLoadFlowParameters`, and the split would not make anything reusable. |
| Move the Newton-Raphson solver (`ac.solver`) to `powsybl-na-equations` so that state estimation can reuse it | Only once its factories no longer depend on `OpenLoadFlowParameters`. State estimation may need a different solver (e.g. Gauss-Newton) anyway. |

Contingency and action modeling (`network.action`, `LfContingency`, `PropagatedContingency`) stays in
`powsybl-na-network`: network topology changes are needed by several analyses, and it only adds a dependency on the
contingency and action APIs.

## Migration steps

1. **Fix the code issues in the single module**, one pull request per issue of [code issues](code_issues.md), and
   add the ArchUnit layering test to prevent new back-dependencies until the split.
2. **Rename the GitHub repository** to `powsybl-network-analysis`. GitHub redirects the old URLs.
3. **Create the multi-module layout** described above and move the sources module by module, with `git mv`. Each move
   is a pure file move, with no code change. Remove the test-jar and the ArchUnit layering test.
4. **Update CI, SonarCloud and ReadTheDocs** to the new names.

Short circuit can join at any point after the split, when the powsybl-open-sc maintainers decide to (see
[short circuit](#short-circuit)).
