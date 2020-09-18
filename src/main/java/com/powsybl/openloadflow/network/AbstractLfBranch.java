/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.ArrayList;
import java.util.List;
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

    protected Optional<LfBranch> controllerBranch = Optional.empty();

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
    public Optional<LfBranch> getControllerBranch() {
        return controllerBranch;
    }

    @Override
    public void setControllerBranch(LfBranch controllerBranch) {
        Objects.requireNonNull(controllerBranch);
        this.controllerBranch = Optional.of(controllerBranch);
    }
}
