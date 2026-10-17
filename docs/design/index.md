# Design: powsybl-network-analysis

This section describes how the `powsybl-open-loadflow` repository will be renamed `powsybl-network-analysis` and
turned into a multi-module Maven project. It is a design document: no code change is described here as done, the
section documents the current architecture, the target architecture and the code issues to solve before the split.

```{toctree}
---
maxdepth: 2
hidden: true
---
current_architecture.md
target_architecture.md
code_issues.md
```

## Motivation

A large part of the code base is not specific to load flow:
- the per-unit network model (`LfNetwork`, `LfBus`, `LfBranch`...) and its loader from IIDM,
- the generic equation system framework and the AC, DC and admittance equation terms,
- the incremental graph connectivity algorithms,
- utilities such as per-unit conversion and Fortescue transforms.

These building blocks are needed by other network analyses: state estimation, short circuit calculation and network
reduction. These analyses will all be part of `powsybl-network-analysis`. State estimation will be added by the end
of 2026. Short circuit and network reduction can start outside the repository and join it later: short circuit is
today provided by [powsybl-open-sc](https://github.com/powsybl/powsybl-open-sc), which will join with a timeline still
to be decided. Network reduction has a proof of concept in
[powsybl-incubator](https://github.com/powsybl/powsybl-incubator/tree/main/simulator/network-reduction). Today the building blocks can only be reused by depending on the whole load flow artifact, and
nothing prevents load flow or security analysis concerns from leaking into them. Splitting the repository into
modules makes the reusable parts explicit and enforces the dependency direction at build time.

## Origin and changes to the initial proposal

The idea comes from [powsybl-open-loadflow#417](https://github.com/powsybl/powsybl-open-loadflow/issues/417), which
proposed a `powsybl-open-simulator` multi-module project. This design keeps the multi-module approach with the
following changes:
- **Name:** `powsybl-network-analysis` instead of `powsybl-open-simulator`, because state estimation and network
  reduction are not simulators. Module artifacts are prefixed `powsybl-na-`.
- **No rename of `LfNetwork`:** the proposed `OsNetwork` rename is abandoned. The network model classes keep their
  names.
- **No transfer of `com.powsybl.math.matrix`:** the matrix package stays in powsybl-core (`powsybl-math`).

## Scope

In scope:
- [current architecture](current_architecture.md): the single module as it is today,
- [target architecture](target_architecture.md): the modules, their content and their allowed dependencies,
- [code issues](code_issues.md): the existing package dependencies that prevent the split and how to fix them.

Out of scope:
- the design of state estimation, short circuit and network reduction themselves: this section only defines the
  modules they build on,
- any change of the public load flow, security analysis and sensitivity analysis behavior.

## Diagrams

The diagrams are written with [LikeC4](https://likec4.dev) (sources in `docs/design/likec4`) and are interactive in
the online documentation: click an element to see its details, use the views browser to navigate between views.
