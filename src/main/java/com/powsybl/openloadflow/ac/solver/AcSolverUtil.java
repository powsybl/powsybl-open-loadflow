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
import com.powsybl.openloadflow.network.LfAsymBus;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Fortescue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class AcSolverUtil {

    private AcSolverUtil() {
    }

    public static boolean isElementAsymBus(LfNetwork network, Variable<AcVariableType> v) {
        LfBus bus = network.getBus(v.getElementNum());
        LfAsymBus asymBus = bus.getAsym();
        return asymBus != null;
    }

    public static void initStateVector(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem, VoltageInitializer initializer) {
        double[] x = new double[equationSystem.getIndex().getSortedVariablesToFind().size()];
        for (Variable<AcVariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            switch (v.getType()) {
                case BUS_V:
                    x[v.getRow()] = initializer.getMagnitude(network.getBus(v.getElementNum()));
                    break;

                case BUS_PHI:
                    x[v.getRow()] = initializer.getAngle(network.getBus(v.getElementNum()));
                    if (isElementAsymBus(network, v)) {
                        x[v.getRow()] = UniformValueVoltageInitializer.getAngle(network.getBus(v.getElementNum()), Fortescue.SequenceType.POSITIVE);
                    }
                    break;

                case SHUNT_B:
                    x[v.getRow()] = network.getShunt(v.getElementNum()).getB();
                    break;

                case BRANCH_ALPHA1:
                    x[v.getRow()] = network.getBranch(v.getElementNum()).getPiModel().getA1();
                    break;

                case BRANCH_RHO1:
                    x[v.getRow()] = network.getBranch(v.getElementNum()).getPiModel().getR1();
                    break;

                case DUMMY_P:
                case DUMMY_Q:
                    x[v.getRow()] = 0;
                    break;

                case BUS_PHI_ZERO:
                    x[v.getRow()] = 0;
                    if (isElementAsymBus(network, v)) {
                        x[v.getRow()] = UniformValueVoltageInitializer.getAngle(network.getBus(v.getElementNum()), Fortescue.SequenceType.ZERO);
                    }
                    break;

                case BUS_PHI_NEGATIVE:
                    x[v.getRow()] = 0;
                    if (isElementAsymBus(network, v)) {
                        x[v.getRow()] = UniformValueVoltageInitializer.getAngle(network.getBus(v.getElementNum()), Fortescue.SequenceType.NEGATIVE);
                    }
                    break;

                case BUS_V_ZERO:
                    x[v.getRow()] = 0.1;
                    if (isElementAsymBus(network, v)) {
                        x[v.getRow()] = UniformValueVoltageInitializer.getMagnitude(network.getBus(v.getElementNum()), Fortescue.SequenceType.ZERO);
                    }
                    break;

                case BUS_V_NEGATIVE:
                    // when balanced, zero and negative sequence should be zero
                    // v_zero and v_negative initially set to zero will bring a singularity to the Jacobian
                    // We chose to set the initial value to a small one, but different from zero
                    // By construction if the system does not carry any asymmetry in its structure,
                    // the resolution of the system on the three sequences will bring a singularity
                    // Therefore, if the system is balanced by construction, we should run a balanced load flow only
                    x[v.getRow()] = 0.1;
                    if (isElementAsymBus(network, v)) {
                        x[v.getRow()] = UniformValueVoltageInitializer.getMagnitude(network.getBus(v.getElementNum()), Fortescue.SequenceType.NEGATIVE);
                    }
                    break;

                default:
                    throw new IllegalStateException("Unknown variable type " + v.getType());
            }
        }
        equationSystem.getStateVector().set(x);
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

                default:
                    throw new IllegalStateException("Unknown variable type " + v.getType());
            }
        }
    }
}
