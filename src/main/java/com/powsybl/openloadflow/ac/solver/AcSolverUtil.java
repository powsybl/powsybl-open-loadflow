/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class AcSolverUtil {

    private AcSolverUtil() {
    }

    public static void updateNetwork(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // update state variable
        StateVector stateVector = equationSystem.getStateVector();
        for (Variable<AcVariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            switch (v.getType()) {
                case BUS_V:
                    network.getBus(v.getElementNum()).setV(stateVector.get(v.getRow()));
                    break;

                case BUS_PHI:
                    network.getBus(v.getElementNum()).setAngle(stateVector.get(v.getRow()));
                    break;

                case BUS_V_ZERO:
                    network.getBus(v.getElementNum()).getAsym().setVz(stateVector.get(v.getRow()));
                    break;

                case BUS_PHI_ZERO:
                    network.getBus(v.getElementNum()).getAsym().setAngleZ(stateVector.get(v.getRow()));
                    break;

                case BUS_V_NEGATIVE:
                    network.getBus(v.getElementNum()).getAsym().setVn(stateVector.get(v.getRow()));
                    break;

                case BUS_PHI_NEGATIVE:
                    network.getBus(v.getElementNum()).getAsym().setAngleN(stateVector.get(v.getRow()));
                    break;

                case SHUNT_B:
                    network.getShunt(v.getElementNum()).setB(stateVector.get(v.getRow()));
                    break;

                case BRANCH_ALPHA1:
                    network.getBranch(v.getElementNum()).getPiModel().setA1(stateVector.get(v.getRow()));
                    break;

                case BRANCH_RHO1:
                    network.getBranch(v.getElementNum()).getPiModel().setR1(stateVector.get(v.getRow()));
                    break;

                case DUMMY_P,
                     DUMMY_Q:
                    // nothing to do
                    break;

                case DC_BUS_V:
                    network.getDcBus(v.getElementNum()).setV(stateVector.get(v.getRow()));
                    break;

                case CONV_P_AC:
                    network.getVoltageSourceConverter(v.getElementNum()).setPac(stateVector.get(v.getRow()));
                    break;

                case CONV_Q_AC:
                    network.getVoltageSourceConverter(v.getElementNum()).setQac(stateVector.get(v.getRow()));
                    break;

                default:
                    throw new IllegalStateException("Unknown variable type " + v.getType());
            }
        }
    }
}
