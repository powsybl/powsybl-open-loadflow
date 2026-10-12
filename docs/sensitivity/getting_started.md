# Getting started

OpenLoadFlow sensitivity calculation engine implements PowSyBl core sensitivity API. 
Here is an example of Java code calculating generator active power to branch flow active power sensitivity for all lines 
of the IEEE 14 network:

```java
    Network network = IeeeCdfNetworkFactory.create14();
    List<SensitivityFactor> factors = new ArrayList<>();
    for (Generator g : network.getGenerators()) {
        for (Line l : network.getLines()) {
            factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, l.getId(),
                                              SensitivityVariableType.INJECTION_ACTIVE_POWER, g.getId(), 
                                              false, ContingencyContext.all()));
        }
    }
    List<Contingency> contingencies = network.getLineStream().map(l -> Contingency.line(l.getId())).collect(Collectors.toList());
    SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters();
    SensitivityAnalysisResult result = SensitivityAnalysis.run(network, factors, contingencies, parameters);
```

As we can see in this example for each sensitivity factor that we want to compute we need to specify the variable type
and the function type. Not all combinations of variable and function types are allowed / implemented in OpenLoadFlow. 
Some are supported only in DC mode, some others only in AC.
Here is a table to summarize supported use cases:

<div style="font-size: 0.6em; width: 100%; overflow-x: auto;">

| Function \ Variable     | INJECTION_ACTIVE_POWER                   | INJECTION_REACTIVE_POWER            | TRANSFORMER_PHASE                        | BUS_TARGET_VOLTAGE                  | HVDC_LINE_ACTIVE_POWER*                  | TRANSFORMER_PHASE_1                      | TRANSFORMER_PHASE_2                      | TRANSFORMER_PHASE_3                      | SHUNT_COMPENSATOR_SUSCEPTANCE       | BRANCH_RESISTANCE | BRANCH_REACTANCE | BRANCH_ADMITTANCE | SVC_PILOT_POINT_TARGET_VOLTAGE |
|-------------------------|------------------------------------------|-------------------------------------|------------------------------------------|-------------------------------------|------------------------------------------|------------------------------------------|------------------------------------------|------------------------------------------|-------------------------------------|-------------------|------------------|-------------------|--------------------------------|
| BRANCH_ACTIVE_POWER_1   | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> |
| BRANCH_CURRENT_1        | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> |
| BRANCH_REACTIVE_POWER_1 | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> |
| BRANCH_ACTIVE_POWER_2   | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> |
| BRANCH_CURRENT_2        | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> |
| BRANCH_REACTIVE_POWER_2 | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> |
| BRANCH_ACTIVE_POWER_3   | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> |
| BRANCH_CURRENT_3        | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> |
| BRANCH_REACTIVE_POWER_3 | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> |
| BUS_VOLTAGE             | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> | <span style="color:green">AC</span> |
| BUS_REACTIVE_POWER      | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">AC</span> | <span style="color:red">AC</span> | <span style="color:red">AC</span> | <span style="color:green">AC</span> |

</div>

*HVDC_LINE_ACTIVE_POWER is used to compute the sensitivity according to the active power set point of the HVDC. That is why it is not supported for HVDCs that are in AC emulation mode: an exception is thrown if the user asks a sensitivity according to an HVDC link with AC emulation (and if hvdcAcEmulation parameter is enabled).

## Input function types

### Branch sensitivity functions : 
BRANCH_ACTIVE_POWER_#, BRANCH_REACTIVE_POWER_# or BRANCH_CURRENT_# are associated to branch objects (lines, transformers, boundary lines, tie lines). The # index corresponding to the side of the studied branch. 
- If it is a line, a tie line, or a two windings transformer : The side is 1 or 2.
- If it is a three windings transformer : The side is 1, 2 or 3 corresponding to the studied leg of the transformer.
- If it is a boundary line : The side is 1 (network side) or 2 (boundary side). Note that if the boundary line is paired, only side 1 (network side) can be specified, and the sensitivity function is computed at the corresponding tie line side.

### Bus sensitivity functions :
BUS_VOLTAGE or BUS_REACTIVE_POWER are associated to the bus of the given network element.

## Input variable types

### SHUNT_COMPENSATOR_SUSCEPTANCE
`SHUNT_COMPENSATOR_SUSCEPTANCE` computes the sensitivity of a function with respect to the susceptance B (in S) of a shunt compensator.
It is associated to a `ShuntCompensator` network element and is only supported in AC mode.

### BRANCH_RESISTANCE, BRANCH_REACTANCE and BRANCH_ADMITTANCE
These variables compute the sensitivity of a function with respect to a series parameter of a branch:
- `BRANCH_RESISTANCE`: the series resistance R (in Ω);
- `BRANCH_REACTANCE`: the series reactance X (in Ω);
- `BRANCH_ADMITTANCE`: the series admittance modulus y = 1 / √(R² + X²) (in S), perturbed at constant ksi = atan2(R, X) (i.e. R and X scaled together).

They are associated to a branch network element (a line or a two windings transformer) and are only supported in AC mode.
The monitored function can be on the same branch (self sensitivity) or on a different one (cross sensitivity).

### SVC_PILOT_POINT_TARGET_VOLTAGE
`SVC_PILOT_POINT_TARGET_VOLTAGE` computes the closed-loop sensitivity of a function with respect to the pilot point
target voltage (in kV) of a secondary voltage control (SVC) zone. The variable is identified by the SVC control zone
name. The computed sensitivity is closed-loop: it accounts for the SVC re-coordination across all zones (controllers
adjust their voltage setpoints to keep the pilot voltages on target and to equalize their reactive levels), exactly as
done by the secondary voltage control outer loop. It is only supported in AC mode and requires secondary voltage
control to be enabled (`OpenLoadFlowParameters.setSecondaryVoltageControl(true)`).


