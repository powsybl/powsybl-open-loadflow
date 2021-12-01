/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
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

    double nominalV;

    private double slope = 0;

    private LfStaticVarCompensatorImpl(StaticVarCompensator svc, AbstractLfBus bus, boolean voltagePerReactivePowerControl, boolean breakers, LfNetworkLoadingReport report) {
        super(0);
        this.svc = svc;
        this.nominalV = svc.getTerminal().getVoltageLevel().getNominalV();
        reactiveLimits = new MinMaxReactiveLimits() {

            @Override
            public double getMinQ() {
                double v = bus.getV().eval() * nominalV;
                return svc.getBmin() * v * v;
            }

            @Override
            public double getMaxQ() {
                double v = bus.getV().eval() * nominalV;
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

        if (svc.getRegulationMode() == StaticVarCompensator.RegulationMode.VOLTAGE) {
            setVoltageControl(svc.getVoltageSetpoint(), svc.getTerminal(), svc.getRegulatingTerminal(), breakers, report);
            if (voltagePerReactivePowerControl && svc.getExtension(VoltagePerReactivePowerControl.class) != null) {
                this.slope = svc.getExtension(VoltagePerReactivePowerControl.class).getSlope() * PerUnit.SB / nominalV;
            }
        }
    }

    public static LfStaticVarCompensatorImpl create(StaticVarCompensator svc, AbstractLfBus bus, boolean voltagePerReactivePowerControl, boolean breakers, LfNetworkLoadingReport report) {
        Objects.requireNonNull(svc);
        return new LfStaticVarCompensatorImpl(svc, bus, voltagePerReactivePowerControl, breakers, report);
    }

    @Override
    public String getId() {
        return svc.getId();
    }

    @Override
    public double getTargetQ() {
        return -svc.getReactivePowerSetpoint() / PerUnit.SB;
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
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(reactiveLimits);
    }

    @Override
    public void updateState() {
        svc.getTerminal()
                .setP(0)
                .setQ(Double.isNaN(calculatedQ) ? svc.getReactivePowerSetpoint() : -calculatedQ);
    }

    @Override
    public double getSlope() {
        return this.slope;
    }

    @Override
    public void setSlope(double slope) {
        this.slope = slope;
    }
}
