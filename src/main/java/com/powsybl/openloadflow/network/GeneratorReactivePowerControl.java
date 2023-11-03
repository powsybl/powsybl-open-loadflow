package com.powsybl.openloadflow.network;

import java.util.Arrays;
import java.util.List;

public class GeneratorReactivePowerControl extends ReactivePowerControl {

    public GeneratorReactivePowerControl(LfBranch controlledBranch, ControlledSide controlledSide, double targetValue) {
        super(targetValue, Type.GENERATOR, controlledBranch, controlledSide);
    }

    @Override
    public void addControllerBus(LfBus controllerBus) {
        super.addControllerBus(controllerBus);
        controllerBus.setGeneratorReactivePowerControl(this);
        controllerBus.setReactivePowerControlEnabled(true);
    }

    public void updateReactiveKeys() {
        updateReactiveKeys(controllerBuses);
    }

    public static void updateReactiveKeys(List<LfBus> controllerBuses) {
        double[] reactiveKeys = createReactiveKeys(controllerBuses, LfGenerator.GeneratorControlType.REMOTE_REACTIVE_POWER);

        // key is 0 only on disabled controllers
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            if (controllerBus.isDisabled()) {
                reactiveKeys[i] = 0d;
            }
        }

        // update bus reactive keys for remote reactive power control
        double reactiveKeysSum = Arrays.stream(reactiveKeys).sum();
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            controllerBus.setRemoteControlReactivePercent(reactiveKeysSum == 0 ? 0 : reactiveKeys[i] / reactiveKeysSum);
        }
    }
}
