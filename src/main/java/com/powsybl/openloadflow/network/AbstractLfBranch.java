/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.openloadflow.network.impl.Transformers;
import com.powsybl.openloadflow.util.Evaluable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfBranch implements LfBranch {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLfBranch.class);

    private int num = -1;

    private final LfBus bus1;

    private final LfBus bus2;

    private final PiModel piModel;

    protected DiscretePhaseControl phaseControl;

    protected DiscreteVoltageControl discreteVoltageControl;

    protected AbstractLfBranch(LfBus bus1, LfBus bus2, PiModel piModel) {
        this.bus1 = bus1;
        this.bus2 = bus2;
        this.piModel = Objects.requireNonNull(piModel);
    }

    @Override
    public int getNum() {
        return num;
    }

    @Override
    public void setNum(int num) {
        this.num = num;
    }

    @Override
    public LfBus getBus1() {
        return bus1;
    }

    @Override
    public LfBus getBus2() {
        return bus2;
    }

    @Override
    public PiModel getPiModel() {
        return piModel;
    }

    @Override
    public DiscretePhaseControl getDiscretePhaseControl() {
        return phaseControl;
    }

    @Override
    public void setDiscretePhaseControl(DiscretePhaseControl discretePhaseControl) {
        this.phaseControl = discretePhaseControl;
    }

    @Override
    public boolean isPhaseController() {
        return phaseControl != null && phaseControl.getController() == this;
    }

    @Override
    public boolean isPhaseControlled() {
        return phaseControl != null && phaseControl.getControlled() == this;
    }

    @Override
    public boolean isPhaseControlled(DiscretePhaseControl.ControlledSide controlledSide) {
        return isPhaseControlled() && phaseControl.getControlledSide() == controlledSide;
    }

    protected void checkTargetDeadband(Evaluable p) {
        // NOTE: calculation is done in per unit
        double distance = Math.abs(p.eval() - phaseControl.getTargetValue());
        if (distance > phaseControl.getTargetDeadband() / 2) {
            LOGGER.warn("The active power on side {} of branch {} ({} MW) is out of the target value ({} MW) +/- deadband/2 ({} MW)",
                phaseControl.getControlledSide(), getId(), p.eval(),
                phaseControl.getTargetValue() * PerUnit.SB, phaseControl.getTargetDeadband() / 2 * PerUnit.SB);
        }
    }

    protected void updateTapPosition(PhaseTapChanger ptc) {
        int tapPosition = Transformers.findTapPosition(ptc, Math.toDegrees(getPiModel().getA1()));
        ptc.setTapPosition(tapPosition);
    }

    @Override
    public DiscreteVoltageControl getDiscreteVoltageControl() {
        return discreteVoltageControl;
    }

    @Override
    public boolean isVoltageController() {
        return discreteVoltageControl != null;
    }

    @Override
    public void setDiscreteVoltageControl(DiscreteVoltageControl discreteVoltageControl) {
        this.discreteVoltageControl = discreteVoltageControl;
    }
}
