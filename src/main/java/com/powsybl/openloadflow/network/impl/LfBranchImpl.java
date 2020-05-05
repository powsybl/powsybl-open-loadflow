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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBranchImpl extends AbstractLfBranch {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfBranchImpl.class);

    private PhaseControl phaseControl;

    private final Branch branch;

    private Evaluable p1 = NAN;

    private Evaluable p2 = NAN;

    private Evaluable q1 = NAN;

    private Evaluable q2 = NAN;

    protected LfBranchImpl(LfBus bus1, LfBus bus2, PiModel piModel, PhaseControl phaseControl, Branch branch) {
        super(bus1, bus2, piModel);
        this.phaseControl = phaseControl;
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

        return new LfBranchImpl(bus1, bus2, piModel, null, line);
    }

    private static LfBranchImpl createTransformer(TwoWindingsTransformer twt, LfBus bus1, LfBus bus2, double nominalV1,
                                                  double nominalV2, double zb) {
        PiModel piModel;
        PhaseControl phaseControl = null;

        PhaseTapChanger ptc = twt.getPhaseTapChanger();
        if (ptc != null
                && ptc.isRegulating()
                && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP) {
            PhaseTapChanger.RegulationMode regulationMode = ptc.getRegulationMode();
            PhaseControl.ControlledSide controlledSide;
            if (ptc.getRegulationTerminal() == twt.getTerminal1()) {
                controlledSide = PhaseControl.ControlledSide.ONE;
            } else if (ptc.getRegulationTerminal() == twt.getTerminal2()) {
                controlledSide = PhaseControl.ControlledSide.TWO;
            } else {
                throw new UnsupportedOperationException("Remote controlled phase not yet supported");
            }

            List<PiModel> models = new ArrayList<>();

            for (int position = ptc.getLowTapPosition(); position <= ptc.getHighTapPosition(); position++) {
                PhaseTapChangerStep step = ptc.getStep(position);
                double r = twt.getR() * (1 + step.getR() / 100) / zb;
                double x = twt.getX() * (1 + step.getX() / 100) / zb;
                double g1 = twt.getG() * (1 + step.getG() / 100) * zb;
                double b1 = twt.getB() * (1 + step.getB() / 100) * zb;
                double r1 = twt.getRatedU2() / twt.getRatedU1() * ptc.getCurrentStep().getRho() / nominalV2 * nominalV1;
                double a1 = Math.toRadians(step.getAlpha());
                models.add(new SimplePiModel()
                        .setR(r)
                        .setX(x)
                        .setG1(g1)
                        .setB1(b1)
                        .setR1(r1)
                        .setA1(a1));
            }

            piModel = new PiModelArray(models, ptc.getLowTapPosition(), ptc.getTapPosition());

            if (regulationMode == PhaseTapChanger.RegulationMode.CURRENT_LIMITER) {
                phaseControl = new PhaseControl(PhaseControl.Mode.LIMITER, controlledSide, ptc.getRegulationValue() / PerUnit.SB, PhaseControl.Unit.A);
            } else if (regulationMode == PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL) {
                phaseControl = new PhaseControl(PhaseControl.Mode.CONTROLLER, controlledSide, ptc.getRegulationValue() / PerUnit.SB, PhaseControl.Unit.MW);
            }
        } else {
            piModel = new SimplePiModel()
                    .setR(Transformers.getR(twt) / zb)
                    .setX(Transformers.getX(twt) / zb)
                    .setG1(Transformers.getG1(twt) * zb)
                    .setB1(Transformers.getB1(twt) * zb)
                    .setR1(Transformers.getRatio(twt) / nominalV2 * nominalV1)
                    .setA1(Transformers.getAngle(twt));
        }

        return new LfBranchImpl(bus1, bus2, piModel, phaseControl, twt);
    }

    public static LfBranchImpl create(Branch branch, LfBus bus1, LfBus bus2) {
        Objects.requireNonNull(branch);
        double nominalV1 = branch.getTerminal1().getVoltageLevel().getNominalV();
        double nominalV2 = branch.getTerminal2().getVoltageLevel().getNominalV();
        double zb = nominalV2 * nominalV2 / PerUnit.SB;
        if (branch instanceof Line) {
            return createLine((Line) branch, bus1, bus2, zb);
        } else if (branch instanceof TwoWindingsTransformer) {
            TwoWindingsTransformer twt = (TwoWindingsTransformer) branch;
            return createTransformer(twt, bus1, bus2, nominalV1, nominalV2, zb);
        } else {
            throw new PowsyblException("Unsupported type of branch for flow equations of branch: " + branch.getId());
        }
    }

    @Override
    public String getId() {
        return branch.getId();
    }

    @Override
    public void setP1(Evaluable p1) {
        this.p1 = Objects.requireNonNull(p1);
    }

    @Override
    public void setP2(Evaluable p2) {
        this.p2 = Objects.requireNonNull(p2);
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
    public Optional<PhaseControl> getPhaseControl() {
        return Optional.ofNullable(phaseControl);
    }

    @Override
    public void updateState() {
        branch.getTerminal1().setP(p1.eval() * PerUnit.SB);
        branch.getTerminal1().setQ(q1.eval() * PerUnit.SB);
        branch.getTerminal2().setP(p2.eval() * PerUnit.SB);
        branch.getTerminal2().setQ(q2.eval() * PerUnit.SB);

        if (phaseControl != null) { // it means there is a regulating phase tap changer
            PhaseTapChanger ptc = ((TwoWindingsTransformer) branch).getPhaseTapChanger();
            int tapPosition = Transformers.findTapPosition(ptc, Math.toDegrees(getPiModel().getA1()));
            ptc.setTapPosition(tapPosition);
            double distance = 0; // we check if the target value deadband is respected.
            double p = Double.NaN;
            if (phaseControl.getControlledSide() == PhaseControl.ControlledSide.ONE) {
                p = p1.eval() * PerUnit.SB;
                distance = Math.abs(p - phaseControl.getTargetValue() * PerUnit.SB);
            } else if (phaseControl.getControlledSide() == PhaseControl.ControlledSide.TWO) {
                p = p2.eval() * PerUnit.SB;
                distance = Math.abs(p - phaseControl.getTargetValue() * PerUnit.SB);
            }
            if (distance > (ptc.getTargetDeadband() / 2)) {
                LOGGER.warn("The active power on side {} of branch {} ({} MW) is out of the target value ({} MW) +/- deadband/2 ({} MW)",
                        phaseControl.getControlledSide(), this.getId(), p, phaseControl.getTargetValue() * PerUnit.SB, ptc.getTargetDeadband() / 2);
            }
        }
    }
}
