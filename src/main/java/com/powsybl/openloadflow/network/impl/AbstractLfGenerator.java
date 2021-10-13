/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfGenerator implements LfGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLfGenerator.class);

    private static final double POWER_EPSILON_SI = 1e-4;

    protected double targetP;

    protected LfBus bus;

    protected double calculatedQ = Double.NaN;

    private double targetV = Double.NaN;

    protected GeneratorControlType generatorControlType = GeneratorControlType.OFF;

    protected String controlledBusId;

    protected String controlledBranchId;

    protected ReactivePowerControl.ControlledSide controlledBranchSide;

    protected double remoteTargetQ = Double.NaN;

    private Object userObject;

    protected AbstractLfGenerator(double targetP) {
        this.targetP = targetP;
    }

    public LfBus getBus() {
        return bus;
    }

    public void setBus(LfBus bus) {
        this.bus = bus;
    }

    @Override
    public double getTargetP() {
        return targetP / PerUnit.SB;
    }

    @Override
    public void setTargetP(double targetP) {
        double newTargetP = targetP * PerUnit.SB;
        if (newTargetP != this.targetP) {
            double oldTargetP = this.targetP;
            this.targetP = newTargetP;
            for (LfNetworkListener listener : bus.getNetwork().getListeners()) {
                listener.onGenerationActivePowerTargetChange(this, oldTargetP, newTargetP);
            }
        }
    }

    @Override
    public double getTargetV() {
        return targetV;
    }

    @Override
    public boolean hasVoltageControl() {
        return generatorControlType == GeneratorControlType.VOLTAGE;
    }

    @Override
    public boolean hasReactivePowerControl() {
        return generatorControlType == GeneratorControlType.REACTIVE_POWER;
    }

    @Override
    public OptionalDouble getRemoteControlReactiveKey() {
        return OptionalDouble.empty();
    }

    protected abstract Optional<ReactiveLimits> getReactiveLimits();

    @Override
    public double getMinQ() {
        return getReactiveLimits()
                .map(limits -> limits.getMinQ(targetP) / PerUnit.SB)
                .orElse(-Double.MAX_VALUE);
    }

    @Override
    public double getMaxQ() {
        return getReactiveLimits()
                .map(limits -> limits.getMaxQ(targetP) / PerUnit.SB)
                .orElse(Double.MAX_VALUE);
    }

    @Override
    public double getMaxRangeQ() {
        double maxRangeQ = Double.NaN;
        ReactiveLimits reactiveLimits = getReactiveLimits().orElse(null);
        if (reactiveLimits != null) {
            switch (reactiveLimits.getKind()) {
                case CURVE:
                    ReactiveCapabilityCurve reactiveCapabilityCurve = (ReactiveCapabilityCurve) reactiveLimits;
                    for (ReactiveCapabilityCurve.Point point : reactiveCapabilityCurve.getPoints()) {
                        if (Double.isNaN(maxRangeQ)) {
                            maxRangeQ = point.getMaxQ() - point.getMinQ();
                        } else {
                            maxRangeQ = Math.max(maxRangeQ, point.getMaxQ() - point.getMinQ());
                        }
                    }
                    break;

                case MIN_MAX:
                    MinMaxReactiveLimits minMaxReactiveLimits = (MinMaxReactiveLimits) reactiveLimits;
                    maxRangeQ = minMaxReactiveLimits.getMaxQ() - minMaxReactiveLimits.getMinQ();
                    break;

                default:
                    throw new IllegalStateException("Unknown reactive limits kind: " + reactiveLimits.getKind());
            }
            return maxRangeQ / PerUnit.SB;
        } else {
            return Double.MAX_VALUE;
        }
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
    public LfBus getControlledBus(LfNetwork lfNetwork) {
        return lfNetwork.getBusById(controlledBusId);
    }

    protected void setVoltageControl(double targetV, Terminal terminal, Terminal regulatingTerminal, boolean breakers, LfNetworkLoadingReport report) {
        if (!checkVoltageControlConsistency(report)) {
            return;
        }
        Bus controlledBus = breakers ? regulatingTerminal.getBusBreakerView().getBus() : regulatingTerminal.getBusView().getBus();
        if (controlledBus == null) {
            LOGGER.warn("Regulating terminal of LfGenerator {} is out of voltage: voltage control discarded", getId());
            return;
        }
        boolean inSameSynchronousComponent = breakers ? regulatingTerminal.getBusBreakerView().getBus().getSynchronousComponent().equals(terminal.getBusBreakerView().getBus().getSynchronousComponent())
                : regulatingTerminal.getBusView().getBus().getSynchronousComponent().equals(terminal.getBusView().getBus().getSynchronousComponent());
        if (!inSameSynchronousComponent) {
            LOGGER.warn("Regulating terminal of LfGenerator {} is not in the same synchronous component: voltage control discarded", getId());
            return;
        }
        this.controlledBusId = controlledBus.getId();
        setTargetV(targetV / regulatingTerminal.getVoltageLevel().getNominalV());
        this.generatorControlType = GeneratorControlType.VOLTAGE;
    }

    protected boolean checkVoltageControlConsistency(LfNetworkLoadingReport report) {
        boolean consistency = true;
        double maxRangeQ = getMaxRangeQ();
        if (maxRangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB) {
            LOGGER.trace("Discard generator '{}' from voltage control because max reactive range ({}) is too small", getId(), maxRangeQ);
            report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall++;
            consistency = false;
        }
        if (Math.abs(getTargetP()) < POWER_EPSILON_SI && getMinP() > POWER_EPSILON_SI) {
            LOGGER.trace("Discard generator '{}' from voltage control because not started (targetP={} MW, minP={} MW)", getId(), getTargetP(), getMinP());
            report.generatorsDiscardedFromVoltageControlBecauseNotStarted++;
            consistency = false;
        }
        return consistency;
    }

    protected void setTargetV(double targetV) {
        double newTargetV = targetV;
        // check that targetV has a plausible value (wrong nominal voltage issue)
        if (targetV < PlausibleValues.MIN_TARGET_VOLTAGE_PU) {
            newTargetV = PlausibleValues.MIN_TARGET_VOLTAGE_PU;
            LOGGER.warn("Generator '{}' has an inconsistent target voltage: {} pu. The target voltage is rescaled to {}",
                getId(), targetV, PlausibleValues.MIN_TARGET_VOLTAGE_PU);
        } else if (targetV > PlausibleValues.MAX_TARGET_VOLTAGE_PU) {
            newTargetV = PlausibleValues.MAX_TARGET_VOLTAGE_PU;
            LOGGER.warn("Generator '{}' has an inconsistent target voltage: {} pu. The target voltage is rescaled to {}",
                getId(), targetV, PlausibleValues.MAX_TARGET_VOLTAGE_PU);
        }
        this.targetV = newTargetV;
    }

    protected void setReactivePowerControl(Terminal regulatingTerminal, double targetQ) {
        Connectable<?> connectable = regulatingTerminal.getConnectable();
        if (connectable instanceof Line) {
            Line l = (Line) connectable;
            this.controlledBranchSide = l.getTerminal(Branch.Side.ONE) == regulatingTerminal ?
                    ReactivePowerControl.ControlledSide.ONE : ReactivePowerControl.ControlledSide.TWO;
            this.controlledBranchId = l.getId();
        } else if (connectable instanceof TwoWindingsTransformer) {
            TwoWindingsTransformer l = (TwoWindingsTransformer) connectable;
            this.controlledBranchSide = l.getTerminal(Branch.Side.ONE) == regulatingTerminal ?
                    ReactivePowerControl.ControlledSide.ONE : ReactivePowerControl.ControlledSide.TWO;
            this.controlledBranchId = l.getId();
        } else {
            LOGGER.error("Generator '{}' is controlled by an instance of {}: not supported",
                    getId(), connectable.getClass());
            return;
        }
        this.generatorControlType = GeneratorControlType.REACTIVE_POWER;
        this.remoteTargetQ = targetQ / PerUnit.SB;
    }

    @Override
    public LfBranch getControlledBranch(LfNetwork lfNetwork) {
        return lfNetwork.getBranchById(controlledBranchId);
    }

    @Override
    public ReactivePowerControl.ControlledSide getControlledBranchSide() {
        return controlledBranchSide;
    }

    @Override
    public double getRemoteTargetQ() {
        return remoteTargetQ;
    }

    protected enum GeneratorControlType {
        OFF, REACTIVE_POWER, VOLTAGE
    }

    @Override
    public Object getUserObject() {
        return userObject;
    }

    @Override
    public void setUserObject(Object userObject) {
        this.userObject = userObject;
    }
}
