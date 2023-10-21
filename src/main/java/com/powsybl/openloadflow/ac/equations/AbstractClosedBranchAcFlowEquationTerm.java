/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractClosedBranchAcFlowEquationTerm extends AbstractBranchAcFlowEquationTerm {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final Variable<AcVariableType> ph1Var;

    protected final Variable<AcVariableType> ph2Var;

    protected Variable<AcVariableType> a1Var;

    protected Variable<AcVariableType> r1Var;

    protected final List<Variable<AcVariableType>> variables;

    private static AcVariableType getVoltageMagnitudeType(Fortescue.SequenceType sequenceType) {
        return switch (sequenceType) {
            case POSITIVE -> AcVariableType.BUS_V;
            case NEGATIVE -> AcVariableType.BUS_V_NEGATIVE;
            case ZERO -> AcVariableType.BUS_V_ZERO;
        };
    }

    private static AcVariableType getVoltageAngleType(Fortescue.SequenceType sequenceType) {
        return switch (sequenceType) {
            case POSITIVE -> AcVariableType.BUS_PHI;
            case NEGATIVE -> AcVariableType.BUS_PHI_NEGATIVE;
            case ZERO -> AcVariableType.BUS_PHI_ZERO;
        };
    }

    protected AbstractClosedBranchAcFlowEquationTerm(AcBranchVector branchVector, int branchNum, int bus1Num, int bus2Num,
                                                     VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1,
                                                     Fortescue.SequenceType sequenceType) {
        super(branchVector, branchNum);
        Objects.requireNonNull(variableSet);
        variables = createVariable(branchVector, branchNum, variableSet, deriveA1, deriveR1, sequenceType);
        v1Var = variables.get(0);
        v2Var = variables.get(1);
        ph1Var = variables.get(2);
        ph2Var = variables.get(3);
        if (deriveA1) {
            a1Var = variables.get(4);
            if (deriveR1) {
                r1Var = variables.get(5);
            }
        } else if (deriveR1) {
            r1Var = variables.get(4);
        }
    }

    public static List<Variable<AcVariableType>> createVariable(AcBranchVector branchVector, int branchNum,
                                                                VariableSet<AcVariableType> variableSet, boolean deriveA1, boolean deriveR1,
                                                                Fortescue.SequenceType sequenceType) {
        AcVariableType vType = getVoltageMagnitudeType(sequenceType);
        AcVariableType angleType = getVoltageAngleType(sequenceType);
        int bus1Num = branchVector.bus1Num[branchNum];
        int bus2Num = branchVector.bus2Num[branchNum];
        List<Variable<AcVariableType>> variables = new ArrayList<>(6);
        variables.add(variableSet.getVariable(bus1Num, vType));
        variables.add(variableSet.getVariable(bus2Num, vType));
        variables.add(variableSet.getVariable(bus1Num, angleType));
        variables.add(variableSet.getVariable(bus2Num, angleType));
        if (deriveA1) {
            variables.add(variableSet.getVariable(branchNum, AcVariableType.BRANCH_ALPHA1));
        }
        if (deriveR1) {
            variables.add(variableSet.getVariable(branchNum, AcVariableType.BRANCH_RHO1));
        }
        return variables;
    }

    public Variable<AcVariableType> getA1Var() {
        return a1Var;
    }

    protected double v1() {
        return sv.get(v1Var.getRow());
    }

    protected double v2() {
        return sv.get(v2Var.getRow());
    }

    protected double ph1() {
        return sv.get(ph1Var.getRow());
    }

    protected double ph2() {
        return sv.get(ph2Var.getRow());
    }

    protected double r1() {
        return r1Var != null ? sv.get(r1Var.getRow()) : branchVector.r1[num];
    }

    protected double a1() {
        return a1Var != null ? sv.get(a1Var.getRow()) : branchVector.a1[num];
    }

    public static double theta1(double ksi, double ph1, double a1, double ph2) {
        return ksi - a1 + A2 - ph1 + ph2;
    }

    public static double theta2(double ksi, double ph1, double a1, double ph2) {
        return ksi + a1 - A2 + ph1 - ph2;
    }

    protected abstract double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1);

    @Override
    public double calculateSensi(DenseMatrix dx, int column) {
        Objects.requireNonNull(dx);
        double dph1 = dx.get(ph1Var.getRow(), column);
        double dph2 = dx.get(ph2Var.getRow(), column);
        double dv1 = dx.get(v1Var.getRow(), column);
        double dv2 = dx.get(v2Var.getRow(), column);
        double da1 = a1Var != null ? dx.get(a1Var.getRow(), column) : 0;
        double dr1 = r1Var != null ? dx.get(r1Var.getRow(), column) : 0;
        return calculateSensi(dph1, dph2, dv1, dv2, da1, dr1);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
