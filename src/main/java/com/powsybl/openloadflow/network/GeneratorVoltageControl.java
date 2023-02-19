/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class GeneratorVoltageControl extends VoltageControl {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratorVoltageControl.class);

    private final Set<LfBus> controllerBuses;

    public GeneratorVoltageControl(LfBus controlled, double targetValue) {
        super(targetValue, controlled);
        this.controllerBuses = new LinkedHashSet<>();
    }

    @Override
    public void setTargetValue(double targetValue) {
        if (targetValue != this.targetValue) {
            this.targetValue = targetValue;
            controlledBus.getNetwork().getListeners().forEach(l -> l.onGeneratorVoltageControlTargetChange(this, targetValue));
        }
    }

    public Set<LfBus> getControllerBuses() {
        return controllerBuses;
    }

    public void addControllerBus(LfBus controllerBus) {
        Objects.requireNonNull(controllerBus);
        controllerBuses.add(controllerBus);
        controllerBus.setGeneratorVoltageControl(this);
    }

    /**
     * Check if the voltage control is ONLY local
     * @return true if the voltage control is ONLY local, false otherwise
     */
    public boolean isLocalControl() {
        return controllerBuses.size() == 1 && controllerBuses.contains(controlledBus);
    }

    /**
     * Check if the voltage control is shared
     * @return true if the voltage control is shared, false otherwise
     */
    public boolean isSharedControl() {
        return controllerBuses.stream().flatMap(lfBus -> lfBus.getGenerators().stream())
                .filter(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE).count() > 1;
    }

    public void updateReactiveKeys() {
        List<LfBus> controllerBusList = new ArrayList<>(this.controllerBuses);

        double[] reactiveKeys = createReactiveKeys(controllerBusList);

        // no reactive dispatch on PQ buses, so we set the key to 0
        for (int i = 0; i < controllerBusList.size(); i++) {
            LfBus controllerBus = controllerBusList.get(i);
            if (controllerBus.isDisabled() || !controllerBus.isGeneratorVoltageControlEnabled()) {
                reactiveKeys[i] = 0d;
            }
        }

        // update bus reactive keys
        double reactiveKeysSum = Arrays.stream(reactiveKeys).sum();
        for (int i = 0; i < controllerBusList.size(); i++) {
            LfBus controllerBus = controllerBusList.get(i);
            controllerBus.setRemoteVoltageControlReactivePercent(reactiveKeysSum == 0 ? 0 : reactiveKeys[i] / reactiveKeysSum);
        }
    }

    private static double[] createUniformReactiveKeys(List<LfBus> controllerBuses) {
        double[] qKeys = new double[controllerBuses.size()];
        for (int i = 0; i < controllerBuses.size(); i++) {
            LfBus controllerBus = controllerBuses.get(i);
            qKeys[i] = controllerBus.getGenerators().stream()
                    .filter(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE).count();
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
                if (Double.isNaN(qKey) || qKey == 0) {
                    if (qKey == 0) {
                        LOGGER.error("Generator '{}' remote control reactive key value is zero", generator.getId());
                    }
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
