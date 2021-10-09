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

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenBranchSide1ReactiveFlowEquationTerm extends AbstractOpenSide1BranchAcFlowEquationTerm {

    private final Variable<AcVariableType> v2Var;

    private double q2;

    private double dq2dv2;

    public OpenBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                   boolean deriveA1, boolean deriveR1) {
        super(branch, AcVariableType.BUS_V, bus2, variableSet, deriveA1, deriveR1);
        v2Var = variableSet.create(bus2.getNum(), AcVariableType.BUS_V);
    }

    @Override
    public void update(double[] x, NetworkBuffer<AcVariableType, AcEquationType> buf) {
        AcNetworkBuffer acBuf = (AcNetworkBuffer) buf;
        double v2 = x[acBuf.v2Row[num]];
        double shunt = getShunt(buf);
        q2 = -R2 * R2 * v2 * v2 * (buf.b2[num] + buf.y[num] * buf.y[num] * buf.b1[num] / shunt - (buf.b1[num] * buf.b1[num] + buf.g1[num] * buf.g1[num]) * buf.y[num] * buf.cosKsi[num] / shunt);
        dq2dv2 = -2 * v2 * R2 * R2 * (buf.b2[num] + buf.y[num] * buf.y[num] * buf.b1[num] / shunt - (buf.b1[num] * buf.b1[num] + buf.g1[num] * buf.g1[num]) * buf.y[num] * buf.cosKsi[num] / shunt);
    }

    @Override
    public double eval() {
        return q2;
    }

    @Override
    public double der(Variable<AcVariableType> variable, NetworkBuffer<AcVariableType, AcEquationType> buf) {
        AcNetworkBuffer acBuf = (AcNetworkBuffer) buf;
        if (variable.getType() == AcVariableType.BUS_V && variable.getRow() == acBuf.v2Row[num]) {
            return dq2dv2;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_q_open_1";
    }
}
