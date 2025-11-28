/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Derivative;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractClosedBranchEquationTermArrayEvaluator extends AbstractBranchEquationTermArrayEvaluator implements ClosedBranchEquationTermArrayEvaluator {

    protected final AcBusVector busVector;

    protected AbstractClosedBranchEquationTermArrayEvaluator(AcBranchVector branchVector, AcBusVector busVector, VariableSet<AcVariableType> variableSet) {
        super(branchVector, variableSet);
        this.busVector = Objects.requireNonNull(busVector);
    }

    @Override
    public double calculateSensi(int branchNum, DenseMatrix dx, int column) {
        Objects.requireNonNull(dx);
        double dph1 = dx.get(branchVector.ph1Row[branchNum], column);
        double dph2 = dx.get(branchVector.ph2Row[branchNum], column);
        double dv1 = dx.get(branchVector.v1Row[branchNum], column);
        double dv2 = dx.get(branchVector.v2Row[branchNum], column);
        int a1Row = branchVector.a1Row[branchNum];
        double da1 = a1Row != -1 ? dx.get(a1Row, column) : 0;
        int r1Row = branchVector.r1Row[branchNum];
        double dr1 = r1Row != -1 ? dx.get(r1Row, column) : 0;
        return calculateSensi(branchNum, dph1, dph2, dv1, dv2, da1, dr1);
    }

    protected abstract double calculateSensi(int branchNum, double dph1, double dph2, double dv1, double dv2, double da1, double dr1);

    @Override
    public List<Derivative<AcVariableType>> getDerivatives(int branchNum) {
        int bus1Num = branchVector.bus1Num[branchNum];
        int bus2Num = branchVector.bus2Num[branchNum];
        boolean deriveA1 = branchVector.deriveA1[branchNum];
        boolean deriveR1 = branchVector.deriveR1[branchNum];
        List<Derivative<AcVariableType>> derivatives = new ArrayList<>(6);
        derivatives.add(new Derivative<>(variableSet.getVariable(bus1Num, AcVariableType.BUS_V), 0));
        derivatives.add(new Derivative<>(variableSet.getVariable(bus2Num, AcVariableType.BUS_V), 1));
        derivatives.add(new Derivative<>(variableSet.getVariable(bus1Num, AcVariableType.BUS_PHI), 2));
        derivatives.add(new Derivative<>(variableSet.getVariable(bus2Num, AcVariableType.BUS_PHI), 3));
        if (deriveA1) {
            derivatives.add(new Derivative<>(variableSet.getVariable(branchNum, AcVariableType.BRANCH_ALPHA1), 4));
        }
        if (deriveR1) {
            derivatives.add(new Derivative<>(variableSet.getVariable(branchNum, AcVariableType.BRANCH_RHO1), 5));
        }
        return derivatives;
    }

    public double r1(int branchNum) {
        return branchVector.r1State[branchNum];
    }

    public double a1(int branchNum) {
        return branchVector.a1State[branchNum];
    }

    public Variable<AcVariableType> getV1Var(int branchNum) {
        return variableSet.getVariable(branchVector.bus1Num[branchNum], AcVariableType.BUS_V);
    }

    public Variable<AcVariableType> getV2Var(int branchNum) {
        return variableSet.getVariable(branchVector.bus2Num[branchNum], AcVariableType.BUS_V);
    }

    public Variable<AcVariableType> getPhi1Var(int branchNum) {
        return variableSet.getVariable(branchVector.bus1Num[branchNum], AcVariableType.BUS_PHI);
    }

    public Variable<AcVariableType> getPhi2Var(int branchNum) {
        return variableSet.getVariable(branchVector.bus2Num[branchNum], AcVariableType.BUS_PHI);
    }

    public Variable<AcVariableType> getA1Var(int branchNum) {
        return variableSet.getVariable(branchNum, AcVariableType.BRANCH_ALPHA1);
    }

    public Variable<AcVariableType> getR1Var(int branchNum) {
        return variableSet.getVariable(branchNum, AcVariableType.BRANCH_RHO1);
    }
}
