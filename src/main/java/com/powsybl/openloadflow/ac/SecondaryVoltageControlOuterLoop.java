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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SecondaryVoltageControlOuterLoop implements OuterLoop {

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

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        LfNetwork network = context.getNetwork();
        List<LfSecondaryVoltageControl> secondaryVoltageControls = network.getSecondaryVoltageControls();
        if (!secondaryVoltageControls.isEmpty()) {
            int[] controlledBusIndex = new int[network.getBuses().size()];
            for (LfSecondaryVoltageControl secondaryVoltageControl : secondaryVoltageControls) {
                int i = 0;
                List<LfBus> controlledBuses = new ArrayList<>(secondaryVoltageControl.getControlledBuses());
                for (LfBus controlledBus : controlledBuses) {
                    controlledBusIndex[controlledBus.getNum()] = i++;
                }
                DenseMatrix sensitivities = calculateSensitivityValues(controlledBuses, controlledBusIndex,
                                                                       context.getAcLoadFlowContext().getEquationSystem(),
                                                                       context.getAcLoadFlowContext().getJacobianMatrix());

                LfBus pilotBus = secondaryVoltageControl.getPilotBus();
                for (LfBus controlledBus : controlledBuses) {
                    double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) pilotBus.getCalculatedV())
                            .calculateSensi(sensitivities, controlledBusIndex[controlledBus.getNum()]);
                    System.out.println(controlledBus.getId() + " " + sensitivity);
                }
                // TODO
            }
        }
        return OuterLoopStatus.STABLE;
    }
}
