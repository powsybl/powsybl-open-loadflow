package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AbstractBranchAcFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractClosedBranchDcFlowDMREquationTerm extends AbstractBranchAcFlowEquationTerm {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final Variable<AcVariableType> v1RVar;

    protected final Variable<AcVariableType> v2RVar;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected final double r;

    protected AbstractClosedBranchDcFlowDMREquationTerm(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = AcVariableType.DC_NODE_V;
        v1Var = variableSet.getVariable(dcNode1.getNum(), vType);
        v2Var = variableSet.getVariable(dcNode2.getNum(), vType);
        v1RVar = variableSet.getVariable(dcNode1.getDcNodeR().getNum(), vType);
        v2RVar = variableSet.getVariable(dcNode2.getDcNodeR().getNum(), vType);
        variables.add(v1Var);
        variables.add(v2Var);
        variables.add(v1RVar);
        variables.add(v2RVar);
        r = dcLine.getR()/ (dcNode1.getNominalV()* dcNode2.getNominalV()/PerUnit.SB);
    }

    protected double v1() {
        return sv.get(v1Var.getRow());
    }

    protected double v1R() {
        return 0;
//        return sv.get(v1RVar.getRow());
    }

    protected double v2() {
        return sv.get(v2Var.getRow());
    }

    protected double v2R() {
        return 0;
//        return sv.get(v2RVar.getRow());
    }


    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}

