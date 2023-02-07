# PowSyBl Open Load Flow

[![Actions Status](https://github.com/powsybl/powsybl-open-loadflow/workflows/CI/badge.svg)](https://github.com/powsybl/powsybl-open-loadflow/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-open-loadflow&metric=coverage)](https://sonarcloud.io/component_measures?id=com.powsybl%3Apowsybl-open-loadflow&metric=coverage)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-open-loadflow&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.powsybl%3Apowsybl-open-loadflow)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
[![Slack](https://img.shields.io/badge/slack-powsybl-blueviolet.svg?logo=slack)](https://join.slack.com/t/powsybl/shared_invite/zt-rzvbuzjk-nxi0boim1RKPS5PjieI0rA)

PowSyBl (**Pow**er **Sy**stem **Bl**ocks) is an open source library written in Java, that makes it easy to write complex
software for power systems’ simulations and analysis. Its modular approach allows developers to extend or customize its
features.

PowSyBl is part of the LF Energy Foundation, a project of The Linux Foundation that supports open source innovation projects
within the energy and electricity sectors.

<p align="center">
<img src="https://raw.githubusercontent.com/powsybl/powsybl-gse/main/gse-spi/src/main/resources/images/logo_lfe_powsybl.svg?sanitize=true" alt="PowSyBl Logo" width="50%"/>
</p>

Read more at https://www.powsybl.org !

This project and everyone participating in it is governed by the [PowSyBl Code of Conduct](https://github.com/powsybl/.github/blob/main/CODE_OF_CONDUCT.md).
By participating, you are expected to uphold this code. Please report unacceptable behavior to [powsybl-tsc@lists.lfenergy.org](mailto:powsybl-tsc@lists.lfenergy.org).

## PowSyBl vs PowSyBl Open Load Flow

PowSyBl Open Load Flow provides:
- An open source implementation of the [LoadFlow API from PowSyBl Core](https://www.powsybl.org/pages/documentation/simulation/powerflow/), we support either DC or AC calculations.
- An open source implementation of the [SecurityAnalysis API from PowSyBl Core](https://www.powsybl.org/pages/documentation/simulation/securityanalysis/), we support either DC or AC calculations.
- An open source implementation of the [SensitivityAnalysis API from PowSyBl Core](https://www.powsybl.org/pages/documentation/simulation/sensitivity/), we support either DC or AC calculations.

Almost all of the code is written in Java. It only relies on native code for the [KLU](http://faculty.cse.tamu.edu/davis/suitesparse.html) sparse linear solver. Linux, Windows and MacOS are supported.

### Common features

The AC calculations are based on full Newton-Raphson algorithm. The DC calculations are based on direct current linear approximation. Open Load Flow relies on:
 - a fast and robust convergence, based on [KLU](http://faculty.cse.tamu.edu/davis/suitesparse.html) sparse solver.
 - a distributed slack (on generation or on loads or on conform loads); Slack bus selection could be automatic or explicit as explained [here](https://www.powsybl.org/pages/documentation/simulation/powerflow/openlf.html#parameters).
 - a support of generators' active and reactive power limits, included the support of reactive capability curves.
 - 5 starting point modes: flat, warm, only voltage angles initialization based on a DC load flow, only voltages magnitude initialization based on a specific initializer, or both voltages angle and magnitude initialization based on the two previous methods.
 - a support of non impedant branches, including complex non impedant sub-networks.
 - a multiple synchronous component calculation, generally linked to HVDC lines.

 ### About controls

 Open Load Flow supports:
 - a generator and static var compensator voltage remote control through PQV bus modelling. It supports any kind of shared voltage control between controllers that can be generators, static var compensators or VSC converter stations.
 - a static var compensator local voltage control involving a slope (support the powsybl-core extension [```VoltagePerReactivePowerControl```](https://www.powsybl.org/pages/documentation/grid/model/extensions.html).
 - a local and remote phase control: phase tap changers can regulate active power flows or limit currents at given terminals.
 - a local and remote voltage control by transformers. It also supports shared controls between them. In case of a controlled bus that has both a voltage control by a generator and a transformer, we have decided in a first approach to discard the transformer control.
 - a local and remote voltage control by shunts. We also support shared controls between them. In case of a controlled bus that has both a voltage control by a generator and a shunt, we have decided in a first approach to discard the shunt voltage control. In case of a controlled bus that has both a voltage control by a transformer and a shunt, we have decided in a first approach to discard the shunt. Several shunts on a controller bus are supported. 
 - a remote reactive power control of a branch by a single generator connected on a bus.

### Security analysis implementation 

 - Network in node/breaker topology and in bus/breaker topology.
 - Contingency on branches and on shunt compensators. Note that for shunt compensators, we don't support a contingency on it with a global voltage control by shunts at this stage.
 - All kind of operational limits violations detection on branches (permanent and temporary limits): current limits, apparent power limits, active power limits.
 - High and low voltage violations detection on buses.
 - Complex cases where the contingency leads to another synchronous component where a new resolution has to be performed are not supported at that stage.
 - The active and reactive power flows on branches, angle or voltage at buses can be monitored and collected for later analysis after the base case and after each contingency.

### Sensitivity analysis implementation 

 Open Load Flow both supports AC and DC calculations. Even if it comes from the same powsybl-core API, the calculations behind are radically different. The AC post contingency sensitivities calculation is based on the same principles than the AC security analysis. The DC post contingency sensitivities calculation is highly optimized and fully documented [here](https://www.powsybl.org/pages/documentation/simulation/sensitivity/openlf.html).

It supports all types of sensitivity factors that can be find in the API: 
- Variables: injection increase, phase angle shift, HVDC set point increase, and for AC calculations only generator, static var compensator, transformers or shunt voltage target increase.
- Functions: the active flow or the current on a branch, and for AC calculations only the voltage on a bus.

It supports contingencies of type:
- branch contingencies,
- load and generator contingencies,
- HVDC line contingency.

## Getting started

Running a load flow with PowSyBl Open Load Flow is easy. First let's start loading a IEEE 14 bus network. We first add a few Maven 
dependencies to respectively have access to network model, IEEE test networks and simple logging capabilities:

```xml
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-iidm-impl</artifactId>
    <version>5.1.0</version>
</dependency>
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-ieee-cdf-converter</artifactId>
    <version>5.1.0</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>1.7.22</version>
</dependency>
```

We are now able to load the IEEE 14 bus:
 ```java
Network network = IeeeCdfNetworkFactory.create14();
 ```

After adding a last Maven dependency on Open Load Flow implementation:
```xml
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-open-loadflow</artifactId>
    <version>1.0.0</version>
</dependency>
```

We can run the load flow with default parameters on the network:
```java
LoadFlow.run(network);
```

State variables and power flows computed by the load flow are have been updated inside the network model and we can for instance 
print on standard output buses voltage magnitude and angle:

```java
network.getBusView().getBusStream().forEach(b -> System.out.println(b.getId() + " " + b.getV() + " " + b.getAngle()));
```
## Contributing to PowSyBl Open Load Flow

PowSyBl Open Load Flow could support more features. The following list is not exhaustive and is an invitation to collaborate:

We can always increase or improves features and implementations. We have thought about:

- Transformer outer loop: support of transformers that have reached an extreme tap after the first Newton-Raphson iteration.
- Shunt outerloop: support of shunts that have reached an extreme section after the first Newton-Raphson iteration.
- Support of all type of contingency present in the security analysis API of PowSyBl Core.
- Improving performances of the AC security and sensitivity analysis implementations.  


For more details, to report bugs or if you need more features, visit our [github](https://github.com/powsybl/powsybl-open-loadflow/issues) and do not hesitate to write new issues.
