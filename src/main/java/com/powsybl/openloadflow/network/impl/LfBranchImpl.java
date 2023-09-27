/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.LineFortescue;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.LfAsymLine;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.results.BranchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBranchImpl extends AbstractImpedantLfBranch {

    private final Ref<Branch<?>> branchRef;

    protected LfBranchImpl(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel, Branch<?> branch, LfNetworkParameters parameters) {
        super(network, bus1, bus2, piModel, parameters);
        this.branchRef = Ref.create(branch, parameters.isCacheEnabled());
    }

    private static void createLineAsym(Line line, double zb, PiModel piModel, LfBranchImpl lfBranch) {
        var extension = line.getExtension(LineFortescue.class);
        if (extension != null) {
            boolean openPhaseA = extension.isOpenPhaseA();
            boolean openPhaseB = extension.isOpenPhaseB();
            boolean openPhaseC = extension.isOpenPhaseC();
            double rz = extension.getRz();
            double xz = extension.getXz();
            SimplePiModel piZeroComponent = new SimplePiModel()
                    .setR(rz / zb)
                    .setX(xz / zb);
            SimplePiModel piPositiveComponent = new SimplePiModel()
                    .setR(piModel.getR())
                    .setX(piModel.getX());
            SimplePiModel piNegativeComponent = new SimplePiModel()
                    .setR(piModel.getR())
                    .setX(piModel.getX());
            lfBranch.setAsymLine(new LfAsymLine(piZeroComponent, piPositiveComponent, piNegativeComponent,
                                                openPhaseA, openPhaseB, openPhaseC));
        }
    }

    private static LfBranchImpl createLine(Line line, LfNetwork network, LfBus bus1, LfBus bus2, double zb, LfNetworkParameters parameters) {
        PiModel piModel = new SimplePiModel()
                .setR1(1 / Transformers.getRatioPerUnitBase(line))
                .setR(line.getR() / zb)
                .setX(line.getX() / zb)
                .setG1(line.getG1() * zb)
                .setG2(line.getG2() * zb)
                .setB1(line.getB1() * zb)
                .setB2(line.getB2() * zb);

        LfBranchImpl lfBranch = new LfBranchImpl(network, bus1, bus2, piModel, line, parameters);
        if (parameters.isAsymmetrical()) {
            createLineAsym(line, zb, piModel, lfBranch);
        }
        return lfBranch;
    }

    private static LfBranchImpl createTransformer(TwoWindingsTransformer twt, LfNetwork network, LfBus bus1, LfBus bus2, double zb,
                                                  boolean retain, LfNetworkParameters parameters) {
        PiModel piModel = null;

        double baseRatio = Transformers.getRatioPerUnitBase(twt);

        PhaseTapChanger ptc = twt.getPhaseTapChanger();
        if (ptc != null
                && (ptc.isRegulating()
                && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP || retain)) {
            // we have a phase control, whatever we also have a voltage control or not, we create a pi model array
            // based on phase taps mixed with voltage current tap
            Integer rtcPosition = Transformers.getCurrentPosition(twt.getRatioTapChanger());
            List<PiModel> models = new ArrayList<>();
            for (int ptcPosition = ptc.getLowTapPosition(); ptcPosition <= ptc.getHighTapPosition(); ptcPosition++) {
                Transformers.TapCharacteristics tapCharacteristics = Transformers.getTapCharacteristics(twt, rtcPosition, ptcPosition);
                models.add(Transformers.createPiModel(tapCharacteristics, zb, baseRatio, parameters.isTwtSplitShuntAdmittance()));
            }
            piModel = new PiModelArray(models, ptc.getLowTapPosition(), ptc.getTapPosition());
        }

        RatioTapChanger rtc = twt.getRatioTapChanger();
        if (rtc != null && (rtc.isRegulating() && rtc.hasLoadTapChangingCapabilities() || retain)) {
            if (piModel == null) {
                // we have a voltage control, we create a pi model array based on voltage taps mixed with phase current
                // tap
                Integer ptcPosition = Transformers.getCurrentPosition(twt.getPhaseTapChanger());
                List<PiModel> models = new ArrayList<>();
                for (int rtcPosition = rtc.getLowTapPosition(); rtcPosition <= rtc.getHighTapPosition(); rtcPosition++) {
                    Transformers.TapCharacteristics tapCharacteristics = Transformers.getTapCharacteristics(twt, rtcPosition, ptcPosition);
                    models.add(Transformers.createPiModel(tapCharacteristics, zb, baseRatio, parameters.isTwtSplitShuntAdmittance()));
                }
                piModel = new PiModelArray(models, rtc.getLowTapPosition(), rtc.getTapPosition());
            } else {
                throw new PowsyblException("Voltage and phase control on same branch '" + twt.getId() + "' is not yet supported");
            }
        }

        if (piModel == null) {
            // we don't have any phase or voltage control, we create a simple pi model (single tap) based on phase current
            // tap and voltage current tap
            Transformers.TapCharacteristics tapCharacteristics = Transformers.getTapCharacteristics(twt);
            piModel = Transformers.createPiModel(tapCharacteristics, zb, baseRatio, parameters.isTwtSplitShuntAdmittance());
        }

        return new LfBranchImpl(network, bus1, bus2, piModel, twt, parameters);
    }

    public static LfBranchImpl create(Branch<?> branch, LfNetwork network, LfBus bus1, LfBus bus2, List<RatioTapChangerHolder> rtcToOperate,
                                      List<PhaseTapChangerHolder> pstToOperate, LfNetworkParameters parameters) {
        Objects.requireNonNull(branch);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        double nominalV2 = branch.getTerminal2().getVoltageLevel().getNominalV();
        double zb = PerUnit.zb(nominalV2);
        if (branch instanceof Line line) {
            return createLine(line, network, bus1, bus2, zb, parameters);
        } else if (branch instanceof TwoWindingsTransformer twt) {
            if (rtcToOperate.contains(twt) || pstToOperate.contains(twt)) {
                return createTransformer(twt, network, bus1, bus2, zb, true, parameters);
            } else {
                return createTransformer(twt, network, bus1, bus2, zb, false, parameters);
            }
        } else {
            throw new PowsyblException("Unsupported type of branch for flow equations of branch: " + branch.getId());
        }
    }

    private Branch<?> getBranch() {
        return branchRef.get();
    }

    @Override
    public String getId() {
        return getBranch().getId();
    }

    @Override
    public BranchType getBranchType() {
        return getBranch() instanceof Line ? BranchType.LINE : BranchType.TRANSFO_2;
    }

    @Override
    public boolean hasPhaseControllerCapability() {
        var branch = getBranch();
        return branch.getType() == IdentifiableType.TWO_WINDINGS_TRANSFORMER
                && ((TwoWindingsTransformer) branch).getPhaseTapChanger() != null;
    }

    @Override
    public BranchResult createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1, boolean createExtension) {
        var branch = getBranch();
        double flowTransfer = Double.NaN;
        if (!Double.isNaN(preContingencyBranchP1) && !Double.isNaN(preContingencyBranchOfContingencyP1)) {
            flowTransfer = (p1.eval() * PerUnit.SB - preContingencyBranchP1) / preContingencyBranchOfContingencyP1;
        }
        double currentScale1 = PerUnit.ib(branch.getTerminal1().getVoltageLevel().getNominalV());
        double currentScale2 = PerUnit.ib(branch.getTerminal2().getVoltageLevel().getNominalV());
        var branchResult = new BranchResult(getId(), p1.eval() * PerUnit.SB, q1.eval() * PerUnit.SB, currentScale1 * i1.eval(),
                                            p2.eval() * PerUnit.SB, q2.eval() * PerUnit.SB, currentScale2 * i2.eval(), flowTransfer);
        if (createExtension) {
            branchResult.addExtension(OlfBranchResult.class, new OlfBranchResult(piModel.getR1(), piModel.getContinuousR1()));
        }
        return branchResult;
    }

    @Override
    public List<LfLimit> getLimits1(final LimitType type) {
        var branch = getBranch();
        switch (type) {
            case ACTIVE_POWER:
                return getLimits1(type, branch.getActivePowerLimits1().orElse(null));
            case APPARENT_POWER:
                return getLimits1(type, branch.getApparentPowerLimits1().orElse(null));
            case CURRENT:
                return getLimits1(type, branch.getCurrentLimits1().orElse(null));
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public List<LfLimit> getLimits2(final LimitType type) {
        var branch = getBranch();
        switch (type) {
            case ACTIVE_POWER:
                return getLimits2(type, branch.getActivePowerLimits2().orElse(null));
            case APPARENT_POWER:
                return getLimits2(type, branch.getApparentPowerLimits2().orElse(null));
            case CURRENT:
                return getLimits2(type, branch.getCurrentLimits2().orElse(null));
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var branch = getBranch();

        updateFlows(p1.eval(), q1.eval(), p2.eval(), q2.eval());

        if (parameters.isPhaseShifterRegulationOn() && isPhaseController()) {
            // it means there is a regulating phase tap changer located on that branch
            updateTapPosition(((TwoWindingsTransformer) branch).getPhaseTapChanger());
        }

        if (parameters.isTransformerVoltageControlOn() && isVoltageController()) { // it means there is a regulating ratio tap changer
            TwoWindingsTransformer twt = (TwoWindingsTransformer) branch;
            RatioTapChanger rtc = twt.getRatioTapChanger();
            double baseRatio = Transformers.getRatioPerUnitBase(twt);
            double rho = getPiModel().getR1() * twt.getRatedU1() / twt.getRatedU2() * baseRatio;
            double ptcRho = twt.getPhaseTapChanger() != null ? twt.getPhaseTapChanger().getCurrentStep().getRho() : 1;
            updateTapPosition(rtc, ptcRho, rho);
        }
    }

    @Override
    public void updateFlows(double p1, double q1, double p2, double q2) {
        var branch = getBranch();
        branch.getTerminal1().setP(p1 * PerUnit.SB)
                .setQ(q1 * PerUnit.SB);
        branch.getTerminal2().setP(p2 * PerUnit.SB)
                .setQ(q2 * PerUnit.SB);
    }
}
