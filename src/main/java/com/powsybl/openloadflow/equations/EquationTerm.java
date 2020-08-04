/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.util.Evaluable;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Objects;

/**
 * An equation term, i.e part of the equation sum.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface EquationTerm extends Evaluable {

    class MultiplyByScalarEquationTerm implements EquationTerm {

        private final EquationTerm term;

        private final double scalar;

        MultiplyByScalarEquationTerm(EquationTerm term, double scalar) {
            this.term = Objects.requireNonNull(term);
            this.scalar = scalar;
        }

        @Override
        public Equation getEquation() {
            return term.getEquation();
        }

        @Override
        public void setEquation(Equation equation) {
            term.setEquation(equation);
        }

        @Override
        public void setActive(boolean active) {
            term.setActive(active);
        }

        @Override
        public boolean isActive() {
            return term.isActive();
        }

        @Override
        public List<Variable> getVariables() {
            return term.getVariables();
        }

        @Override
        public void update(double[] x) {
            term.update(x);
        }

        @Override
        public double eval() {
            return scalar * term.eval();
        }

        @Override
        public double der(Variable variable) {
            return scalar * term.der(variable);
        }

        @Override
        public boolean hasRhs() {
            return term.hasRhs();
        }

        @Override
        public double rhs() {
            return scalar * term.rhs();
        }

        @Override
        public void write(Writer writer) throws IOException {
            writer.write(Double.toString(scalar));
            writer.write(" * ");
            term.write(writer);
        }
    }

    static EquationTerm multiply(EquationTerm term, double scalar) {
        return new MultiplyByScalarEquationTerm(term, scalar);
    }

    Equation getEquation();

    void setEquation(Equation equation);

    boolean isActive();

    void setActive(boolean active);

    /**
     * Get the list of variable this equation term depends on.
     * @return the list of variable this equation term depends on.
     */
    List<Variable> getVariables();

    /**
     * Update equation term using {@code x} variable values.
     * @param x variables values vector
     */
    void update(double[] x);

    /**
     * Evaluate equation term.
     * @return value of the equation term
     */
    double eval();

    /**
     * Get partial derivative.
     *
     * @param variable the variable the partial derivative is with respect to
     * @return value of the partial derivative
     */
    double der(Variable variable);

    /**
     * Check {@link #rhs()} can return a value different from zero.
     *
     * @return true if {@link #rhs()} can return a value different from zero, false otherwise
     */
    boolean hasRhs();

    /**
     * Get part of the partial derivative that has to be moved to right hand side.
     * @return value of part of the partial derivative that has to be moved to right hand side
     */
    double rhs();

    void write(Writer writer) throws IOException;
}
