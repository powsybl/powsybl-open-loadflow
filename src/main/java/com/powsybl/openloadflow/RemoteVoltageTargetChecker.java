/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.google.common.base.Stopwatch;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.ac.AcJacobianMatrix;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.adm.AdmittanceEquationSystem;
import com.powsybl.openloadflow.adm.AdmittanceMatrix;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jgrapht.Graph;
import org.jgrapht.graph.Pseudograph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class RemoteVoltageTargetChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteVoltageTargetChecker.class);

    private final LfNetwork network;

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    private final JacobianMatrix<AcVariableType, AcEquationType> j;

    public RemoteVoltageTargetChecker(LfNetwork network,
                                      EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                      JacobianMatrix<AcVariableType, AcEquationType> j) {
        this.network = Objects.requireNonNull(network);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.j = Objects.requireNonNull(j);
    }

    public static RemoteVoltageTarget.Result findElementsToDiscardFromVoltageControl(Network network, LoadFlowParameters parameters) {
        return findElementsToDiscardFromVoltageControl(network, parameters, new SparseMatrixFactory());
    }

    public static RemoteVoltageTarget.Result findElementsToDiscardFromVoltageControl(Network network, LoadFlowParameters parameters, MatrixFactory matrixFactory) {
        List<RemoteVoltageTarget.IncompatibleTargetResolution> incompatibleTargetResolutions = new ArrayList<>();
        List<RemoteVoltageTarget.UnrealisticTarget> unrealisticTargets = new ArrayList<>();
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(parameters);
        GraphConnectivityFactory<LfBus, LfBranch> selectedConnectivityFactory = OpenLoadFlowParameters.getConnectivityFactory(parametersExt, new EvenShiloachGraphDecrementalConnectivityFactory<>());
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt, matrixFactory, selectedConnectivityFactory);
        for (LfNetwork lfNetwork : LfNetwork.load(network, new LfNetworkLoaderImpl(), acParameters.getNetworkParameters())) {
            var equationSystem = new AcEquationSystemCreator(lfNetwork, acParameters.getEquationSystemCreationParameters())
                    .create();
            try (var j = new AcJacobianMatrix(equationSystem, matrixFactory, lfNetwork)) {
                var result = new RemoteVoltageTargetChecker(lfNetwork, equationSystem, j)
                        .check(new RemoteVoltageTargetCheckerParameters(acParameters.getMatrixFactory()));
                List<RemoteVoltageTarget.LfIncompatibleTargetResolution> incomatibleTargetResolutions = resolveIncompatbleTargets(result.lfIncompatibleTarget());
                incomatibleTargetResolutions.forEach(tr -> incompatibleTargetResolutions.add(tr.toIidm()));

                result.lfUnrealisticTargets().forEach(ut -> unrealisticTargets.add(ut.toIidm()));
            }
        }
        // Return sorted list for repeatable results
        return new RemoteVoltageTarget.Result(incompatibleTargetResolutions.stream().sorted(Comparator.comparing(RemoteVoltageTarget.IncompatibleTargetResolution::controlledBusToFixId)).toList(),
                unrealisticTargets.stream().sorted(Comparator.comparing(RemoteVoltageTarget.UnrealisticTarget::controllerBusId)).toList());
    }

    private static Graph<LfBus, LfBranch> createGraph(LfNetwork lfNetwork) {
        Graph<LfBus, LfBranch> graph = new Pseudograph<>(LfBranch.class);
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null && !branch.isDisabled()) {
                if (!graph.containsVertex(bus1)) {
                    graph.addVertex(bus1);
                }
                if (!graph.containsVertex(bus2)) {
                    graph.addVertex(bus2);
                }
                graph.addEdge(bus1, bus2, branch);
            }
        }
        return graph;
    }

    private static Set<LfBus> exploreNeighbors(Graph<LfBus, LfBranch> graph, LfBus bus, int maxDepth) {
        var it = new BreadthFirstIterator<>(graph, bus);
        Set<LfBus> neighbors = new HashSet<>();
        while (it.hasNext()) {
            LfBus b = it.next();
            int currentDepth = it.getDepth(b);
            if (currentDepth > maxDepth) {
                break;
            }
            if (b != bus) {
                neighbors.add(b);
            }
        }
        return neighbors;
    }

    private void checkIncompatibleTargets(RemoteVoltageTargetCheckerParameters parameters,
                                          Set<LfBus> controlledBuses,
                                          RemoteVoltageTarget.LfResult result) {
        Graph<LfBus, LfBranch> graph = createGraph(network);

        var ySystem = AdmittanceEquationSystem.create(network, new VariableSet<>());
        try (var y = AdmittanceMatrix.create(ySystem, parameters.getMatrixFactory())) {
            for (LfBus controlledBus : controlledBuses) {
                Set<LfBus> neighborControlledBuses = exploreNeighbors(graph, controlledBus, parameters.getControlledBusNeighborsExplorationDepth())
                        .stream().filter(controlledBuses::contains)
                        .collect(Collectors.toSet());
                if (!neighborControlledBuses.isEmpty()) {
                    for (LfBus neighborControlledBus : neighborControlledBuses) {
                        double z = y.getZ(controlledBus, neighborControlledBus).abs();
                        double dv = Math.abs(controlledBus.getHighestPriorityTargetV().orElseThrow() - neighborControlledBus.getHighestPriorityTargetV().orElseThrow());
                        double targetVoltagePlausibilityIndicator = dv / z;
                        LOGGER.debug("Indicator: {}/{} dv = {} dz = {} indicator = {}",
                                controlledBus.getId(), neighborControlledBus.getId(), dv, z, targetVoltagePlausibilityIndicator);
                        if (targetVoltagePlausibilityIndicator > parameters.getTargetVoltagePlausibilityIndicatorThreshold()) {
                            result.lfIncompatibleTarget().add(new RemoteVoltageTarget.LfIncompatibleTarget(controlledBus, neighborControlledBus, targetVoltagePlausibilityIndicator));
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private EquationTerm<AcEquationType, AcEquationType> getCalculatedV(LfBus controllerBus) {
        return (EquationTerm<AcEquationType, AcEquationType>) controllerBus.getCalculatedV();
    }

    private void checkUnrealisticTargets(RemoteVoltageTargetCheckerParameters parameters,
                                         List<LfBus> generatorControlledBuses,
                                         RemoteVoltageTarget.LfResult result) {
        AcSolverUtil.initStateVector(network, equationSystem, new UniformValueVoltageInitializer());

        // calculate target voltage to calculated voltage sensibilities
        var busNumToSensiColumn = LfBus.buildIndex(new ArrayList<>(generatorControlledBuses));
        DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), generatorControlledBuses.size());
        for (LfBus controlledBus : generatorControlledBuses) {
            equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                    .ifPresent(equation -> {
                        if (equation.getColumn() >= 0) {
                            rhs.set(equation.getColumn(), busNumToSensiColumn.get(controlledBus.getNum()), 1d);
                        }
                    });
        }

        j.solveTransposed(rhs);

        for (LfBus controlledBus : generatorControlledBuses) {
            int controlledBusSensiColumn = busNumToSensiColumn.get(controlledBus.getNum());
            GeneratorVoltageControl voltageControl = controlledBus.getGeneratorVoltageControl().orElseThrow();
            if (!voltageControl.isLocalControl()) {
                for (LfBus controllerBus : voltageControl.getControllerElements()) {
                    var term = getCalculatedV(controllerBus);
                    double sensiVv = term.calculateSensi(rhs, controlledBusSensiColumn);
                    // this is the targe voltage shift from a normal value (1 pu)
                    double dvControlled = voltageControl.getTargetValue() - 1.0;
                    // thanks to the sensitivity, compute the corresponding controller bus side
                    // resulting calculated voltage
                    double estimatedDvController = dvControlled * sensiVv;
                    // check if not too far from 1 pu
                    if (Math.abs(estimatedDvController) > parameters.getControllerBusAcceptableVoltageDrop()) {
                        result.lfUnrealisticTargets().add(new RemoteVoltageTarget.LfUnrealisticTarget(controllerBus, estimatedDvController));
                    }
                }
            }
        }
    }

    RemoteVoltageTarget.LfResult check(RemoteVoltageTargetCheckerParameters parameters) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        RemoteVoltageTarget.LfResult result = new RemoteVoltageTarget.LfResult();

        // controlled buses whatever the voltage control type it is
        Set<LfBus> controlledBuses = network.getBuses().stream()
                .filter(bus -> !bus.isDisabled()
                        && bus.isVoltageControlled()
                        && bus.getVoltageControls().stream().anyMatch(vc -> !vc.isDisabled()))
                .collect(Collectors.toSet());
        checkIncompatibleTargets(parameters, controlledBuses, result);

        // for this check we only keep generator voltage controls
        List<LfBus> generatorControlledBuses = controlledBuses.stream()
                .filter(LfBus::isGeneratorVoltageControlled)
                .toList();
        checkUnrealisticTargets(parameters, generatorControlledBuses, result);

        LOGGER.debug("Remote voltage targets checked in {} ms", stopwatch.elapsed().toMillis());

        return result;
    }

    private static List<RemoteVoltageTarget.LfIncompatibleTargetResolution> resolveIncompatbleTargets(List<RemoteVoltageTarget.LfIncompatibleTarget> lfIncompatibleTargets) {

        List<RemoteVoltageTarget.LfIncompatibleTargetResolution> result = new ArrayList<>();

        // some buses could be part of multiple target v incompatible buses couples
        // fix the most referenced ones
        Map<LfBus, MutableInt> incompatibleControlledBusRefCount = new HashMap<>();
        for (RemoteVoltageTarget.LfIncompatibleTarget lfIncompatibleTarget : lfIncompatibleTargets) {
            incompatibleControlledBusRefCount.computeIfAbsent(lfIncompatibleTarget.controlledBus1(), k -> new MutableInt(0)).increment();
            incompatibleControlledBusRefCount.computeIfAbsent(lfIncompatibleTarget.controlledBus2(), k -> new MutableInt(0)).increment();
        }
        List<RemoteVoltageTarget.LfIncompatibleTarget> sorteLfIncompatibleTargets = lfIncompatibleTargets.stream()
                .sorted(Comparator.comparingDouble(t -> -Math.abs(t.targetVoltagePlausibilityIndicator())))
                .toList();
        Set<LfBus> controlledBusesToFix = new HashSet<>();
        for (RemoteVoltageTarget.LfIncompatibleTarget lfIncompatibleTarget : sorteLfIncompatibleTargets) {
            LfBus controlledBus1 = lfIncompatibleTarget.controlledBus1();
            LfBus controlledBus2 = lfIncompatibleTarget.controlledBus2();
            if (controlledBusesToFix.contains(controlledBus1) || controlledBusesToFix.contains(controlledBus2)) {
                continue;
            }
            LfBus controlledBusToFix = incompatibleControlledBusRefCount.get(controlledBus1).intValue() > incompatibleControlledBusRefCount.get(controlledBus2).intValue()
                    ? controlledBus1 : controlledBus2;
            // predicatable choice based on alphabetic id order in case of same refcount
            if (incompatibleControlledBusRefCount.get(controlledBus1).intValue() == incompatibleControlledBusRefCount.get(controlledBus2).intValue()) {
                controlledBusToFix = controlledBus1.getId().compareTo(controlledBus2.getId()) < 0
                        ? controlledBus1 : controlledBus2;
            }
            controlledBusesToFix.add(controlledBusToFix);
            Set<? extends LfElement> elementsToDisable = controlledBusToFix.getVoltageControls().stream().flatMap(vc -> vc.getControllerElements().stream()).collect(Collectors.toSet());
            result.add(new RemoteVoltageTarget.LfIncompatibleTargetResolution(controlledBusToFix, elementsToDisable, lfIncompatibleTarget));
        }

        return result;
    }

    public void fix(RemoteVoltageTargetCheckerParameters parameters) {
        RemoteVoltageTarget.LfResult result = check(parameters);

        List<RemoteVoltageTarget.LfIncompatibleTargetResolution> incompatibleTargetResolutions = resolveIncompatbleTargets(result.lfIncompatibleTarget());
        for (RemoteVoltageTarget.LfIncompatibleTargetResolution incompatibleTargetResolution : incompatibleTargetResolutions) {
            for (VoltageControl<? extends LfElement> voltageControl : incompatibleTargetResolution.controlledBusToFix().getVoltageControls()) {
                LOGGER.warn("Controlled buses '{}' and '{}' have incompatible target voltages (plausibility indicator: {}): disable controller elements {}",
                        incompatibleTargetResolution.largestLfIncompatibleTarget().controlledBus1().getId(),
                        incompatibleTargetResolution.largestLfIncompatibleTarget().controlledBus2().getId(),
                        incompatibleTargetResolution.largestLfIncompatibleTarget().targetVoltagePlausibilityIndicator(),
                        voltageControl.getControllerElements().stream().map(LfElement::getId).toList());
                for (var controllerElement : voltageControl.getControllerElements()) {
                    controllerElement.setVoltageControlEnabled(false);
                }
            }
        }

        long remainingPV = network.getControllerElements(VoltageControl.Type.GENERATOR).stream().map(elt -> (LfBus) elt)
                .filter(LfBus::isGeneratorVoltageControlEnabled).count();

        for (RemoteVoltageTarget.LfUnrealisticTarget lfUnrealisticTarget : result.lfUnrealisticTargets()) {
            if (remainingPV > 1) {
                LfBus controllerBus = lfUnrealisticTarget.controllerBus();
                LfBus controlledBus = controllerBus.getGeneratorVoltageControl().orElseThrow().getControlledBus();
                LOGGER.warn("Controlled bus '{}' has an unrealistic target voltage {} Kv, causing a severe controller bus '{}' voltage drop (estimated at {} pu): disable controller bus",
                        controlledBus.getId(), controlledBus.getHighestPriorityTargetV().orElseThrow() * controlledBus.getNominalV(),
                        controllerBus.getId(), lfUnrealisticTarget.estimatedDvController());
                controllerBus.setGeneratorVoltageControlEnabled(false);
                remainingPV -= 1;
            }
        }

    }

}
