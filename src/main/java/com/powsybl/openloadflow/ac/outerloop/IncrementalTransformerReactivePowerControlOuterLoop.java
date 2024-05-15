/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.lf.outerloop.IncrementalContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class IncrementalTransformerReactivePowerControlOuterLoop extends AbstractTransformerReactivePowerControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalTransformerReactivePowerControlOuterLoop.class);

    public static final String NAME = "IncrementalTransformerReactivePowerControl";

    private static final int MAX_DIRECTION_CHANGE = 2;

    private final int maxTapShift;

    public IncrementalTransformerReactivePowerControlOuterLoop(int maxTapShift) {
        this.maxTapShift = maxTapShift;
    }

    @Override
    public String getName() {
        return NAME;
    }

    private static boolean isOutOfDeadband(TransformerReactivePowerControl reactivePowerControl) {
        double diffQ = getDiffQ(reactivePowerControl);
        double halfTargetDeadband = getHalfTargetDeadband(reactivePowerControl);
        boolean outOfDeadband = Math.abs(diffQ) > halfTargetDeadband;
        if (outOfDeadband) {
            LfBranch controllerBranch = reactivePowerControl.getControllerBranch();
            LfBranch controlledBranch = reactivePowerControl.getControlledBranch();
            LOGGER.trace("Controlled branch '{}' ({} controller) is outside of its deadband (half is {} MVar) and could need a reactive power adjustment of {} MVar",
                    controlledBranch.getId(), controllerBranch.getId(), halfTargetDeadband * PerUnit.SB, diffQ * PerUnit.SB);
        }
        return outOfDeadband;
    }

    public static List<LfBranch> getControllerBranches(LfNetwork network) {
        return network.getBranches().stream()
                .filter(branch -> !branch.isDisabled() && branch.isTransformerReactivePowerController())
                .toList();
    }

    public static List<LfBranch> getControlledBranchesOutOfDeadband(LfNetwork network) {
        return network.getBranches().stream()
                .filter(LfBranch::isTransformerReactivePowerControlled)
                .filter(branch -> isOutOfDeadband(branch.getTransformerReactivePowerControl().orElseThrow()))
                .filter(Predicate.not(LfBranch::isDisabled))
                .toList();
    }

    public static List<LfBranch> getControllerBranchesOutOfDeadband(List<LfBranch> controlledBranchesOutOfDeadband) {
        return controlledBranchesOutOfDeadband.stream()
                .map(controlledBranch -> controlledBranch.getTransformerReactivePowerControl().orElseThrow().getControllerBranch())
                .filter(Predicate.not(LfBranch::isDisabled))
                .toList();
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        var contextData = new IncrementalContextData();
        context.setData(contextData);

        for (LfBranch branch : getControllerBranches(context.getNetwork())) {
            contextData.getControllersContexts().put(branch.getId(), new IncrementalContextData.ControllerContext(MAX_DIRECTION_CHANGE));
        }
    }

    static class SensitivityContext {

        private final DenseMatrix sensitivities;

        private final int[] controllerBranchIndex;

        public SensitivityContext(LfNetwork network, List<LfBranch> controllerBranches,
                                  EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                  JacobianMatrix<AcVariableType, AcEquationType> j) {
            controllerBranchIndex = LfBranch.createIndex(network, controllerBranches);
            sensitivities = calculateSensitivityValues(controllerBranches, controllerBranchIndex, equationSystem, j);
        }

        private static DenseMatrix calculateSensitivityValues(List<LfBranch> controllerBranches, int[] controllerBranchIndex,
                                                              EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                              JacobianMatrix<AcVariableType, AcEquationType> j) {
            DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBranches.size());
            for (LfBranch controllerBranch : controllerBranches) {
                equationSystem.getEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_RHO1)
                        .ifPresent(equation -> rhs.set(equation.getColumn(), controllerBranchIndex[controllerBranch.getNum()], 1d));
            }
            j.solveTransposed(rhs);
            return rhs;
        }

        @SuppressWarnings("unchecked")
        private static EquationTerm<AcVariableType, AcEquationType> getCalculatedQ(LfBranch controlledBranch, TwoSides controlledSide) {
            var calculatedQ = controlledSide == TwoSides.ONE ? controlledBranch.getQ1() : controlledBranch.getQ2();
            return (EquationTerm<AcVariableType, AcEquationType>) calculatedQ;
        }

        double calculateSensitivityFromRToQ(LfBranch controllerBranch, LfBranch controlledBranch, TwoSides controlledSide) {
            return getCalculatedQ(controlledBranch, controlledSide)
                    .calculateSensi(sensitivities, controllerBranchIndex[controllerBranch.getNum()]);
        }
    }

    private boolean adjustWithController(LfBranch controllerBranch, LfBranch controlledBranch, TwoSides controlledSide, IncrementalContextData contextData,
                                         double diffQ, SensitivityContext sensitivities,
                                         List<String> controlledBranchesWithAllItsControllersToLimit) {
        // only one transformer controls a branch
        var controllerContext = contextData.getControllersContexts().get(controllerBranch.getId());
        double sensitivity = sensitivities.calculateSensitivityFromRToQ(controllerBranch, controlledBranch, controlledSide);
        PiModel piModel = controllerBranch.getPiModel();
        int previousTapPosition = piModel.getTapPosition();
        double deltaR1 = diffQ / sensitivity;
        return piModel.updateTapPositionToReachNewR1(deltaR1, maxTapShift, controllerContext.getAllowedDirection()).map(direction -> {
            controllerContext.updateAllowedDirection(direction);
            Range<Integer> tapPositionRange = piModel.getTapPositionRange();
            LOGGER.debug("Controller branch '{}' change tap from {} to {} (full range: {})", controllerBranch.getId(),
                    previousTapPosition, piModel.getTapPosition(), tapPositionRange);
            if (piModel.getTapPosition() == tapPositionRange.getMinimum()
                    || piModel.getTapPosition() == tapPositionRange.getMaximum()) {
                controlledBranchesWithAllItsControllersToLimit.add(controlledBranch.getId());
            }
            return direction;
        }).isPresent();
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        LfNetwork network = context.getNetwork();
        AcLoadFlowContext loadFlowContext = context.getLoadFlowContext();
        var contextData = (IncrementalContextData) context.getData();

        // branches which are out of their deadbands
        List<LfBranch> controlledBranchesOutOfDeadband = getControlledBranchesOutOfDeadband(network);
        List<LfBranch> controllerBranchesOutOfDeadband = getControllerBranchesOutOfDeadband(controlledBranchesOutOfDeadband);

        if (controllerBranchesOutOfDeadband.isEmpty()) {
            return new OuterLoopResult(this, status.getValue());
        }

        SensitivityContext sensitivityContext = new SensitivityContext(network, controllerBranchesOutOfDeadband,
                loadFlowContext.getEquationSystem(), loadFlowContext.getJacobianMatrix());

        // for synthetics logs
        List<String> controlledBranchesAdjusted = new ArrayList<>();
        List<String> controlledBranchesWithAllItsControllersToLimit = new ArrayList<>();

        controlledBranchesOutOfDeadband.forEach(controlledBranch -> {
            TransformerReactivePowerControl reactivePowerControl = controlledBranch.getTransformerReactivePowerControl().orElseThrow();
            double diffQ = getDiffQ(reactivePowerControl);
            LfBranch controller = reactivePowerControl.getControllerBranch();
            TwoSides controlledSide = reactivePowerControl.getControlledSide();

            // TODO : add case with more controllers
            boolean adjusted = adjustWithController(controller, controlledBranch, controlledSide, contextData, diffQ, sensitivityContext, controlledBranchesWithAllItsControllersToLimit);
            if (adjusted) {
                controlledBranchesAdjusted.add(controlledBranch.getId());
                status.setValue(OuterLoopStatus.UNSTABLE);
            }
        });

        ReportNode iterationReportNode = !controlledBranchesOutOfDeadband.isEmpty() || !controlledBranchesAdjusted.isEmpty() || !controlledBranchesWithAllItsControllersToLimit.isEmpty() ?
                Reports.createOuterLoopIterationReporter(reportNode, context.getOuterLoopTotalIterations() + 1) : null;

        if (!controlledBranchesOutOfDeadband.isEmpty()) {
            if (LOGGER.isInfoEnabled()) {
                Map<String, Double> largestMismatches = controllerBranchesOutOfDeadband.stream()
                        .map(controlledBranch -> Pair.of(controlledBranch.getId(), Math.abs(getDiffQ(controlledBranch.getTransformerReactivePowerControl().get()))))
                        .sorted((p1, p2) -> Double.compare(p2.getRight() * PerUnit.SB, p1.getRight() * PerUnit.SB))
                        .limit(3) // 3 largest
                        .collect(Collectors.toMap(Pair::getLeft, Pair::getRight, (key1, key2) -> key1, LinkedHashMap::new));
                LOGGER.info("{} controlled branch reactive power are outside of their target deadband, largest ones are: {}",
                        controllerBranchesOutOfDeadband.size(), largestMismatches);
            }
            Reports.reportTransformerControlBranchesOutsideDeadband(Objects.requireNonNull(iterationReportNode), controlledBranchesOutOfDeadband.size());
        }
        if (!controlledBranchesAdjusted.isEmpty()) {
            LOGGER.info("{} controlled branch reactive power have been adjusted by changing at least one tap",
                    controlledBranchesAdjusted.size());
            Reports.reportTransformerControlChangedTaps(Objects.requireNonNull(iterationReportNode), controlledBranchesAdjusted.size());
        }
        if (!controlledBranchesWithAllItsControllersToLimit.isEmpty()) {
            LOGGER.info("{} controlled branches have all its controllers to a tap limit: {}",
                    controlledBranchesWithAllItsControllersToLimit.size(), controlledBranchesWithAllItsControllersToLimit);
            Reports.reportTransformerControlTapLimit(Objects.requireNonNull(iterationReportNode), controlledBranchesWithAllItsControllersToLimit.size());
        }

        return new OuterLoopResult(this, status.getValue());
    }

    private static double getDiffQ(TransformerReactivePowerControl reactivePowerControl) {
        double targetQ = getHighestPriorityReactivePowerTarget(reactivePowerControl.getControlledBranch());
        double q = reactivePowerControl.getControlledSide() == TwoSides.ONE ? reactivePowerControl.getControlledBranch().getQ1().eval()
                : reactivePowerControl.getControlledBranch().getQ2().eval();
        return targetQ - q;
    }

    private static double getHighestPriorityReactivePowerTarget(LfBranch controlledBranch) {
        return controlledBranch.getGeneratorReactivePowerControl()
                .map(GeneratorReactivePowerControl::getTargetValue)
                .orElseGet(() -> controlledBranch.getTransformerReactivePowerControl()
                        .orElseThrow()
                        .getTargetValue());
    }
}
