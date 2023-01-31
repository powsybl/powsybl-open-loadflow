/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class IncrementalPhaseControlOuterLoop extends AbstractPhaseControlOuterLoop {

    @Override
    public String getType() {
        return "Incremental phase control";
    }

    @Override
    public void initialize(OuterLoopContext context) {
        fixPhaseShifterNecessaryForConnectivity(context.getNetwork());
    }

    static class SensitivityContext {

        private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

        private final DenseMatrix sensitivities;

        private final int[] controllerBranchIndex;

        public SensitivityContext(LfNetwork network, List<LfBranch> controllerBranches,
                                  EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                  JacobianMatrix<AcVariableType, AcEquationType> j) {
            this.equationSystem = Objects.requireNonNull(equationSystem);
            controllerBranchIndex = LfBranch.createIndex(network, controllerBranches);
            sensitivities = calculateSensitivityValues(controllerBranches, controllerBranchIndex, equationSystem, j);
        }

        private static DenseMatrix calculateSensitivityValues(List<LfBranch> controllerBranches, int[] controllerBranchIndex,
                                                              EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                              JacobianMatrix<AcVariableType, AcEquationType> j) {
            DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBranches.size());
            for (LfBranch controllerBranch : controllerBranches) {
                equationSystem.getEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_ALPHA1)
                        .ifPresent(equation -> rhs.set(equation.getColumn(), controllerBranchIndex[controllerBranch.getNum()], Math.toRadians(1d)));
            }
            j.solveTransposed(rhs);
            return rhs;
        }

        @SuppressWarnings("unchecked")
        private static EquationTerm<AcVariableType, AcEquationType> getI(LfBranch controlledBranch) {
            return (EquationTerm<AcVariableType, AcEquationType>) controlledBranch.getI1();
        }

        double calculateSensitivityFromA2I(LfBranch controllerBranch, LfBranch controlledBranch) {
            EquationTerm<AcVariableType, AcEquationType> i = getI(controlledBranch);
            double sensi = i.calculateSensi(sensitivities, controllerBranchIndex[controllerBranch.getNum()]);
            if (controllerBranch == controlledBranch) {
                var a1Var = equationSystem.getVariable(controllerBranch.getNum(), AcVariableType.BRANCH_ALPHA1);
                sensi += Math.toRadians(i.der(a1Var));
            }
            return sensi;
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        LfNetwork network = context.getNetwork();

        List<LfBranch> controllerBranches = getControllerBranches(network);

        // find list of phase controls that are in current limiter
        List<DiscretePhaseControl> currentLimiterPhaseControls = controllerBranches.stream()
                .flatMap(branch -> branch.getDiscretePhaseControl().stream())
                .filter(phaseControl -> phaseControl.getMode() == DiscretePhaseControl.Mode.LIMITER)
                .collect(Collectors.toList());

        var sensitivityContext = new SensitivityContext(network,
                                                        controllerBranches,
                                                        context.getAcLoadFlowContext().getEquationSystem(),
                                                        context.getAcLoadFlowContext().getJacobianMatrix());

        for (DiscretePhaseControl phaseControl : currentLimiterPhaseControls) {
            LfBranch controller = phaseControl.getController();
            double sensiA2I = sensitivityContext.calculateSensitivityFromA2I(controller, phaseControl.getControlled());
            double i1 = controller.getI1().eval();
            if (i1 > phaseControl.getTargetValue()) {
                System.out.println(controller.getId());
                double ib = PerUnit.ib(controller.getBus1().getNominalV());
                System.out.println("i1=" + (i1 * ib));
                System.out.println("targetValue=" + (phaseControl.getTargetValue() * ib));
                double di = i1 - phaseControl.getTargetValue();
                double da = Math.toRadians(di / -sensiA2I);
                System.out.println("di=" + (di * ib));
                System.out.println("da=" + Math.toDegrees(da));
                PiModel piModel = controller.getPiModel();
                System.out.println("tap=" + piModel.getTapPosition());
                piModel.updateTapPositionToReachNewA1(da, 100, AllowedDirection.BOTH);
                System.out.println("tap=" + piModel.getTapPosition());
                status = OuterLoopStatus.UNSTABLE;
            }
        }

        return status;
    }
}
