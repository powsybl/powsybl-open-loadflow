/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;
import java.util.Optional;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfLegBranch extends AbstractLfBranch {

    private PhaseControl phaseControl;

    private final ThreeWindingsTransformer twt;

    private final ThreeWindingsTransformer.Leg leg;

    private Evaluable p = NAN;

    private Evaluable q = NAN;

    private double a1 = Double.NaN;

    protected LfLegBranch(LfBus bus1, LfBus bus0, PiModel piModel, PhaseControl phaseControl, ThreeWindingsTransformer twt,
                          ThreeWindingsTransformer.Leg leg) {
        super(bus1, bus0, piModel);
        this.phaseControl = phaseControl;
        this.twt = twt;
        this.leg = leg;
    }

    public static LfLegBranch create(LfBus bus1, LfBus bus0, ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg) {
        Objects.requireNonNull(bus0);
        Objects.requireNonNull(twt);
        Objects.requireNonNull(leg);
        double nominalV1 = leg.getTerminal().getVoltageLevel().getNominalV();
        double nominalV2 = twt.getRatedU0();
        double zb = nominalV2 * nominalV2 / PerUnit.SB;
        PhaseControl phaseControl = null;
        PiModel piModel = new PiModel()
                .setR(Transformers.getR(leg) / zb)
                .setX(Transformers.getX(leg) / zb)
                .setG1(Transformers.getG1(leg) * zb)
                .setB1(Transformers.getB1(leg) * zb)
                .setR1(Transformers.getRatioLeg(twt, leg) / nominalV2 * nominalV1)
                .setA1(Transformers.getAngleLeg(leg));

        PhaseTapChanger ptc = leg.getPhaseTapChanger();
        if (ptc != null && ptc.isRegulating()) {
            PhaseTapChanger.RegulationMode regulationMode = ptc.getRegulationMode();
            PhaseControl.ControlledSide controlledSide;
            if (ptc.getRegulationTerminal() == leg.getTerminal()) {
                controlledSide = PhaseControl.ControlledSide.ONE; // Network side.
            } else {
                throw new UnsupportedOperationException("Remote controlled phase not yet supported");
            }
            if (regulationMode == PhaseTapChanger.RegulationMode.CURRENT_LIMITER) {
                phaseControl = new PhaseControl(PhaseControl.Mode.LIMITER, controlledSide, ptc.getRegulationValue() / PerUnit.SB, PhaseControl.Unit.A);
            } else if (regulationMode == PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL) {
                phaseControl = new PhaseControl(PhaseControl.Mode.CONTROLLER, controlledSide, ptc.getRegulationValue() / PerUnit.SB, PhaseControl.Unit.MW);
            }
        }
        return new LfLegBranch(bus1, bus0, piModel, phaseControl, twt, leg);
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
    public void setA1(double a1) {
        this.a1 = a1;
    }

    @Override
    public void setA2(double a2) {
        // nothing to do
    }

    @Override
    public Optional<PhaseControl> getPhaseControl() {
        return Optional.empty();
    }

    public Branch getBranch() {
        return null;
    }

    @Override
    public void updateState() {
        leg.getTerminal().setP(p.eval() * PerUnit.SB);
        leg.getTerminal().setQ(q.eval() * PerUnit.SB);

        if (!Double.isNaN(a1)) {
            PhaseTapChanger ptc = leg.getPhaseTapChanger();
            if (ptc != null && ptc.isRegulating() && ptc.getRegulationMode() == PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL) {
                int step = Transformers.findStep(ptc, a1);
                ptc.setTapPosition(step);
            }
        }
    }
}

