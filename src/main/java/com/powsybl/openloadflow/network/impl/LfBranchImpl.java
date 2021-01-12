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
import com.powsybl.openloadflow.util.Evaluable;

import java.util.*;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBranchImpl extends AbstractLfBranch {

    private final Branch<?> branch;

    private Evaluable p1 = NAN;

    private Evaluable p2 = NAN;

    private Evaluable q1 = NAN;

    private Evaluable q2 = NAN;

    protected LfBranchImpl(LfBus bus1, LfBus bus2, PiModel piModel, Branch<?> branch) {
        super(bus1, bus2, piModel);
        this.branch = branch;
    }

    private static LfBranchImpl createLine(Line line, LfBus bus1, LfBus bus2, double zb) {
        PiModel piModel = new SimplePiModel()
                .setR(line.getR() / zb)
                .setX(line.getX() / zb)
                .setG1(line.getG1() * zb)
                .setG2(line.getG2() * zb)
                .setB1(line.getB1() * zb)
                .setB2(line.getB2() * zb);

        return new LfBranchImpl(bus1, bus2, piModel, line);
    }

    private static LfBranchImpl createTransformer(TwoWindingsTransformer twt, LfBus bus1, LfBus bus2, double zb, boolean twtSplitShuntAdmittance) {
        PiModel piModel = null;

        double baseRatio = Transformers.getRatioPerUnitBase(twt);

        PhaseTapChanger ptc = twt.getPhaseTapChanger();
        if (ptc != null
                && ptc.isRegulating()
                && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP) {

            Integer rtcPosition = Transformers.getCurrentPosition(twt.getRatioTapChanger());
            List<PiModel> models = new ArrayList<>();
            for (int ptcPosition = ptc.getLowTapPosition(); ptcPosition <= ptc.getHighTapPosition(); ptcPosition++) {
                double r = Transformers.getR(twt, rtcPosition, ptcPosition) / zb;
                double x = Transformers.getX(twt, rtcPosition, ptcPosition) / zb;
                double g1 = Transformers.getG1(twt, rtcPosition, ptcPosition, twtSplitShuntAdmittance) * zb;
                double g2 = twtSplitShuntAdmittance ? g1 : 0;
                double b1 = Transformers.getB1(twt, rtcPosition, ptcPosition, twtSplitShuntAdmittance) * zb;
                double b2 = twtSplitShuntAdmittance ? b1 : 0;
                double r1 = Transformers.getRatio(twt, rtcPosition, ptcPosition) / baseRatio;
                double a1 = Transformers.getAngle(twt, ptcPosition);
                models.add(new SimplePiModel()
                        .setR(r)
                        .setX(x)
                        .setG1(g1)
                        .setG2(g2)
                        .setB1(b1)
                        .setB2(b2)
                        .setR1(r1)
                        .setA1(a1));
            }
            piModel = new PiModelArray(models, ptc.getLowTapPosition(), ptc.getTapPosition());
        }

        RatioTapChanger rtc = twt.getRatioTapChanger();
        if (rtc != null && rtc.isRegulating()) {
            if (piModel == null) {
                Integer ptcPosition = Transformers.getCurrentPosition(twt.getPhaseTapChanger());
                List<PiModel> models = new ArrayList<>();
                for (int rtcPosition = rtc.getLowTapPosition(); rtcPosition <= rtc.getHighTapPosition(); rtcPosition++) {
                    double r = Transformers.getR(twt, rtcPosition, ptcPosition) / zb;
                    double x = Transformers.getX(twt, rtcPosition, ptcPosition) / zb;
                    double g1 = Transformers.getG1(twt, rtcPosition, ptcPosition, twtSplitShuntAdmittance) * zb;
                    double g2 = twtSplitShuntAdmittance ? g1 : 0;
                    double b1 = Transformers.getB1(twt, rtcPosition, ptcPosition, twtSplitShuntAdmittance) * zb;
                    double b2 = twtSplitShuntAdmittance ? b1 : 0;
                    double r1 = Transformers.getRatio(twt, rtcPosition, ptcPosition) / baseRatio;
                    double a1 = Transformers.getAngle(twt, ptcPosition);
                    models.add(new SimplePiModel()
                            .setR(r)
                            .setX(x)
                            .setG1(g1)
                            .setG2(g2)
                            .setB1(b1)
                            .setB2(b2)
                            .setR1(r1)
                            .setA1(a1));
                }
                piModel = new PiModelArray(models, rtc.getLowTapPosition(), rtc.getTapPosition());
            } else {
                throw new PowsyblException("Voltage and phase control on same branch '" + twt.getId() + "' is not yet supported");
            }
        }

        if (piModel == null) {
            piModel = new SimplePiModel()
                    .setR(Transformers.getR(twt) / zb)
                    .setX(Transformers.getX(twt) / zb)
                    .setG1(Transformers.getG1(twt, twtSplitShuntAdmittance) * zb)
                    .setG2(twtSplitShuntAdmittance ? Transformers.getG1(twt, twtSplitShuntAdmittance) * zb : 0)
                    .setB1(Transformers.getB1(twt, twtSplitShuntAdmittance) * zb)
                    .setB2(twtSplitShuntAdmittance ? Transformers.getB1(twt, twtSplitShuntAdmittance) * zb : 0)
                    .setR1(Transformers.getRatio(twt) / baseRatio)
                    .setA1(Transformers.getAngle(twt));
        }

        return new LfBranchImpl(bus1, bus2, piModel, twt);
    }

    public static LfBranchImpl create(Branch<?> branch, LfBus bus1, LfBus bus2, boolean twtSplitShuntAdmittance) {
        Objects.requireNonNull(branch);
        double nominalV2 = branch.getTerminal2().getVoltageLevel().getNominalV();
        double zb = nominalV2 * nominalV2 / PerUnit.SB;
        if (branch instanceof Line) {
            return createLine((Line) branch, bus1, bus2, zb);
        } else if (branch instanceof TwoWindingsTransformer) {
            TwoWindingsTransformer twt = (TwoWindingsTransformer) branch;
            return createTransformer(twt, bus1, bus2, zb, twtSplitShuntAdmittance);
        } else {
            throw new PowsyblException("Unsupported type of branch for flow equations of branch: " + branch.getId());
        }
    }

    @Override
    public String getId() {
        return branch.getId();
    }

    @Override
    public boolean hasPhaseControlCapability() {
        return branch.getType() == ConnectableType.TWO_WINDINGS_TRANSFORMER
                && ((TwoWindingsTransformer) branch).getPhaseTapChanger() != null;
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p1 = Objects.requireNonNull(p1);
    }

    @Override
    public double getP1() {
        return p1.eval();
    }

    @Override
    public void setP2(Evaluable p2) {
        this.p2 = Objects.requireNonNull(p2);
    }

    @Override
    public double getP2() {
        return p2.eval();
    }

    @Override
    public void setQ1(Evaluable q1) {
        this.q1 = Objects.requireNonNull(q1);
    }

    @Override
    public void setQ2(Evaluable q2) {
        this.q2 = Objects.requireNonNull(q2);
    }

    @Override
    public double getI1() {
        return getBus1() != null ? Math.hypot(p1.eval(), q1.eval())
            / (Math.sqrt(3.) * getBus1().getV() / 1000) : Double.NaN;
    }

    @Override
    public double getI2() {
        return getBus2() != null ? Math.hypot(p2.eval(), q2.eval())
            / (Math.sqrt(3.) * getBus2().getV() / 1000) : Double.NaN;
    }

    @Override
    public double getPermanentLimit1() {
        return branch.getCurrentLimits1() != null ? branch.getCurrentLimits1().getPermanentLimit() * getBus1().getNominalV() / PerUnit.SB : Double.NaN;
    }

    @Override
    public double getPermanentLimit2() {
        return branch.getCurrentLimits2() != null ? branch.getCurrentLimits2().getPermanentLimit() * getBus2().getNominalV() / PerUnit.SB : Double.NaN;
    }

    @Override
    public Map<Integer, Double> getTemporaryLimits1() {
        Map<Integer, Double> map = new HashMap();
        if (branch.getCurrentLimits1() != null) {
            for (CurrentLimits.TemporaryLimit temporaryLimit : branch.getCurrentLimits1().getTemporaryLimits()) {
                map.put(temporaryLimit.getAcceptableDuration(),
                        temporaryLimit.getValue() != Double.MAX_VALUE ? temporaryLimit.getValue() * getBus1().getNominalV() / PerUnit.SB : Double.MAX_VALUE);
            }
        }
        return map;
    }

    @Override
    public Map<Integer, Double> getTemporaryLimits2() {
        Map<Integer, Double> map = new HashMap();
        if (branch.getCurrentLimits2() != null) {
            for (CurrentLimits.TemporaryLimit temporaryLimit : branch.getCurrentLimits2().getTemporaryLimits()) {
                map.put(temporaryLimit.getAcceptableDuration(),
                        temporaryLimit.getValue() != Double.MAX_VALUE ? temporaryLimit.getValue() * getBus2().getNominalV() / PerUnit.SB : Double.MAX_VALUE);
            }
        }
        return map;
    }

    @Override
    public void updateState(boolean phaseShifterRegulationOn, boolean isTransformerVoltageControlOn) {
        branch.getTerminal1().setP(p1.eval() * PerUnit.SB);
        branch.getTerminal1().setQ(q1.eval() * PerUnit.SB);
        branch.getTerminal2().setP(p2.eval() * PerUnit.SB);
        branch.getTerminal2().setQ(q2.eval() * PerUnit.SB);

        if (phaseShifterRegulationOn && isPhaseController()  && phaseControl.getMode() == DiscretePhaseControl.Mode.OFF) {
            // it means there is a regulating phase tap changer located on that branch
            updateTapPosition(((TwoWindingsTransformer) branch).getPhaseTapChanger());
        }

        if (phaseShifterRegulationOn && isPhaseControlled()) {
            // check if the target value deadband is respected
            checkTargetDeadband(phaseControl.getControlledSide() == DiscretePhaseControl.ControlledSide.ONE ? p1.eval() : p2.eval());
        }

        if (isTransformerVoltageControlOn && isVoltageController()) { // it means there is a regulating ratio tap changer
            TwoWindingsTransformer twt = (TwoWindingsTransformer) branch;
            RatioTapChanger rtc = twt.getRatioTapChanger();
            double baseRatio = Transformers.getRatioPerUnitBase(twt);
            double rho = getPiModel().getR1() * twt.getRatedU1() / twt.getRatedU2() * baseRatio;
            updateTapPosition(rtc, rho);
            checkTargetDeadband(rtc);
        }
    }
}
