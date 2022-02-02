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
import com.powsybl.security.results.BranchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBranchImpl extends AbstractLfBranch {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfBranchImpl.class);

    private final Branch<?> branch;

    private Evaluable p1 = NAN;

    private Evaluable p2 = NAN;

    private Evaluable q1 = NAN;

    private Evaluable q2 = NAN;

    private Evaluable i1 = NAN;

    private Evaluable i2 = NAN;

    protected LfBranchImpl(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel, Branch<?> branch) {
        super(network, bus1, bus2, piModel);
        this.branch = branch;
    }

    private static LfBranchImpl createLine(Line line, LfNetwork network, LfBus bus1, LfBus bus2, double zb, boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds,
                                           LfNetworkLoadingReport report) {
        double nominalV1 = line.getTerminal1().getVoltageLevel().getNominalV();
        double nominalV2 = line.getTerminal2().getVoltageLevel().getNominalV();
        double r1 = 1;
        if (addRatioToLinesWithDifferentNominalVoltageAtBothEnds && nominalV1 != nominalV2) {
            LOGGER.trace("Line '{}' has a different nominal voltage at both ends ({} and {}): add a ration", line.getId(), nominalV1, nominalV2);
            report.linesWithDifferentNominalVoltageAtBothEnds++;
            r1 = 1 / Transformers.getRatioPerUnitBase(line);
        }
        PiModel piModel = new SimplePiModel()
                .setR1(r1)
                .setR(line.getR() / zb)
                .setX(line.getX() / zb)
                .setG1(line.getG1() * zb)
                .setG2(line.getG2() * zb)
                .setB1(line.getB1() * zb)
                .setB2(line.getB2() * zb);

        return new LfBranchImpl(network, bus1, bus2, piModel, line);
    }

    private static LfBranchImpl createTransformer(TwoWindingsTransformer twt, LfNetwork network, LfBus bus1, LfBus bus2, double zb, boolean twtSplitShuntAdmittance) {
        PiModel piModel = null;

        double baseRatio = Transformers.getRatioPerUnitBase(twt);

        PhaseTapChanger ptc = twt.getPhaseTapChanger();
        if (ptc != null
                && ptc.isRegulating()
                && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP) {
            // we have a phase control, whatever we also have a voltage control or not, we create a pi model array
            // based on phase taps mixed with voltage current tap
            Integer rtcPosition = Transformers.getCurrentPosition(twt.getRatioTapChanger());
            List<PiModel> models = new ArrayList<>();
            for (int ptcPosition = ptc.getLowTapPosition(); ptcPosition <= ptc.getHighTapPosition(); ptcPosition++) {
                Transformers.TapCharacteristics tapCharacteristics = Transformers.getTapCharacteristics(twt, rtcPosition, ptcPosition);
                models.add(Transformers.createPiModel(tapCharacteristics, zb, baseRatio, twtSplitShuntAdmittance));
            }
            piModel = new PiModelArray(models, ptc.getLowTapPosition(), ptc.getTapPosition(), network);
        }

        RatioTapChanger rtc = twt.getRatioTapChanger();
        if (rtc != null && rtc.isRegulating() && rtc.hasLoadTapChangingCapabilities()) {
            if (piModel == null) {
                // we have a voltage control, we create a pi model array based on voltage taps mixed with phase current
                // tap
                Integer ptcPosition = Transformers.getCurrentPosition(twt.getPhaseTapChanger());
                List<PiModel> models = new ArrayList<>();
                for (int rtcPosition = rtc.getLowTapPosition(); rtcPosition <= rtc.getHighTapPosition(); rtcPosition++) {
                    Transformers.TapCharacteristics tapCharacteristics = Transformers.getTapCharacteristics(twt, rtcPosition, ptcPosition);
                    models.add(Transformers.createPiModel(tapCharacteristics, zb, baseRatio, twtSplitShuntAdmittance));
                }
                piModel = new PiModelArray(models, rtc.getLowTapPosition(), rtc.getTapPosition(), network);
            } else {
                throw new PowsyblException("Voltage and phase control on same branch '" + twt.getId() + "' is not yet supported");
            }
        }

        if (piModel == null) {
            // we don't have any phase or voltage control, we create a simple pi model (single tap) based on phase current
            // tap and voltage current tap
            Transformers.TapCharacteristics tapCharacteristics = Transformers.getTapCharacteristics(twt);
            piModel = Transformers.createPiModel(tapCharacteristics, zb, baseRatio, twtSplitShuntAdmittance);
        }

        return new LfBranchImpl(network, bus1, bus2, piModel, twt);
    }

    public static LfBranchImpl create(Branch<?> branch, LfNetwork network, LfBus bus1, LfBus bus2, boolean twtSplitShuntAdmittance,
                                      boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds, LfNetworkLoadingReport report) {
        Objects.requireNonNull(branch);
        double nominalV2 = branch.getTerminal2().getVoltageLevel().getNominalV();
        double zb = nominalV2 * nominalV2 / PerUnit.SB;
        if (branch instanceof Line) {
            return createLine((Line) branch, network, bus1, bus2, zb, addRatioToLinesWithDifferentNominalVoltageAtBothEnds, report);
        } else if (branch instanceof TwoWindingsTransformer) {
            TwoWindingsTransformer twt = (TwoWindingsTransformer) branch;
            return createTransformer(twt, network, bus1, bus2, zb, twtSplitShuntAdmittance);
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
        return branch.getType() == IdentifiableType.TWO_WINDINGS_TRANSFORMER
                && ((TwoWindingsTransformer) branch).getPhaseTapChanger() != null;
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p1 = Objects.requireNonNull(p1);
    }

    @Override
    public Evaluable getP1() {
        return p1;
    }

    @Override
    public void setP2(Evaluable p2) {
        this.p2 = Objects.requireNonNull(p2);
    }

    @Override
    public Evaluable getP2() {
        return p2;
    }

    @Override
    public void setQ1(Evaluable q1) {
        this.q1 = Objects.requireNonNull(q1);
    }

    @Override
    public Evaluable getQ1() {
        return q1;
    }

    @Override
    public void setQ2(Evaluable q2) {
        this.q2 = Objects.requireNonNull(q2);
    }

    @Override
    public Evaluable getQ2() {
        return q2;
    }

    @Override
    public void setI1(Evaluable i1) {
        this.i1 = Objects.requireNonNull(i1);
    }

    @Override
    public Evaluable getI1() {
        return i1;
    }

    @Override
    public void setI2(Evaluable i2) {
        this.i2 = Objects.requireNonNull(i2);
    }

    @Override
    public Evaluable getI2() {
        return i2;
    }

    @Override
    public BranchResult createBranchResult(double preContingencyP1, double branchInContingencyP1) {
        double flowTransfer = Double.NaN;
        if (!Double.isNaN(preContingencyP1) && !Double.isNaN(branchInContingencyP1)) {
            flowTransfer = (p1.eval() * PerUnit.SB - preContingencyP1) / branchInContingencyP1;
        }
        double currentScale1 = PerUnit.ib(branch.getTerminal1().getVoltageLevel().getNominalV());
        double currentScale2 = PerUnit.ib(branch.getTerminal2().getVoltageLevel().getNominalV());
        return new BranchResult(getId(), p1.eval() * PerUnit.SB, q1.eval() * PerUnit.SB, currentScale1 * i1.eval(),
                                p2.eval() * PerUnit.SB, q2.eval() * PerUnit.SB, currentScale2 * i2.eval(), flowTransfer);
    }

    @Override
    public List<LfLimit> getLimits1(final LimitType type) {
        switch (type) {
            case ACTIVE_POWER:
                return getLimits1(type, branch.getActivePowerLimits1());
            case APPARENT_POWER:
                return getLimits1(type, branch.getApparentPowerLimits1());
            case CURRENT:
                return getLimits1(type, branch.getCurrentLimits1());
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public List<LfLimit> getLimits2(final LimitType type) {
        switch (type) {
            case ACTIVE_POWER:
                return getLimits2(type, branch.getActivePowerLimits2());
            case APPARENT_POWER:
                return getLimits2(type, branch.getApparentPowerLimits2());
            case CURRENT:
                return getLimits2(type, branch.getCurrentLimits2());
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public void updateState(boolean phaseShifterRegulationOn, boolean isTransformerVoltageControlOn) {
        branch.getTerminal1().setP(p1.eval() * PerUnit.SB);
        branch.getTerminal1().setQ(q1.eval() * PerUnit.SB);
        branch.getTerminal2().setP(p2.eval() * PerUnit.SB);
        branch.getTerminal2().setQ(q2.eval() * PerUnit.SB);

        if (phaseShifterRegulationOn && isPhaseController()) {
            // it means there is a regulating phase tap changer located on that branch
            updateTapPosition(((TwoWindingsTransformer) branch).getPhaseTapChanger());
            // check if the target value deadband is respected
            checkTargetDeadband(discretePhaseControl.getControlledSide() == DiscretePhaseControl.ControlledSide.ONE ? p1.eval() : p2.eval());
        }

        if (isTransformerVoltageControlOn && isVoltageController()) { // it means there is a regulating ratio tap changer
            TwoWindingsTransformer twt = (TwoWindingsTransformer) branch;
            RatioTapChanger rtc = twt.getRatioTapChanger();
            double baseRatio = Transformers.getRatioPerUnitBase(twt);
            double rho = getPiModel().getR1() * twt.getRatedU1() / twt.getRatedU2() * baseRatio;
            double ptcRho = twt.getPhaseTapChanger() != null ? twt.getPhaseTapChanger().getCurrentStep().getRho() : 1;
            updateTapPosition(rtc, ptcRho, rho);
            checkTargetDeadband(rtc);
        }
    }
}
