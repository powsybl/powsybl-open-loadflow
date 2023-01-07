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
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfSecondaryVoltageControl;
import com.powsybl.openloadflow.network.VoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SecondaryVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecondaryVoltageControlOuterLoop.class);

    private static final double TARGET_V_DIFF_EPS = 10e-4;

    @Override
    public String getType() {
        return "SecondaryVoltageControl";
    }

    private static DenseMatrix calculateSensitivityValues(List<LfBus> controllerBuses, int[] controlledBusIndex,
                                                          EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                          JacobianMatrix<AcVariableType, AcEquationType> j) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBuses.size());
        for (LfBus controlledBus : controllerBuses) {
            equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                    .ifPresent(equation -> rhs.set(equation.getColumn(), controlledBusIndex[controlledBus.getNum()], 1d));
        }
        j.solveTransposed(rhs);
        return rhs;
    }

    @SuppressWarnings("unchecked")
    private static EquationTerm<AcVariableType, AcEquationType> getCalculatedV(LfBus pilotBus) {
        return (EquationTerm<AcVariableType, AcEquationType>) pilotBus.getCalculatedV(); // this is safe
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        LfNetwork network = context.getNetwork();
        List<LfSecondaryVoltageControl> secondaryVoltageControls = network.getSecondaryVoltageControls().stream()
                .filter(control -> !control.getPilotBus().isDisabled())
                .collect(Collectors.toList());
        if (!secondaryVoltageControls.isEmpty()) {
            int[] controlledBusIndex = new int[network.getBuses().size()];
            for (LfSecondaryVoltageControl secondaryVoltageControl : secondaryVoltageControls) {
                List<LfBus> controlledBuses = secondaryVoltageControl.getControlledBuses().stream()
                        .filter(b -> !b.isDisabled() && b.isVoltageControlEnabled())
                        .collect(Collectors.toList());
                if (controlledBuses.isEmpty()) {
                    continue;
                }

                for (int i = 0; i < controlledBuses.size(); i++) {
                    LfBus controlledBus = controlledBuses.get(i);
                    controlledBusIndex[controlledBus.getNum()] = i;
                }
                DenseMatrix sensitivities = calculateSensitivityValues(controlledBuses, controlledBusIndex,
                                                                       context.getAcLoadFlowContext().getEquationSystem(),
                                                                       context.getAcLoadFlowContext().getJacobianMatrix());

                LfBus pilotBus = secondaryVoltageControl.getPilotBus();
                double svcTargetDv = secondaryVoltageControl.getTargetValue() - pilotBus.getV();
                if (Math.abs(svcTargetDv) > TARGET_V_DIFF_EPS) {
                    LOGGER.info("Secondary voltage control of zone '{}' needs a pilot point voltage adjustment: {} -> {}",
                            secondaryVoltageControl.getZoneName(), pilotBus.getV(), secondaryVoltageControl.getTargetValue());
                    for (LfBus controlledBus : controlledBuses) {
                        double sensitivity = getCalculatedV(pilotBus)
                                .calculateSensi(sensitivities, controlledBusIndex[controlledBus.getNum()]);
                        double pvcTargetDv = svcTargetDv / controlledBuses.size() / sensitivity;
                        VoltageControl primaryVoltageControl = controlledBus.getVoltageControl().orElseThrow();
                        double newPvcTargetV = primaryVoltageControl.getTargetValue() + pvcTargetDv;
                        LOGGER.info("Adjust primary voltage control target of bus {}: {} -> {}",
                                controlledBus.getId(), primaryVoltageControl.getTargetValue(), newPvcTargetV);
                        primaryVoltageControl.setTargetValue(newPvcTargetV);
                    }
                    status = OuterLoopStatus.UNSTABLE;
                }
            }
        }

        return status;
    }
}
