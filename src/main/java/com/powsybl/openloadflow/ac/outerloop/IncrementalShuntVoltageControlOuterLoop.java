/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.lf.outerloop.IncrementalContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public void initialize(AcOuterLoopContext context) {
        var contextData = new IncrementalContextData();
        context.setData(contextData);

        // All shunt voltage control are disabled for the first equation system resolution.
        for (LfShunt shunt : context.getNetwork().<LfShunt>getControllerElements(VoltageControl.Type.SHUNT)) {
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

            if (nColumns == 1) {
                double[] vectorRhs = new double[nRows];
                Arrays.fill(vectorRhs, 0);
                for (LfShunt controllerShunt : controllerShunts) {
                    equationSystem.getEquation(controllerShunt.getNum(), AcEquationType.SHUNT_TARGET_B)
                            .ifPresent(equation -> vectorRhs[equation.getColumn()] = 1d);
                }
                j.solveTransposed(vectorRhs);

                // generate matrix with solution in rhs
                DenseMatrix rhs = new DenseMatrix(nRows, 1);
                for (int i = 0; i < nRows; i++) {
                    rhs.set(i, 0, vectorRhs[i]);
                }
                return rhs;
            }

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
                         SensitivityContext sensitivityContext, double diffV, MutableObject<OuterLoopStatus> status) {
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
                                status.setValue(OuterLoopStatus.UNSTABLE);
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

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
        MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        LfNetwork network = context.getNetwork();
        AcLoadFlowContext loadFlowContext = context.getLoadFlowContext();
        var contextData = (IncrementalContextData) context.getData();

        List<LfBus> controlledBuses = network.getControlledBuses(VoltageControl.Type.SHUNT);

        // check which shunts are not within their deadbands
        List<LfShunt> controllerShuntsOutsideOfDeadband = new ArrayList<>();
        List<LfBus> controlledBusesOutsideOfDeadband = new ArrayList<>();
        controlledBuses.forEach(controlledBus -> {
            ShuntVoltageControl voltageControl = controlledBus.getShuntVoltageControl().orElseThrow();
            double diffV = voltageControl.getTargetValue() - voltageControl.getControlledBus().getV();
            double halfTargetDeadband = getHalfTargetDeadband(voltageControl);
            if (Math.abs(diffV) > halfTargetDeadband) {
                List<LfShunt> controllers = voltageControl.getMergedControllerElements().stream()
                        .filter(b -> !b.isDisabled())
                        .collect(Collectors.toList());

                controllerShuntsOutsideOfDeadband.addAll(controllers);
                controlledBusesOutsideOfDeadband.add(controlledBus);
            }
        });

        // all shunts are within their deadbands
        if (controllerShuntsOutsideOfDeadband.isEmpty()) {
            return status.getValue();
        }

        SensitivityContext sensitivityContext = new SensitivityContext(network, controllerShuntsOutsideOfDeadband,
                loadFlowContext.getEquationSystem(), loadFlowContext.getJacobianMatrix());

        controlledBusesOutsideOfDeadband.forEach(controlledBus -> {
            ShuntVoltageControl voltageControl = controlledBus.getShuntVoltageControl().orElseThrow();
            double diffV = voltageControl.getTargetValue() - voltageControl.getControlledBus().getV();
            List<LfShunt> sortedControllers = voltageControl.getMergedControllerElements().stream()
                    .filter(shunt -> !shunt.isDisabled())
                    .sorted(Comparator.comparingDouble(LfShunt::getBMagnitude).reversed())
                    .collect(Collectors.toList());
            adjustB(voltageControl, sortedControllers, controlledBus, contextData, sensitivityContext, diffV, status);
        });

        return status.getValue();
    }

    protected static double getHalfTargetDeadband(ShuntVoltageControl voltageControl) {
        return voltageControl.getTargetDeadband().orElse(MIN_TARGET_DEADBAND_KV / voltageControl.getControlledBus().getNominalV()) / 2;
    }
}
