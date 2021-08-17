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
public interface LfGenerator {

    String getId();

    LfBus getBus();

    void setBus(LfBus bus);

    boolean hasVoltageControl();

    double getTargetV();

    OptionalDouble getRemoteControlReactiveKey();

    double getTargetQ();

    double getTargetP();

    void setTargetP(double targetP);

    double getMinP();

    double getMaxP();

    double getMinQ();

    double getMaxQ();

    double getMaxRangeQ();

    boolean isParticipating();

    double getDroop();

    double getCalculatedQ();

    void setCalculatedQ(double calculatedQ);

    void updateState();

    LfBus getControlledBus(LfNetwork lfNetwork);

    double getSlope();

    void setSlope(double slope);

    default boolean isStandByAutomaton() {
        return false;
    }

    default void setStandByAutomaton(boolean standByAutomaton) {
        // nothing to do
    }

    default double getLowTargetV() {
        return Double.NaN;
    }

    default double getHighTargetV() {
        return Double.NaN;
    }

    default double getLowVoltageThreshold() {
        return Double.NaN;
    }

    default double getHighVoltageThreshold() {
        return Double.NaN;
    }
}
