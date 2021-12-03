/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.VoltageInitializer;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VoltageMagnitudeInitializer implements VoltageInitializer {

    public enum InitVmEquationType implements Quantity {
        BUS_V("v", ElementType.BUS),
        BUS_ZERO("z", ElementType.BUS);

        private final String symbol;

        private final ElementType elementType;

        InitVmEquationType(String symbol, ElementType elementType) {
            this.symbol = symbol;
            this.elementType = elementType;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public ElementType getElementType() {
            return elementType;
        }
    }

    public enum InitVmVariableType implements Quantity {
        BUS_V("v", ElementType.BUS);

        private final String symbol;

        private final ElementType elementType;

        InitVmVariableType(String symbol, ElementType elementType) {
            this.symbol = symbol;
            this.elementType = elementType;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public ElementType getElementType() {
            return elementType;
        }
    }

    public static final class InitVmBusEquationTerm extends AbstractNamedEquationTerm<InitVmVariableType, InitVmEquationType> {

        private final LfBus bus;

        private final List<Variable<InitVmVariableType>> variables;

        private final List<LfBranch> branches;

        private double value;

        private double[] der;

        public InitVmBusEquationTerm(LfBus bus, VariableSet<InitVmVariableType> variableSet) {
            this.bus = Objects.requireNonNull(bus);
            branches = bus.getBranches().stream()
                    .filter(branch -> {
                        LfBus otherBus = branch.getBus1() == bus ? branch.getBus2() : branch.getBus1();
                        return otherBus != null;
                    })
                    .collect(Collectors.toList());
            variables = branches.stream()
                    .map(branch -> {
                        LfBus otherBus = branch.getBus1() == bus ? branch.getBus2() : branch.getBus1();
                        return variableSet.getVariable(otherBus.getNum(), InitVmVariableType.BUS_V);
                    })
                    .collect(Collectors.toList());
            der = new double[variables.size()];
        }

        @Override
        public ElementType getElementType() {
            return ElementType.BUS;
        }

        @Override
        public int getElementNum() {
            return bus.getNum();
        }

        @Override
        public List<Variable<InitVmVariableType>> getVariables() {
            return variables;
        }

        @Override
        public void update(double[] x) {
            value = 0;
            double bs = 0;
            for (int i = 0; i < branches.size(); i++) {
                LfBranch branch = branches.get(i);
                PiModel piModel = branch.getPiModel();
                double b = Math.abs(1 / piModel.getX());
                double r = branch.getBus1() == bus ? piModel.getR1() : 1 / piModel.getR1();
                bs += b;
                value += x[variables.get(i).getRow()] * b * r;
                der[i] = b * r;
            }
            value /= bs;
            for (int i = 0; i < branches.size(); i++) {
                der[i] /= bs;
            }
        }

        @Override
        public double eval() {
            return value;
        }

        @Override
        public double der(Variable<InitVmVariableType> variable) {
            int i = variables.indexOf(variable); // bof...
            return der[i];
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
        protected String getName() {
            return "v";
        }
    }

    private final MatrixFactory matrixFactory;

    public VoltageMagnitudeInitializer(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    @Override
    public void prepare(LfNetwork network) {
        EquationSystem<InitVmVariableType, InitVmEquationType> equationSystem = new EquationSystem<>();
        VariableSet<InitVmVariableType> variableSet = new VariableSet<>();
        for (LfBus bus : network.getBuses()) {
            EquationTerm<InitVmVariableType, InitVmEquationType> v = EquationTerm.createVariableTerm(bus, InitVmVariableType.BUS_V, variableSet);
            if (bus.isVoltageControlled()) {
                equationSystem.createEquation(bus.getNum(), InitVmEquationType.BUS_V)
                        .addTerm(v);
            } else {
                equationSystem.createEquation(bus.getNum(), InitVmEquationType.BUS_ZERO)
                        .addTerm(new InitVmBusEquationTerm(bus, variableSet))
                        .addTerm(EquationTerm.multiply(v, -1));
            }
        }

        try (JacobianMatrix<InitVmVariableType, InitVmEquationType> j = new JacobianMatrix<>(equationSystem, matrixFactory)) {
            double[] b = TargetVector.createArray(network, equationSystem, (equation, network1, targets) -> {
                switch (equation.getType()) {
                    case BUS_V:
                        targets[equation.getColumn()] = network.getBus(equation.getNum()).getVoltageControl().orElseThrow().getTargetValue();
                        break;
                    case BUS_ZERO:
                        targets[equation.getColumn()] = 0;
                        break;
                }
            });

            double[] x = new double[network.getBuses().size()];
            Arrays.fill(x, 1);
            equationSystem.updateEquations(x);

            j.solveTransposed(b);

            for (Variable<InitVmVariableType> variable : equationSystem.getSortedVariablesToFind()) {
                network.getBus(variable.getNum()).setV(() -> b[variable.getRow()]);
            }
        }
    }

    @Override
    public double getMagnitude(LfBus bus) {
        return bus.getV().eval();
    }

    @Override
    public double getAngle(LfBus bus) {
        return 0;
    }
}
