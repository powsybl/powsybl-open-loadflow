/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.util.Evaluable;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
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
        public ElementType getElementType() {
            return term.getElementType();
        }

        @Override
        public int getElementNum() {
            return term.getElementNum();
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
        public double calculateSensi(DenseMatrix x, int column) {
            return scalar * term.calculateSensi(x, column);
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

    class VariableEquationTerm extends AbstractEquationTerm {

        private final int elementNum;

        private final List<Variable> variables;

        private double value;

        VariableEquationTerm(int elementNum, VariableType variableType, VariableSet variableSet) {
            this.elementNum = elementNum;
            this.variables = Collections.singletonList(variableSet.getVariable(elementNum, variableType));
        }

        @Override
        public ElementType getElementType() {
            return variables.get(0).getType().getElementType();
        }

        @Override
        public int getElementNum() {
            return elementNum;
        }

        @Override
        public List<Variable> getVariables() {
            return variables;
        }

        @Override
        public void update(double[] x) {
            value = x[variables.get(0).getRow()];
        }

        @Override
        public double eval() {
            return value;
        }

        @Override
        public double der(Variable variable) {
            return 1;
        }

        @Override
        public boolean hasRhs() {
            return false;
        }

        @Override
        public double rhs() {
            return 0;
        }

        @Override
        public void write(Writer writer) throws IOException {
            variables.get(0).write(writer);
        }
    }

    static VariableEquationTerm createVariableTerm(LfElement element, VariableType variableType, VariableSet variableSet) {
        Objects.requireNonNull(element);
        Objects.requireNonNull(variableType);
        Objects.requireNonNull(variableSet);
        if (element.getType() != variableType.getElementType()) {
            throw new IllegalArgumentException("Wrong variable element type: " + variableType.getElementType()
                    + ", expected: " + element.getType());
        }
        return new VariableEquationTerm(element.getNum(), variableType, variableSet);
    }

    Equation getEquation();

    void setEquation(Equation equation);

    boolean isActive();

    void setActive(boolean active);

    ElementType getElementType();

    int getElementNum();

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

    double calculateSensi(DenseMatrix x, int column);

    void write(Writer writer) throws IOException;
}
