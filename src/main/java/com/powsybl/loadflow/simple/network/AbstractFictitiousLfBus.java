/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractFictitiousLfBus extends AbstractLfBus {

    protected AbstractFictitiousLfBus(int num) {
        super(num, Double.NaN, Double.NaN);
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
    public double getLoadTargetP() {
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
    public void setGenerationTargetP(double generationTargetP) {
        throw new IllegalStateException("Cannot change fictitious bus generation");
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
    public double getMinP() {
        return Double.NaN;
    }

    @Override
    public double getMaxP() {
        return Double.NaN;
    }

    @Override
    public double getParticipationFactor() {
        return 0;
    }

    @Override
    public double getTargetV() {
        return Double.NaN;
    }

    @Override
    public List<LfShunt> getShunts() {
        return Collections.emptyList();
    }

    @Override
    public Optional<LfReactiveDiagram> getReactiveDiagram() {
        return Optional.empty();
    }

    @Override
    public void updateState() {
        // nothing to update
    }
}
