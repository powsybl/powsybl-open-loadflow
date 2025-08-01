package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.List;
import java.util.Objects;

public class VscToAcActivePowerEquationTerm extends AbstractVscToAcEquationTerm {

    private final List<Variable<AcVariableType>> variables;

    public VscToAcActivePowerEquationTerm(LfDcNode vscDcNode, LfBus bus, VariableSet<AcVariableType> variableSet) {
        super(vscDcNode, bus, variableSet);
        variables = List.of(pDcVar);
    }

    public static double pac(double pdc) {
        return pdc;
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    @Override
    public double eval() {
        return pac(pdc());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(pDcVar)) {
            return 1.0;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_vsc";
    }
}
