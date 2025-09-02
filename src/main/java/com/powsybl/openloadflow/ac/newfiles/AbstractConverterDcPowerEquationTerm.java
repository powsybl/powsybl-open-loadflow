package com.powsybl.openloadflow.ac.newfiles;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractConverterDcPowerEquationTerm extends AbstractElementEquationTerm<LfAcDcConverter, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> vRVar;

    protected final Variable<AcVariableType> iConvVar;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected boolean isBipolar;

    protected AbstractConverterDcPowerEquationTerm(LfAcDcConverter converter, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(converter);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = AcVariableType.DC_NODE_V;
        AcVariableType iType = AcVariableType.CONV_I;
        v1Var = variableSet.getVariable(dcNode1.getNum(), vType);
        vRVar = variableSet.getVariable(dcNode2.getNum(), vType);
        iConvVar = variableSet.getVariable(converter.getNum(), iType);
        variables.add(v1Var);
        variables.add(vRVar);
        variables.add(iConvVar);
        isBipolar = true;
    }

    protected AbstractConverterDcPowerEquationTerm(LfAcDcConverter converter, LfDcNode dcNode1, VariableSet<AcVariableType> variableSet) {
        super(converter);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = AcVariableType.DC_NODE_V;
        AcVariableType iType = AcVariableType.CONV_I;
        v1Var = variableSet.getVariable(dcNode1.getNum(), vType);
        vRVar = null;
        iConvVar = variableSet.getVariable(converter.getNum(), iType);
        variables.add(v1Var);
        variables.add(iConvVar);
        isBipolar = false;
    }

    protected double v1() {
        return sv.get(v1Var.getRow());
    }

    protected double vR() {
        if (isBipolar) {
            return sv.get(vRVar.getRow());
        } else {
            return 0;
        }
    }

    protected double iConv() {
        return sv.get(iConvVar.getRow());
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}

