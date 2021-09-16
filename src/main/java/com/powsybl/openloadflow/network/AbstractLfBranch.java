/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.impl.Transformers;
import com.powsybl.openloadflow.util.Evaluable;
import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfBranch extends AbstractElement implements LfBranch {

    public static final class LfLimit {

        private int acceptableDuration;
        private final double value;

        private LfLimit(int acceptableDuration, double value) {
            this.acceptableDuration = acceptableDuration;
            this.value = value;
        }

        private static LfLimit createTemporaryLimit(int acceptableDuration, double valuePerUnit) {
            return new LfLimit(acceptableDuration, valuePerUnit);
        }

        private static LfLimit createPermanentLimit(double valuePerUnit) {
            return new LfLimit(Integer.MAX_VALUE, valuePerUnit);
        }

        public int getAcceptableDuration() {
            return acceptableDuration;
        }

        public double getValue() {
            return value;
        }

        public void setAcceptableDuration(int acceptableDuration) {
            this.acceptableDuration = acceptableDuration;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLfBranch.class);

    private final LfBus bus1;

    private final LfBus bus2;

    private final Map<LimitType, List<LfLimit>> limits1 = new EnumMap<>(LimitType.class);

    private final Map<LimitType, List<LfLimit>> limits2 = new EnumMap<>(LimitType.class);

    private final PiModel piModel;

    protected DiscretePhaseControl phaseControl;

    protected DiscreteVoltageControl discreteVoltageControl;

    protected boolean disabled = false;

    protected boolean spanningTreeEdge = false;

    protected Evaluable a1;

    protected AbstractLfBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel) {
        super(network);
        this.bus1 = bus1;
        this.bus2 = bus2;
        this.piModel = Objects.requireNonNull(piModel);
    }

    protected static List<LfLimit> createSortedLimitsList(LoadingLimits loadingLimits, LfBus bus) {
        LinkedList<LfLimit> sortedLimits = new LinkedList<>();
        if (loadingLimits != null) {
            double toPerUnit = getScaleForLimitType(loadingLimits.getLimitType(), bus);

            for (LoadingLimits.TemporaryLimit temporaryLimit : loadingLimits.getTemporaryLimits()) {
                if (temporaryLimit.getAcceptableDuration() != 0) {
                    // it is not useful to add a limit with acceptable duration equal to zero as the only value plausible
                    // for this limit is infinity.
                    // https://javadoc.io/doc/com.powsybl/powsybl-core/latest/com/powsybl/iidm/network/CurrentLimits.html
                    double valuePerUnit = temporaryLimit.getValue() * toPerUnit;
                    sortedLimits.addFirst(LfLimit.createTemporaryLimit(temporaryLimit.getAcceptableDuration(), valuePerUnit));
                }
            }
            sortedLimits.addLast(LfLimit.createPermanentLimit(loadingLimits.getPermanentLimit() * toPerUnit));
        }
        if (sortedLimits.size() > 1) {
            // we only make that fix if there is more than a permanent limit attached to the branch.
            for (int i = sortedLimits.size() - 1; i > 0; i--) {
                // From the permanent limit to the most serious temporary limit.
                sortedLimits.get(i).setAcceptableDuration(sortedLimits.get(i - 1).getAcceptableDuration());
            }
            sortedLimits.getFirst().setAcceptableDuration(0);
        }
        return sortedLimits;
    }

    @Override
    public ElementType getType() {
        return ElementType.BRANCH;
    }

    @Override
    public LfBus getBus1() {
        return bus1;
    }

    @Override
    public LfBus getBus2() {
        return bus2;
    }

    public List<LfLimit> getLimits1(LimitType type, LoadingLimits loadingLimits) {
        return limits1.computeIfAbsent(type, v -> createSortedLimitsList(loadingLimits, bus1));
    }

    public List<LfLimit> getLimits2(LimitType type, LoadingLimits loadingLimits) {
        return limits2.computeIfAbsent(type, v -> createSortedLimitsList(loadingLimits, bus2));
    }

    @Override
    public PiModel getPiModel() {
        return piModel;
    }

    @Override
    public Optional<DiscretePhaseControl> getDiscretePhaseControl() {
        return Optional.ofNullable(phaseControl);
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
            double v = discreteVoltageControl.getControlled().getV().eval();
            double distance = Math.abs(v - discreteVoltageControl.getTargetValue()); // in per unit system
            if (distance > rtc.getTargetDeadband() / 2) {
                LOGGER.warn("The voltage on bus {} ({} kV) is out of the target value ({} kV) +/- deadband/2 ({} kV)",
                        discreteVoltageControl.getControlled().getId(), v * nominalV, rtc.getTargetV(), rtc.getTargetDeadband() / 2);
            }
        }
    }

    protected static double getScaleForLimitType(LimitType type, LfBus bus) {
        switch (type) {
            case ACTIVE_POWER:
            case APPARENT_POWER:
                return 1.0 / PerUnit.SB;
            case CURRENT:
                return 1.0 / PerUnit.ib(bus.getNominalV());
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting scale for limit type %s is not supported.", type));
        }
    }

    @Override
    public Optional<DiscreteVoltageControl> getDiscreteVoltageControl() {
        return Optional.ofNullable(discreteVoltageControl);
    }

    @Override
    public boolean isVoltageController() {
        return discreteVoltageControl != null;
    }

    @Override
    public void setDiscreteVoltageControl(DiscreteVoltageControl discreteVoltageControl) {
        this.discreteVoltageControl = discreteVoltageControl;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public double computeApparentPower1() {
        double p = getP1().eval();
        double q = getQ1().eval();
        return FastMath.sqrt(p * p + q * q);
    }

    @Override
    public double computeApparentPower2() {
        double p = getP2().eval();
        double q = getQ2().eval();
        return FastMath.sqrt(p * p + q * q);
    }

    @Override
    public void setSpanningTreeEdge(boolean spanningTreeEdge) {
        this.spanningTreeEdge = spanningTreeEdge;
    }

    @Override
    public boolean isSpanningTreeEdge() {
        return this.spanningTreeEdge;
    }

    @Override
    public Evaluable getA1() {
        return a1;
    }

    @Override
    public void setA1(Evaluable a1) {
        this.a1 = a1;
    }
}
