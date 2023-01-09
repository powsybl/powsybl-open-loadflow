/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.Vectors;
import com.powsybl.openloadflow.network.ElementType;
import net.jafama.FastMath;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DefaultNewtonRaphsonStoppingCriteria implements NewtonRaphsonStoppingCriteria {

    /**
     * Convergence epsilon per equation: 10^-4 in p.u => 10^-2 in Kv, Mw or MVar
     */
    public static final double DEFAULT_CONV_EPS_PER_EQ = Math.pow(10, -4);

    // TODO validate that value by default is correct
    private final double maxVoltageAngleMismatch = Math.pow(10, -4);

    private final double maxVoltageMismatch = Math.pow(10, -4);
    private final double maxReactivePowerMismatch = Math.pow(10, -4);
    private final double maxActivePowerMismatch  = Math.pow(10, -4);


    private final double convEpsPerEq;

    public DefaultNewtonRaphsonStoppingCriteria() {
        this(DEFAULT_CONV_EPS_PER_EQ);
    }

    public DefaultNewtonRaphsonStoppingCriteria(double convEpsPerEq) {
        this.convEpsPerEq = convEpsPerEq;
    }

    @Override
    public TestResult test(double[] fx) {
        return new TestResult(stop(fx), norm(fx));
    }

    private double norm(double[] fx) {
        // calculate norm L2 of equations mismatch vector
        return Vectors.norm2(fx);
    }

    private boolean stop(double[] fx) {
        return norm(fx) < FastMath.sqrt(convEpsPerEq * convEpsPerEq * fx.length);
    }

    private boolean stop_improved(double[] fx, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        for(var eq : equationSystem.getIndex().getSortedEquationsToSolve()) {
            var type = eq.getType();
            var value = fx[eq.getColumn()];
            switch (type){
                case BUS_TARGET_V:
                case BUS_TARGET_V_WITH_SLOPE:
                case ZERO_V:
                    if (value > maxVoltageMismatch)
                        return false;
                    break;
                case BUS_TARGET_P:
                case BRANCH_TARGET_P:
                case DUMMY_TARGET_P:
                    if (value > maxActivePowerMismatch)
                        return false;
                    break;
                case BUS_TARGET_Q:
                case BRANCH_TARGET_Q:
                case DISTR_Q:
                case DUMMY_TARGET_Q:
                    if (value > maxReactivePowerMismatch)
                        return false;
                    break;
                case BUS_TARGET_PHI:
                case ZERO_PHI:
                    if (value > maxVoltageAngleMismatch)
                        return false;
                    break;
                case BRANCH_TARGET_RHO1:
                case DISTR_RHO:
                    //FIXME value compared should be "transformer voltage control ratio"
                    if (value > 0)
                        return false;
                    break;
                case SHUNT_TARGET_B:
                    //FIXME value compared should be ""shunt susceptance""
                    if (value > 0)
                        return false;
                    break;
                case BRANCH_TARGET_ALPHA1:
                    //FIXME value compared should be "phase shifter constant shift"
                    if (value > 0)
                        return false;
                    break;
                case DISTR_SHUNT_B:
                    //FIXME value compared should be "shunt remote voltage control susceptance distribution"
                    if (value > 0)
                        return false;
                    break;
                default:
                    //FIXME add a raise error because unknown type
            }
        }
        return true;
    }

}
