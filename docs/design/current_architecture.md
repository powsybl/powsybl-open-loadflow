# Current architecture

`powsybl-open-loadflow` is a single Maven module (artifact `com.powsybl:powsybl-open-loadflow`) and a single Java
package tree `com.powsybl.openloadflow`. It implements three powsybl-core APIs through providers registered with
`@AutoService`:
- `OpenLoadFlowProvider` (`LoadFlowProvider`),
- `sa.OpenSecurityAnalysisProvider` (`SecurityAnalysisProvider`),
- `sensi.OpenSensitivityAnalysisProvider` (`SensitivityAnalysisProvider`).

Other SPIs: `ac.solver.AcSolverFactory` implementations, `sa.DefaultContingencyActivePowerLossDistribution`,
`sa.extensions.ContingencyLoadFlowParametersJsonSerializer` and the report resource bundle
`util.report.PowsyblOpenLoadFlowReportResourceBundle`.

## Package groups

The diagram below groups the packages by responsibility and shows the intended dependencies between the groups.

```{raw} html
<likec4-view view-id="current"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

| Group | Packages | Responsibility |
|---|---|---|
| Providers and parameters | root package | SPI providers, `OpenLoadFlowParameters`, network caches, `VoltageTargetChecker` |
| Security analysis | `sa`, `sa.extensions` | AC, DC and Woodbury DC security analysis |
| Sensitivity analysis | `sensi`, `sensi.mt` | AC and DC sensitivity analysis |
| Load flow engines | `lf`, `lf.outerloop`, `ac`, `ac.outerloop`, `ac.solver`, `dc`, `dc.fastdc` | engines, outer loops, Newton-Raphson solvers, fast DC |
| Equation system | `equations`, `ac.equations`, `dc.equations` | generic equation system framework, AC and DC equation terms, equation system creators |
| Admittance matrix | `adm` | admittance equation system, used by `VoltageTargetChecker` |
| Network model | `network`, `network.impl`, `network.util`, `network.action` | `LfNetwork` model, IIDM loader, contingencies and actions |
| Graph connectivity | `graph`, `graph.dtree` | incremental connectivity algorithms |
| Utilities | `util`, `util.report`, `util.mt` | per-unit, Fortescue, evaluables, reports, multi-threading helpers |

## External dependencies

| Artifact | Used by |
|---|---|
| `powsybl-iidm-api`, `powsybl-iidm-extensions` | network model loader, providers |
| `powsybl-loadflow-api` | providers, engines, network model |
| `powsybl-security-analysis-api` | security analysis, network model (results, limit violations) |
| `powsybl-sensitivity-analysis-api` | sensitivity analysis |
| `powsybl-math` | equation system (sparse matrices, KLU), graph |
| `powsybl-commons` | everywhere (reports, extensions, configuration) |

The contingency, action, `iidm.criteria` and computation APIs are used in the code but not declared in `pom.xml`;
they only come in transitively.

## Dependencies that do not respect the layering

Several reusable package groups depend on load flow or security analysis code of this repository. These
dependencies are harmless in a single module but would create cyclic dependencies between modules. Dependencies on
powsybl-core are not concerned. They are detailed in
[code issues](code_issues.md).

```{raw} html
<likec4-view view-id="currentViolations"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```
