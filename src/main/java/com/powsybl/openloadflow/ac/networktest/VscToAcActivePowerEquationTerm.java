package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.List;
import java.util.Objects;

public class VscToAcActivePowerEquationTerm extends AbstractVscToAcEquationTerm {

    private final List<Variable<AcVariableType>> variables;

    public VscToAcActivePowerEquationTerm(LfDcNode vscDcNode, LfBus bus, VariableSet<AcVariableType> variableSet) {
        super(vscDcNode, bus, variableSet);
        variables = List.of(pDcVar);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    public static double pac(double pdc) {
        return pdc / PerUnit.SB;
    } //TODO : check convention for sign of pac

    @Override
    public double eval() {
        return pac(pdc());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(pDcVar)) {
            return 1 / PerUnit.SB; //TODO : check convention for sign of pac
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_vsc";
    }

}
