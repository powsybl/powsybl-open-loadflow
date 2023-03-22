package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.List;
import java.util.Objects;

public abstract class AbstractEquivalentShuntIyEquationTerm extends AbstractEquivalentShuntEquationTerm {

    private final List<Variable<AcVariableType>> variables;

    public AbstractEquivalentShuntIyEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
        variables = List.of(vVar, phVar);
    }

    // By definition :
    // I is the current flowing out of the node in the shunt equipment
    // I = y.V with y = g+jb
    // Therefore Ix + jIy = g.Vx - b.Vy + j(g.Vy + b.Vx)
    // then Iy = g.Vmagnitude.sin(theta) + b.Vmagnitude.cos(theta)

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    // TODO : uniformize g and b with Ix
    protected double g() {
        return 1.;
    } // TODO : check acceptable large value for target V close to zero

    protected double b() {
        return 1.;
    } // TODO : check acceptable large value for target V close to zero

    private static double iy(double v, double phi, double g, double b) {
        return g * v * Math.sin(phi) + b * v * Math.cos(phi);
    }

    private static double diydv(double phi, double g, double b) {
        return g * Math.sin(phi) + b * Math.cos(phi);
    }

    private static double diydph(double v, double phi, double g, double b) {
        return g * v * Math.cos(phi) - b * v * Math.sin(phi);
    }

    @Override
    public double eval() {
        return iy(v(), ph(), g(), b());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return diydv(ph(), g(), b());
        } else if (variable.equals(phVar)) {
            return diydph(v(), ph(), g(), b());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }
}
