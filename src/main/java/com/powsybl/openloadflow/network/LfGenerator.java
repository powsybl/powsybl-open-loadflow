/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.TwoSides;

import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface LfGenerator extends PropertyBag {

    enum GeneratorControlType {
        OFF, REMOTE_REACTIVE_POWER, VOLTAGE, MONITORING_VOLTAGE
    }

    enum ReactiveRangeMode {
        MIN, MAX, TARGET_P
    }

    /**
     * k is a normalized value of reactive power that ensure that at q min k is -1 and at q max k is + 1
     * q = 1 / 2 * (k * (qmax - qmin) + qmax + qmin)
     */
    static double kToQ(double k, LfGenerator generator) {
        double minQ = generator.getMinQ();
        double maxQ = generator.getMaxQ();
        return 0.5d * (k * (maxQ - minQ) + maxQ + minQ);
    }

    static double qToK(LfGenerator generator, double q) {
        double minQ = generator.getMinQ();
        double maxQ = generator.getMaxQ();
        return (2 * q - maxQ - minQ) / (maxQ - minQ);
    }

    static boolean isTargetVoltageNotPlausible(double targetV, double minPlausibleTargetVoltage, double maxPlausibleTargetVoltage) {
        return targetV < minPlausibleTargetVoltage || targetV > maxPlausibleTargetVoltage;
    }

    String getId();

    String getOriginalId();

    LfBus getBus();

    void setBus(LfBus bus);

    boolean isFictitious();

    boolean hasRemoteReactivePowerControl();

    GeneratorControlType getGeneratorControlType();

    void setGeneratorControlType(GeneratorControlType generatorControlType);

    double getTargetV();

    OptionalDouble getRemoteControlReactiveKey();

    double getTargetQ();

    double getInitialTargetP();

    double getTargetP();

    void setTargetP(double targetP);

    double getMinP();

    double getMaxP();

    double getMinQ();

    double getMaxQ();

    double getRangeQ(ReactiveRangeMode reactiveRangeMode);

    default boolean isParticipating() {
        return false;
    }

    void setParticipating(boolean participating);

    default double getDroop() {
        return 0;
    }

    default double getParticipationFactor() {
        return 0;
    }

    double getCalculatedQ();

    void setCalculatedQ(double calculatedQ);

    void updateState();

    LfBus getControlledBus();

    default double getSlope() {
        return 0;
    }

    default void setSlope(double slope) {
        // nothing to do
    }

    LfBranch getControlledBranch();

    TwoSides getControlledBranchSide();

    double getRemoteTargetQ();

    default boolean isDisabled() {
        return false;
    }

    void setDisabled(boolean disabled);

    LfAsymGenerator getAsym();

    void setAsym(LfAsymGenerator asym);
}
