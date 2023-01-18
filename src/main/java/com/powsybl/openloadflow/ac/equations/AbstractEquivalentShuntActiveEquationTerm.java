package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.List;
import java.util.Objects;

public abstract class AbstractEquivalentShuntActiveEquationTerm extends AbstractEquivalentShuntEquationTerm {

    private final List<Variable<AcVariableType>> variables;

    public AbstractEquivalentShuntActiveEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
        variables = List.of(vVar);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    protected double g() {
        return 1.;
    } // TODO : check acceptable large value for target V close to zero

    private static double p(double v, double g) {
        return g * v * v;
    }

    private static double dpdv(double v, double g) {
        return 2 * g * v;
    }

    @Override
    public double eval() {
        return p(v(), g());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dpdv(v(), g());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    /*@Override
    protected String getName() {
        return "ac_q_gen_shunt";
    }*/
}
