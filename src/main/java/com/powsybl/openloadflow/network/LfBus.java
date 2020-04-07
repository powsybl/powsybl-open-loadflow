/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfBus {

    String getId();

    int getNum();

    void setNum(int num);

    boolean isFictitious();

    boolean isSlack();

    void setSlack(boolean slack);

    boolean hasVoltageControlCapability();

    boolean hasVoltageControl();

    void setVoltageControl(boolean voltageControl);

    Optional<LfBus> getControlledBus();

    List<LfBus> getControllerBuses();

    double getTargetP();

    double getTargetQ();

    double getLoadTargetP();

    double getLoadTargetQ();

    double getGenerationTargetP();

    double getGenerationTargetQ();

    void setGenerationTargetQ(double generationTargetQ);

    double getTargetV();

    double getMinQ();

    double getMaxQ();

    double getV();

    void setV(double v);

    double getAngle();

    void setAngle(double angle);

    double getCalculatedQ();

    void setCalculatedQ(double calculatedQ);

    /**
     * Get nominal voltage in Kv.
     * @return nominal voltage in Kv
     */
    double getNominalV();

    List<LfGenerator> getGenerators();

    List<LfShunt> getShunts();

    List<LfBranch> getBranches();

    void addBranch(LfBranch branch);

    void updateState(boolean reactiveLimits);
}
