/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfLegBranch extends AbstractLfBranch {

    private final ThreeWindingsTransformer twt;

    private final ThreeWindingsTransformer.Leg leg;

    private Evaluable p = NAN;

    private Evaluable q = NAN;

    protected LfLegBranch(LfBus bus1, LfBus bus0, PiModel piModel, ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg) {
        super(bus1, bus0, piModel);
        this.twt = twt;
        this.leg = leg;
    }

    public static LfLegBranch create(LfBus bus1, LfBus bus0, ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg,
                                     boolean twtSplitShuntAdmittance) {
        Objects.requireNonNull(bus0);
        Objects.requireNonNull(twt);
        Objects.requireNonNull(leg);

        PiModel piModel;

        double nominalV1 = leg.getTerminal().getVoltageLevel().getNominalV();
        double nominalV2 = twt.getRatedU0();
        double zb = nominalV2 * nominalV2 / PerUnit.SB;
        PhaseTapChanger ptc = leg.getPhaseTapChanger();
        if (ptc != null
                && ptc.isRegulating()
                && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP) {

            Integer rtcPosition = Transformers.getCurrentPosition(leg.getRatioTapChanger());
            List<PiModel> models = new ArrayList<>();
            for (int ptcPosition = ptc.getLowTapPosition(); ptcPosition <= ptc.getHighTapPosition(); ptcPosition++) {
                double r = Transformers.getR(leg, rtcPosition, ptcPosition) / zb;
                double x = Transformers.getX(leg, rtcPosition, ptcPosition) / zb;
                double g1 = Transformers.getG1(leg, rtcPosition, ptcPosition, twtSplitShuntAdmittance) * zb;
                double g2 = twtSplitShuntAdmittance ? g1 : 0;
                double b1 = Transformers.getB1(leg, rtcPosition, ptcPosition, twtSplitShuntAdmittance) * zb;
                double b2 = twtSplitShuntAdmittance ? b1 : 0;
                double r1 = Transformers.getRatioLeg(twt, leg) / nominalV2 * nominalV1;
                double a1 = Transformers.getAngleLeg(leg, ptcPosition);
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

        } else {
            piModel = new SimplePiModel()
                    .setR(Transformers.getR(leg) / zb)
                    .setX(Transformers.getX(leg) / zb)
                    .setG1(Transformers.getG1(leg, twtSplitShuntAdmittance) * zb)
                    .setG2(twtSplitShuntAdmittance ? Transformers.getG1(leg, twtSplitShuntAdmittance) * zb : 0)
                    .setB1(Transformers.getB1(leg, twtSplitShuntAdmittance) * zb)
                    .setB2(twtSplitShuntAdmittance ? Transformers.getB1(leg, twtSplitShuntAdmittance) * zb : 0)
                    .setR1(Transformers.getRatioLeg(twt, leg) / nominalV2 * nominalV1)
                    .setA1(Transformers.getAngleLeg(leg));
        }

        return new LfLegBranch(bus1, bus0, piModel, twt, leg);
    }

    private int getLegNum() {
        if (leg == twt.getLeg1()) {
            return 1;
        } else if (leg == twt.getLeg2()) {
            return 2;
        } else {
            return 3;
        }
    }

    @Override
    public String getId() {
        return twt.getId() + "_leg_" + getLegNum();
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p = Objects.requireNonNull(p1);
    }

    @Override
    public void setP2(Evaluable p2) {
        // nothing to do
    }

    @Override
    public void setQ1(Evaluable q1) {
        this.q = Objects.requireNonNull(q1);
    }

    @Override
    public void setQ2(Evaluable q2) {
        // nothing to do
    }

    @Override
    public double getI1() {
        return getBus1() != null ? Math.hypot(p.eval(), q.eval())
            / (Math.sqrt(3.) * getBus1().getV() / 1000) : Double.NaN;
    }

    @Override
    public double getI2() {
        return Double.NaN;
    }

    @Override
    public double getPermanentLimit1() {
        return leg.getCurrentLimits() != null ? leg.getCurrentLimits().getPermanentLimit() * getBus1().getNominalV() / PerUnit.SB : Double.NaN;
    }

    @Override
    public double getPermanentLimit2() {
        return Double.NaN;
    }

    @Override
    public void updateState() {
        leg.getTerminal().setP(p.eval() * PerUnit.SB);
        leg.getTerminal().setQ(q.eval() * PerUnit.SB);

        if (isPhaseController() && phaseControl.getMode() == DiscretePhaseControl.Mode.OFF) {
            // it means there is a regulating phase tap changer
            updateTapPosition(leg.getPhaseTapChanger());
        }

        if (isPhaseControlled() && phaseControl.getControlledSide() == DiscretePhaseControl.ControlledSide.ONE) {
            // Check if the target value deadband is respected
            checkTargetDeadband(p);
        }
    }
}
