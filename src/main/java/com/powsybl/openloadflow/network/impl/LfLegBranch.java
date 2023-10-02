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
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.ThreeWindingsTransformerResult;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfLegBranch extends AbstractImpedantLfBranch {

    private final Ref<ThreeWindingsTransformer> twtRef;

    private final Ref<ThreeWindingsTransformer.Leg> legRef;

    private LfLegBranch(LfNetwork network, LfBus bus1, LfBus bus0, PiModel piModel, ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg,
                        LfNetworkParameters parameters) {
        super(network, bus1, bus0, piModel, parameters);
        this.twtRef = Ref.create(twt, parameters.isCacheEnabled());
        this.legRef = Ref.create(leg, parameters.isCacheEnabled());
    }

    private ThreeWindingsTransformer getTwt() {
        return twtRef.get();
    }

    private ThreeWindingsTransformer.Leg getLeg() {
        return legRef.get();
    }

    public static LfLegBranch create(LfNetwork network, LfBus bus1, LfBus bus0, ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg,
                                     LfNetworkParameters parameters) {
        Objects.requireNonNull(bus0);
        Objects.requireNonNull(twt);
        Objects.requireNonNull(leg);
        Objects.requireNonNull(parameters);

        PiModel piModel = null;

        double zb = PerUnit.zb(twt.getRatedU0());
        double baseRatio = Transformers.getRatioPerUnitBase(leg, twt);
        PhaseTapChanger ptc = leg.getPhaseTapChanger();
        if (ptc != null
                && ptc.isRegulating()
                && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP) {
            // we have a phase control, whatever we also have a voltage control or not, we create a pi model array
            // based on phase taps mixed with voltage current tap
            Integer rtcPosition = Transformers.getCurrentPosition(leg.getRatioTapChanger());
            List<PiModel> models = new ArrayList<>();
            for (int ptcPosition = ptc.getLowTapPosition(); ptcPosition <= ptc.getHighTapPosition(); ptcPosition++) {
                Transformers.TapCharacteristics tapCharacteristics = Transformers.getTapCharacteristics(twt, leg, rtcPosition, ptcPosition);
                models.add(Transformers.createPiModel(tapCharacteristics, zb, baseRatio, parameters.isTwtSplitShuntAdmittance()));
            }
            piModel = new PiModelArray(models, ptc.getLowTapPosition(), ptc.getTapPosition());
        }

        RatioTapChanger rtc = leg.getRatioTapChanger();
        if (rtc != null && rtc.isRegulating() && rtc.hasLoadTapChangingCapabilities()) {
            if (piModel == null) {
                // we have a voltage control, we create a pi model array based on voltage taps mixed with phase current
                // tap
                Integer ptcPosition = Transformers.getCurrentPosition(leg.getPhaseTapChanger());
                List<PiModel> models = new ArrayList<>();
                for (int rtcPosition = rtc.getLowTapPosition(); rtcPosition <= rtc.getHighTapPosition(); rtcPosition++) {
                    Transformers.TapCharacteristics tapCharacteristics = Transformers.getTapCharacteristics(twt, leg, rtcPosition, ptcPosition);
                    models.add(Transformers.createPiModel(tapCharacteristics, zb, baseRatio, parameters.isTwtSplitShuntAdmittance()));
                }
                piModel = new PiModelArray(models, rtc.getLowTapPosition(), rtc.getTapPosition());
            } else {
                throw new PowsyblException("Unsupported type of branch for voltage and phase controls of branch: " + twt.getId());
            }
        }

        if (piModel == null) {
            // we don't have any phase or voltage control, we create a simple pi model (single tap) based on phase current
            // tap and voltage current tap
            Transformers.TapCharacteristics tapCharacteristics = Transformers.getTapCharacteristics(twt, leg);
            piModel = Transformers.createPiModel(tapCharacteristics, zb, baseRatio, parameters.isTwtSplitShuntAdmittance());
        }

        return new LfLegBranch(network, bus1, bus0, piModel, twt, leg, parameters);
    }

    private int getLegNum() {
        var twt = getTwt();
        var leg = getLeg();
        if (leg == twt.getLeg1()) {
            return 1;
        } else if (leg == twt.getLeg2()) {
            return 2;
        } else {
            return 3;
        }
    }

    public static String getId(String twtId, int legNum) {
        return twtId + "_leg_" + legNum;
    }

    public static String getId(ThreeWindingsTransformer.Side side, String transformerId) {
        int legNum = switch (side) {
            case ONE -> 1;
            case TWO -> 2;
            case THREE -> 3;
        };
        return getId(transformerId, legNum);
    }

    @Override
    public String getId() {
        return getId(getTwt().getId(), getLegNum());
    }

    @Override
    public BranchType getBranchType() {
        var twt = getTwt();
        var leg = getLeg();
        if (leg == twt.getLeg1()) {
            return BranchType.TRANSFO_3_LEG_1;
        } else if (leg == twt.getLeg2()) {
            return BranchType.TRANSFO_3_LEG_2;
        } else {
            return BranchType.TRANSFO_3_LEG_3;
        }
    }

    @Override
    public List<String> getOriginalIds() {
        return List.of(getTwt().getId());
    }

    @Override
    public boolean hasPhaseControllerCapability() {
        return getLeg().getPhaseTapChanger() != null;
    }

    @Override
    public BranchResult createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1, boolean createExtension) {
        throw new PowsyblException("Unsupported type of branch for branch result: " + getId());
    }

    @Override
    public List<LfLimit> getLimits1(final LimitType type) {
        var leg = getLeg();
        switch (type) {
            case ACTIVE_POWER:
                return getLimits1(type, leg.getActivePowerLimits().orElse(null));
            case APPARENT_POWER:
                return getLimits1(type, leg.getApparentPowerLimits().orElse(null));
            case CURRENT:
                return getLimits1(type, leg.getCurrentLimits().orElse(null));
            case VOLTAGE:
            default:
                throw new UnsupportedOperationException(String.format("Getting %s limits is not supported.", type.name()));
        }
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var twt = getTwt();
        var leg = getLeg();

        updateFlows(p1.eval(), q1.eval(), Double.NaN, Double.NaN);

        if (parameters.isPhaseShifterRegulationOn() && isPhaseController()) {
            // it means there is a regulating phase tap changer located on that leg
            updateTapPosition(leg.getPhaseTapChanger());
        }

        if (parameters.isTransformerVoltageControlOn() && isVoltageController()) { // it means there is a regulating ratio tap changer
            RatioTapChanger rtc = leg.getRatioTapChanger();
            double baseRatio = Transformers.getRatioPerUnitBase(leg, twt);
            double rho = getPiModel().getR1() * leg.getRatedU() / twt.getRatedU0() * baseRatio;
            double ptcRho = leg.getPhaseTapChanger() != null ? leg.getPhaseTapChanger().getCurrentStep().getRho() : 1;
            updateTapPosition(rtc, ptcRho, rho);
        }
    }

    @Override
    public void updateFlows(double p1, double q1, double p2, double q2) {
        // Star bus is always on side 2.
        getLeg().getTerminal().setP(p1 * PerUnit.SB)
                .setQ(q1 * PerUnit.SB);
    }

    public static ThreeWindingsTransformerResult createThreeWindingsTransformerResult(LfNetwork network, String threeWindingsTransformerId) {
        LfLegBranch leg1 = (LfLegBranch) network.getBranchById(LfLegBranch.getId(threeWindingsTransformerId, 1));
        LfLegBranch leg2 = (LfLegBranch) network.getBranchById(LfLegBranch.getId(threeWindingsTransformerId, 2));
        LfLegBranch leg3 = (LfLegBranch) network.getBranchById(LfLegBranch.getId(threeWindingsTransformerId, 3));

        double i1Base = leg1.isConnectedAtBothSides() ? PerUnit.ib(leg1.getBus1().getNominalV()) : PerUnit.ib(leg1.legRef.get().getTerminal().getVoltageLevel().getNominalV());
        double i2Base = leg2.isConnectedAtBothSides() ? PerUnit.ib(leg2.getBus1().getNominalV()) : PerUnit.ib(leg2.legRef.get().getTerminal().getVoltageLevel().getNominalV());
        double i3Base = leg3.isConnectedAtBothSides() ? PerUnit.ib(leg3.getBus1().getNominalV()) : PerUnit.ib(leg3.legRef.get().getTerminal().getVoltageLevel().getNominalV());
        return new ThreeWindingsTransformerResult(threeWindingsTransformerId,
                leg1.getP1().eval() * PerUnit.SB, leg1.getQ1().eval() * PerUnit.SB, leg1.getI1().eval() * i1Base,
                leg2.getP1().eval() * PerUnit.SB, leg2.getQ1().eval() * PerUnit.SB, leg2.getI1().eval() * i2Base,
                leg3.getP1().eval() * PerUnit.SB, leg3.getQ1().eval() * PerUnit.SB, leg3.getI1().eval() * i3Base);
    }
}
