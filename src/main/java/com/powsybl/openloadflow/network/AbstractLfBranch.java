/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.CurrentLimits;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.RatioTapChanger;
import com.powsybl.openloadflow.network.impl.Transformers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfBranch implements LfBranch {

    public static class LfTemporaryLimit {

        private final int acceptableDuration;
        private final double value;

        public LfTemporaryLimit(int acceptableDuration, double value) {
            this.acceptableDuration = acceptableDuration;
            this.value = value;
        }

        public int getAcceptableDuration() {
            return acceptableDuration;
        }

        public double getValue() {
            return value;
        }

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLfBranch.class);

    private static final Comparator<LfTemporaryLimit> TEMPORARY_LIMITS_COMPARATOR =
        Comparator.comparingDouble(LfTemporaryLimit::getValue).reversed();

    private int num = -1;

    private final LfBus bus1;

    private final LfBus bus2;

    private final PiModel piModel;

    protected DiscretePhaseControl phaseControl;

    protected DiscreteVoltageControl discreteVoltageControl;

    protected AbstractLfBranch(LfBus bus1, LfBus bus2, PiModel piModel) {
        this.bus1 = bus1;
        this.bus2 = bus2;
        this.piModel = Objects.requireNonNull(piModel);
    }

    protected static SortedSet<LfTemporaryLimit> createSortedTemporaryLimitsSet(CurrentLimits currentLimits, LfBus bus) {
        SortedSet<LfTemporaryLimit> temporaryLimits = new TreeSet<>(TEMPORARY_LIMITS_COMPARATOR);
        if (currentLimits != null) {
            for (CurrentLimits.TemporaryLimit temporaryLimit : currentLimits.getTemporaryLimits()) {
                double valuePerUnit = temporaryLimit.getValue() != Double.MAX_VALUE ? temporaryLimit.getValue() * bus.getNominalV() / PerUnit.SB : Double.MAX_VALUE;
                temporaryLimits.add(new LfTemporaryLimit(temporaryLimit.getAcceptableDuration(), valuePerUnit));
            }
        }
        return temporaryLimits;
    }

    @Override
    public int getNum() {
        return num;
    }

    @Override
    public void setNum(int num) {
        this.num = num;
    }

    @Override
    public LfBus getBus1() {
        return bus1;
    }

    @Override
    public LfBus getBus2() {
        return bus2;
    }

    @Override
    public PiModel getPiModel() {
        return piModel;
    }

    @Override
    public DiscretePhaseControl getDiscretePhaseControl() {
        return phaseControl;
    }

    @Override
    public void setDiscretePhaseControl(DiscretePhaseControl discretePhaseControl) {
        this.phaseControl = discretePhaseControl;
    }

    @Override
    public boolean isPhaseController() {
        return phaseControl != null && phaseControl.getController() == this;
    }

    @Override
    public boolean isPhaseControlled() {
        return phaseControl != null && phaseControl.getControlled() == this;
    }

    @Override
    public boolean isPhaseControlled(DiscretePhaseControl.ControlledSide controlledSide) {
        return isPhaseControlled() && phaseControl.getControlledSide() == controlledSide;
    }

    protected void updateTapPosition(PhaseTapChanger ptc) {
        int tapPosition = Transformers.findTapPosition(ptc, Math.toDegrees(getPiModel().getA1()));
        ptc.setTapPosition(tapPosition);
    }

    protected void updateTapPosition(RatioTapChanger rtc, double ptcRho, double rho) {
        int tapPosition = Transformers.findTapPosition(rtc, ptcRho, rho);
        rtc.setTapPosition(tapPosition);
    }

    protected void checkTargetDeadband(double p) {
        double distance = Math.abs(p - phaseControl.getTargetValue()); // in per unit system
        if (distance > phaseControl.getTargetDeadband() / 2) {
            LOGGER.warn("The active power on side {} of branch {} ({} MW) is out of the target value ({} MW) +/- deadband/2 ({} MW)",
                    phaseControl.getControlledSide(), getId(), p,
                    phaseControl.getTargetValue() * PerUnit.SB, phaseControl.getTargetDeadband() / 2 * PerUnit.SB);
        }
    }

    protected void checkTargetDeadband(RatioTapChanger rtc) {
        if (rtc.getTargetDeadband() != 0) {
            double nominalV = rtc.getRegulationTerminal().getVoltageLevel().getNominalV();
            double v = discreteVoltageControl.getControlled().getV();
            double distance = Math.abs(v - discreteVoltageControl.getTargetValue()); // in per unit system
            if (distance > rtc.getTargetDeadband() / 2) {
                LOGGER.warn("The voltage on bus {} ({} kV) is out of the target value ({} kV) +/- deadband/2 ({} kV)",
                        discreteVoltageControl.getControlled().getId(), v * nominalV, rtc.getTargetV(), rtc.getTargetDeadband() / 2);
            }
        }
    }

    @Override
    public DiscreteVoltageControl getDiscreteVoltageControl() {
        return discreteVoltageControl;
    }

    @Override
    public boolean isVoltageController() {
        return discreteVoltageControl != null;
    }

    @Override
    public void setDiscreteVoltageControl(DiscreteVoltageControl discreteVoltageControl) {
        this.discreteVoltageControl = discreteVoltageControl;
    }
}
