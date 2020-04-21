/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Collections;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractFictitiousLfBus extends AbstractLfBus {

    protected AbstractFictitiousLfBus() {
        super(Double.NaN, Double.NaN);
    }

    @Override
    public boolean isFictitious() {
        return true;
    }

    @Override
    public boolean hasVoltageControlCapability() {
        return false;
    }

    @Override
    public boolean hasVoltageControl() {
        return false;
    }

    @Override
    public void setVoltageControl(boolean voltageControl) {
        throw new IllegalStateException("Cannot change fictitious bus voltage control status");
    }

    @Override
    public int getVoltageControlSwitchOffCount() {
        return 0;
    }

    @Override
    public List<LfBus> getControllerBuses() {
        return Collections.emptyList();
    }

    @Override
    public double getLoadTargetP() {
        return 0;
    }

    @Override
    public void setLoadTargetP(double loadTargetQ) {
        throw new IllegalStateException("Cannot change fictitious bus load");
    }

    @Override
    public int getLoadCount() {
        return 0;
    }

    @Override
    public double getLoadTargetQ() {
        return 0;
    }

    @Override
    public double getGenerationTargetP() {
        return 0;
    }

    @Override
    public double getGenerationTargetQ() {
        return 0;
    }

    @Override
    public void setGenerationTargetQ(double generationTargetQ) {
        throw new IllegalStateException("Cannot change fictitious bus generation");
    }

    @Override
    public double getTargetV() {
        return Double.NaN;
    }

    @Override
    public double getMinQ() {
        return -Double.MAX_VALUE;
    }

    @Override
    public double getMaxQ() {
        return Double.MAX_VALUE;
    }

    @Override
    public List<LfShunt> getShunts() {
        return Collections.emptyList();
    }

    @Override
    public List<LfGenerator> getGenerators() {
        return Collections.emptyList();
    }

    @Override
    public void updateState(boolean reactiveLimits) {
        // nothing to update
    }
}
