/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfBranch implements LfBranch {

    private int num = -1;

    private final LfBus bus1;

    private final LfBus bus2;

    private final PiModel piModel;

    protected final PhaseControl phaseControl;

    protected final VoltageControl voltageControl;

    protected LfBranch controllerBranch;

    protected AbstractLfBranch(LfBus bus1, LfBus bus2, PiModel piModel, PhaseControl phaseControl, VoltageControl voltageControl) {
        this.bus1 = bus1;
        this.bus2 = bus2;
        this.piModel = Objects.requireNonNull(piModel);
        this.phaseControl = phaseControl;
        this.voltageControl = voltageControl;

        if (phaseControl != null && voltageControl != null) {
            throw new PowsyblException("Only one regulating control enabled is allowed");
        }
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
    public Optional<LfBranch> getControllerBranch() {
        return Optional.ofNullable(controllerBranch);
    }

    @Override
    public void setControllerBranch(LfBranch controllerBranch) {
        this.controllerBranch = Objects.requireNonNull(controllerBranch, "Controller branch cannot be null");
    }

    @Override
    public Optional<PhaseControl> getPhaseControl() {
        return Optional.ofNullable(phaseControl);
    }

    @Override
    public Optional<VoltageControl> getVoltageControl() {
        return Optional.ofNullable(voltageControl);
    }
}
