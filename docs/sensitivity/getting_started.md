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

| Function \ Variable     | INJECTION_ACTIVE_POWER                   | INJECTION_REACTIVE_POWER            | TRANSFORMER_PHASE                        | BUS_TARGET_VOLTAGE                  | HVDC_LINE_ACTIVE_POWER                   | TRANSFORMER_PHASE_1                      | TRANSFORMER_PHASE_2                      | TRANSFORMER_PHASE_3                      |
|-------------------------|------------------------------------------|-------------------------------------|------------------------------------------|-------------------------------------|------------------------------------------|------------------------------------------|------------------------------------------|------------------------------------------|
| BRANCH_ACTIVE_POWER_1   | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> |
| BRANCH_CURRENT_1        | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      |
| BRANCH_REACTIVE_POWER_1 | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       |
| BRANCH_ACTIVE_POWER_2   | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> |
| BRANCH_CURRENT_2        | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      |
| BRANCH_REACTIVE_POWER_2 | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       |
| BRANCH_ACTIVE_POWER_3   | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:red">N/A</span>  | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> | <span style="color:green">AC + DC</span> |
| BRANCH_CURRENT_3        | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span> | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      | <span style="color:green">AC</span>      |
| BRANCH_REACTIVE_POWER_3 | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       |
| BUS_VOLTAGE             | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       |
| BUS_REACTIVE_POWER      | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:green">AC</span> | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       | <span style="color:red">N/A</span>       |

</div>