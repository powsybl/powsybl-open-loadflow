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
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public abstract class AbstractClosedBranchDisymDecoupledFlowEquationTerm extends AbstractBranchAcFlowEquationTerm {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final Variable<AcVariableType> ph1Var;

    protected final Variable<AcVariableType> ph2Var;

    protected final Variable<AcVariableType> a1Var;

    protected final Variable<AcVariableType> r1Var;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected AbstractClosedBranchDisymDecoupledFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                                 boolean deriveA1, boolean deriveR1, DisymAcSequenceType sequenceType) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = null;
        AcVariableType angleType = null;
        switch (sequenceType) {
            case HOMOPOLAR:
                vType = AcVariableType.BUS_V_HOMOPOLAR;
                angleType = AcVariableType.BUS_PHI_HOMOPOLAR;
                break;

            case DIRECT:
                vType = AcVariableType.BUS_V;
                angleType = AcVariableType.BUS_PHI;
                break;

            case INVERSE:
                vType = AcVariableType.BUS_V_INVERSE;
                angleType = AcVariableType.BUS_PHI_INVERSE;
                break;

            default:
                throw new IllegalStateException("Unknown sequence type " + sequenceType);
        }
        v1Var = variableSet.getVariable(bus1.getNum(), vType);
        v2Var = variableSet.getVariable(bus2.getNum(), vType);
        ph1Var = variableSet.getVariable(bus1.getNum(), angleType);
        ph2Var = variableSet.getVariable(bus2.getNum(), angleType);
        a1Var = deriveA1 ? variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_ALPHA1) : null;
        r1Var = deriveR1 ? variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_RHO1) : null;
        variables.add(v1Var);
        variables.add(v2Var);
        variables.add(ph1Var);
        variables.add(ph2Var);
        if (a1Var != null) {
            variables.add(a1Var);
        }
        if (r1Var != null) {
            variables.add(r1Var);
        }
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
        return r1Var != null ? sv.get(r1Var.getRow()) : element.getPiModel().getR1();
    }

    protected double a1() {
        return a1Var != null ? sv.get(a1Var.getRow()) : element.getPiModel().getA1();
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
        double da1 = a1Var != null ? dx.get(a1Var.getRow(), column) : element.getPiModel().getA1();
        double dr1 = r1Var != null ? dx.get(r1Var.getRow(), column) : element.getPiModel().getR1();
        return calculateSensi(dph1, dph2, dv1, dv2, da1, dr1);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
