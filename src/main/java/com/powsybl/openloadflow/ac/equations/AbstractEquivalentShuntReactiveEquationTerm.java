package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.List;
import java.util.Objects;

public abstract class AbstractEquivalentShuntReactiveEquationTerm extends AbstractEquivalentShuntEquationTerm {

    protected final List<Variable<AcVariableType>> variables;

    public AbstractEquivalentShuntReactiveEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
        variables = List.of(vVar);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    protected double b() {
        return 1;
    } // TODO : check acceptable large value for target V close to zero

    private static double p(double v, double b) {
        return b * v * v;
    }

    private static double dpdv(double v, double b) {
        return 2 * b * v;
    }
    // TODO : MAKE A SIGN CORRECTION

    @Override
    public double eval() {
        return p(v(), b());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dpdv(v(), b());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

}
