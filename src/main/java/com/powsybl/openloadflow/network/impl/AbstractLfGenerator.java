/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfGenerator extends AbstractPropertyBag implements LfGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLfGenerator.class);

    private static final double POWER_EPSILON_SI = 1e-4;

    protected static final double DEFAULT_DROOP = 4; // why not

    protected final LfNetwork network;

    protected double initialTargetP;

    protected double targetP;

    protected LfBus bus;

    protected double calculatedQ = Double.NaN;

    protected double targetV = Double.NaN;

    protected GeneratorControlType generatorControlType = GeneratorControlType.OFF;

    protected String controlledBusId;

    protected String controlledBranchId;

    protected ControlledSide controlledBranchSide;

    protected double remoteTargetQ = Double.NaN;

    private boolean disabled;

    protected AbstractLfGenerator(LfNetwork network, double targetP) {
        this.network = Objects.requireNonNull(network);
        this.targetP = targetP;
        this.initialTargetP = targetP;
    }

    @Override
    public String getOriginalId() {
        return getId();
    }

    public LfBus getBus() {
        return bus;
    }

    public void setBus(LfBus bus) {
        this.bus = bus;
    }

    @Override
    public boolean isFictitious() {
        return false;
    }

    @Override
    public double getInitialTargetP() {
        return initialTargetP / PerUnit.SB;
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
            bus.invalidateGenerationTargetP();
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
    public GeneratorControlType getGeneratorControlType() {
        return generatorControlType;
    }

    @Override
    public void setGeneratorControlType(GeneratorControlType generatorControlType) {
        this.generatorControlType = Objects.requireNonNull(generatorControlType);
    }

    @Override
    public boolean hasRemoteReactivePowerControl() {
        return generatorControlType == GeneratorControlType.REMOTE_REACTIVE_POWER;
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
    public double getRangeQ(ReactiveRangeMode rangeMode) {
        double rangeQ = Double.NaN;
        ReactiveLimits reactiveLimits = getReactiveLimits().orElse(null);
        if (reactiveLimits != null) {
            switch (reactiveLimits.getKind()) {
                case CURVE:
                    ReactiveCapabilityCurve reactiveCapabilityCurve = (ReactiveCapabilityCurve) reactiveLimits;
                    if (rangeMode == ReactiveRangeMode.MIN || rangeMode == ReactiveRangeMode.MAX) {
                        for (ReactiveCapabilityCurve.Point point : reactiveCapabilityCurve.getPoints()) {
                            if (Double.isNaN(rangeQ)) {
                                rangeQ = point.getMaxQ() - point.getMinQ();
                            } else {
                                rangeQ = rangeMode == ReactiveRangeMode.MAX ? Math.max(rangeQ, point.getMaxQ() - point.getMinQ())
                                                                            : Math.min(rangeQ, point.getMaxQ() - point.getMinQ());
                            }
                        }
                    } else if (rangeMode == ReactiveRangeMode.TARGET_P) {
                        rangeQ = reactiveLimits.getMaxQ(targetP) - reactiveLimits.getMinQ(targetP);
                    } else {
                        throw new PowsyblException("Unsupported reactive range mode: " + rangeMode);
                    }
                    break;

                case MIN_MAX:
                    MinMaxReactiveLimits minMaxReactiveLimits = (MinMaxReactiveLimits) reactiveLimits;
                    rangeQ = minMaxReactiveLimits.getMaxQ() - minMaxReactiveLimits.getMinQ();
                    break;

                default:
                    throw new IllegalStateException("Unknown reactive limits kind: " + reactiveLimits.getKind());
            }
            return rangeQ / PerUnit.SB;
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
    public LfBus getControlledBus() {
        return network.getBusById(controlledBusId);
    }

    protected void setVoltageControl(double targetV, Terminal terminal, Terminal regulatingTerminal, LfNetworkParameters parameters,
                                     LfNetworkLoadingReport report) {
        if (!checkVoltageControlConsistency(parameters, report)) {
            return;
        }
        Bus controlledBus = parameters.isBreakers() ? regulatingTerminal.getBusBreakerView().getBus() : regulatingTerminal.getBusView().getBus();
        if (controlledBus == null) {
            LOGGER.warn("Regulating terminal of LfGenerator {} is out of voltage: voltage control discarded", getId());
            return;
        }
        boolean inSameSynchronousComponent = parameters.isBreakers()
                ? regulatingTerminal.getBusBreakerView().getBus().getSynchronousComponent().getNum() == terminal.getBusBreakerView().getBus().getSynchronousComponent().getNum()
                : regulatingTerminal.getBusView().getBus().getSynchronousComponent().getNum() == terminal.getBusView().getBus().getSynchronousComponent().getNum();
        if (!inSameSynchronousComponent) {
            LOGGER.warn("Regulating terminal of LfGenerator {} is not in the same synchronous component: voltage control discarded", getId());
            return;
        }
        if (!checkTargetV(targetV / regulatingTerminal.getVoltageLevel().getNominalV(), parameters, report)) {
            return;
        }
        this.controlledBusId = controlledBus.getId();
        this.targetV = targetV / regulatingTerminal.getVoltageLevel().getNominalV();
        this.generatorControlType = GeneratorControlType.VOLTAGE;
    }

    protected boolean checkVoltageControlConsistency(LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        boolean consistency = true;
        if (parameters.isReactiveLimits()) {
            double rangeQ;
            switch (parameters.getReactiveRangeCheckMode()) {
                case MIN_MAX:
                    double minRangeQ = getRangeQ(ReactiveRangeMode.MIN);
                    double maxRangeQ = getRangeQ(ReactiveRangeMode.MAX);
                    if (maxRangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB || minRangeQ == 0.0) {
                        LOGGER.trace("Discard generator '{}' from voltage control because min or max reactive ranges (min: {} and max: {}) are too small", getId(), minRangeQ, maxRangeQ);
                        report.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall++;
                        consistency = false;
                    }
                    break;
                case MAX:
                    rangeQ = getRangeQ(ReactiveRangeMode.MAX);
                    if (rangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB) {
                        LOGGER.trace("Discard generator '{}' from voltage control because max reactive range ({}) is too small", getId(), rangeQ);
                        report.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall++;
                        consistency = false;
                    }
                    break;
                case TARGET_P:
                    rangeQ = getRangeQ(ReactiveRangeMode.TARGET_P);
                    if (rangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB) {
                        LOGGER.trace("Discard generator '{}' from voltage control because reactive range at targetP ({}) is too small", getId(), rangeQ);
                        report.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall++;
                        consistency = false;
                    }
                    break;
                default:
                    throw new IllegalStateException("Unknown reactive range check mode: " + parameters.getReactiveRangeCheckMode());
            }
        }
        if (Math.abs(getTargetP()) < POWER_EPSILON_SI && getMinP() > POWER_EPSILON_SI) {
            LOGGER.trace("Discard generator '{}' from voltage control because not started (targetP={} MW, minP={} MW)", getId(), getTargetP(), getMinP());
            report.generatorsDiscardedFromVoltageControlBecauseNotStarted++;
            consistency = false;
        }
        return consistency;
    }

    protected boolean checkTargetV(double targetV, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        // check that targetV has a plausible value (wrong nominal voltage issue)
        if (targetV < parameters.getMinPlausibleTargetVoltage() || targetV > parameters.getMaxPlausibleTargetVoltage()) {
            LOGGER.trace("Generator '{}' has an inconsistent target voltage: {} pu: generator voltage control discarded",
                getId(), targetV);
            report.generatorsWithInconsistentTargetVoltage++;
            return false;
        }
        return true;
    }

    protected void setReactivePowerControl(Terminal regulatingTerminal, double targetQ) {
        Connectable<?> connectable = regulatingTerminal.getConnectable();
        if (connectable instanceof Line) {
            Line l = (Line) connectable;
            this.controlledBranchSide = l.getTerminal(Branch.Side.ONE) == regulatingTerminal ?
                    ControlledSide.ONE : ControlledSide.TWO;
            this.controlledBranchId = l.getId();
        } else if (connectable instanceof TwoWindingsTransformer) {
            TwoWindingsTransformer l = (TwoWindingsTransformer) connectable;
            this.controlledBranchSide = l.getTerminal(Branch.Side.ONE) == regulatingTerminal ?
                    ControlledSide.ONE : ControlledSide.TWO;
            this.controlledBranchId = l.getId();
        } else {
            LOGGER.error("Generator '{}' is controlled by an instance of {}: not supported",
                    getId(), connectable.getClass());
            return;
        }
        this.generatorControlType = GeneratorControlType.REMOTE_REACTIVE_POWER;
        this.remoteTargetQ = targetQ / PerUnit.SB;
    }

    @Override
    public LfBranch getControlledBranch() {
        return network.getBranchById(controlledBranchId);
    }

    @Override
    public ControlledSide getControlledBranchSide() {
        return controlledBranchSide;
    }

    @Override
    public double getRemoteTargetQ() {
        return remoteTargetQ;
    }

    @Override
    public void setParticipating(boolean participating) {
        // nothing to do
    }

    public static boolean checkActivePowerControl(String generatorId, double targetP, double minP, double maxP, double plausibleActivePowerLimit,
                                                  LfNetworkLoadingReport report) {
        boolean participating = true;
        if (Math.abs(targetP) < POWER_EPSILON_SI) {
            LOGGER.trace("Discard generator '{}' from active power control because targetP ({}) equals 0",
                    generatorId, targetP);
            report.generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero++;
            participating = false;
        }
        if (targetP > maxP) {
            LOGGER.trace("Discard generator '{}' from active power control because targetP ({}) > maxP ({})",
                    generatorId, targetP, maxP);
            report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP++;
            participating = false;
        }
        if (targetP < minP && minP > 0) {
            LOGGER.trace("Discard generator '{}' from active power control because targetP ({}) < minP ({})",
                    generatorId, targetP, minP);
            report.generatorsDiscardedFromActivePowerControlBecauseTargetPLowerThanMinP++;
            participating = false;
        }
        if (maxP > plausibleActivePowerLimit) {
            LOGGER.trace("Discard generator '{}' from active power control because maxP ({}) > {}} MW",
                    generatorId, maxP, plausibleActivePowerLimit);
            report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible++;
            participating = false;
        }
        if ((maxP - minP) < POWER_EPSILON_SI) {
            LOGGER.trace("Discard generator '{}' from active power control because maxP ({} MW) equals minP ({} MW)",
                    generatorId, maxP, minP);
            report.generatorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP++;
            participating = false;
        }
        return participating;
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
}
