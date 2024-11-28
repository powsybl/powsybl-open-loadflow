package com.powsybl.openloadflow.equations;

public class BusPowerEquation<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends Equation<V, E> {

    BusPowerEquation(int elementNum, E type, EquationSystem<V, E> equationSystem) {
        super(elementNum, type, equationSystem);
    }

    /**
     * Sum the terms of the equation with the rhs. Represents the power balance equation of a bus.
     */
    @Override
    public double eval() {
        double value = 0;
        for (EquationTerm<V, E> term : getTerms()) {
            if (term.isActive()) {
                value += term.eval();
            }
        }
        return value;
    }
}
