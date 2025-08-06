package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AbstractBranchAcFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractClosedBranchDcFlowEquationTermV2 extends AbstractBranchAcFlowEquationTerm {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    public static AcVariableType getVoltageMagnitudeType(Fortescue.SequenceType sequenceType) {
        return switch (sequenceType) {
            case POSITIVE -> AcVariableType.DC_NODE_V;
            case NEGATIVE -> AcVariableType.BUS_V_NEGATIVE;
            case ZERO -> AcVariableType.BUS_V_ZERO;
        };
    }

    protected AbstractClosedBranchDcFlowEquationTermV2(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        super(dcLine);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = getVoltageMagnitudeType(sequenceType);
        v1Var = variableSet.getVariable(dcNode1.getNum(), vType);
        v2Var = variableSet.getVariable(dcNode2.getNum(), vType);
        variables.add(v1Var);
        variables.add(v2Var);
    }

    protected double v1() {
        return sv.get(v1Var.getRow());
    }

    protected double v2() {
        return sv.get(v2Var.getRow());
    }

    protected abstract double calculateSensi(double dv1, double dv2);

    @Override
    public double calculateSensi(DenseMatrix dx, int column) {
        Objects.requireNonNull(dx);
        double dv1 = dx.get(v1Var.getRow(), column);
        double dv2 = dx.get(v2Var.getRow(), column);
        return calculateSensi(dv1, dv2);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}

