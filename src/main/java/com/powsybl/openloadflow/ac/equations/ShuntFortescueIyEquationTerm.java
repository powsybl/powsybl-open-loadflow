package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShuntFortescueIyEquationTerm extends AbstractShuntFortescueCurrentEquationTerm {

    public ShuntFortescueIyEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
    }

    // By definition :
    // I is the current flowing out of the node in the shunt equipment
    // I = y.V with y = g+jb
    // Therefore Ix + jIy = g.Vx - b.Vy + j(g.Vy + b.Vx)
    // then Iy = g.Vmagnitude.sin(theta) + b.Vmagnitude.cos(theta)

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

    @Override
    protected String getName() {
        return "ac_iy_fortescue_shunt";
    }
}
