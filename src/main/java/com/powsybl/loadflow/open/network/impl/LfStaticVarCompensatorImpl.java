/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.open.network.impl;

import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.loadflow.open.network.AbstractLfGenerator;
import com.powsybl.loadflow.open.network.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfStaticVarCompensatorImpl extends AbstractLfGenerator {

    private final StaticVarCompensator svc;

    private LfStaticVarCompensatorImpl(StaticVarCompensator svc) {
        super(0);
        this.svc = svc;
    }

    public static LfStaticVarCompensatorImpl create(StaticVarCompensator svc) {
        Objects.requireNonNull(svc);
        return new LfStaticVarCompensatorImpl(svc);
    }

    @Override
    public String getId() {
        return svc.getId();
    }

    @Override
    public boolean hasVoltageControl() {
        return svc.getRegulationMode() == StaticVarCompensator.RegulationMode.VOLTAGE;
    }

    @Override
    public double getTargetQ() {
        return svc.getReactivePowerSetPoint() / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        return -Double.MAX_VALUE;
    }

    @Override
    public double getMaxP() {
        return Double.MAX_VALUE;
    }

    @Override
    public double getParticipationFactor() {
        return 0;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.empty();
    }

    @Override
    public void updateState() {
        svc.getTerminal()
                .setP(0)
                .setQ(Double.isNaN(calculatedQ) ? -svc.getReactivePowerSetPoint() : -calculatedQ);
    }
}
