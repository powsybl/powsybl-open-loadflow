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
import com.powsybl.openloadflow.util.Fortescue;
import net.jafama.FastMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractClosedBranchAcFlowEquationTerm extends AbstractBranchAcFlowEquationTerm {

    public enum FlowType {
        I1X,
        I1Y,
        I2X,
        I2Y;
    }

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final Variable<AcVariableType> ph1Var;

    protected final Variable<AcVariableType> ph2Var;

    protected final Variable<AcVariableType> a1Var;

    protected final Variable<AcVariableType> r1Var;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

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

    protected AbstractClosedBranchAcFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1, Fortescue.SequenceType sequenceType) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = getVoltageMagnitudeType(sequenceType);
        AcVariableType angleType = getVoltageAngleType(sequenceType);
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

    public static DenseMatrix getCartesianVoltageVector(double v1, double ph1, double v2, double ph2) {
        DenseMatrix mV = new DenseMatrix(4, 1);
        mV.add(0, 0, v1 * FastMath.cos(ph1));
        mV.add(1, 0, v1 * FastMath.sin(ph1));
        mV.add(2, 0, v2 * FastMath.cos(ph2));
        mV.add(3, 0, v2 * FastMath.sin(ph2));

        return mV;
    }

    public static int getIndexline(ClosedBranchTfoNegativeIflowEquationTerm.FlowType flowType) {
        switch (flowType) {
            case I1X:
                return 0;

            case I1Y:
                return 1;

            case I2X:
                return 2;

            case I2Y:
                return 3;

            default:
                throw new IllegalStateException("Unknown flow type at branch : ??? ");
        }
    }

    public DenseMatrix getdVdx(Variable<AcVariableType> variable) {
        double dv1x = 0;
        double dv1y = 0;
        double dv2x = 0;
        double dv2y = 0;
        if (variable.equals(v1Var)) {
            dv1x = FastMath.cos(ph1());
            dv1y = FastMath.sin(ph1());
        } else if (variable.equals(v2Var)) {
            dv2x = FastMath.cos(ph2());
            dv2y = FastMath.sin(ph2());
        } else if (variable.equals(ph1Var)) {
            dv1x = -v1() * FastMath.sin(ph1());
            dv1y = v1() * FastMath.cos(ph1());
        } else if (variable.equals(ph2Var)) {
            dv2x = -v2() * FastMath.sin(ph2());
            dv2y = v2() * FastMath.cos(ph2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }

        DenseMatrix mdV = new DenseMatrix(4, 1);
        mdV.add(0, 0, dv1x);
        mdV.add(1, 0, dv1y);
        mdV.add(2, 0, dv2x);
        mdV.add(3, 0, dv2y);

        return mdV;
    }

}
