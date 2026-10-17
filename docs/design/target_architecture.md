# Target architecture

## Landscape

The repository is renamed `powsybl-network-analysis`. It still sits between powsybl-core and the users of the load
flow, security analysis and sensitivity analysis providers. It will also host the state estimation, short circuit
and network reduction modules.

```{raw} html
<likec4-view view-id="index"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

## Modules

The parent POM `com.powsybl:powsybl-network-analysis` (packaging `pom`, parent `powsybl-parent`) aggregates the
following modules, all sharing the same version:

```{raw} html
<likec4-view view-id="target"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

| Module | Content (current packages) | Depends on |
|---|---|---|
| `powsybl-na-commons` | `util` (after `Reports` is split and `Derivable` moved, see issue I6), `util.report` | powsybl-core |
| `powsybl-na-graph` | `graph`, `graph.dtree` | `powsybl-na-commons`, powsybl-core, jgrapht |
| `powsybl-na-network` | `network`, `network.impl`, `network.util`, `network.action` | `powsybl-na-graph`, powsybl-core (IIDM, loadflow, security analysis, contingency and action APIs) |
| `powsybl-na-equations` | `equations`, AC and DC equation terms (`ac.equations`, `dc.equations` without the equation system creators and updaters), `adm` | `powsybl-na-network`, powsybl-core |
| `powsybl-open-loadflow` | root package, `lf`, `ac`, `ac.outerloop`, `ac.solver`, `dc`, `dc.fastdc`, AC and DC equation system creators and updaters, `sa`, `sensi`, `util.mt` | all the modules above, powsybl-core |
| `powsybl-na-test` | test networks and test utilities (see [test utilities](#test-utilities)) | `powsybl-na-commons`, powsybl-core, JUnit |

Dependencies only go one way: `powsybl-open-loadflow` → `powsybl-na-equations` → `powsybl-na-network` →
`powsybl-na-graph` → `powsybl-na-commons`. A dependency in the other direction fails the build, which is what makes
the reusable modules really reusable. Every module may depend on any powsybl-core artifact, including the loadflow
and security analysis APIs. Each module declares the powsybl-core artifacts it uses: today the contingency, action,
`iidm.criteria` and computation APIs are only obtained transitively.

`powsybl-na-test` is a regular module whose content is in `src/main/java`. The other modules depend on it with the
`test` scope, and downstream projects can do the same. It only depends on `powsybl-na-commons`, so that every other
module can use it in its tests without creating a cycle in the Maven reactor.

### Compatibility for users

- The `powsybl-open-loadflow` artifactId is kept and still contains the three providers and `OpenLoadFlowParameters`.
  pypowsybl and other users keep the same Maven dependency and the same provider names; the reusable modules come
  in transitively.
- Java package names are kept (`com.powsybl.openloadflow.*`). `LfNetworkLoaderPostProcessor` plugins, external
  `AcSolver` implementations and outer loop configurations keep compiling.
- A Java package lives in exactly one module (no split packages). This is why the AC and DC equation system creators
  and updaters, which are load flow specific, move out of `ac.equations`/`dc.equations` into a load flow package,
  and why the test utilities move to `com.powsybl.openloadflow.test`.
- Test code of downstream projects using the test-jar must switch to `powsybl-na-test` and update its imports. This
  only affects test code.

### Planned analyses

State estimation, short circuit and network reduction will be modules of `powsybl-network-analysis`. They depend
on the reusable modules only, never on `powsybl-open-loadflow`. Short circuit and network reduction can be developed
outside the repository first, depending on the published `powsybl-na-*` artifacts, and join it later as modules.

```{raw} html
<likec4-view view-id="targetReuse"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

| Analysis | Status | Joins the repository | Reuses |
|---|---|---|---|
| State estimation | developed in the repository (module `powsybl-na-state-estimation`, proposed name) | by the end of 2026 | network model, AC/DC equation terms for measurement equations, equation system and Jacobian |
| Short circuit | exists outside: [powsybl-open-sc](https://github.com/powsybl/powsybl-open-sc) | timeline to be decided | network model, admittance matrix |
| Network reduction | proof of concept outside: [powsybl-incubator](https://github.com/powsybl/powsybl-incubator/tree/main/simulator/network-reduction) | timeline to be decided | network model, admittance matrix, graph connectivity to split internal and external areas |

State estimation will be added by the end of 2026, so at least the code issues blocking `powsybl-na-commons`,
`powsybl-na-graph`, `powsybl-na-network` and `powsybl-na-equations` should be fixed before then.

## SPI, resources and test utilities

| Item | Target module |
|---|---|
| `OpenLoadFlowProvider`, `OpenSecurityAnalysisProvider`, `OpenSensitivityAnalysisProvider` | `powsybl-open-loadflow` |
| `AcSolverFactory` implementations, `ContingencyActivePowerLossDistribution` | `powsybl-open-loadflow` |
| `LfNetworkLoaderPostProcessor` SPI | `powsybl-na-network` |
| Report resource bundle and `reports*.properties` | one bundle per module, each module owning its report keys |

`@AutoService` keeps generating the `META-INF/services` files of each module.

### Test utilities

The test-jar of `powsybl-open-loadflow` is removed. The test networks and the test utilities become the main code of
the `powsybl-na-test` module, in the dedicated package `com.powsybl.openloadflow.test`. Today they are in
the root package and `com.powsybl.openloadflow.network`, `util` and `sa`, packages owned by other modules: keeping them there would split
these packages over two modules (see issue I9).

| Item | Target |
|---|---|
| `AbstractLoadFlowNetworkFactory` and the test network factories (`network/**/*Factory*`) | `powsybl-na-test` |
| `LoadFlowAssert`, `LoadFlowResultBuilder` | `powsybl-na-test` |
| `CommonTestConfig` (matrix factory used by the tests), `ServiceParameterResolver` (JUnit parameter resolver) | `powsybl-na-test` |
| Generic part of `AbstractOpenSecurityAnalysisTest` (contingency and limit helpers, result assertions) | `powsybl-na-test` |
| Open Load Flow specific part of `AbstractOpenSecurityAnalysisTest` (`OpenLoadFlowProvider`, `OpenLoadFlowParameters`, `LfBus`/`LfBranch` checks), `OLFDefaultParametersLoaderMock` | test sources of `powsybl-open-loadflow`, not published |

Despite their names, `LoadFlowAssert`, `LoadFlowResultBuilder` and the network factories are not load flow specific:
they build IIDM networks and check IIDM values with powsybl-core APIs only, so any analysis can use them.

`powsybl-na-test` cannot depend on `powsybl-open-loadflow`, whose tests use it: the classes that need the load flow
provider or the internal network model stay in the test sources of the module that owns them (see issue I8).
Downstream projects replace their dependency on the test-jar (`<type>test-jar</type>`) with a `test` scope
dependency on `powsybl-na-test`, and update their imports to `com.powsybl.openloadflow.test`.

## Build, CI and documentation

- `powsybl-parent` remains the parent of the parent POM, so checkstyle and spotbugs settings are unchanged.
- The CI builds the whole reactor. SonarCloud analysis needs an aggregated JaCoCo report and a new project key.
- The documentation stays a single ReadTheDocs project for the whole repository. `docs/conf.py` must read the version
  from the new parent artifactId (`module_name`).

## Open decisions

| Decision | Recommendation |
|---|---|
| Rename Java packages to `com.powsybl.networkanalysis.*` (or similar) for the reusable modules | Not in the first step: it breaks every downstream plugin for no functional gain. Can be done later, module by module. |
| Split security analysis and sensitivity analysis into their own modules | Not in the first step: both depend heavily on the load flow engines and on `OpenLoadFlowParameters`, and the split would not make anything reusable. |
| Move the Newton-Raphson solver (`ac.solver`) to `powsybl-na-equations` so that state estimation can reuse it | Only once its factories no longer depend on `OpenLoadFlowParameters`. State estimation may need a different solver (e.g. Gauss-Newton) anyway. |
| Keep contingency and action modeling (`network.action`, `LfContingency`, `PropagatedContingency`) in `powsybl-na-network` | Keep it: network topology changes are needed by several analyses, and it only adds a dependency on the contingency and action APIs. |

## Migration steps

1. **Fix the code issues in the single module.** Each issue of [code issues](code_issues.md) gets its own pull
   request. A layering test (e.g. with ArchUnit) can be added to prevent new back-dependencies until the split.
2. **Rename the GitHub repository** to `powsybl-network-analysis`. GitHub redirects the old URLs.
3. **Create the multi-module layout** and move the packages module by module, starting from `powsybl-na-commons`.
   Each move is a pure file move, with no code change.
4. **Update CI, SonarCloud and ReadTheDocs** to the new names.
