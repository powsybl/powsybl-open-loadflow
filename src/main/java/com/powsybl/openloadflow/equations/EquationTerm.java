/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.google.common.collect.Streams;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.util.Evaluable;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

/**
 * An equation term, i.e part of the equation sum.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface EquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends Evaluable {

    class MultiplyByScalarEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationTerm<V, E> {

        private final EquationTerm<V, E> term;

        private final DoubleSupplier scalarSupplier;

        MultiplyByScalarEquationTerm(EquationTerm<V, E> term, double scalar) {
            this(term, () -> scalar);
        }

        MultiplyByScalarEquationTerm(EquationTerm<V, E> term, DoubleSupplier scalarSupplier) {
            this.term = Objects.requireNonNull(term);
            this.scalarSupplier = Objects.requireNonNull(scalarSupplier);
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
        public void setStateVector(StateVector sv) {
            term.setStateVector(sv);
        }

        @Override
        public double eval() {
            return scalarSupplier.getAsDouble() * term.eval();
        }

        @Override
        public double der(Variable<V> variable) {
            return scalarSupplier.getAsDouble() * term.der(variable);
        }

        @Override
        public boolean hasRhs() {
            return term.hasRhs();
        }

        @Override
        public double rhs() {
            return scalarSupplier.getAsDouble() * term.rhs();
        }

        @Override
        public double calculateSensi(DenseMatrix x, int column) {
            return scalarSupplier.getAsDouble() * term.calculateSensi(x, column);
        }

        @Override
        public void write(Writer writer) throws IOException {
            writer.write(Double.toString(scalarSupplier.getAsDouble()));
            writer.write(" * ");
            term.write(writer);
        }

        @Override
        public List<EquationTerm<V, E>> getChildren() {
            return List.of(term);
        }
    }

    static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> EquationTerm<V, E> multiply(EquationTerm<V, E> term, DoubleSupplier scalarSupplier) {
        return new MultiplyByScalarEquationTerm<>(term, scalarSupplier);
    }

    static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> EquationTerm<V, E> multiply(EquationTerm<V, E> term, double scalar) {
        return new MultiplyByScalarEquationTerm<>(term, scalar);
    }

    class VariableEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractEquationTerm<V, E> {

        private final List<Variable<V>> variables;

        VariableEquationTerm(Variable<V> variable) {
            this.variables = List.of(Objects.requireNonNull(variable));
        }

        private Variable<V> getVariable() {
            return variables.get(0);
        }

        @Override
        public ElementType getElementType() {
            return getVariable().getType().getElementType();
        }

        @Override
        public int getElementNum() {
            return getVariable().getElementNum();
        }

        @Override
        public List<Variable<V>> getVariables() {
            return variables;
        }

        @Override
        public double eval() {
            return sv.get(getVariable().getRow());
        }

        @Override
        public double der(Variable<V> variable) {
            return 1;
        }

        @Override
        public double calculateSensi(DenseMatrix dx, int column) {
            return dx.get(getVariable().getRow(), column);
        }

        @Override
        public void write(Writer writer) throws IOException {
            getVariable().write(writer);
        }
    }

    class MultiplyEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationTerm<V, E> {

        private final EquationTerm<V, E> term1;

        private final EquationTerm<V, E> term2;

        private final List<Variable<V>> variables;

        MultiplyEquationTerm(EquationTerm<V, E> term1, EquationTerm<V, E> term2) {
            this.term1 = Objects.requireNonNull(term1);
            this.term2 = Objects.requireNonNull(term2);
            if (term1.hasRhs() || term2.hasRhs()) {
                throw new UnsupportedOperationException("Terms with RHS not supported");
            }
            variables = Streams.concat(term1.getVariables().stream(), term2.getVariables().stream())
                    .distinct()
                    .collect(Collectors.toList());
        }

        @Override
        public Equation<V, E> getEquation() {
            return term1.getEquation();
        }

        @Override
        public void setEquation(Equation<V, E> equation) {
            term1.setEquation(equation);
            term2.setEquation(equation);
        }

        @Override
        public boolean isActive() {
            return term1.isActive();
        }

        @Override
        public void setActive(boolean active) {
            term1.setActive(active);
            term2.setActive(active);
        }

        @Override
        public ElementType getElementType() {
            return null;
        }

        @Override
        public int getElementNum() {
            return -1;
        }

        @Override
        public List<Variable<V>> getVariables() {
            return variables;
        }

        @Override
        public void setStateVector(StateVector sv) {
            term1.setStateVector(sv);
            term2.setStateVector(sv);
        }

        @Override
        public double eval() {
            return term1.eval() * term2.eval();
        }

        @Override
        public double der(Variable<V> variable) {
            return term1.der(variable) * term2.eval() + term1.eval() * term2.der(variable);
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
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EquationTerm<V, E>> getChildren() {
            return List.of(term1, term2);
        }

        @Override
        public void write(Writer writer) throws IOException {
            writer.write("multiply(");
            term1.write(writer);
            writer.write(", ");
            term2.write(writer);
            writer.write(")");
        }
    }

    static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> EquationTerm<V, E> multiply(EquationTerm<V, E> term1, EquationTerm<V, E> term2) {
        return new MultiplyEquationTerm<>(term1, term2);
    }

    class SumEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationTerm<V, E> {

        private final List<EquationTerm<V, E>> terms;

        private final List<Variable<V>> variables;

        private final boolean hasRhs;

        SumEquationTerm(List<EquationTerm<V, E>> terms) {
            this.terms = Objects.requireNonNull(terms);
            if (terms.isEmpty()) {
                throw new IllegalArgumentException("Empty term list");
            }
            Set<Variable<V>> distinctVariables = new HashSet<>();
            for (var term : terms) {
                distinctVariables.addAll(term.getVariables());
            }
            variables = new ArrayList<>(distinctVariables);
            hasRhs = terms.stream().anyMatch(term -> hasRhs());
        }

        @Override
        public Equation<V, E> getEquation() {
            return terms.get(0).getEquation();
        }

        @Override
        public void setEquation(Equation<V, E> equation) {
            for (var term : terms) {
                term.setEquation(equation);
            }
        }

        @Override
        public boolean isActive() {
            return terms.get(0).isActive();
        }

        @Override
        public void setActive(boolean active) {
            for (var term : terms) {
                term.setActive(active);
            }
        }

        @Override
        public ElementType getElementType() {
            return null;
        }

        @Override
        public int getElementNum() {
            return -1;
        }

        @Override
        public List<Variable<V>> getVariables() {
            return variables;
        }

        @Override
        public void setStateVector(StateVector sv) {
            for (var term : terms) {
                term.setStateVector(sv);
            }
        }

        @Override
        public double eval() {
            double val = 0;
            for (var term : terms) {
                val += term.eval();
            }
            return val;
        }

        @Override
        public double der(Variable<V> variable) {
            double der = 0;
            for (var term : terms) {
                der += term.der(variable);
            }
            return der;
        }

        @Override
        public boolean hasRhs() {
            return hasRhs;
        }

        @Override
        public double rhs() {
            double rhs = 0;
            for (var term : terms) {
                rhs += term.rhs();
            }
            return rhs;
        }

        @Override
        public double calculateSensi(DenseMatrix x, int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EquationTerm<V, E>> getChildren() {
            return terms;
        }

        @Override
        public void write(Writer writer) throws IOException {
            writer.write("add(");
            terms.get(0).write(writer);
            for (int i = 1; i < terms.size(); i++) {
                writer.write(", ");
                terms.get(i).write(writer);
            }
            writer.write(")");
        }
    }

    static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> EquationTerm<V, E> sum(List<EquationTerm<V, E>> terms) {
        if (terms.size() == 1) {
            return terms.get(0);
        }
        return new SumEquationTerm<>(terms);
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
     * Set state vector to use for term evaluation.
     * @param sv the state vector
     */
    void setStateVector(StateVector sv);

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

    List<EquationTerm<V, E>> getChildren();

    default EquationTerm<V, E> multiply(DoubleSupplier scalarSupplier) {
        return multiply(this, scalarSupplier);
    }

    default EquationTerm<V, E> multiply(double scalar) {
        return multiply(this, scalar);
    }

    default EquationTerm<V, E> multiply(EquationTerm<V, E> other) {
        return multiply(this, other);
    }

    default EquationTerm<V, E> minus() {
        return multiply(-1);
    }
}
