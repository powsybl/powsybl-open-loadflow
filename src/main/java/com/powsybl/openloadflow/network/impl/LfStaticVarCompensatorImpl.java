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
import com.powsybl.iidm.network.extensions.StandbyAutomaton;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControl;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class LfStaticVarCompensatorImpl extends AbstractLfGenerator implements LfStaticVarCompensator {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfStaticVarCompensatorImpl.class);

    private final Ref<StaticVarCompensator> svcRef;

    private final ReactiveLimits reactiveLimits;

    double nominalV;

    private double slope = 0;

    double targetQ = 0;

    private StandByAutomaton standByAutomaton;

    private double b0 = 0.0;

    private LfShunt standByAutomatonShunt;

    private LfStaticVarCompensatorImpl(StaticVarCompensator svc, LfNetwork network, AbstractLfBus bus, LfNetworkParameters parameters,
                                       LfNetworkLoadingReport report) {
        super(network, 0);
        this.svcRef = Ref.create(svc, parameters.isCacheEnabled());
        this.nominalV = svc.getTerminal().getVoltageLevel().getNominalV();
        this.reactiveLimits = new MinMaxReactiveLimits() {

            @Override
            public double getMinQ() {
                double v = bus.getV() * nominalV;
                return svcRef.get().getBmin() * v * v;
            }

            @Override
            public double getMaxQ() {
                double v = bus.getV() * nominalV;
                return svcRef.get().getBmax() * v * v;
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
            setVoltageControl(svc.getVoltageSetpoint(), svc.getTerminal(), svc.getRegulatingTerminal(), parameters, report);

            // slope model: check if to be applied based on 1/ option and 2/ this SVC extension
            VoltagePerReactivePowerControl voltagePerReactivePowerControl = svc.getExtension(VoltagePerReactivePowerControl.class);
            boolean svcWithVoltagePerReactivePowerControl = parameters.isVoltagePerReactivePowerControl() && voltagePerReactivePowerControl != null;

            // standby automaton: same, check if to be applied based on 1/ option and 2/ this SVC extension
            StandbyAutomaton standbyAutomaton = svc.getExtension(StandbyAutomaton.class);
            boolean svcWithStandbyAutomaton = parameters.isSvcVoltageMonitoring() && standbyAutomaton != null;

            // we can't do both slope model & standby automaton. Keep only standby automaton if both present.
            if (svcWithStandbyAutomaton && svcWithVoltagePerReactivePowerControl) {
                LOGGER.warn("Static var compensator {} has VoltagePerReactivePowerControl" +
                        " and StandbyAutomaton extensions: VoltagePerReactivePowerControl extension ignored", svc.getId());
                svcWithVoltagePerReactivePowerControl = false;
            }

            if (svcWithVoltagePerReactivePowerControl) {
                this.slope = voltagePerReactivePowerControl.getSlope() * PerUnit.SB / nominalV;
            }
            if (svcWithStandbyAutomaton) {
                if (standbyAutomaton.getB0() != 0.0) {
                    // a static var compensator with an extension stand by automaton includes an offset of B0,
                    // whatever it is in stand by or not.
                    b0 = standbyAutomaton.getB0();
                }
                if (standbyAutomaton.isStandby()) {
                    standByAutomaton = new StandByAutomaton(standbyAutomaton.getHighVoltageThreshold() / nominalV,
                                                            standbyAutomaton.getLowVoltageThreshold() / nominalV,
                                                            standbyAutomaton.getHighVoltageSetpoint() / nominalV,
                                                            standbyAutomaton.getLowVoltageSetpoint() / nominalV);
                    generatorControlType = GeneratorControlType.MONITORING_VOLTAGE;
                }
            }
        }
        if (svc.getRegulationMode() == StaticVarCompensator.RegulationMode.REACTIVE_POWER) {
            targetQ = -svc.getReactivePowerSetpoint() / PerUnit.SB;
        }
    }

    public static LfStaticVarCompensatorImpl create(StaticVarCompensator svc, LfNetwork network, AbstractLfBus bus, LfNetworkParameters parameters,
                                                    LfNetworkLoadingReport report) {
        Objects.requireNonNull(svc);
        Objects.requireNonNull(network);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(report);
        return new LfStaticVarCompensatorImpl(svc, network, bus, parameters, report);
    }

    private StaticVarCompensator getSvc() {
        return svcRef.get();
    }

    @Override
    public String getId() {
        return getSvc().getId();
    }

    @Override
    public double getTargetQ() {
        return targetQ;
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
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        double vSquare = bus.getV() * bus.getV() * nominalV * nominalV;
        double q = (Double.isNaN(calculatedQ) ? -targetQ : -calculatedQ) * PerUnit.SB;
        getSvc().getTerminal()
                .setP(0)
                .setQ(q - b0 * vSquare);
    }

    @Override
    public double getSlope() {
        return this.slope;
    }

    @Override
    public void setSlope(double slope) {
        this.slope = slope;
    }

    @Override
    public double getB0() {
        return b0;
    }

    @Override
    public Optional<StandByAutomaton> getStandByAutomaton() {
        return Optional.ofNullable(standByAutomaton);
    }

    @Override
    public Optional<LfShunt> getStandByAutomatonShunt() {
        return Optional.ofNullable(standByAutomatonShunt);
    }

    @Override
    public void setStandByAutomatonShunt(LfShunt standByAutomatonShunt) {
        this.standByAutomatonShunt = standByAutomatonShunt;
    }
}
