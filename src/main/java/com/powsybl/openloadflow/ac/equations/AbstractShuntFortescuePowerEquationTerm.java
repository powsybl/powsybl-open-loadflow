package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.List;

public abstract class AbstractShuntFortescuePowerEquationTerm extends AbstractShuntFortescueEquationTerm {

    private final List<Variable<AcVariableType>> variables;

    public AbstractShuntFortescuePowerEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
        variables = List.of(vVar);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

}
