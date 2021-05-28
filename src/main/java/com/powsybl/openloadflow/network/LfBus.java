/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Load;
import com.powsybl.openloadflow.network.impl.LfLoads;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfBus extends LfElement {

    String getVoltageLevelId();

    boolean isFictitious();

    boolean isSlack();

    void setSlack(boolean slack);

    boolean hasVoltageControllerCapability();

    boolean isVoltageControllerEnabled();

    boolean isVoltageControlled();

    /**
     * Get the number of time, voltage control status has be set from true to false.
     *
     * @return the number of time, voltage control status has be set from true to false
     */
    int getVoltageControlSwitchOffCount();

    void setVoltageControlSwitchOffCount(int voltageControlSwitchOffCount);

    void setVoltageControllerEnabled(boolean voltageControl);

    Optional<VoltageControl> getVoltageControl();

    void setVoltageControl(VoltageControl voltageControl);

    double getTargetP();

    double getTargetQ();

    double getLoadTargetP();

    void setLoadTargetP(double loadTargetP);

    double getInitialLoadTargetP();

    int getLoadCount();

    double getLoadTargetQ();

    void setLoadTargetQ(double loadTargetQ);

    boolean ensurePowerFactorConstantByLoad();

    double getGenerationTargetP();

    double getGenerationTargetQ();

    void setGenerationTargetQ(double generationTargetQ);

    double getMinQ();

    double getMaxQ();

    Evaluable getV();

    void setV(Evaluable v);

    double getAngle();

    void setAngle(double angle);

    double getCalculatedQ();

    void setCalculatedQ(double calculatedQ);

    /**
     * Get nominal voltage in Kv.
     * @return nominal voltage in Kv
     */
    double getNominalV();

    double getLowVoltageLimit();

    double getHighVoltageLimit();

    List<LfGenerator> getGenerators();

    List<LfShunt> getShunts();

    List<Load> getLoads();

    LfLoads getLfLoads();

    List<LfBranch> getBranches();

    void addBranch(LfBranch branch);

    void updateState(boolean reactiveLimits, boolean writeSlackBus, boolean distributedOnConformLoad, boolean loadPowerFactorConstant);

    DiscreteVoltageControl getDiscreteVoltageControl();

    boolean isDiscreteVoltageControlled();

    void setDiscreteVoltageControl(DiscreteVoltageControl discreteVoltageControl);

    boolean isDisabled();

    void setDisabled(boolean disabled);

    void setP(Evaluable p);

    Evaluable getP();

    void setQ(Evaluable q);

    Evaluable getQ();

    boolean isParticipating();
}
