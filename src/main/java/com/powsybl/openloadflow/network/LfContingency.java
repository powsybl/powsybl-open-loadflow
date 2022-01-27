/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.util.PropagatedContingency;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfContingency {

    private final String id;

    private final int index;

    private final Set<LfBus> buses;

    private final Set<LfBranch> branches;

    private final Set<Pair<LfShunt, Double>> shunts;

    private final Set<Triple<LfBus, Pair<Double, Double>, Double>> loadBuses;

    private final Set<LfGenerator> generators;

    private double activePowerLoss;

    public LfContingency(String id, int index, Set<LfBus> buses, Set<LfBranch> branches, Set<Pair<LfShunt, Double>> shunts,
                         Set<Triple<LfBus, Pair<Double, Double>, Double>> loadBuses, Set<LfGenerator> generators) {
        this.id = Objects.requireNonNull(id);
        this.index = index;
        this.buses = Objects.requireNonNull(buses);
        this.branches = Objects.requireNonNull(branches);
        this.shunts = Objects.requireNonNull(shunts);
        this.loadBuses = Objects.requireNonNull(loadBuses);
        this.generators = Objects.requireNonNull(generators);
        double lose = 0;
        for (LfBus bus : buses) {
            lose += bus.getGenerationTargetP() - bus.getLoadTargetP();
        }
        for (Triple<LfBus, Pair<Double, Double>, Double> loadBus : loadBuses) {
            lose -= loadBus.getMiddle().getKey();
        }
        for (LfGenerator generator : generators) {
            lose += generator.getTargetP();
        }
        this.activePowerLoss = lose;
    }

    public String getId() {
        return id;
    }

    public int getIndex() {
        return index;
    }

    public Set<LfBus> getBuses() {
        return buses;
    }

    public Set<LfBranch> getBranches() {
        return branches;
    }

    public double getActivePowerLoss() {
        return activePowerLoss;
    }

    public static Optional<LfContingency> create(PropagatedContingency propagatedContingency, LfNetwork network,
                                                 GraphDecrementalConnectivity<LfBus> connectivity, boolean useSmallComponents) {
        // find contingency branches that are part of this network
        Set<LfBranch> branches = new HashSet<>(1);
        for (String branchId : propagatedContingency.getBranchIdsToOpen()) {
            LfBranch branch = network.getBranchById(branchId);
            if (branch != null) {
                branches.add(branch);
            }
        }

        // check if contingency split this network into multiple components
        if (branches.isEmpty() && propagatedContingency.getShuntIdsToLose().isEmpty()
                && propagatedContingency.getLoadIdsToLose().isEmpty() && propagatedContingency.getGeneratorIdsToLose().isEmpty()) {
            return Optional.empty();
        }

        // update connectivity with triggered branches
        for (LfBranch branch : branches) {
            connectivity.cut(branch.getBus1(), branch.getBus2());
        }

        // add to contingency description buses and branches that won't be part of the main connected
        // component in post contingency state
        Set<LfBus> buses;
        if (useSmallComponents) {
            buses = connectivity.getSmallComponents().stream().flatMap(Set::stream).collect(Collectors.toSet());
        } else {
            int slackBusComponent = connectivity.getComponentNumber(network.getSlackBus());
            buses = network.getBuses().stream().filter(b -> connectivity.getComponentNumber(b) != slackBusComponent).collect(Collectors.toSet());
        }
        buses.forEach(b -> branches.addAll(b.getBranches()));

        Set<Pair<LfShunt, Double>> shunts = new HashSet<>(1);
        for (Pair<String, Double> shuntAndB : propagatedContingency.getShuntIdsToLose()) {
            LfShunt shunt = network.getShuntById(shuntAndB.getKey());
            if (shunt != null) {
                shunts.add(Pair.of(shunt, shuntAndB.getValue()));
            }
        }

        Set<LfGenerator> generators = new HashSet<>(1);
        for (Pair<String, Double> generatorInfo : propagatedContingency.getGeneratorIdsToLose()) {
            LfGenerator generator = network.getGeneratorById(generatorInfo.getKey());
            generators.add(generator);
        }

        Set<Triple<LfBus, Pair<Double, Double>, Double>> loadBuses = new HashSet<>(1);
        for (Triple<String, Pair<Double, Double>, Double> loadInfo : propagatedContingency.getLoadIdsToLose()) {
            LfBus bus = network.getBusById(loadInfo.getLeft());
            if (bus != null) {
                loadBuses.add(Triple.of(bus, loadInfo.getMiddle(), loadInfo.getRight()));
            }
        }

        // reset connectivity to discard triggered branches
        connectivity.reset();

        return Optional.of(new LfContingency(propagatedContingency.getContingency().getId(), propagatedContingency.getIndex(), buses, branches, shunts, loadBuses, generators));
    }

    public void apply(LoadFlowParameters parameters) {
        for (LfBranch branch : branches) {
            branch.setDisabled(true);
        }
        for (LfBus bus : buses) {
            bus.setDisabled(true);
        }
        for (Pair<LfShunt, Double> shuntInfo : shunts) {
            LfShunt shunt = shuntInfo.getKey();
            shunt.setB(shunt.getB() - shuntInfo.getValue());
        }
        for (Triple<LfBus, Pair<Double, Double>, Double> loadInfo : loadBuses) {
            LfBus bus = loadInfo.getLeft();
            bus.setLoadTargetP(bus.getLoadTargetP() - getUpdatedLoadP0(bus, parameters, loadInfo.getMiddle().getKey(), loadInfo.getMiddle().getValue()));
            bus.setLoadTargetQ(bus.getLoadTargetQ() - loadInfo.getRight());
            bus.getLfLoads().setAbsVariableLoadTargetP(bus.getLfLoads().getAbsVariableLoadTargetP() - Math.abs(loadInfo.getMiddle().getValue()) * PerUnit.SB);
        }
        for (LfGenerator generator : generators) {
            generator.setTargetP(0);
            LfBus bus = generator.getBus();
            generator.setParticipating(false);
            if (generator.getGeneratorControlType() != LfGenerator.GeneratorControlType.OFF) {
                generator.setGeneratorControlType(LfGenerator.GeneratorControlType.OFF);
            } else {
                bus.setGenerationTargetQ(bus.getGenerationTargetQ() - generator.getTargetQ());
            }
        }
    }

    public static double getUpdatedLoadP0(LfBus bus, LoadFlowParameters parameters, double initialP0, double initialVariableActivePower) {
        double factor = 0.0;
        if (parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD) {
            factor = Math.abs(initialP0) / (bus.getLfLoads().getAbsVariableLoadTargetP() / PerUnit.SB);
        } else if (parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD) {
            factor = initialVariableActivePower / (bus.getLfLoads().getAbsVariableLoadTargetP() / PerUnit.SB);
        }
        return initialP0 + (bus.getLoadTargetP() - bus.getInitialLoadTargetP()) * factor;
    }

    public void writeJson(Writer writer) {
        Objects.requireNonNull(writer);
        try (JsonGenerator jsonGenerator = new JsonFactory()
                .createGenerator(writer)
                .useDefaultPrettyPrinter()) {
            jsonGenerator.writeStartObject();

            jsonGenerator.writeStringField("id", id);

            jsonGenerator.writeFieldName("buses");
            int[] sortedBuses = buses.stream().mapToInt(LfBus::getNum).sorted().toArray();
            jsonGenerator.writeArray(sortedBuses, 0, sortedBuses.length);

            jsonGenerator.writeFieldName("branches");
            int[] sortedBranches = branches.stream().mapToInt(LfBranch::getNum).sorted().toArray();
            jsonGenerator.writeArray(sortedBranches, 0, sortedBranches.length);

            jsonGenerator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
