# Getting started

This guide explains how to run a Ward network reduction using PowSyBl Open Load Flow in Java.

## Basic usage

To perform a network reduction, you need:
1. An IIDM `Network` instance.
2. A `WardReductionParameters` object configured with at least the set of internal voltage level IDs defining the study area to retain.

```java
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.reduction.Reducer;
import com.powsybl.openloadflow.reduction.WardReduction;
import com.powsybl.openloadflow.reduction.WardReductionParameters;

import java.util.Set;

public class ReductionExample {

    public static void main(String[] args) {
        // 1. Load or create your network
        Network network = ...;

        // 2. Run an initial load flow calculation before reduction
        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        LoadFlow.find().run(network, loadFlowParameters);

        // 3. Define the internal voltage level IDs to keep
        Set<String> internalVlIds = Set.of("VL1", "VL2");

        // 4. Configure reduction parameters
        WardReductionParameters parameters = new WardReductionParameters()
            .setInternalVoltageLevelIds(internalVlIds);

        // 5. Execute network reduction
        Reducer.ReductionChanges changes = WardReduction.reduce(network, parameters);

        // 6. Inspect changes
        System.out.println("Removed voltage levels: " + changes.removedVoltageLevels());
        System.out.println("Added equivalent lines: " + changes.addedLines());
        System.out.println("Added equivalent shunts: " + changes.addedShuntCompensators());
        System.out.println("Added equivalent generators: " + changes.addedGenerators());

        // 7. Run a load flow calculation on the reduced network
        LoadFlow.find().run(network, loadFlowParameters);
    }
}
```

## Reduction changes inspection

The `WardReduction.reduce(network, parameters)` method returns a `ReductionChanges` record containing:
- `removedVoltageLevels()`: List of `VoltageLevel` instances that were removed from the network (external zone).
- `addedLines()`: List of equivalent `Line` instances added between boundary buses.
- `addedShuntCompensators()`: List of equivalent `ShuntCompensator` instances added at boundary buses.
- `addedGenerators()`: List of equivalent `Generator` instances added at boundary buses.
