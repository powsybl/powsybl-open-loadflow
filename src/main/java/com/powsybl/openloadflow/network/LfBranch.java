/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfBranch {

    String getId();

    int getNum();

    void setNum(int num);

    LfBus getBus1();

    LfBus getBus2();

    void setP1(Evaluable p1);

    double getP1();

    void setP2(Evaluable p2);

    double getP2();

    void setQ1(Evaluable q1);

    void setQ2(Evaluable q2);

    PiModel getPiModel();

    double getI1();

    double getI2();

    double getPermanentLimit1();

    double getPermanentLimit2();

    boolean hasPhaseControlCapability();

    DiscretePhaseControl getDiscretePhaseControl();

    void updateState(boolean phaseShifterRegulationOn, boolean isTransformerVoltageControlOn);

    boolean isPhaseController();

    boolean isPhaseControlled(DiscretePhaseControl.ControlledSide controlledSide);

    boolean isPhaseControlled();

    void setDiscretePhaseControl(DiscretePhaseControl discretePhaseControl);

    DiscreteVoltageControl getDiscreteVoltageControl();

    boolean isVoltageController();

    void setDiscreteVoltageControl(DiscreteVoltageControl discreteVoltageControl);
}
