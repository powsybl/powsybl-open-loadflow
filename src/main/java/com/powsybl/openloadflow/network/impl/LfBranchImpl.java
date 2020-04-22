/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Objects;
import java.util.Optional;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBranchImpl extends AbstractLfBranch {

    private PhaseControl phaseControl;

    private final Branch branch;

    private Evaluable p1 = NAN;

    private Evaluable p2 = NAN;

    private Evaluable q1 = NAN;

    private Evaluable q2 = NAN;

    private double a1 = Double.NaN;

    protected LfBranchImpl(LfBus bus1, LfBus bus2, PiModel piModel, PhaseControl phaseControl, Branch branch) {
        super(bus1, bus2, piModel);
        this.phaseControl = phaseControl;
        this.branch = branch;
    }

    public static LfBranchImpl create(Branch branch, LfBus bus1, LfBus bus2) {
        Objects.requireNonNull(branch);
        double nominalV1 = branch.getTerminal1().getVoltageLevel().getNominalV();
        double nominalV2 = branch.getTerminal2().getVoltageLevel().getNominalV();
        double zb = nominalV2 * nominalV2 / PerUnit.SB;
        PiModel piModel;
        PhaseControl phaseControl = null;
        if (branch instanceof Line) {
            Line line = (Line) branch;
            piModel = new PiModel()
                    .setR(line.getR() / zb)
                    .setX(line.getX() / zb)
                    .setG1(line.getG1() * zb)
                    .setG2(line.getG2() * zb)
                    .setB1(line.getB1() * zb)
                    .setB2(line.getB2() * zb);
        } else if (branch instanceof TwoWindingsTransformer) {
            TwoWindingsTransformer twt = (TwoWindingsTransformer) branch;
            piModel = new PiModel()
                    .setR(Transformers.getR(twt) / zb)
                    .setX(Transformers.getX(twt) / zb)
                    .setG1(Transformers.getG1(twt) * zb)
                    .setB1(Transformers.getB1(twt) * zb)
                    .setR1(Transformers.getRatio(twt) / nominalV2 * nominalV1)
                    .setA1(Transformers.getAngle(twt));

            PhaseTapChanger ptc = twt.getPhaseTapChanger();
            if (ptc != null && ptc.isRegulating()) {
                PhaseTapChanger.RegulationMode regulationMode = ptc.getRegulationMode();
                PhaseControl.ControlledSide controlledSide;
                if (ptc.getRegulationTerminal() == twt.getTerminal1()) {
                    controlledSide = PhaseControl.ControlledSide.ONE;
                } else if (ptc.getRegulationTerminal() == twt.getTerminal2()) {
                    controlledSide = PhaseControl.ControlledSide.TWO;
                } else {
                    throw new UnsupportedOperationException("Remote controlled phase not yet supported");
                }
                if (regulationMode == PhaseTapChanger.RegulationMode.CURRENT_LIMITER) {
                    phaseControl = new PhaseControl(PhaseControl.Mode.LIMITER, controlledSide, ptc.getRegulationValue() / PerUnit.SB, PhaseControl.Unit.A);
                } else if (regulationMode == PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL) {
                    phaseControl = new PhaseControl(PhaseControl.Mode.CONTROLLER, controlledSide, ptc.getRegulationValue() / PerUnit.SB, PhaseControl.Unit.MW);
                }
            }
        } else {
            throw new PowsyblException("Unsupported type of branch for flow equations of branch: " + branch.getId());
        }
        return new LfBranchImpl(bus1, bus2, piModel, phaseControl, branch);
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
    public void setA1(double a1) {
        this.a1 = a1;
    }

    @Override
    public void setA2(double a2) {
        // nothing to do
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

        if (!Double.isNaN(a1)) {
            // TODO
        }
    }
}
