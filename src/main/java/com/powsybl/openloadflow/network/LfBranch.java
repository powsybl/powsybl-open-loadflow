/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfBranch extends LfElement {

    LfBus getBus1();

    LfBus getBus2();

    void setP1(Evaluable p1);

    Evaluable getP1();

    void setP2(Evaluable p2);

    Evaluable getP2();

    Evaluable getQ1();

    void setQ1(Evaluable q1);

    Evaluable getQ2();

    void setQ2(Evaluable q2);

    PiModel getPiModel();

    void setI1(Evaluable i1);

    void setI2(Evaluable i2);

    Evaluable getI1();

    Evaluable getI2();

    List<AbstractLfBranch.LfLimit> getLimits1();

    List<AbstractLfBranch.LfLimit> getLimits2();

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

    void setDiscreteVoltageControlEnabled(boolean enabled);

    boolean isDiscreteVoltageControllerEnabled();
}
