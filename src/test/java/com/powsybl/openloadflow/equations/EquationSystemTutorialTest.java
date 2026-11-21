/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfElementContainer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source of the code snippets of the equation system documentation (docs/advanced_programming/equation_system):
 * the markers delimit the parts included in the documentation, so keep them when editing this class.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class EquationSystemTutorialTest {

    // [quantities-start]
    enum PointVariableType implements Quantity {
        X("x"),
        Y("y");

        private final String symbol;

        PointVariableType(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public ElementType getElementType() {
            return ElementType.BUS; // any element type: the point is element 0 of this type
        }
    }

    enum PointEquationType implements Quantity {
        CIRCLE("circle"),
        HYPERBOLA("hyperbola"),
        FIXED_X("fixed_x");

        private final String symbol;

        PointEquationType(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public ElementType getElementType() {
            return ElementType.BUS;
        }
    }
    // [quantities-end]

    // [square-term-start]
    static class SquareTerm extends AbstractNamedEquationTerm<PointVariableType, PointEquationType> {

        private final Variable<PointVariableType> variable;

        SquareTerm(Variable<PointVariableType> variable) {
            super(true);
            this.variable = Objects.requireNonNull(variable);
        }

        @Override
        protected String getName() {
            return "square";
        }

        @Override
        public ElementType getElementType() {
            return variable.getType().getElementType();
        }

        @Override
        public int getElementNum() {
            return variable.getElementNum();
        }

        @Override
        public List<Variable<PointVariableType>> getVariables() {
            return List.of(variable);
        }

        private double value() {
            return sv.get(variable.getRow());
        }

        @Override
        public double eval() {
            return value() * value();
        }

        @Override
        public double der(Variable<PointVariableType> variable) {
            if (!variable.equals(this.variable)) {
                throw new IllegalArgumentException("Unknown variable: " + variable);
            }
            return 2 * value();
        }

        @Override
        public double calculateSensi(DenseMatrix dx, int column) {
            return der(variable) * dx.get(variable.getRow(), column);
        }
    }
    // [square-term-end]

    // [product-term-start]
    static class ProductTerm extends AbstractNamedEquationTerm<PointVariableType, PointEquationType> {

        private final Variable<PointVariableType> a;

        private final Variable<PointVariableType> b;

        ProductTerm(Variable<PointVariableType> a, Variable<PointVariableType> b) {
            super(true);
            this.a = Objects.requireNonNull(a);
            this.b = Objects.requireNonNull(b);
        }

        @Override
        protected String getName() {
            return "product";
        }

        @Override
        public ElementType getElementType() {
            return a.getType().getElementType();
        }

        @Override
        public int getElementNum() {
            return a.getElementNum();
        }

        @Override
        public List<Variable<PointVariableType>> getVariables() {
            return List.of(a, b);
        }

        @Override
        public double eval() {
            return sv.get(a.getRow()) * sv.get(b.getRow());
        }

        @Override
        public double der(Variable<PointVariableType> variable) {
            if (variable.equals(a)) {
                return sv.get(b.getRow());
            } else if (variable.equals(b)) {
                return sv.get(a.getRow());
            }
            throw new IllegalArgumentException("Unknown variable: " + variable);
        }
    }
    // [product-term-end]

    // [container-start]
    private static final LfElementContainer NO_ELEMENT_CONTAINER = new LfElementContainer() {
        @Override
        public int getElementCount(ElementType elementType) {
            return 0;
        }

        @Override
        public LfElement getElement(ElementType elementType, int elementNum) {
            throw new UnsupportedOperationException();
        }
    };
    // [container-end]

    // [targets-start]
    private static double[] createTargets(EquationSystem<PointVariableType, PointEquationType> equationSystem,
                                          Map<PointEquationType, Double> targetByType) {
        double[] targets = new double[equationSystem.getIndex().getColumnCount()];
        for (SingleEquation<PointVariableType, PointEquationType> equation : equationSystem.getIndex().getSortedSingleEquationsToSolve()) {
            targets[equation.getColumn()] = targetByType.get(equation.getType());
        }
        return targets;
    }
    // [targets-end]

    // [newton-start]
    private static final int MAX_ITERATIONS = 20;

    private static final double EPSILON = 1e-12;

    private static int solve(EquationSystem<PointVariableType, PointEquationType> equationSystem,
                             EquationVector<PointVariableType, PointEquationType> equationVector,
                             JacobianMatrix<PointVariableType, PointEquationType> j,
                             double[] targets) {
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            // mismatch = f(x) - targets, indexed by equation column
            double[] mismatch = equationVector.getArray().clone();
            Vectors.minus(mismatch, targets);
            if (Vectors.norm2(mismatch) < EPSILON) {
                return iteration;
            }
            // J dx = mismatch: the matrix is stored transposed, hence solveTransposed
            j.solveTransposed(mismatch);
            // mismatch now holds dx, indexed by variable row; updating the state invalidates f(x) and J
            equationSystem.getStateVector().minus(mismatch);
        }
        throw new IllegalStateException("Newton-Raphson did not converge in " + MAX_ITERATIONS + " iterations");
    }
    // [newton-end]

    @Test
    void circleAndHyperbola() {
        // [build-start]
        EquationSystem<PointVariableType, PointEquationType> equationSystem
                = new EquationSystem<>(PointEquationType.class, NO_ELEMENT_CONTAINER);

        Variable<PointVariableType> x = equationSystem.getVariable(0, PointVariableType.X);
        Variable<PointVariableType> y = equationSystem.getVariable(0, PointVariableType.Y);

        SquareTerm xSquare = new SquareTerm(x);
        equationSystem.createEquation(0, PointEquationType.CIRCLE)
                .addTerm(xSquare)
                .addTerm(new SquareTerm(y));
        equationSystem.createEquation(0, PointEquationType.HYPERBOLA)
                .addTerm(new ProductTerm(x, y));
        // [build-end]

        assertEquals(String.join(System.lineSeparator(),
                        "circle0 = square(x0) + square(y0)",
                        "hyperbola0 = product(x0, y0)") + System.lineSeparator(),
                equationSystem.writeToString());

        // [state-start]
        double[] initialState = new double[equationSystem.getIndex().getRowCount()];
        initialState[x.getRow()] = 2;
        initialState[y.getRow()] = 0.5;
        equationSystem.getStateVector().set(initialState);
        // [state-end]

        // [solve-start]
        Map<PointEquationType, Double> targetByType = new EnumMap<>(PointEquationType.class);
        targetByType.put(PointEquationType.CIRCLE, 4.0);
        targetByType.put(PointEquationType.HYPERBOLA, 1.0);

        try (EquationVector<PointVariableType, PointEquationType> equationVector = new EquationVector<>(equationSystem);
             JacobianMatrix<PointVariableType, PointEquationType> j = new JacobianMatrix<>(equationSystem, new DenseMatrixFactory())) {

            int iterations = solve(equationSystem, equationVector, j, createTargets(equationSystem, targetByType));

            StateVector stateVector = equationSystem.getStateVector();
            double xValue = stateVector.get(x.getRow());
            double yValue = stateVector.get(y.getRow());
            // [solve-end]
            assertEquals(4, iterations);
            double xSolution = Math.sqrt(2 + Math.sqrt(3));
            double ySolution = 1 / xSolution;
            assertEquals(xSolution, xValue, 1e-10);
            assertEquals(ySolution, yValue, 1e-10);

            // [sensi-start]
            int circleColumn = equationSystem.getEquation(0, PointEquationType.CIRCLE).orElseThrow().getColumn();
            DenseMatrix dx = new DenseMatrix(equationSystem.getIndex().getColumnCount(), 1);
            dx.set(circleColumn, 0, 1); // unit increase of the circle target
            j.solveTransposed(dx); // dx now holds the variation of each variable, indexed by variable row

            double dxdr = dx.get(x.getRow(), 0);
            double dx2dr = xSquare.calculateSensi(dx, 0);
            // [sensi-end]
            double xs = xSolution;
            double ys = ySolution;
            assertEquals(xs / (2 * (xs * xs - ys * ys)), dxdr, 1e-10);
            assertEquals(2 * xs * dxdr, dx2dr, 1e-10);

            // [restructure-start]
            equationSystem.getEquation(0, PointEquationType.HYPERBOLA).orElseThrow().setActive(false);
            equationSystem.createEquation(0, PointEquationType.FIXED_X)
                    .addTerm(x.createTerm());
            targetByType.put(PointEquationType.FIXED_X, 1.5);

            solve(equationSystem, equationVector, j, createTargets(equationSystem, targetByType));
            // [restructure-end]
            assertEquals(1.5, stateVector.get(x.getRow()), 1e-10);
            assertEquals(Math.sqrt(4 - 1.5 * 1.5), stateVector.get(y.getRow()), 1e-10);
        }
    }

    // [chain-quantities-start]
    enum ChainVariableType implements Quantity {
        U("u"); // position of a node

        private final String symbol;

        ChainVariableType(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public ElementType getElementType() {
            return ElementType.BUS;
        }
    }

    enum ChainEquationType implements Quantity {
        FORCE_BALANCE("force"),
        FIXED_POSITION("fixed");

        private final String symbol;

        ChainEquationType(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public ElementType getElementType() {
            return ElementType.BUS;
        }
    }
    // [chain-quantities-end]

    // [spring-evaluator-start]
    /**
     * Force of spring s, connecting node s to node s + 1, on one of its two nodes: k_s * (u_this - u_other).
     * Term element numbers are spring numbers.
     */
    static class SpringEvaluator implements EquationTermArray.Evaluator<ChainVariableType> {

        private final double[] stiffness;

        private final boolean onFirstNode;

        private final VariableSet<ChainVariableType> variableSet;

        private final StateVector sv;

        // derivatives are constant for a linear term: computed once, indexed by spring number
        private final double[] derThis;

        private final double[] derOther;

        SpringEvaluator(double[] stiffness, boolean onFirstNode, EquationSystem<ChainVariableType, ChainEquationType> equationSystem) {
            this.stiffness = stiffness;
            this.onFirstNode = onFirstNode;
            this.variableSet = equationSystem.getVariableSet();
            this.sv = equationSystem.getStateVector();
            this.derThis = stiffness.clone();
            this.derOther = new double[stiffness.length];
            for (int s = 0; s < stiffness.length; s++) {
                derOther[s] = -stiffness[s];
            }
        }

        private Variable<ChainVariableType> uThis(int spring) {
            return variableSet.getVariable(onFirstNode ? spring : spring + 1, ChainVariableType.U);
        }

        private Variable<ChainVariableType> uOther(int spring) {
            return variableSet.getVariable(onFirstNode ? spring + 1 : spring, ChainVariableType.U);
        }

        @Override
        public String getName() {
            return onFirstNode ? "spring_force_1" : "spring_force_2";
        }

        @Override
        public boolean isDisabled(int spring) {
            return false;
        }

        @Override
        public double eval(int spring) {
            return stiffness[spring] * (sv.get(uThis(spring).getRow()) - sv.get(uOther(spring).getRow()));
        }

        @Override
        public double[] eval() {
            double[] values = new double[stiffness.length];
            for (int s = 0; s < stiffness.length; s++) {
                values[s] = eval(s);
            }
            return values;
        }

        @Override
        public double[][] evalDer() {
            // one array per local derivative index, in the order given by getDerivatives
            return new double[][] {derThis, derOther};
        }

        @Override
        public List<Derivative<ChainVariableType>> getDerivatives(int spring) {
            return List.of(new Derivative<>(uThis(spring), 0),
                           new Derivative<>(uOther(spring), 1));
        }
    }
    // [spring-evaluator-end]

    // [chain-container-start]
    private static LfElementContainer createNodeContainer(int nodeCount) {
        LfElement node = Mockito.mock(LfElement.class); // only isDisabled() is used: false
        return new LfElementContainer() {
            @Override
            public int getElementCount(ElementType elementType) {
                return nodeCount;
            }

            @Override
            public LfElement getElement(ElementType elementType, int elementNum) {
                return node;
            }
        };
    }
    // [chain-container-end]

    @Test
    void springChain() {
        // [chain-build-start]
        double[] stiffness = {1, 2, 4}; // spring s connects node s to node s + 1
        int nodeCount = stiffness.length + 1;
        double load = 4; // pulling the last node

        EquationSystem<ChainVariableType, ChainEquationType> equationSystem
                = new EquationSystem<>(ChainEquationType.class, createNodeContainer(nodeCount));

        // one force balance equation per node, except node 0 whose position is fixed
        EquationArray<ChainVariableType, ChainEquationType> forceBalance
                = equationSystem.createEquationArray(ChainEquationType.FORCE_BALANCE);
        forceBalance.setElementActive(0, false);
        equationSystem.createEquation(0, ChainEquationType.FIXED_POSITION)
                .addTerm(equationSystem.getVariable(0, ChainVariableType.U).createTerm());

        EquationTermArray<ChainVariableType, ChainEquationType> forceOnFirstNode
                = new EquationTermArray<>(ElementType.BRANCH, new SpringEvaluator(stiffness, true, equationSystem));
        EquationTermArray<ChainVariableType, ChainEquationType> forceOnSecondNode
                = new EquationTermArray<>(ElementType.BRANCH, new SpringEvaluator(stiffness, false, equationSystem));
        forceBalance.addTermArray(forceOnFirstNode);
        forceBalance.addTermArray(forceOnSecondNode);
        for (int spring = 0; spring < stiffness.length; spring++) {
            forceOnFirstNode.addTerm(spring, spring); // equation of node "spring", term of spring "spring"
            forceOnSecondNode.addTerm(spring + 1, spring);
        }
        forceOnFirstNode.compress();
        forceOnSecondNode.compress();
        // [chain-build-end]

        // [chain-solve-start]
        double[] targets = new double[equationSystem.getIndex().getColumnCount()]; // all zero, except:
        targets[forceBalance.getElementNumToColumn(nodeCount - 1)] = load;

        double[] positions = targets.clone();
        try (JacobianMatrix<ChainVariableType, ChainEquationType> j = new JacobianMatrix<>(equationSystem, new DenseMatrixFactory(), false)) {
            j.solveTransposed(positions); // linear system: a single solve
        }
        equationSystem.getStateVector().set(positions);
        // [chain-solve-end]

        double[] expected = {0, 4, 6, 7};
        for (int node = 0; node < nodeCount; node++) {
            assertEquals(expected[node], positions[equationSystem.getVariable(node, ChainVariableType.U).getRow()], 1e-10);
        }
        try (EquationVector<ChainVariableType, ChainEquationType> equationVector = new EquationVector<>(equationSystem)) {
            assertArrayEquals(targets, equationVector.getArray(), 1e-10);
        }
    }
}
