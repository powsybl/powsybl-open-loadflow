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
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControl;
import com.powsybl.openloadflow.network.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfStaticVarCompensatorImpl extends AbstractLfGenerator {

    private final StaticVarCompensator svc;

    private final ReactiveLimits reactiveLimits;

    private final double nominalV;

    private final VoltagePerReactivePowerControl voltagePerReactivePowerControl;

    private LfStaticVarCompensatorImpl(StaticVarCompensator svc, AbstractLfBus bus) {
        super(0);
        this.svc = svc;
        this.nominalV = svc.getTerminal().getVoltageLevel().getNominalV();
        this.voltagePerReactivePowerControl = svc.getExtension(VoltagePerReactivePowerControl.class);
        reactiveLimits = new MinMaxReactiveLimits() {

            @Override
            public double getMinQ() {
                double v = bus.getV() * nominalV;
                return svc.getBmin() * v * v;
            }

            @Override
            public double getMaxQ() {
                double v = bus.getV() * nominalV;
                return svc.getBmax() * v * v;
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
    }

    public static LfStaticVarCompensatorImpl create(StaticVarCompensator svc, AbstractLfBus bus) {
        Objects.requireNonNull(svc);
        return new LfStaticVarCompensatorImpl(svc, bus);
    }

    public StaticVarCompensator getSvc() {
        return svc;
    }

    public double getSlope() {
        if (voltagePerReactivePowerControl != null) {
            return voltagePerReactivePowerControl.getSlope() * PerUnit.SB / svc.getTerminal().getVoltageLevel().getNominalV();
        }
        return 0;
    }

    public VoltagePerReactivePowerControl getVoltagePerReactivePowerControl() {
        return voltagePerReactivePowerControl;
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
