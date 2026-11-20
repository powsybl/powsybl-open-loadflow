/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class AcStateVectorUtil {

    private AcStateVectorUtil() {
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

                case DUMMY_P,
                     DUMMY_Q,
                     BUS_PHI_ZERO,
                     BUS_PHI_NEGATIVE:
                    x[v.getRow()] = 0;
                    break;

                case BUS_V_ZERO,
                     BUS_V_NEGATIVE:
                    // when balanced, zero and negative sequence should be zero
                    // v_zero and v_negative initially set to zero will bring a singularity to the Jacobian
                    // We chose to set the initial value to a small one, but different from zero
                    // By construction if the system does not carry any asymmetry in its structure,
                    // the resolution of the system on the three sequences will bring a singularity
                    // Therefore, if the system is balanced by construction, we should run a balanced load flow only
                    x[v.getRow()] = 0.1;
                    break;

                case DC_BUS_V:
                    x[v.getRow()] = initializer.getDcVoltage(network.getDcBus(v.getElementNum()));
                    break;

                case CONV_P_AC:
                    double p = network.getVoltageSourceConverter(v.getElementNum()).getPac();
                    x[v.getRow()] = Double.isNaN(p) || !(initializer instanceof PreviousValueVoltageInitializer) ? 0.0 : p;
                    break;

                case CONV_Q_AC:
                    double q = network.getVoltageSourceConverter(v.getElementNum()).getQac();
                    x[v.getRow()] = Double.isNaN(q) || !(initializer instanceof PreviousValueVoltageInitializer) ? 0.0 : q;
                    break;
                default:
                    throw new IllegalStateException("Unknown variable type " + v.getType());
            }
        }
        equationSystem.getStateVector().set(x);
    }
}
