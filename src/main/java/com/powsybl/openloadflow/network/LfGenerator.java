/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfGenerator extends PropertyBag {

    enum GeneratorControlType {
        OFF, REMOTE_REACTIVE_POWER, VOLTAGE, MONITORING_VOLTAGE
    }

    enum ReactiveRangeMode {
        MIN, MAX, TARGET_P
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

    ControlledSide getControlledBranchSide();

    double getRemoteTargetQ();
}
