package com.powsybl.openloadflow.ac.newfiles;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractClosedBranchDcFlowEquationTerm extends AbstractElementEquationTerm<LfDcLine, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected final double r;

    protected AbstractClosedBranchDcFlowEquationTerm(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = AcVariableType.DC_NODE_V;
        v1Var = variableSet.getVariable(dcNode1.getNum(), vType);
        v2Var = variableSet.getVariable(dcNode2.getNum(), vType);
        variables.add(v1Var);
        variables.add(v2Var);
        r = dcLine.getR() / (dcNode1.getNominalV() * dcNode2.getNominalV() / PerUnit.SB);
    }

    protected double v1() {
        return sv.get(v1Var.getRow());
    }

    protected double v2() {
        return sv.get(v2Var.getRow());
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}

