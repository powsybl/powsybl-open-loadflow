/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractClosedBranchAcFlowEquationTerm extends AbstractBranchAcFlowEquationTerm {

    protected final ClosedBranchAcVariables variables;

    protected AbstractClosedBranchAcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1, Fortescue.SequenceType sequenceType) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        variables = new ClosedBranchAcVariables(branch.getNum(), bus1.getNum(), bus2.getNum(), variableSet, deriveA1, deriveR1, sequenceType);
    }

    public Variable<AcVariableType> getA1Var() {
        return variables.getA1Var();
    }

    protected double v1() {
        return sv.get(variables.getV1Var().getRow());
    }

    protected double v2() {
        return sv.get(variables.getV2Var().getRow());
    }

    protected double ph1() {
        return sv.get(variables.getPh1Var().getRow());
    }

    protected double ph2() {
        return sv.get(variables.getPh2Var().getRow());
    }

    protected double r1() {
        var r1Var = variables.getR1Var();
        return r1Var != null ? sv.get(r1Var.getRow()) : element.getPiModel().getR1();
    }

    protected double a1() {
        var a1Var = variables.getA1Var();
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
        double dph1 = dx.get(variables.getPh1Var().getRow(), column);
        double dph2 = dx.get(variables.getPh2Var().getRow(), column);
        double dv1 = dx.get(variables.getV1Var().getRow(), column);
        double dv2 = dx.get(variables.getV2Var().getRow(), column);
        var a1Var = variables.getA1Var();
        double da1 = a1Var != null ? dx.get(a1Var.getRow(), column) : 0;
        var r1Var = variables.getR1Var();
        double dr1 = r1Var != null ? dx.get(r1Var.getRow(), column) : 0;
        return calculateSensi(dph1, dph2, dv1, dv2, da1, dr1);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables.getVariables();
    }
}
