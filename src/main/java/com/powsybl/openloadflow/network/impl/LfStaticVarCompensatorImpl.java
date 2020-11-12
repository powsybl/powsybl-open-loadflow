/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.MinMaxReactiveLimits;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.ReactiveLimitsKind;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.openloadflow.network.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfStaticVarCompensatorImpl extends AbstractLfGenerator {

    private final StaticVarCompensator svc;

    private final ReactiveLimits reactiveLimits;

    private LfStaticVarCompensatorImpl(StaticVarCompensator svc) {
        super(0);
        this.svc = svc;
        double nominalV = svc.getTerminal().getVoltageLevel().getNominalV();
        // min and  max reactive limit are calculated at nominal voltage
        double minQ = svc.getBmin() * nominalV * nominalV;
        double maxQ = svc.getBmax() * nominalV * nominalV;
        reactiveLimits = new MinMaxReactiveLimits() {

            @Override
            public double getMinQ() {
                return minQ;
            }

            @Override
            public double getMaxQ() {
                return maxQ;
            }

            @Override
            public ReactiveLimitsKind getKind() {
                return ReactiveLimitsKind.MIN_MAX;
            }

            @Override
            public double getMinQ(double p) {
                return getMinQ();
            }

            @Override
            public double getMaxQ(double p) {
                return getMaxQ();
            }
        };
        if (hasVoltageControl()) {
            // compute targetV in per-unit system
            targetV = svc.getVoltageSetpoint() / svc.getRegulatingTerminal().getVoltageLevel().getNominalV();
        }
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
    public double getTargetV() {
        return targetV;
    }

    @Override
    public double getTargetQ() {
        return -svc.getReactivePowerSetPoint() / PerUnit.SB;
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
    public boolean isParticipating() {
        return false;
    }

    @Override
    public double getParticipationFactor() {
        return 0;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(reactiveLimits);
    }

    @Override
    public void updateState() {
        svc.getTerminal()
                .setP(0)
                .setQ(Double.isNaN(calculatedQ) ? svc.getReactivePowerSetPoint() : -calculatedQ);
    }
}
