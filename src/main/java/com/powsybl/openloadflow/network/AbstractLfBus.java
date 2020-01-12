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
public abstract class AbstractLfBus implements LfBus {

    private int num = -1;

    private boolean slack = false;

    protected double v;

    protected double angle;

    protected double calculatedQ = Double.NaN;

    protected final List<LfBranch> branches = new ArrayList<>();

    protected AbstractLfBus(double v, double angle) {
        this.v = v;
        this.angle = angle;
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
    public Optional<LfBus> getRemoteControlTargetBus() {
        return Optional.empty();
    }

    @Override
    public boolean isSlack() {
        return slack;
    }

    @Override
    public void setSlack(boolean slack) {
        this.slack = slack;
    }

    @Override
    public double getTargetP() {
        return getGenerationTargetP() - getLoadTargetP();
    }

    @Override
    public double getTargetQ() {
        return getGenerationTargetQ() - getLoadTargetQ();
    }

    @Override
    public double getV() {
        return v;
    }

    @Override
    public void setV(double v) {
        this.v = v;
    }

    @Override
    public double getAngle() {
        return angle;
    }

    @Override
    public void setAngle(double angle) {
        this.angle = angle;
    }

    @Override
    public double getCalculatedQ() {
        return calculatedQ / PerUnit.SB;
    }

    @Override
    public void setCalculatedQ(double calculatedQ) {
        this.calculatedQ = calculatedQ * PerUnit.SB;
    }

    @Override
    public List<LfBranch> getBranches() {
        return branches;
    }

    @Override
    public void addBranch(LfBranch branch) {
        branches.add(Objects.requireNonNull(branch));
    }
}
