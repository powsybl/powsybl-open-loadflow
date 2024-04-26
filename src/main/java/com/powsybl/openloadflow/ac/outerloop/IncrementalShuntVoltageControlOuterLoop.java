/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
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
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Hadrien Godard {@literal <hadrien.godard at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class IncrementalShuntVoltageControlOuterLoop extends AbstractShuntVoltageControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalShuntVoltageControlOuterLoop.class);

    public static final String NAME = "IncrementalShuntVoltageControl";

    // Maximum number of directional inversions for each controller during incremental outer loop
    private static final int MAX_DIRECTION_CHANGE = 2;

    private static final double MIN_TARGET_DEADBAND_KV = 0.1; // kV

    @Override
    public String getName() {
        return NAME;
    }

    public static List<LfBus> getControlledBusesOutOfDeadband(IncrementalContextData contextData) {
        return IncrementalContextData.getControlledBuses(contextData.getCandidateControlledBuses(), VoltageControl.Type.SHUNT).stream()
                .filter(bus -> isOutOfDeadband(bus.getShuntVoltageControl().orElseThrow()))
                .toList();
    }

    public static List<LfShunt> getControllerElementsOutOfDeadband(List<LfBus> controlledBusesOutOfDeadband) {
        return controlledBusesOutOfDeadband.stream()
                .flatMap(bus -> bus.getShuntVoltageControl().orElseThrow().getMergedControllerElements().stream())
                .filter(Predicate.not(LfShunt::isDisabled))
                .toList();
    }

    public static List<LfShunt> getControllerElements(IncrementalContextData contextData) {
        return IncrementalContextData.getControllerElements(contextData.getCandidateControlledBuses(), VoltageControl.Type.SHUNT);
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        var contextData = new IncrementalContextData(context.getNetwork(), VoltageControl.Type.SHUNT);
        context.setData(contextData);

        // All shunt voltage control are disabled for the first equation system resolution.
        for (LfShunt shunt : getControllerElements(contextData)) {
            shunt.getVoltageControl().ifPresent(voltageControl -> shunt.setVoltageControlEnabled(false));
            for (LfShunt.Controller lfShuntController : shunt.getControllers()) {
                contextData.getControllersContexts().put(lfShuntController.getId(), new IncrementalContextData.ControllerContext(MAX_DIRECTION_CHANGE));
            }
        }
    }

    static class SensitivityContext {

        private final DenseMatrix sensitivities;

        private final int[] controllerShuntIndex;

        public SensitivityContext(LfNetwork network, List<LfShunt> controllerShunts,
                                  EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                  JacobianMatrix<AcVariableType, AcEquationType> j) {
            controllerShuntIndex = createControllerShuntIndex(network, controllerShunts);
            sensitivities = calculateSensitivityValues(controllerShunts, controllerShuntIndex, equationSystem, j);
        }

        private static int[] createControllerShuntIndex(LfNetwork network, List<LfShunt> controllerShunts) {
            int[] controllerShuntIndex = new int[network.getShunts().size()];
            for (int i = 0; i < controllerShunts.size(); i++) {
                LfShunt controllerShunt = controllerShunts.get(i);
                controllerShuntIndex[controllerShunt.getNum()] = i;
            }
            return controllerShuntIndex;
        }

        private static DenseMatrix calculateSensitivityValues(List<LfShunt> controllerShunts, int[] controllerShuntIndex,
                                                              EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                              JacobianMatrix<AcVariableType, AcEquationType> j) {
            int nRows = equationSystem.getIndex().getSortedEquationsToSolve().size();
            int nColumns = controllerShunts.size();

            DenseMatrix rhs = new DenseMatrix(nRows, nColumns);
            for (LfShunt controllerShunt : controllerShunts) {
                equationSystem.getEquation(controllerShunt.getNum(), AcEquationType.SHUNT_TARGET_B)
                        .ifPresent(equation -> rhs.set(equation.getColumn(), controllerShuntIndex[controllerShunt.getNum()], 1d));
            }
            j.solveTransposed(rhs);
            return rhs;
        }

        @SuppressWarnings("unchecked")
        private static EquationTerm<AcVariableType, AcEquationType> getCalculatedV(LfBus controlledBus) {
            return (EquationTerm<AcVariableType, AcEquationType>) controlledBus.getCalculatedV();
        }

        double calculateSensitivityFromBToV(LfShunt controllerShunt, LfBus controlledBus) {
            return getCalculatedV(controlledBus)
                    .calculateSensi(sensitivities, controllerShuntIndex[controllerShunt.getNum()]);
        }
    }

    private void adjustB(ShuntVoltageControl voltageControl, List<LfShunt> sortedControllerShunts, LfBus controlledBus, IncrementalContextData contextData,
                         SensitivityContext sensitivityContext, double diffV, MutableObject<Integer> numAdjustedShunts) {
        // several shunts could control the same bus
        double remainingDiffV = diffV;
        boolean hasChanged = true;
        while (hasChanged) {
            hasChanged = false;
            for (LfShunt controllerShunt : sortedControllerShunts) {
                List<LfShunt.Controller> controllers = controllerShunt.getControllers();
                if (!controllers.isEmpty()) {
                    double sensitivity = sensitivityContext.calculateSensitivityFromBToV(controllerShunt, controlledBus);
                    for (LfShunt.Controller controller : controllers) {
                        var controllerContext = contextData.getControllersContexts().get(controller.getId());
                        double halfTargetDeadband = getHalfTargetDeadband(voltageControl);
                        if (Math.abs(remainingDiffV) > halfTargetDeadband) {
                            double previousB = controller.getB();
                            double deltaB = remainingDiffV / sensitivity;
                            Direction direction = controller.updateSectionB(deltaB, 1, controllerContext.getAllowedDirection()).orElse(null);
                            if (direction != null) {
                                controllerContext.updateAllowedDirection(direction);
                                remainingDiffV -= (controller.getB() - previousB) * sensitivity;
                                hasChanged = true;
                                numAdjustedShunts.setValue(numAdjustedShunts.getValue() + 1);
                            }
                        } else {
                            LOGGER.trace("Controller shunt '{}' is in its deadband: deadband {} vs voltage difference {}", controllerShunt.getId(),
                                    halfTargetDeadband * controlledBus.getNominalV(), Math.abs(diffV) * controlledBus.getNominalV());
                        }
                    }
                }
            }
        }
    }

    private static double getDiffV(ShuntVoltageControl voltageControl) {
        double targetV = voltageControl.getControlledBus().getHighestPriorityTargetV().orElseThrow();
        double v = voltageControl.getControlledBus().getV();
        return targetV - v;
    }

    private static boolean isOutOfDeadband(ShuntVoltageControl voltageControl) {
        double diffV = getDiffV(voltageControl);
        double halfTargetDeadband = getHalfTargetDeadband(voltageControl);
        boolean outOfDeadband = Math.abs(diffV) > halfTargetDeadband;
        if (outOfDeadband) {
            List<LfShunt> controllers = voltageControl.getMergedControllerElements().stream()
                    .filter(shunt -> !shunt.isDisabled())
                    .toList();
            LOGGER.trace("Controlled bus '{}' ({} controllers) is outside of its deadband (half is {} kV) and could need a voltage adjustment of {} kV",
                    voltageControl.getControlledBus().getId(), controllers.size(), halfTargetDeadband * voltageControl.getControlledBus().getNominalV(),
                    diffV * voltageControl.getControlledBus().getNominalV());
        }
        return outOfDeadband;
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        LfNetwork network = context.getNetwork();
        AcLoadFlowContext loadFlowContext = context.getLoadFlowContext();
        var contextData = (IncrementalContextData) context.getData();

        // check which shunts are not within their deadbands
        List<LfBus> controlledBusesOutOfDeadband = getControlledBusesOutOfDeadband(contextData);
        List<LfShunt> controllerShuntsOutOfDeadband = getControllerElementsOutOfDeadband(controlledBusesOutOfDeadband);

        // all shunts are within their deadbands
        if (controllerShuntsOutOfDeadband.isEmpty()) {
            return new OuterLoopResult(status.getValue());
        }

        MutableObject<Integer> numAdjustedShunts = new MutableObject<>(0);

        SensitivityContext sensitivityContext = new SensitivityContext(network, controllerShuntsOutOfDeadband,
                loadFlowContext.getEquationSystem(), loadFlowContext.getJacobianMatrix());

        controlledBusesOutOfDeadband.forEach(controlledBus -> {
            ShuntVoltageControl voltageControl = controlledBus.getShuntVoltageControl().orElseThrow();
            double diffV = getDiffV(voltageControl);
            List<LfShunt> sortedControllers = voltageControl.getMergedControllerElements().stream()
                    .filter(shunt -> !shunt.isDisabled())
                    .sorted(Comparator.comparingDouble(LfShunt::getBMagnitude).reversed())
                    .toList();
            adjustB(voltageControl, sortedControllers, controlledBus, contextData, sensitivityContext, diffV, numAdjustedShunts);
        });

        if (numAdjustedShunts.getValue() != 0) {
            status.setValue(OuterLoopStatus.UNSTABLE);
            ReportNode iterationReportNode = Reports.createOuterLoopIterationReporter(reportNode, context.getOuterLoopTotalIterations() + 1);
            Reports.reportShuntVoltageControlChangedSection(iterationReportNode, numAdjustedShunts.getValue());
        }

        return new OuterLoopResult(status.getValue());
    }

    protected static double getHalfTargetDeadband(ShuntVoltageControl voltageControl) {
        return voltageControl.getTargetDeadband().orElse(MIN_TARGET_DEADBAND_KV / voltageControl.getControlledBus().getNominalV()) / 2;
    }
}
