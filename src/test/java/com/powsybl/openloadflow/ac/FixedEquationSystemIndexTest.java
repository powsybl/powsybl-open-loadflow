/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.SparseMatrix;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcStateVectorUtil;
import com.powsybl.openloadflow.ac.equations.AcVariablePairing;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.fixed.FixedEquationSystemIndex;
import com.powsybl.openloadflow.equations.fixed.FixedStructure;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfSynchronousNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.test.networks.DistributedSlackNetworkFactory;
import com.powsybl.openloadflow.test.networks.HvdcNetworkFactory;
import com.powsybl.openloadflow.test.networks.NodeBreakerNetworkFactory;
import com.powsybl.openloadflow.test.networks.PhaseControlFactory;
import com.powsybl.openloadflow.test.networks.ReactivePowerControlNetworkFactory;
import com.powsybl.openloadflow.test.networks.ShuntNetworkFactory;
import com.powsybl.openloadflow.test.networks.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.test.networks.ZeroImpedanceNetworkFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The matrix and the equation values of a {@link FixedEquationSystemIndex} must be OLF's own: the Jacobian of
 * {@code JacobianMatrix} and the values of {@code EquationVector}, with each active equation in the column of its position
 * and an identity column for each position that holds no equation. Checked on the base state of each network, where the
 * fixed system must also give OLF's Newton step, then after each step of a random walk through what OLF can activate
 * and deactivate: branches, buses, the voltage control of generators, transformers and shunts, phase control.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class FixedEquationSystemIndexTest {

    private static final int STEPS = 60;

    /** A network, the parameters that switch its controls on, and whether its switches are kept as branches. */
    private record Case(String name, Supplier<Network> network, Consumer<LoadFlowParameters> configuration, boolean breakers) {

        Case(String name, Supplier<Network> network) {
            this(name, network, parameters -> { }, false);
        }

        Case(String name, Supplier<Network> network, Consumer<LoadFlowParameters> configuration) {
            this(name, network, configuration, false);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static Network transformerVoltageControl() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformers().iterator().next();
        transformer.getRatioTapChanger()
                .setTapPosition(0)
                .getVoltageRegulation()
                    .setTerminal(transformer.getTerminal2(), 34.0)
                    .setTargetDeadband(0)
                    .setRegulating(true);
        return network;
    }

    private static Network phaseControl() {
        Network network = PhaseControlFactory.createNetworkWithT2wt();
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformers().iterator().next();
        transformer.getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1)
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(transformer.getTerminal1())
                .setRegulationValue(83);
        return network;
    }

    private static List<Case> cases() {
        return List.of(
                new Case("ieee14", IeeeCdfNetworkFactory::create14),
                new Case("ieee57", IeeeCdfNetworkFactory::create57),
                new Case("ieee118", IeeeCdfNetworkFactory::create118),
                new Case("ieee300", IeeeCdfNetworkFactory::create300),
                new Case("eurostag", EurostagTutorialExample1Factory::create),
                new Case("distributed slack", DistributedSlackNetworkFactory::create),
                new Case("generator remote voltage control", VoltageControlNetworkFactory::createWithGeneratorRemoteControl),
                new Case("static var compensator", VoltageControlNetworkFactory::createWithStaticVarCompensator),
                new Case("transformer voltage control", FixedEquationSystemIndexTest::transformerVoltageControl,
                        parameters -> parameters.setTransformerVoltageControlOn(true)),
                new Case("shared transformer voltage control", VoltageControlNetworkFactory::createWithTransformerSharedRemoteControl,
                        parameters -> parameters.setTransformerVoltageControlOn(true)),
                new Case("shared shunt voltage control", VoltageControlNetworkFactory::createWithShuntSharedRemoteControl,
                        parameters -> parameters.setShuntCompensatorVoltageControlOn(true)),
                new Case("shunt", ShuntNetworkFactory::create,
                        parameters -> parameters.setShuntCompensatorVoltageControlOn(true)),
                new Case("phase control", FixedEquationSystemIndexTest::phaseControl,
                        parameters -> parameters.setPhaseShifterRegulationOn(true)),
                new Case("generator remote reactive power control", ReactivePowerControlNetworkFactory::createWithGeneratorRemoteControl,
                        parameters -> OpenLoadFlowParameters.create(parameters).setGeneratorReactivePowerRemoteControl(true)),
                new Case("hvdc in ac emulation", HvdcNetworkFactory::createWithHvdcInAcEmulation),
                new Case("zero impedance branches", ZeroImpedanceNetworkFactory::createWithVoltageControl),
                new Case("node breaker", NodeBreakerNetworkFactory::create, parameters -> { }, true));
    }

    private static Stream<Arguments> arguments() {
        return cases().stream().flatMap(c -> Stream.of(Arguments.of(c, false), Arguments.of(c, true)));
    }

    private static AcLoadFlowParameters acParameters(Network network, LoadFlowParameters parameters, boolean breakers, boolean vectorized) {
        return OpenLoadFlowParameters.createAcParameters(network, parameters, OpenLoadFlowParameters.create(parameters),
                        new DenseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>(), breakers, false)
                .setVectorized(vectorized);
    }

    @ParameterizedTest(name = "{0}, vectorized = {1}")
    @MethodSource("arguments")
    void matchesTheEquationSystem(Case c, boolean vectorized) {
        Network network = c.network().get();
        LoadFlowParameters parameters = new LoadFlowParameters();
        c.configuration().accept(parameters);
        AcLoadFlowParameters acParameters = acParameters(network, parameters, c.breakers(), vectorized);
        LfNetwork lfNetwork = Networks.load(network, acParameters.getNetworkParameters()).get(0);
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters);
             FixedEquationSystemIndex<AcVariableType, AcEquationType> fixed =
                     new FixedEquationSystemIndex<>(context.getEquationSystem(), new AcVariablePairing(lfNetwork))) {
            check(lfNetwork, context, fixed, "base state");
            checkNewtonStep(context, fixed);

            List<Runnable> moves = moves(lfNetwork);
            Random random = new Random(0);
            int checked = 0;
            for (int step = 0; step < STEPS; step++) {
                Runnable move = moves.get(random.nextInt(moves.size()));
                move.run();
                var index = context.getEquationSystem().getIndex();
                if (index.getRowCount() == index.getColumnCount()) { // else a state OLF itself cannot solve
                    check(lfNetwork, context, fixed, "step " + step);
                    checked++;
                }
            }
            assertTrue(checked > STEPS / 2, "Only " + checked + " states checked");
            // compiled once, even where OLF removes equations and creates them again: on the zero impedance network
            assertEquals(1, fixed.getStructureBuildCount());
            // and with every equation and every controller position in it from the start, the matrix structure is built once: also when
            // a reactive balance first takes the position of a tap, of a susceptance or of a remote generator bus
            assertEquals(1, fixed.getMatrixStructureBuildCount());
        }
    }

    /**
     * The four buses of the Eurostag example: bus 0 holds its voltage, bus 1 is the slack. Their positions hold the voltage
     * and the angle target, on the matrix structure of the balance that can take their place. When bus 3 is lost, its two positions
     * hold nothing and keep their matrix structure.
     */
    @Test
    void writesItsStructure() throws IOException {
        Network network = EurostagTutorialExample1Factory.create();
        AcLoadFlowParameters acParameters = acParameters(network, new LoadFlowParameters(), false, true);
        LfNetwork lfNetwork = Networks.load(network, acParameters.getNetworkParameters()).get(0);
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters);
             FixedEquationSystemIndex<AcVariableType, AcEquationType> fixed =
                     new FixedEquationSystemIndex<>(context.getEquationSystem(), new AcVariablePairing(lfNetwork))) {
            String rows01 = " | BUS_V:0 BUS_PHI:0 BUS_V:1 BUS_PHI:1\n";
            String rows012 = " | BUS_V:0 BUS_PHI:0 BUS_V:1 BUS_PHI:1 BUS_V:2 BUS_PHI:2\n";
            String rows123 = " | BUS_V:1 BUS_PHI:1 BUS_V:2 BUS_PHI:2 BUS_V:3 BUS_PHI:3\n";
            String rows23 = " | BUS_V:2 BUS_PHI:2 BUS_V:3 BUS_PHI:3\n";
            String firstPositions = "BUS_V:0 <- BUS_TARGET_V:0" + rows01
                    + "BUS_PHI:0 <- BUS_TARGET_P:0" + rows01
                    + "BUS_V:1 <- BUS_TARGET_Q:1" + rows012
                    + "BUS_PHI:1 <- BUS_TARGET_PHI:1" + rows012
                    + "BUS_V:2 <- BUS_TARGET_Q:2" + rows123
                    + "BUS_PHI:2 <- BUS_TARGET_P:2" + rows123;
            assertEquals(firstPositions + "BUS_V:3 <- BUS_TARGET_Q:3" + rows23 + "BUS_PHI:3 <- BUS_TARGET_P:3" + rows23, write(fixed));

            LfBus lost = lfNetwork.getBusById("VLLOAD_0");
            lost.setDisabled(true);
            lost.getBranches().forEach(branch -> branch.setDisabled(true));
            assertEquals(firstPositions + "BUS_V:3 <- none" + rows23 + "BUS_PHI:3 <- none" + rows23, write(fixed));
            assertEquals(1, fixed.getMatrixStructureBuildCount());
        }
    }

    /**
     * The slack bus is excluded, as when a contingency takes it out: OLF picks another reference bus and creates its
     * angle equation, one the structure has not seen. The arrays are compiled again, and the matrix structure is kept: the new
     * equation fits in the column of its position.
     */
    @Test
    void keepsTheMatrixStructureWhenAnEquationIsCreated() {
        Network network = IeeeCdfNetworkFactory.create14();
        AcLoadFlowParameters acParameters = acParameters(network, new LoadFlowParameters(), false, true);
        LfNetwork lfNetwork = Networks.load(network, acParameters.getNetworkParameters()).get(0);
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters);
             FixedEquationSystemIndex<AcVariableType, AcEquationType> fixed =
                     new FixedEquationSystemIndex<>(context.getEquationSystem(), new AcVariablePairing(lfNetwork))) {
            check(lfNetwork, context, fixed, "base state");
            int[] rowIndices = fixed.getStructure().getRowIndices();

            LfSynchronousNetwork synchronousNetwork = lfNetwork.getSynchronousNetworks().get(0);
            LfBus reference = synchronousNetwork.getReferenceBus();
            synchronousNetwork.setExcludedSlackBuses(Set.of(reference));
            assertNotSame(reference, synchronousNetwork.getReferenceBus());
            check(lfNetwork, context, fixed, "another reference bus");
            assertEquals(2, fixed.getStructureBuildCount());
            assertEquals(1, fixed.getMatrixStructureBuildCount());
            assertSame(rowIndices, fixed.getStructure().getRowIndices());
        }
    }

    /**
     * OLF holds its single equations in a hash map whose order changes from one run to the next. The structure numbers
     * them by type and element: where several pairings are possible the choice follows the numbers, and two runs
     * must make the same one.
     */
    @Test
    void numbersTheSingleEquationsByTypeAndElement() {
        Network network = ZeroImpedanceNetworkFactory.createWithVoltageControl();
        AcLoadFlowParameters acParameters = acParameters(network, new LoadFlowParameters(), false, true);
        LfNetwork lfNetwork = Networks.load(network, acParameters.getNetworkParameters()).get(0);
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters);
             FixedEquationSystemIndex<AcVariableType, AcEquationType> fixed =
                     new FixedEquationSystemIndex<>(context.getEquationSystem(), new AcVariablePairing(lfNetwork))) {
            FixedStructure<AcVariableType, AcEquationType> structure = fixed.getStructure();
            assertTrue(structure.getSingleEquationCount() > 10);
            for (int equation = 1; equation < structure.getSingleEquationCount(); equation++) {
                int types = structure.getType(equation - 1).compareTo(structure.getType(equation));
                assertTrue(types < 0 || types == 0 && structure.getElementNum(equation - 1) < structure.getElementNum(equation));
            }
        }
    }

    private static String write(FixedEquationSystemIndex<AcVariableType, AcEquationType> fixed) throws IOException {
        StringWriter writer = new StringWriter();
        fixed.write(writer);
        return writer.toString();
    }

    /**
     * Everything a contingency, an action or an outer loop toggles. The moves keep what OLF keeps: a branch is on only
     * between buses that are on, and a bus that is on keeps a branch.
     */
    private static List<Runnable> moves(LfNetwork network) {
        List<Runnable> moves = new ArrayList<>();
        for (LfBranch branch : network.getBranches()) {
            moves.add(() -> {
                if (branch.isDisabled()) {
                    if (isOn(branch.getBus1()) && isOn(branch.getBus2())) {
                        branch.setDisabled(false);
                    }
                } else if (keepsABranchWithout(branch.getBus1(), branch) && keepsABranchWithout(branch.getBus2(), branch)) {
                    branch.setDisabled(true);
                }
            });
            if (branch.isVoltageController()) {
                moves.add(() -> branch.setVoltageControlEnabled(!branch.isVoltageControlEnabled()));
            }
            if (branch.isPhaseController()) {
                moves.add(() -> branch.setPhaseControlEnabled(!branch.isPhaseControlEnabled()));
            }
        }
        for (LfBus bus : network.getBuses()) {
            if (!bus.isSlack() && !bus.isReference()) { // OLF relocates the slack before it disables its bus
                moves.add(() -> toggle(bus));
            }
            if (bus.isGeneratorVoltageControlEnabled()) {
                moves.add(() -> bus.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(!bus.isGeneratorVoltageControlEnabled()));
            }
            bus.getControllerShunt().ifPresent(shunt ->
                    moves.add(() -> shunt.setVoltageControlEnabled(!shunt.isVoltageControlEnabled())));
        }
        return moves;
    }

    private static void toggle(LfBus bus) {
        if (bus.isDisabled()) {
            List<LfBranch> branches = bus.getBranches().stream().filter(branch -> isOn(other(branch, bus))).toList();
            if (!branches.isEmpty()) {
                bus.setDisabled(false);
                branches.forEach(branch -> branch.setDisabled(false));
            }
        } else if (bus.getBranches().stream().filter(branch -> !branch.isDisabled())
                .allMatch(branch -> keepsABranchWithout(other(branch, bus), bus))) {
            bus.setDisabled(true);
            bus.getBranches().forEach(branch -> branch.setDisabled(true));
        }
    }

    private static boolean isOn(LfBus bus) {
        return bus == null || !bus.isDisabled();
    }

    private static LfBus other(LfBranch branch, LfBus bus) {
        return branch.getBus1() == bus ? branch.getBus2() : branch.getBus1();
    }

    /** True when the bus, if on, keeps a branch that is on once the given branch is off. */
    private static boolean keepsABranchWithout(LfBus bus, LfBranch without) {
        return bus == null || bus.isDisabled()
                || bus.getBranches().stream().anyMatch(branch -> branch != without && !branch.isDisabled());
    }

    /** True when the bus, if on, keeps a branch that is on once the given neighbour and the branches to it are off. */
    private static boolean keepsABranchWithout(LfBus bus, LfBus without) {
        return bus == null || bus.isDisabled()
                || bus.getBranches().stream().anyMatch(branch -> !branch.isDisabled() && other(branch, bus) != without);
    }

    private static void check(LfNetwork lfNetwork, AcLoadFlowContext context,
                              FixedEquationSystemIndex<AcVariableType, AcEquationType> fixed, String state) {
        EquationSystem<AcVariableType, AcEquationType> equationSystem = context.getEquationSystem();
        // a state away from the flat start, of the size of OLF's current variables
        AcStateVectorUtil.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());
        double[] x = equationSystem.getStateVector().get().clone();
        for (int i = 0; i < x.length; i++) {
            x[i] += 0.05 * Math.sin(i + 1.0);
        }
        equationSystem.getStateVector().set(x);

        DenseMatrix reference = (DenseMatrix) context.getJacobianMatrix().getMatrix();
        fixed.updateDer();
        FixedStructure<AcVariableType, AcEquationType> structure = fixed.getStructure();
        int variableCount = structure.getVariableCount();
        double[][] column = new double[variableCount][variableCount];
        for (int position = 0; position < variableCount; position++) {
            for (int i = structure.getColumnStart()[position]; i < structure.getColumnStart()[position + 1]; i++) {
                column[position][structure.getRowIndices()[i]] = fixed.getValues()[i];
            }
        }

        int activeCount = 0;
        for (int equation = 0; equation < structure.getEquationCount(); equation++) {
            int position = fixed.getPositionOfEquation(equation);
            String name = state + ", " + structure.getType(equation) + " " + structure.getElementNum(equation);
            assertEquals(structure.isActive(equation), position >= 0, name);
            if (position >= 0) {
                activeCount++;
                int referenceColumn = structure.getColumnInEquationSystem(equation);
                for (int row = 0; row < variableCount; row++) {
                    int referenceRow = structure.getVariable(row).getRow();
                    double expected = referenceRow >= 0 ? reference.get(referenceRow, referenceColumn) : 0;
                    assertClose(expected, column[position][row], name + ", " + structure.getVariable(row));
                }
            }
        }
        assertEquals(equationSystem.getIndex().getColumnCount(), activeCount, state);
        for (int position = 0; position < variableCount; position++) {
            if (fixed.getEquationOfPosition(position) < 0) {
                for (int row = 0; row < variableCount; row++) {
                    assertEquals(row == position ? 1 : 0, column[position][row], state + ", unpaired position " + structure.getVariable(position));
                }
            }
        }

    }

    /**
     * Solving the fixed system gives OLF's own Newton step: the positions only put the equations in another order, and a
     * position without equation leaves its variable where it is.
     */
    private static void checkNewtonStep(AcLoadFlowContext context, FixedEquationSystemIndex<AcVariableType, AcEquationType> fixed) {
        double[] referenceStep = context.getEquationVector().getArray().clone();
        double[] targets = context.getTargetVector().getArray();
        for (int i = 0; i < referenceStep.length; i++) {
            referenceStep[i] -= targets[i];
        }
        double[] mismatch = referenceStep.clone();
        context.getJacobianMatrix().solveTransposed(referenceStep);

        FixedStructure<AcVariableType, AcEquationType> structure = fixed.getStructure();
        int variableCount = structure.getVariableCount();
        double[] step = new double[variableCount];
        for (int equation = 0; equation < structure.getEquationCount(); equation++) {
            if (fixed.getPositionOfEquation(equation) >= 0) {
                step[fixed.getPositionOfEquation(equation)] = mismatch[structure.getColumnInEquationSystem(equation)];
            }
        }
        // the matrix a solver would keep: built once on the matrix structure, then filled in place
        SparseMatrix matrix = new SparseMatrix(variableCount, variableCount, structure.getColumnStart(), structure.getRowIndices(),
                new double[structure.getValueCount()]);
        fixed.updateDer(matrix.getValues());
        try (LUDecomposition lu = matrix.decomposeLU()) {
            lu.solveTransposed(step);
        }
        for (int position = 0; position < variableCount; position++) {
            int row = structure.getVariable(position).getRow();
            double expected = row >= 0 ? referenceStep[row] : 0;
            assertEquals(expected, step[position], 1e-8 * Math.max(1, Math.abs(expected)), "Newton step of " + structure.getVariable(position));
        }
    }

    private static void assertClose(double expected, double actual, String message) {
        assertEquals(expected, actual, 1e-12 * Math.max(1, Math.abs(expected)), message);
    }
}
