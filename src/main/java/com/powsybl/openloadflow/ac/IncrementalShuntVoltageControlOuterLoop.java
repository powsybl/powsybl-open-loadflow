/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class IncrementalShuntVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalShuntVoltageControlOuterLoop.class);

    // Maximum number of directional inversions for each controller during incremental outer loop
    private static final int MAX_DIRECTION_CHANGE = 2;

    private static final double MIN_TARGET_DEADBAND_KV = 0.1; // kV

    private static final class ControllerContext {

        private final MutableInt directionChangeCount = new MutableInt();

        private AllowedDirection allowedDirection = AllowedDirection.BOTH;

        public MutableInt getDirectionChangeCount() {
            return directionChangeCount;
        }

        private AllowedDirection getAllowedDirection() {
            return allowedDirection;
        }

        private void setAllowedDirection(AllowedDirection allowedDirection) {
            this.allowedDirection = Objects.requireNonNull(allowedDirection);
        }
    }

    private static final class ContextData {

        private final Map<String, ControllerContext> controllersContexts = new HashMap<>();

        private Map<String, ControllerContext> getControllersContexts() {
            return controllersContexts;
        }
    }

    @Override
    public String getType() {
        return "Incremental Shunt voltage control";
    }

    @Override
    public void initialize(OuterLoopContext context) {
        var contextData = new ContextData();
        context.setData(contextData);

        // All shunt voltage control are disabled for the first equation system resolution.
        for (LfShunt shunt : getControllerShunts(context.getNetwork())) {
            shunt.getVoltageControl().ifPresent(voltageControl -> shunt.setVoltageControlEnabled(false));
            for (LfShunt.Controller lfShuntController : shunt.getControllers()) {
                contextData.getControllersContexts().put(lfShuntController.getId(), new ControllerContext());
            }
        }
    }

    private static void updateAllowedDirection(ControllerContext controllerContext, Direction direction) {
        if (controllerContext.getDirectionChangeCount().getValue() <= MAX_DIRECTION_CHANGE) {
            if (!controllerContext.getAllowedDirection().equals(direction.getAllowedDirection())) {
                // both vs increase or decrease
                // increase vs decrease or decrease vs increase
                controllerContext.getDirectionChangeCount().increment();
            }
            controllerContext.setAllowedDirection(direction.getAllowedDirection());
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
            DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerShunts.size());
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

    private static List<LfShunt> getControllerShunts(LfNetwork network) {
        return network.getBuses().stream()
                .flatMap(bus -> bus.getControllerShunt().stream())
                .filter(controllerShunt -> !controllerShunt.isDisabled() && controllerShunt.hasVoltageControlCapability())
                .collect(Collectors.toList());
    }

    private void adjustB(ShuntVoltageControl voltageControl, List<LfShunt> sortedControllerShunts, LfBus controlledBus, ContextData contextData,
                         SensitivityContext sensitivityContext, double diffV, MutableObject<OuterLoopStatus> status) {
        // several shunts control the same bus
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
                                updateAllowedDirection(controllerContext, direction);
                                remainingDiffV -= (controller.getB() - previousB) * sensitivity;
                                hasChanged = true;
                                status.setValue(OuterLoopStatus.UNSTABLE);
                            }
                        } else {
                            LOGGER.trace("Controller shunt '{}' is in its deadband: deadband {} vs voltage difference {}", controllerShunt.getId(),
                                    halfTargetDeadband * controlledBus.getNominalV(), Math.abs(diffV) * controlledBus.getNominalV());
                        }
                    }
                    if (hasChanged) {
                        controllerShunt.updateB();
                        controllerShunt.updateG();
                    }
                }
            }
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        LfNetwork network = context.getNetwork();
        AcLoadFlowContext loadFlowContext = context.getAcLoadFlowContext();
        var contextData = (ContextData) context.getData();

        List<LfShunt> controllerShunts = getControllerShunts(network);
        SensitivityContext sensitivityContext = new SensitivityContext(network, controllerShunts,
                loadFlowContext.getEquationSystem(), loadFlowContext.getJacobianMatrix());

        network.getBuses().stream()
                .filter(LfBus::isShuntVoltageControlled)
                .forEach(controlledBus -> {
                    ShuntVoltageControl voltageControl = controlledBus.getShuntVoltageControl().orElseThrow();
                    double diffV = voltageControl.getTargetValue() - voltageControl.getControlled().getV();
                    List<LfShunt> sortedControllers = voltageControl.getControllers().stream()
                            .sorted(Comparator.comparingDouble(LfShunt::getBMagnitude).reversed())
                            .collect(Collectors.toList());
                    adjustB(voltageControl, sortedControllers, controlledBus, contextData, sensitivityContext, diffV, status);
                });
        return status.getValue();
    }

    protected static double getHalfTargetDeadband(ShuntVoltageControl voltageControl) {
        return voltageControl.getTargetDeadband().orElse(MIN_TARGET_DEADBAND_KV / voltageControl.getControlled().getNominalV()) / 2;
    }
}
