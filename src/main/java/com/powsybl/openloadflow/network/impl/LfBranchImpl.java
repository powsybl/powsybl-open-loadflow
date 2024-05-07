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
import com.powsybl.iidm.network.extensions.LineFortescue;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerFortescue;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.extensions.*;
import com.powsybl.openloadflow.network.extensions.iidm.LineAsymmetrical;
import com.powsybl.openloadflow.network.extensions.iidm.StepWindingConnectionType;
import com.powsybl.openloadflow.network.extensions.iidm.Tfo3Phases;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.results.BranchResult;
import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfBranchImpl extends AbstractImpedantLfBranch {

    private final Ref<Branch<?>> branchRef;

    protected LfBranchImpl(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel, Branch<?> branch, LfNetworkParameters parameters) {
        super(network, bus1, bus2, piModel, parameters);
        this.branchRef = Ref.create(branch, parameters.isCacheEnabled());
    }

    private static void createLineAsym(Line line, double zb, PiModel piModel, LfBranchImpl lfBranch) {
        var extension = line.getExtension(LineFortescue.class);
        var extension2 = line.getExtension(LineAsymmetrical.class);
        if (extension != null && extension2 != null) {
            boolean openPhaseA = extension.isOpenPhaseA();
            boolean openPhaseB = extension.isOpenPhaseB();
            boolean openPhaseC = extension.isOpenPhaseC();
            LfAsymLine asymLine;
            if (extension2.getYabc() != null) {
                // the prioritized option is to use data from ABC three phase admittance matrix
                ComplexMatrix yabcPu = extension2.getYabc().scale(zb);
                boolean isBus1FortescueRepresented = true;
                boolean isBus2FortescueRepresented = true;

                AsymBusVariableType bus1VariableType = AsymBusVariableType.WYE;
                AsymBusVariableType bus2VariableType = AsymBusVariableType.WYE;

                boolean hasPhaseA1 = true;
                boolean hasPhaseB1 = true;
                boolean hasPhaseC1 = true;
                boolean hasPhaseA2 = true;
                boolean hasPhaseB2 = true;
                boolean hasPhaseC2 = true;

                LfAsymBus asymBus1 = lfBranch.getBus1().getAsym();
                LfAsymBus asymBus2 = lfBranch.getBus2().getAsym();

                if (asymBus1 != null) {
                    isBus1FortescueRepresented = asymBus1.isFortescueRepresentation();
                    hasPhaseA1 = asymBus1.isHasPhaseA();
                    hasPhaseB1 = asymBus1.isHasPhaseB();
                    hasPhaseC1 = asymBus1.isHasPhaseC();
                    bus1VariableType = asymBus1.getAsymBusVariableType();
                }
                if (asymBus2 != null) {
                    isBus2FortescueRepresented = asymBus2.isFortescueRepresentation();
                    hasPhaseA2 = asymBus2.isHasPhaseA();
                    hasPhaseB2 = asymBus2.isHasPhaseB();
                    hasPhaseC2 = asymBus2.isHasPhaseC();
                    bus1VariableType = asymBus2.getAsymBusVariableType();
                }

                asymLine = new LfAsymLine(yabcPu, openPhaseA, openPhaseB, openPhaseC, isBus1FortescueRepresented, isBus2FortescueRepresented,
                        hasPhaseA1, hasPhaseB1, hasPhaseC1, hasPhaseA2, hasPhaseB2, hasPhaseC2, bus1VariableType, bus2VariableType);
            } else {
                // last option, use the Pi models of the Fortescue sequences
                // this means that nodes should be Fortescue represented with WYE variables
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
                asymLine = new LfAsymLine(piZeroComponent, piPositiveComponent, piNegativeComponent,
                        openPhaseA, openPhaseB, openPhaseC, AsymBusVariableType.WYE, AsymBusVariableType.WYE);
            }

            lfBranch.setAsymLine(asymLine);
        }
    }

    private static LfBranchImpl createLine(Line line, LfNetwork network, LfBus bus1, LfBus bus2, LfNetworkParameters parameters) {
        double r1;
        double r;
        double x;
        double g1;
        double b1;
        double g2;
        double b2;
        double zb;
        if (parameters.getLinePerUnitMode() == LinePerUnitMode.RATIO) {
            double nominalV2 = line.getTerminal2().getVoltageLevel().getNominalV();
            zb = PerUnit.zb(nominalV2);
            r1 = 1 / Transformers.getRatioPerUnitBase(line);
            r = line.getR() / zb;
            x = line.getX() / zb;
            g1 = line.getG1() * zb;
            g2 = line.getG2() * zb;
            b1 = line.getB1() * zb;
            b2 = line.getB2() * zb;
        } else { // IMPEDANCE
            r1 = 1;
            double zSquare = line.getR() * line.getR() + line.getX() * line.getX();
            if (zSquare == 0) {
                r = 0;
                x = 0;
                g1 = 0;
                b1 = 0;
                g2 = 0;
                b2 = 0;
                zb = 0;
            } else {
                double nominalV1 = line.getTerminal1().getVoltageLevel().getNominalV();
                double nominalV2 = line.getTerminal2().getVoltageLevel().getNominalV();
                double g = line.getR() / zSquare;
                double b = -line.getX() / zSquare;
                zb = nominalV1 * nominalV2 / PerUnit.SB;
                r = line.getR() / zb;
                x = line.getX() / zb;
                g1 = (line.getG1() * nominalV1 * nominalV1 + g * nominalV1 * (nominalV1 - nominalV2)) / PerUnit.SB;
                b1 = (line.getB1() * nominalV1 * nominalV1 + b * nominalV1 * (nominalV1 - nominalV2)) / PerUnit.SB;
                g2 = (line.getG2() * nominalV2 * nominalV2 + g * nominalV2 * (nominalV2 - nominalV1)) / PerUnit.SB;
                b2 = (line.getB2() * nominalV2 * nominalV2 + b * nominalV2 * (nominalV2 - nominalV1)) / PerUnit.SB;
            }
        }

        PiModel piModel = new SimplePiModel()
                .setR1(r1)
                .setR(r)
                .setX(x)
                .setG1(g1)
                .setG2(g2)
                .setB1(b1)
                .setB2(b2);

        LfBranchImpl lfBranchImpl = new LfBranchImpl(network, bus1, bus2, piModel, line, parameters);
        if (parameters.isAsymmetrical()) {
            createLineAsym(line, zb, piModel, lfBranchImpl);
        }
        return lfBranchImpl;
    }

    private static void createTransfoToAsym(TwoWindingsTransformer t2w, double zb, LfBranchImpl lfBranch) {
        var extension = t2w.getExtension(TwoWindingsTransformerFortescue.class);
        if (extension != null) {
            var extension2 = t2w.getExtension(Tfo3Phases.class);
            if (extension2 != null) {
                // We check in priority the most detailed way to describe a Three Phase transformer
                double vTfoBase1 = t2w.getRatedU1();
                double vTfoBase2 = t2w.getRatedU2();

                WindingConnectionType leg1Type = extension.getConnectionType1();
                WindingConnectionType leg2Type = extension.getConnectionType2();

                Complex rho = Complex.ONE.multiply(vTfoBase2 / vTfoBase1);

                Complex zG1pu = new Complex(extension.getGroundingR1(), extension.getGroundingX1());
                Complex zG2pu = new Complex(extension.getGroundingR2(), extension.getGroundingX2());

                List<Boolean> connectionList = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    connectionList.add(true);
                }
                if (extension2.getOpenPhaseA1().equals(Boolean.TRUE)) {
                    connectionList.set(0, false);
                }
                if (extension2.getOpenPhaseB1().equals(Boolean.TRUE)) {
                    connectionList.set(1, false);
                }
                if (extension2.getOpenPhaseC1().equals(Boolean.TRUE)) {
                    connectionList.set(2, false);
                }

                StepWindingConnectionType stepWindingConnectionType = extension2.getStepWindingConnectionType();
                StepType stepLegConnectionType;
                if (stepWindingConnectionType == StepWindingConnectionType.STEP_UP) {
                    stepLegConnectionType = StepType.STEP_UP;
                } else if (stepWindingConnectionType == StepWindingConnectionType.DEFAULT) {
                    stepLegConnectionType = StepType.DEFAULT;
                } else if (stepWindingConnectionType == StepWindingConnectionType.STEP_DOWN) {
                    stepLegConnectionType = StepType.STEP_DOWN;
                } else {
                    stepLegConnectionType = StepType.NONE;
                }

                AsymBusVariableType side1VariableType = AsymBusVariableType.WYE;
                AsymBusVariableType side2VariableType = AsymBusVariableType.WYE;

                LfAsymBus asymBus1 = lfBranch.getBus1().getAsym();
                LfAsymBus asymBus2 = lfBranch.getBus2().getAsym();

                if (asymBus1 != null) {
                    side1VariableType = asymBus1.getAsymBusVariableType();
                }
                if (asymBus2 != null) {
                    side2VariableType = asymBus2.getAsymBusVariableType();
                }

                AsymThreePhaseTransfo asym3phaseTfo = new AsymThreePhaseTransfo(leg1Type, leg2Type, stepLegConnectionType,
                        extension2.getYa(), extension2.getYb(), extension2.getYc(), rho, zG1pu, zG2pu, connectionList);

                DenseMatrix yabcReal = asym3phaseTfo.getYabc();

                double vNom1 = t2w.getTerminal(TwoSides.ONE).getVoltageLevel().getNominalV();
                double vNom2 = t2w.getTerminal(TwoSides.TWO).getVoltageLevel().getNominalV();

                double iBase1 = 100. / vNom1;
                double iBase2 = 100. / vNom2;
                Complex iBase1Complex = new Complex(1 / iBase1, 0.);
                Complex iBase2Complex = new Complex(1 / iBase2, 0.);

                ComplexMatrix mIbasePu = new ComplexMatrix(6, 6);
                mIbasePu.set(1, 1, iBase1Complex);
                mIbasePu.set(2, 2, iBase1Complex);
                mIbasePu.set(3, 3, iBase1Complex);
                mIbasePu.set(4, 4, iBase2Complex);
                mIbasePu.set(5, 5, iBase2Complex);
                mIbasePu.set(6, 6, iBase2Complex);

                ComplexMatrix mVbasePu = new ComplexMatrix(6, 6);
                Complex vNom1Complex = new Complex(vNom1, 0.);
                Complex vNom2Complex = new Complex(vNom2, 0.);
                mVbasePu.set(1, 1, vNom1Complex);
                mVbasePu.set(2, 2, vNom1Complex);
                mVbasePu.set(3, 3, vNom1Complex);
                mVbasePu.set(4, 4, vNom2Complex);
                mVbasePu.set(5, 5, vNom2Complex);
                mVbasePu.set(6, 6, vNom2Complex);

                ComplexMatrix yabcPu = ComplexMatrix.fromRealCartesian(mIbasePu.toRealCartesianMatrix().times(yabcReal.times(mVbasePu.toRealCartesianMatrix())));
                LfAsymLine asymBranch = new LfAsymLine(yabcPu, extension2.getOpenPhaseA1(), extension2.getOpenPhaseB1(), extension2.getOpenPhaseC1(),
                        true, true, true, true, true, true, true, true, side1VariableType, side2VariableType);

                lfBranch.setAsymLine(asymBranch);

            } else {
                AsymTransfo2W asymTransfo2W;
                var extensionIidm = t2w.getExtension(TwoWindingsTransformerFortescue.class);
                if (extensionIidm != null) {
                    double rz = extensionIidm.getRz() / zb;
                    double xz = extensionIidm.getXz() / zb;
                    asymTransfo2W = new AsymTransfo2W(extension.getConnectionType1(), extension.getConnectionType2(), new Complex(rz, xz), extension.isFreeFluxes(),
                            new Complex(extension.getGroundingR1() / zb, extension.getGroundingX1() / zb),
                            new Complex(extension.getGroundingR2() / zb, extension.getGroundingX2() / zb));
                } else {
                    throw new PowsyblException("Asymmetrical branch '" + lfBranch.getId() + "' has no assymmetrical Pi values input data defined");
                }
                lfBranch.setProperty(AsymTransfo2W.PROPERTY_ASYMMETRICAL, asymTransfo2W);
            }

        }
    }

    private static LfBranchImpl createTransformer(TwoWindingsTransformer twt, LfNetwork network, LfBus bus1, LfBus bus2,
                                                  boolean retainPtc, boolean retainRtc, LfNetworkParameters parameters) {
        PiModel piModel = null;

        double baseRatio = Transformers.getRatioPerUnitBase(twt);
        double nominalV2 = twt.getTerminal2().getVoltageLevel().getNominalV();
        double zb = PerUnit.zb(nominalV2);

        PhaseTapChanger ptc = twt.getPhaseTapChanger();
        if (ptc != null
                && (ptc.isRegulating()
                && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP || retainPtc)) {
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
        if (rtc != null && (rtc.isRegulating() && rtc.hasLoadTapChangingCapabilities() || retainRtc)) {
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

        LfBranchImpl lfBranch = new LfBranchImpl(network, bus1, bus2, piModel, twt, parameters);
        if (parameters.isAsymmetrical()) {
            createTransfoToAsym(twt, zb, lfBranch);
        }
        return lfBranch;
    }

    public static LfBranchImpl create(Branch<?> branch, LfNetwork network, LfBus bus1, LfBus bus2, LfTopoConfig topoConfig,
                                      LfNetworkParameters parameters) {
        Objects.requireNonNull(branch);
        Objects.requireNonNull(network);
        Objects.requireNonNull(topoConfig);
        Objects.requireNonNull(parameters);
        LfBranchImpl lfBranch;
        if (branch instanceof Line line) {
            lfBranch = createLine(line, network, bus1, bus2, parameters);
        } else if (branch instanceof TwoWindingsTransformer twt) {
            lfBranch = createTransformer(twt, network, bus1, bus2, topoConfig.isRetainedPtc(twt.getId()),
                    topoConfig.isRetainedRtc(twt.getId()), parameters);
        } else {
            throw new PowsyblException("Unsupported type of branch for flow equations of branch: " + branch.getId());
        }
        if (bus1 != null && topoConfig.getBranchIdsOpenableSide1().contains(lfBranch.getId())) {
            lfBranch.setDisconnectionAllowedSide1(true);
        }
        if (bus2 != null && topoConfig.getBranchIdsOpenableSide2().contains(lfBranch.getId())) {
            lfBranch.setDisconnectionAllowedSide2(true);
        }
        return lfBranch;
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
    public List<BranchResult> createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1, boolean createExtension) {
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
            branchResult.addExtension(OlfBranchResult.class, new OlfBranchResult(piModel.getR1(), piModel.getContinuousR1(),
                    getV1() * branch.getTerminal1().getVoltageLevel().getNominalV(),
                    getV2() * branch.getTerminal2().getVoltageLevel().getNominalV(),
                    Math.toDegrees(getAngle1()),
                    Math.toDegrees(getAngle2())));
        }
        return List.of(branchResult);
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

        if (parameters.isTransformerVoltageControlOn() && isVoltageController()
                || parameters.isTransformerReactivePowerControlOn() && isTransformerReactivePowerController()) { // it means there is a regulating ratio tap changer
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
