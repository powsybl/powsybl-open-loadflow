package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Objects;

public class ShuntFortescuePowerReactiveEquationTerm extends AbstractShuntFortescuePowerEquationTerm {

    public ShuntFortescuePowerReactiveEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
    }

    @Override
    protected String getName() {
        return "ac_q_fortescue_shunt";
    }

    private static double q(double v, double b) {
        return -b * v * v;
    }

    private static double dqdv(double v, double b) {
        return -2 * b * v;
    }

    @Override
    public double eval() {
        return q(v(), b());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dqdv(v(), b());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

}
