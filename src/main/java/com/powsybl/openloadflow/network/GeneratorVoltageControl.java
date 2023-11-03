/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Arrays;
import java.util.List;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
public class GeneratorVoltageControl extends VoltageControl<LfBus> {

    private static final int PRIORITY = 0;

    public GeneratorVoltageControl(LfBus controlledBus, double targetValue) {
        super(targetValue, Type.GENERATOR, PRIORITY, controlledBus);
    }

    @Override
    public boolean isControllerEnabled(LfBus controllerElement) {
        return controllerElement.isGeneratorVoltageControlEnabled();
    }

    @Override
    public void setTargetValue(double targetValue) {
        if (targetValue != this.targetValue) {
            this.targetValue = targetValue;
            controlledBus.getNetwork().getListeners().forEach(l -> l.onGeneratorVoltageControlTargetChange(this, targetValue));
        }
    }

    @Override
    public void addControllerElement(LfBus controllerBus) {
        super.addControllerElement(controllerBus);
        controllerBus.setGeneratorVoltageControl(this);
    }

    /**
     * Check if the voltage control is ONLY local
     * @return true if the voltage control is ONLY local, false otherwise
     */
    public boolean isLocalControl() {
        return isLocalControl(getMergedControllerElements());
    }

    private boolean isLocalControl(List<LfBus> controllerElements) {
        return controllerElements.size() == 1 && controllerElements.contains(controlledBus);
    }

    /**
     * Check if the voltage control is shared
     * @return true if the voltage control is shared, false otherwise
     */
    public boolean isSharedControl() {
        return controllerElements.stream().flatMap(lfBus -> lfBus.getGenerators().stream())
                .filter(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE).count() > 1;
    }

    public void updateReactiveKeys() {
        updateReactiveKeys(getMergedControllerElements());
    }

    public static void updateReactiveKeys(List<LfBus> controllerBuses) {
        double[] reactiveKeys = createReactiveKeys(controllerBuses, LfGenerator.GeneratorControlType.VOLTAGE);

        // no reactive dispatch on PQ buses, so we set the key to 0
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            if (controllerBus.isDisabled() || !controllerBus.isGeneratorVoltageControlEnabled()) {
                reactiveKeys[i] = 0d;
            }
        }

        // update bus reactive keys for remote voltage control
        double reactiveKeysSum = Arrays.stream(reactiveKeys).sum();
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            controllerBus.setRemoteControlReactivePercent(reactiveKeysSum == 0 ? 0 : reactiveKeys[i] / reactiveKeysSum);
        }
    }
}
