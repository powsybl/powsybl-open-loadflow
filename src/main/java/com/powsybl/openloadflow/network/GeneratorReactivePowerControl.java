package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.PerUnit;

import java.util.Arrays;
import java.util.List;

public class GeneratorReactivePowerControl extends ReactivePowerControl<LfBus> {
    private static final int PRIORITY = 0;

    public GeneratorReactivePowerControl(LfBranch controlledBranch, ControlledSide controlledSide, double targetValue) {
        super(targetValue, Type.GENERATOR, PRIORITY, controlledBranch, controlledSide);
    }

    @Override
    public boolean isControllerEnabled(LfBus controllerElement) {
        return true;
//        return controllerElement.isGeneratorReactivePowerControlEnabled(); // TODO: initialize the variable somewhere
    }

    @Override
    public void addControllerElement(LfBus controllerBus) {
        super.addControllerElement(controllerBus);
        controllerBus.setGeneratorReactivePowerControl(this);
    }

    public void updateReactiveKeys() {
        updateReactiveKeys(getMergedControllerElements());
    }

    public static void updateReactiveKeys(List<LfBus> controllerBuses) {
        double[] reactiveKeys = createReactiveKeys(controllerBuses);

        // no reactive dispatch on PQ buses, so we set the key to 0
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            if (controllerBus.isDisabled()) { // TODO: removed other condition to have nonzero keys
                reactiveKeys[i] = 0d;
            }
        }

        // update bus reactive keys
        double reactiveKeysSum = Arrays.stream(reactiveKeys).sum();
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            controllerBus.setRemoteReactivePowerControlReactivePercent(reactiveKeysSum == 0 ? 0 : reactiveKeys[i] / reactiveKeysSum);
        }
    }

    private static double[] createUniformReactiveKeys(List<LfBus> controllerBuses) {
        double[] qKeys = new double[controllerBuses.size()];
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            qKeys[i] = controllerBus.getGenerators().stream()
                    .filter(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.REMOTE_REACTIVE_POWER).count();
        }
        return qKeys;
    }

    private static double[] createReactiveKeysFromMaxReactivePowerRange(List<LfBus> controllerBuses) {
        double[] qKeys = new double[controllerBuses.size()];
        // try to build keys from reactive power range
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            for (LfGenerator generator : controllerBus.getGenerators()) {
                double maxRangeQ = generator.getRangeQ(LfGenerator.ReactiveRangeMode.MAX);
                // if one reactive range is not plausible, we fallback to uniform keys
                if (maxRangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB || maxRangeQ > PlausibleValues.MAX_REACTIVE_RANGE / PerUnit.SB) {
                    return createUniformReactiveKeys(controllerBuses);
                } else {
                    qKeys[i] += maxRangeQ;
                }
            }
        }
        return qKeys;
    }

    private static double[] createReactiveKeys(List<LfBus> controllerBuses) {
        double[] qKeys = new double[controllerBuses.size()];
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            for (LfGenerator generator : controllerBus.getGenerators()) {
                double qKey = generator.getRemoteControlReactiveKey().orElse(Double.NaN);
                if (Double.isNaN(qKey)) {
                    // in case of one missing key, we fallback to keys based on reactive power range
                    return createReactiveKeysFromMaxReactivePowerRange(controllerBuses);
                } else {
                    qKeys[i] += qKey;
                }
            }
        }
        return qKeys;
    }

}
