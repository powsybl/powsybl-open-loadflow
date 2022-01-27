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

    boolean hasRemoteReactivePowerControl();

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

    default boolean isParticipating() {
        return false;
    }

    default double getDroop() {
        return 0;
    }

    double getCalculatedQ();

    void setCalculatedQ(double calculatedQ);

    void updateState();

    LfBus getControlledBus(LfNetwork lfNetwork);

    default double getSlope() {
        return 0;
    }

    default void setSlope(double slope) {
        // nothing to do
    }

    LfBranch getControlledBranch(LfNetwork lfNetwork);

    ReactivePowerControl.ControlledSide getControlledBranchSide();

    double getRemoteTargetQ();

    Object getUserObject();

    void setUserObject(Object userObject);
}
