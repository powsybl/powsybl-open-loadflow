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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractClosedBranchAcFlowEquationTerm extends AbstractBranchAcFlowEquationTerm {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final Variable<AcVariableType> ph1Var;

    protected final Variable<AcVariableType> ph2Var;

    protected final Variable<AcVariableType> a1Var;

    protected final Variable<AcVariableType> r1Var;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected AbstractClosedBranchAcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1, Fortescue.SequenceType sequenceType) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);

        v1Var = createV1Var(bus1, variableSet, sequenceType);
        ph1Var = createPh1Var(bus1, variableSet, sequenceType);
        v2Var = createV2Var(bus2, variableSet, sequenceType);
        ph2Var = createPh2Var(bus2, variableSet, sequenceType);

        a1Var = deriveA1 ? variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_ALPHA1) : null;
        r1Var = deriveR1 ? variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_RHO1) : null;
        if (v1Var != null) {
            variables.add(v1Var);
        }
        if (v2Var != null) {
            variables.add(v2Var);
        }
        if (ph1Var != null) {
            variables.add(ph1Var);
        }
        if (ph2Var != null) {
            variables.add(ph2Var);
        }
        if (a1Var != null) {
            variables.add(a1Var);
        }
        if (r1Var != null) {
            variables.add(r1Var);
        }
    }

    protected Variable<AcVariableType> createV1Var(LfBus bus1, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        return variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V);
    }

    protected Variable<AcVariableType> createPh1Var(LfBus bus1, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        return variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI);
    }

    protected Variable<AcVariableType> createV2Var(LfBus bus2, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        return variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V);
    }

    protected Variable<AcVariableType> createPh2Var(LfBus bus2, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        return variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI);
    }

    public Variable<AcVariableType> getA1Var() {
        return a1Var;
    }

    protected double v1() {
        if (v1Var == null) {
            return 0.;
        }
        return sv.get(v1Var.getRow());
    }

    protected double v2() {
        if (v2Var == null) {
            return 0.;
        }
        return sv.get(v2Var.getRow());
    }

    protected double ph1() {
        if (ph1Var == null) {
            return 0.;
        }
        return sv.get(ph1Var.getRow());
    }

    protected double ph2() {
        if (ph2Var == null) {
            return 0.;
        }
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
        double da1 = a1Var != null ? dx.get(a1Var.getRow(), column) : 0;
        double dr1 = r1Var != null ? dx.get(r1Var.getRow(), column) : 0;
        return calculateSensi(dph1, dph2, dv1, dv2, da1, dr1);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
