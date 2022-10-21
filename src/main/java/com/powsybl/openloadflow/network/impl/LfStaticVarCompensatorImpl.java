/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.StandbyAutomaton;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControl;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.PerUnit;

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

    private Double b0;

    double targetQ;

    private StandByAutomaton standByAutomaton;

    public static class StandByAutomaton {
        // if the static var compensator has an automaton in stand by, this object must be field.

        private double highVoltageThreshold;
        private double lowVoltageThreshold;
        private double highTargetV;
        private double lowTargetV;

        StandByAutomaton(double highVoltageThreshold, double lowVoltageThreshold, double highTargetV, double lowTargetV) {
            this.highVoltageThreshold = highVoltageThreshold;
            this.lowVoltageThreshold = lowVoltageThreshold;
            this.highTargetV = highTargetV;
            this.lowTargetV = lowTargetV;
        }

        public double getLowTargetV() {
            return lowTargetV;
        }

        public double getHighTargetV() {
            return highTargetV;
        }

        public double getLowVoltageThreshold() {
            return lowVoltageThreshold;
        }

        public double getHighVoltageThreshold() {
            return highVoltageThreshold;
        }
    }

    private LfStaticVarCompensatorImpl(StaticVarCompensator svc, LfNetwork network, AbstractLfBus bus, boolean voltagePerReactivePowerControl,
                                       boolean breakers, boolean reactiveLimits, LfNetworkLoadingReport report,
                                       double minPlausibleTargetVoltage, double maxPlausibleTargetVoltage) {
        super(network, 0);
        this.svc = svc;
        this.nominalV = svc.getTerminal().getVoltageLevel().getNominalV();
        this.reactiveLimits = new MinMaxReactiveLimits() {

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

        StandbyAutomaton standbyAutomaton = svc.getExtension(StandbyAutomaton.class);
        if (standbyAutomaton != null) {
            if (standbyAutomaton.getB0() != 0.0) {
                // a static var compensator with an extension stand by automaton includes an offset of B0,
                // whatever it is in stand by or not. FIXME: should be verified.
                this.b0 = standbyAutomaton.getB0();
            }
        }

        if (svc.getRegulationMode() == StaticVarCompensator.RegulationMode.VOLTAGE) {
            setVoltageControl(svc.getVoltageSetpoint(), svc.getTerminal(), svc.getRegulatingTerminal(), breakers,
                    reactiveLimits, report, minPlausibleTargetVoltage, maxPlausibleTargetVoltage);
            if (voltagePerReactivePowerControl && svc.getExtension(VoltagePerReactivePowerControl.class) != null) {
                this.slope = svc.getExtension(VoltagePerReactivePowerControl.class).getSlope() * PerUnit.SB / nominalV;
            }
            if (standbyAutomaton != null && standbyAutomaton.isStandby()) {
                this.standByAutomaton = new StandByAutomaton(standbyAutomaton.getHighVoltageThreshold() / nominalV,
                        standbyAutomaton.getLowVoltageThreshold() / nominalV,
                        standbyAutomaton.getHighVoltageSetpoint() / nominalV,
                        standbyAutomaton.getLowVoltageSetpoint() / nominalV);
                this.generatorControlType = GeneratorControlType.MONITORING_VOLTAGE; // FIXME?
            }
        }
        // FIXME: if slope and b0, not supported.

        targetQ = -svc.getReactivePowerSetPoint();
    }

    public static LfStaticVarCompensatorImpl create(StaticVarCompensator svc, LfNetwork network, AbstractLfBus bus, boolean voltagePerReactivePowerControl,
                                                    boolean breakers, boolean reactiveLimits, LfNetworkLoadingReport report,
                                                    double minPlausibleTargetVoltage, double maxPlausibleTargetVoltage) {
        Objects.requireNonNull(svc);
        return new LfStaticVarCompensatorImpl(svc, network, bus, voltagePerReactivePowerControl, breakers, reactiveLimits,
                report, minPlausibleTargetVoltage, maxPlausibleTargetVoltage);
    }

    @Override
    public String getId() {
        return svc.getId();
    }

    @Override
    public double getTargetQ() {
        return targetQ / PerUnit.SB;
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
        double vSquare = bus.getV() * bus.getV() * nominalV * nominalV;
        svc.getTerminal()
                .setP(0)
                .setQ((Double.isNaN(calculatedQ) ? (Double.isNaN(targetQ) ? 0 : -targetQ) : -calculatedQ) - (b0 != null ? b0 : 0.0) * vSquare);
    }

    @Override
    public double getSlope() {
        return this.slope;
    }

    @Override
    public void setSlope(double slope) {
        this.slope = slope;
    }

    public Optional<Double> getB0() {
        return Optional.ofNullable(b0);
    }

    public Optional<StandByAutomaton> getStandByAutomaton() {
        return Optional.ofNullable(standByAutomaton);
    }
}
