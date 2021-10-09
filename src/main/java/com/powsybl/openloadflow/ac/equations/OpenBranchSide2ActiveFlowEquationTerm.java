/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.NetworkBuffer;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenBranchSide2ActiveFlowEquationTerm extends AbstractOpenSide2BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v1Var;

    private double p1;

    private double dp1dv1;

    public OpenBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, VariableSet<AcVariableType> variableSet,
                                                 boolean deriveA1, boolean deriveR1) {
        super(branch, AcVariableType.BUS_V, bus1, variableSet, deriveA1, deriveR1);
        v1Var = variableSet.create(bus1.getNum(), AcVariableType.BUS_V);
    }

    @Override
    public void update(double[] x, NetworkBuffer<AcVariableType, AcEquationType> buf) {
        AcNetworkBuffer acBuf = (AcNetworkBuffer) buf;
        double v1 = x[acBuf.v1Row(num)];
        double r1 = acBuf.r1Row(num) != -1 ? x[acBuf.r1Row(num)] : buf.r1(num);
        double shunt = getShunt(buf);
        p1 = r1 * r1 * v1 * v1 * (buf.g1(num) + buf.y(num) * buf.y(num) * buf.g2(num) / shunt + (buf.b2(num) * buf.b2(num) + buf.g2(num) * buf.g2(num)) * buf.y(num) * buf.sinKsi(num) / shunt);
        dp1dv1 = 2 * r1 * r1 * v1 * (buf.g1(num) + buf.y(num) * buf.y(num) * buf.g2(num) / shunt + (buf.b2(num) * buf.b2(num) + buf.g2(num) * buf.g2(num)) * buf.y(num) * buf.sinKsi(num) / shunt);
    }

    @Override
    public double eval() {
        return p1;
    }

    @Override
    public double der(Variable<AcVariableType> variable, NetworkBuffer<AcVariableType, AcEquationType> buf) {
        AcNetworkBuffer acBuf = (AcNetworkBuffer) buf;
        if (variable.getType() == AcVariableType.BUS_V && variable.getRow() == acBuf.v1Row(num)) {
            return dp1dv1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_open_2";
    }
}
