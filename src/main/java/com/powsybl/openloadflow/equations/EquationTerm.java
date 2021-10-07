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
public interface EquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends Evaluable {

    class MultiplyByScalarEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationTerm<V, E> {

        private final EquationTerm<V, E> term;

        private final double scalar;

        MultiplyByScalarEquationTerm(EquationTerm<V, E> term, double scalar) {
            this.term = Objects.requireNonNull(term);
            this.scalar = scalar;
        }

        @Override
        public Equation<V, E> getEquation() {
            return term.getEquation();
        }

        @Override
        public void setEquation(Equation<V, E> equation) {
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
        public List<Variable<V>> getVariables() {
            return term.getVariables();
        }

        @Override
        public void update(double[] x, BranchVector<V, E> vec) {
            term.update(x, vec);
        }

        @Override
        public double eval() {
            return scalar * term.eval();
        }

        @Override
        public double der(Variable<V> variable) {
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

    static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> EquationTerm<V, E> multiply(EquationTerm<V, E> term, double scalar) {
        return new MultiplyByScalarEquationTerm<>(term, scalar);
    }

    class VariableEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractEquationTerm<V, E> {

        private final int elementNum;

        private final List<Variable<V>> variables;

        private double value;

        VariableEquationTerm(int elementNum, V variableType, VariableSet<V> variableSet, double initialValue) {
            this.elementNum = elementNum;
            this.variables = Collections.singletonList(variableSet.create(elementNum, variableType));
            value = initialValue;
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
        public List<Variable<V>> getVariables() {
            return variables;
        }

        @Override
        public void update(double[] x, BranchVector<V, E> vec) {
            value = x[variables.get(0).getRow()];
        }

        @Override
        public double eval() {
            return value;
        }

        @Override
        public double der(Variable<V> variable) {
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
        public double calculateSensi(DenseMatrix x, int column) {
            return x.get(variables.get(0).getRow(), column);
        }

        @Override
        public void write(Writer writer) throws IOException {
            variables.get(0).write(writer);
        }
    }

    static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> VariableEquationTerm<V, E> createVariableTerm(LfElement element, V variableType, VariableSet<V> variableSet) {
        return createVariableTerm(element, variableType, variableSet, Double.NaN);
    }

    static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> VariableEquationTerm<V, E> createVariableTerm(LfElement element, V variableType, VariableSet<V> variableSet, double initialValue) {
        Objects.requireNonNull(element);
        Objects.requireNonNull(variableType);
        Objects.requireNonNull(variableSet);
        if (element.getType() != variableType.getElementType()) {
            throw new IllegalArgumentException("Wrong variable element type: " + variableType.getElementType()
                + ", expected: " + element.getType());
        }
        return new VariableEquationTerm<>(element.getNum(), variableType, variableSet, initialValue);
    }

    Equation<V, E> getEquation();

    void setEquation(Equation<V, E> equation);

    boolean isActive();

    void setActive(boolean active);

    ElementType getElementType();

    int getElementNum();

    /**
     * Get the list of variable this equation term depends on.
     * @return the list of variable this equation term depends on.
     */
    List<Variable<V>> getVariables();

    /**
     * Update equation term using {@code x} variable values.
     * @param x variables values vector
     */
    void update(double[] x, BranchVector<V, E> vec);

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
    double der(Variable<V> variable);

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
