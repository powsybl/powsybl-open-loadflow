package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Objects;

public class ShuntFortescuePowerActiveEquationTerm extends AbstractShuntFortescuePowerEquationTerm {

    public ShuntFortescuePowerActiveEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
    }

    @Override
    protected String getName() {
        return "ac_p_fortescue_shunt";
    }

    @Override
    public int getElementNum() {
        return bus.getNum(); // TODO : check if acceptable
    }

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

}
