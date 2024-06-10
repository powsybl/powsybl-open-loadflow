![PowSyBl Logo](_static/logos/logo_lfe_powsybl.svg)
# Powsybl Open LoadFlow

## PowSyBl vs PowSyBl Open Load Flow

PowSyBl Open Load Flow provides:
- An open-source implementation of the [LoadFlow API from PowSyBl Core](inv:powsyblcore:simulation/loadflow/index.md), supporting DC and AC calculations.
- An open-source implementation of the [SecurityAnalysis API from PowSyBl Core](inv:powsyblcore:simulation/security/index.md), supporting DC and AC calculations.
- An open-source implementation of the [SensitivityAnalysis API from PowSyBl Core](inv:powsyblcore:simulation/sensitivity/sensitivity.md), supporting DC and AC calculations.

Most of the code is written in Java. It only relies on native code for the [KLU](http://faculty.cse.tamu.edu/davis/suitesparse.html) sparse linear solver.
Linux, Windows and MacOS are supported. KLU is distributed with license LGPL-2.1+.


### Common features

The AC calculations are based on full Newton-Raphson algorithm. The DC calculations are based on direct current linear approximation. Open Load Flow relies on:
- Fast and robust convergence, based on [KLU](http://faculty.cse.tamu.edu/davis/suitesparse.html) sparse solver.
- Distributed slack (on generators, on loads, or on conform loads); Manual or automatic slack bus selection as explained [here](loadflow/parameters.md).
- Support of generators' active and reactive power limits, including the support of reactive capability curves.
- 5 voltage initialization modes: flat, warm, angles-only based on a DC load flow, magnitude-only initialization based on a specific initializer,
or both voltages angle and magnitude initialization based on the two previous methods.
- Support of zero impedance branches, including complex zero impedance subnetworks, particularly important in case of voltage controls
and topology changes involved in contingencies or in remedial actions.
- Multiple synchronous component calculation, generally linked to HVDC lines.
- Modeling of secondary voltage control following research of [Balthazar Donon, Liège University](https://www.montefiore.uliege.be/cms/c_3482915/en/montefiore-directory?uid=u239564).
- Support of asymmetrical calculations.
- Implementation of three methods to update the state vector in the Newton-Raphson algorithm: classic, rescaling under maximum voltage change and linear search rescaling.

### About controls

Open Load Flow supports:
- Generator and static var compensator voltage remote control through PQV bus modelling. It supports any kind of shared voltage control between controllers that can be generators, static var compensators, or VSC converter stations.
- Static var compensator local voltage control with a slope (support the powsybl-core extension [VoltagePerReactivePowerControl](inv:powsyblcore/grid_model/extensions.md#remote-reactive-power-control).
- Local and remote phase control: phase tap changers can regulate active power flows or limit currents at given terminals.
- Local and remote voltage control by transformers, including shared controls.
- Local and remote voltage control by shunts, including shared controls.
- Remote reactive power control of a branch by generators, including shared controls.
- Remote reactive power control of a branch by transformers.

Heterogeneous voltage controls management has become a key feature. All well-modeled voltage controls are kept and managed
through a priority and a complex management of zero impedance lines. The generators have the first priority, followed by transformers,
and then shunts. In a load flow run, in a controlled bus, only the main voltage control of highest priority controls voltage.
When incremental outer loops are used, secondary priorities voltage controls can help generators that have reached reactive limits.


```{toctree}
---
maxdepth: 2
hidden: true
---
loadflow/index.md
security/index.md
sensitivity/index.md
```

