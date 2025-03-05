/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.LfAsymGenerator;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractLfGenerator extends AbstractLfInjection implements LfGenerator, LfReferencePriorityInjection {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLfGenerator.class);

    private static final double POWER_EPSILON_SI = 1e-4;

    protected static final double DEFAULT_DROOP = 4; // why not

    protected final LfNetwork network;

    protected LfBus bus;

    protected double calculatedQ = Double.NaN;

    protected double targetV = Double.NaN;

    protected GeneratorControlType generatorControlType = GeneratorControlType.OFF;

    protected String controlledBusId;

    protected String controlledBranchId;

    protected TwoSides controlledBranchSide;

    protected double remoteTargetQ = Double.NaN;

    private boolean disabled;

    protected LfAsymGenerator asym;

    protected int referencePriority;

    protected boolean reference;

    protected AbstractLfGenerator(LfNetwork network, double targetP) {
        super(targetP, targetP);
        this.network = Objects.requireNonNull(network);
    }

    protected record ActivePowerControlHelper(boolean participating, double participationFactor, double droop, double minTargetP, double maxTargetP) {

        @SuppressWarnings("unchecked")
        static ActivePowerControlHelper create(Injection<?> injection, double minP, double maxP) {
            boolean participating = true;
            double participationFactor = 0;
            double droop = DEFAULT_DROOP;
            double minTargetP = minP;
            double maxTargetP = maxP;

            var activePowerControl = injection.getExtension(ActivePowerControl.class);
            if (activePowerControl != null) {
                participating = activePowerControl.isParticipate();
                if (!Double.isNaN(activePowerControl.getDroop())) {
                    droop = activePowerControl.getDroop();
                }
                if (activePowerControl.getParticipationFactor() > 0) {
                    participationFactor = activePowerControl.getParticipationFactor();
                }
                if (activePowerControl.getMinTargetP().isPresent()) {
                    minTargetP = activePowerControl.getMinTargetP().getAsDouble();
                }
                if (activePowerControl.getMaxTargetP().isPresent()) {
                    maxTargetP = activePowerControl.getMaxTargetP().getAsDouble();
                }
            }

            return new ActivePowerControlHelper(participating, participationFactor, droop, minTargetP, maxTargetP);
        }
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
    public void setTargetP(double targetP) {
        if (targetP != this.targetP) {
            double oldTargetP = this.targetP;
            this.targetP = targetP;
            bus.invalidateGenerationTargetP();
            for (LfNetworkListener listener : bus.getNetwork().getListeners()) {
                listener.onGenerationActivePowerTargetChange(this, oldTargetP, targetP);
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
                .map(limits -> limits.getMinQ(targetP * PerUnit.SB) / PerUnit.SB)
                .orElse(-Double.MAX_VALUE);
    }

    @Override
    public double getMaxQ() {
        return getReactiveLimits()
                .map(limits -> limits.getMaxQ(targetP * PerUnit.SB) / PerUnit.SB)
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
                        rangeQ = reactiveLimits.getMaxQ(targetP * PerUnit.SB) - reactiveLimits.getMinQ(targetP * PerUnit.SB);
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
        return calculatedQ;
    }

    @Override
    public void setCalculatedQ(double calculatedQ) {
        this.calculatedQ = calculatedQ;
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
        if (!checkTargetV(getId(), targetV / regulatingTerminal.getVoltageLevel().getNominalV(), regulatingTerminal.getVoltageLevel().getNominalV(), parameters, report)) {
            return;
        }
        this.controlledBusId = controlledBus.getId();
        this.targetV = targetV / regulatingTerminal.getVoltageLevel().getNominalV();
        this.generatorControlType = GeneratorControlType.VOLTAGE;
    }

    protected boolean checkVoltageControlConsistency(LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        return checkIfReactiveRangesAreLargeEnoughForVoltageControl(parameters, report) &&
                checkIfGeneratorStartedForVoltageControl(report) &&
                checkIfGeneratorIsInsideActivePowerLimitsForVoltageControl(parameters, report);
    }

    protected boolean checkIfReactiveRangesAreLargeEnoughForVoltageControl(LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        if (!parameters.isReactiveLimits()) {
            return true;
        }

        boolean consistency = true;
        double rangeQ;
        switch (parameters.getReactiveRangeCheckMode()) {
            case MIN_MAX -> {
                double minRangeQ = getRangeQ(ReactiveRangeMode.MIN);
                double maxRangeQ = getRangeQ(ReactiveRangeMode.MAX);
                if (maxRangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB || minRangeQ == 0.0) {
                    LOGGER.trace("Discard generator '{}' from voltage control because min or max reactive ranges (min: {} and max: {}) are too small", getId(), minRangeQ, maxRangeQ);
                    report.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall++;
                    consistency = false;
                }
            }
            case MAX -> {
                rangeQ = getRangeQ(ReactiveRangeMode.MAX);
                if (rangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB) {
                    LOGGER.trace("Discard generator '{}' from voltage control because max reactive range ({}) is too small", getId(), rangeQ);
                    report.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall++;
                    consistency = false;
                }
            }
            case TARGET_P -> {
                rangeQ = getRangeQ(ReactiveRangeMode.TARGET_P);
                if (rangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB) {
                    LOGGER.trace("Discard generator '{}' from voltage control because reactive range at targetP ({}) is too small", getId(), rangeQ);
                    report.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall++;
                    consistency = false;
                }
            }
            default ->
                    throw new IllegalStateException("Unknown reactive range check mode: " + parameters.getReactiveRangeCheckMode());
        }

        return consistency;
    }

    protected boolean checkIfGeneratorStartedForVoltageControl(LfNetworkLoadingReport report) {
        if (Math.abs(getTargetP()) < POWER_EPSILON_SI && getMinP() > POWER_EPSILON_SI) {
            LOGGER.trace("Discard generator '{}' from voltage control because not started (targetP={} MW, minP={} MW)", getId(), getTargetP(), getMinP());
            report.generatorsDiscardedFromVoltageControlBecauseNotStarted++;
            return false;
        }
        return true;
    }

    protected boolean checkIfGeneratorIsInsideActivePowerLimitsForVoltageControl(LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        if (parameters.isUseActiveLimits() &&
            parameters.isDisableVoltageControlOfGeneratorsOutsideActivePowerLimits() &&
            (getTargetP() < getMinP() || getTargetP() > getMaxP())) {
            LOGGER.trace("Discard generator '{}' from voltage control because targetP is outside active power limits (targetP={} MW, minP={} MW, maxP={} MW)", getId(), getTargetP(), getMinP(), getMaxP());
            report.generatorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits++;
            return false;
        }
        return true;
    }

    public static boolean checkTargetV(String generatorId, double targetV, double nominalV, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        // check that targetV has a plausible value (wrong nominal voltage issue)
        if (!VoltageControl.checkTargetV(targetV, nominalV, parameters)) {
            LOGGER.trace("Generator '{}' has an implausible target voltage: {} pu: generator voltage control discarded",
                generatorId, targetV);
            if (report != null) {
                report.generatorsWithImplausibleTargetVoltage++;
            }
            return false;
        }
        return true;
    }

    protected void setRemoteReactivePowerControl(Terminal regulatingTerminal, double targetQ) {
        Connectable<?> connectable = regulatingTerminal.getConnectable();
        if (connectable instanceof Branch<?> branch) {
            this.controlledBranchSide = branch.getSide(regulatingTerminal);
            this.controlledBranchId = branch.getId();
        } else if (connectable instanceof ThreeWindingsTransformer t3w) {
            this.controlledBranchSide = TwoSides.ONE; // side 2 is star bus of t3w.
            this.controlledBranchId = LfLegBranch.getId(t3w.getSide(regulatingTerminal), t3w.getId());
        } else {
            LOGGER.error("Generator '{}' is remotely controlling reactive power of an instance of {}: not supported",
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
    public TwoSides getControlledBranchSide() {
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

    public static boolean checkActivePowerControl(String generatorId, double targetP, double maxP,
                                                  double minTargetP, double maxTargetP, double plausibleActivePowerLimit,
                                                  boolean useActiveLimits, LfNetworkLoadingReport report) {
        boolean participating = true;
        if (Math.abs(targetP) < POWER_EPSILON_SI) {
            LOGGER.trace("Discard generator '{}' from active power control because targetP ({} MW) equals 0",
                    generatorId, targetP);
            if (report != null) {
                report.generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero++;
            }
            participating = false;
        }
        if (maxP > plausibleActivePowerLimit) {
            // note that we still want this check applied even if active power limits are not to be enforced,
            // e.g. in case of distribution modes proportional to maxP or remaining margin, we don't want to introduce crazy high participation
            // factors for fictitious elements.
            LOGGER.trace("Discard generator '{}' from active power control because maxP ({} MW) > plausibleLimit ({} MW)",
                    generatorId, maxP, plausibleActivePowerLimit);
            if (report != null) {
                report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible++;
            }
            participating = false;
        }
        if (!useActiveLimits) {
            // if active power limits are not to be enforced, we can skip the rest of the checks
            return participating;
        }
        if (targetP > maxTargetP) {
            LOGGER.trace("Discard generator '{}' from active power control because targetP ({} MW) > maxTargetP ({} MW)",
                    generatorId, targetP, maxTargetP);
            if (report != null) {
                report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP++;
            }
            participating = false;
        }
        if (targetP < minTargetP) {
            LOGGER.trace("Discard generator '{}' from active power control because targetP ({} MW) < minTargetP ({} MW)",
                    generatorId, targetP, minTargetP);
            if (report != null) {
                report.generatorsDiscardedFromActivePowerControlBecauseTargetPLowerThanMinP++;
            }
            participating = false;
        }
        if ((maxTargetP - minTargetP) < POWER_EPSILON_SI) {
            LOGGER.trace("Discard generator '{}' from active power control because maxP ({} MW) equals minP ({} MW)",
                    generatorId, minTargetP, minTargetP);
            if (report != null) {
                report.generatorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP++;
            }
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

    @Override
    public LfAsymGenerator getAsym() {
        return asym;
    }

    @Override
    public void setAsym(LfAsymGenerator asym) {
        this.asym = asym;
    }

    @Override
    public int getReferencePriority() {
        return referencePriority;
    }

    @Override
    public void setReferencePriority(int referencePriority) {
        this.referencePriority = referencePriority;
    }

    @Override
    public boolean isReference() {
        return reference;
    }

    @Override
    public void setReference(boolean reference) {
        this.reference = reference;
    }

    @Override
    public void reApplyActivePowerControlChecks(LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        // nothing to do
    }
}
