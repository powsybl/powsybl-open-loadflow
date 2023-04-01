package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShuntFortescueIxEquationTerm extends AbstractShuntFortescueCurrentEquationTerm {

    public ShuntFortescueIxEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
    }
    // By definition :
    // I is the current flowing out of the node in the shunt equipment
    // I = y.V with y = g+jb
    // Therefore Ix + jIy = g.Vx - b.Vy + j(g.Vy + b.Vx)
    // then Ix = g.Vmagnitude.cos(theta) - b.Vmagnitude.sin(theta)

    private static double ix(double v, double phi, double g, double b) {
        return g * v * Math.cos(phi) - b * v * Math.sin(phi);
    }

    private static double dixdv(double phi, double g, double b) {
        return g * Math.cos(phi) - b * Math.sin(phi);
    }

    private static double dixdph(double v, double phi, double g, double b) {
        return -g * v * Math.sin(phi) - b * v * Math.cos(phi);
    }

    @Override
    public double eval() {
        return ix(v(), ph(), g(), b());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dixdv(ph(), g(), b());
        } else if (variable.equals(phVar)) {
            return dixdph(v(), ph(), g(), b());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_ix_fortescue_shunt";
    }
}
