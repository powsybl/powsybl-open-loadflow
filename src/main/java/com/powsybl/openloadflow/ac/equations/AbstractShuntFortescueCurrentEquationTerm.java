package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.List;

public abstract class AbstractShuntFortescueCurrentEquationTerm extends AbstractShuntFortescueEquationTerm {

    private final List<Variable<AcVariableType>> variables;

    public AbstractShuntFortescueCurrentEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
        variables = List.of(vVar, phVar);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
