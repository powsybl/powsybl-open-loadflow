package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.Objects;

public class ConverterDcPowerEquationTerm extends AbstractConverterDcPowerEquationTerm {

    public ConverterDcPowerEquationTerm(LfAcDcConverter converter, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(converter, dcNode1, dcNode2, variableSet);
    }

    public ConverterDcPowerEquationTerm(LfAcDcConverter converter, LfDcNode dcNode1, VariableSet<AcVariableType> variableSet) {
        super(converter, dcNode1, variableSet);
    }

    public static double pConv(double v1, double vR, double iConv) {
        return iConv * (v1 - vR);
    }

    public static double dpConvdv1(double iConv) {
        return -iConv;
    }

    public static double dpConvdvR(double iConv) {
        return iConv;
    }

    public static double dpConvdiConv(double v1, double vR) {
        //TODO: find a better way to avoid V initialization error
        //V initialization set v1 = 1 and vR = 1 so there is a problem
        if (v1 == vR) {
            vR = vR + 0.1;
        }
        return v1 - vR;
    }

    @Override
    public double eval() {
        return pConv(v1(), vR(), iConv());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dpConvdv1(iConv());
        } else if (variable.equals(vRVar)) {
            return dpConvdvR(iConv());
        } else if (variable.equals(iConvVar)) {
            return dpConvdiConv(v1(), vR());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "conv_power_balance";
    }
}
