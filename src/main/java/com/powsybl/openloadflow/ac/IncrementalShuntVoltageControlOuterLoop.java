/**
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
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfShuntImpl;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */

public class IncrementalShuntVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalShuntVoltageControlOuterLoop.class);

    // Maximum number of directional inversions for each controller during incremental outerloop
    private static final int MAX_DIRECTION_CHANGE = 2;

    private static final double MIN_TARGET_DEADBAND_KV = 0.1; // kV

    private static final class ControllerContext {

        private final MutableInt directionChangeCount = new MutableInt();

        private LfShuntImpl.Controller.AllowedDirection allowedDirection = LfShuntImpl.Controller.AllowedDirection.BOTH;

        public MutableInt getDirectionChangeCount() {
            return directionChangeCount;
        }

        private LfShuntImpl.Controller.AllowedDirection getAllowedDirection() {
            return allowedDirection;
        }

        private void setAllowedDirection(LfShuntImpl.Controller.AllowedDirection allowedDirection) {
            this.allowedDirection = Objects.requireNonNull(allowedDirection);
        }
    }

    private static final class ContextData {

        private final Map<String, ControllerContext> controllersContexts = new HashMap<>();

        private Map<String, ControllerContext> getControllersContexts() {
            return controllersContexts;
        }
    }

    private static List<LfShunt> getControllerShunts(LfNetwork network) {
        return network.getBuses().stream()
                .flatMap(bus -> bus.getControllerShunt().stream())
                .filter(controllerShunt -> !controllerShunt.isDisabled() && controllerShunt.hasVoltageControlCapability())
                .collect(Collectors.toList());
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
            // FIX ME: not safe casting
            for (LfShuntImpl.Controller lfShuntController : ((LfShuntImpl) shunt).getControllers()) {
                contextData.getControllersContexts().put(lfShuntController.getId(), new ControllerContext());
            }
        }
    }

    private static void updateAllowedDirection(ControllerContext controllerContext, LfShuntImpl.Controller.Direction direction) {
        if (controllerContext.getDirectionChangeCount().getValue() <= MAX_DIRECTION_CHANGE) {
            if (!controllerContext.getAllowedDirection().equals(direction.getAllowedDirection())) {
                // both vs increase or decrease
                // increase vs decrease or decrease vs increase
                controllerContext.getDirectionChangeCount().increment();
            }
            controllerContext.setAllowedDirection(direction.getAllowedDirection());
        }
    }

    private void adjustB(ShuntVoltageControl voltageControl, List<LfShunt> sortedControllerShunts, LfBus controlledBus, ContextData contextData,
                         int[] controllerShuntIndex, DenseMatrix sensitivities, double diffV, MutableObject<OuterLoopStatus> status) {
        // several shunts control the same bus
        double remainingDiffV = diffV;
        boolean hasChanged = true;
        while (hasChanged) {
            hasChanged = false;
            for (LfShunt controllerShunt : sortedControllerShunts) {
                double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) controlledBus.getCalculatedV())
                        .calculateSensi(sensitivities, controllerShuntIndex[controllerShunt.getNum()]);
                // FIX ME: Not safe casting
                LfShuntImpl controllerShuntImpl = (LfShuntImpl) controllerShunt;
                // Not very efficient because sorting is performed at each iteration. However, in practical should not be an issue.
                // Considering storing the controllers sorted already as same order is used everywhere else
                for (LfShuntImpl.Controller controller : controllerShuntImpl.getControllers().stream().sorted(Comparator.comparing(LfShuntImpl.Controller::getBMagnitude)).collect(Collectors.toList())) {
                    var controllerContext = contextData.getControllersContexts().get(controller.getId());
                    double halfTargetDeadband = getHalfTargetDeadband(voltageControl);
                    if (Math.abs(remainingDiffV) > halfTargetDeadband) {
                        double previousB = controller.getB();
                        double deltaB = remainingDiffV / sensitivity;
                        LfShuntImpl.Controller.Direction direction = controller.updateTapPositionB(deltaB, 1, controllerContext.getAllowedDirection()).orElse(null);
                        if (direction != null) {
                            updateAllowedDirection(controllerContext, direction);
                            remainingDiffV -= (controller.getB() - previousB) * sensitivity;
                            hasChanged = true;
                            status.setValue(OuterLoopStatus.UNSTABLE);
                            LOGGER.debug("Increment shunt susceptance value of '{}': {} -> {}", controller.getId(), previousB, controller.getB());
                        }
                    } else {
                        LOGGER.trace("Controller shunt '{}' is in its deadband: deadband {} vs voltage difference {}", controllerShunt.getId(), halfTargetDeadband, Math.abs(diffV)); // perunit ?
                    }
                }
                if (hasChanged) {
                    controllerShuntImpl.updateB();
                    controllerShuntImpl.updateG();
                    for (LfNetworkListener listener : controllerShunt.getNetwork().getListeners()) {
                        listener.onShuntTargetBChange(controllerShunt, controllerShunt.getB());
                    }
                }
            }
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        LfNetwork network = context.getNetwork();
        List<LfShunt> controllerShunts = getControllerShunts(network);
        int[] controllerShuntIndex = new int[network.getShunts().size()];
        for (int i = 0; i < controllerShunts.size(); i++) {
            LfShunt controllerShunt = controllerShunts.get(i);
            controllerShuntIndex[controllerShunt.getNum()] = i;
        }

        DenseMatrix sensitivities = calculateSensitivityValues(controllerShunts, controllerShuntIndex,
                context.getAcLoadFlowContext().getEquationSystem(),
                context.getAcLoadFlowContext().getJacobianMatrix());

        var contextData = (IncrementalShuntVoltageControlOuterLoop.ContextData) context.getData();

        network.getBuses().stream()
                .filter(LfBus::isShuntVoltageControlled)
                .forEach(controlledBus -> {
                    ShuntVoltageControl voltageControl = controlledBus.getShuntVoltageControl().orElseThrow();
                    double targetV = voltageControl.getTargetValue();
                    double v = voltageControl.getControlled().getV();
                    double diffV = targetV - v;
                    List<LfShunt> sortedControllers = voltageControl.getControllers().stream().sorted(Comparator.comparing(LfShunt::getBMagnitude)).collect(Collectors.toList());
                    adjustB(voltageControl, sortedControllers, controlledBus, contextData, controllerShuntIndex, sensitivities, diffV, status);
                });
        return status.getValue();
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

    protected static double getHalfTargetDeadband(ShuntVoltageControl voltageControl) {
        return voltageControl.getTargetDeadband().orElse(MIN_TARGET_DEADBAND_KV / voltageControl.getControlled().getNominalV()) / 2;
    }
}
