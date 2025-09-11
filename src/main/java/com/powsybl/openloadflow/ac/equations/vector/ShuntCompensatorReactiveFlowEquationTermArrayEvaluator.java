/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ShuntCompensatorReactiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.Derivative;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class ShuntCompensatorReactiveFlowEquationTermArrayEvaluator extends AbstractShuntCompensatorEquationTermArrayEvaluator {

    private final AcBusVector busVector;

    public ShuntCompensatorReactiveFlowEquationTermArrayEvaluator(AcShuntVector shuntVector, AcBusVector busVector, VariableSet<AcVariableType> variableSet) {
        super(shuntVector, variableSet);
        this.busVector = Objects.requireNonNull(busVector);
    }

    @Override
    public String getName() {
        return "ac_q_array_shunt";
    }

    @Override
    public double calculateSensi(int shuntNum, DenseMatrix dx, int column) {
        int busNum = shuntVector.busNum[shuntNum];
        int vRow = busNum != -1 ? busVector.vRow[busNum] : -1;
        double dv = dx.get(vRow, column);
        int bRow = shuntVector.bRow[shuntNum];
        double db = bRow != -1 ? dx.get(bRow, column) : 0;
        double v = busVector.v[shuntVector.busNum[shuntNum]];
        double b = shuntVector.b[shuntNum];
        return ShuntCompensatorReactiveFlowEquationTerm.calculateSensi(v, b, dv, db);
    }

    @Override
    public double[] eval() {
        return shuntVector.q;
    }

    @Override
    public double eval(int shuntNum) {
        return shuntVector.q[shuntNum];
    }

    @Override
    public double[][] evalDer() {
        return new double[][] {
            shuntVector.dqdv,
            shuntVector.dqdb
        };
    }

    @Override
    public List<Derivative<AcVariableType>> getDerivatives(int shuntNum) {
        if (shuntVector.deriveB[shuntNum]) {
            return List.of(new Derivative<>(variableSet.getVariable(shuntVector.busNum[shuntNum], AcVariableType.BUS_V), 0),
                           new Derivative<>(variableSet.getVariable(shuntNum, AcVariableType.SHUNT_B), 1));
        } else {
            return super.getDerivatives(shuntNum);
        }
    }
}
