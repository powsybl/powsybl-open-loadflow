/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.powsybl.contingency.Contingency;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfContingency {

    private final Contingency contingency;

    private final Set<LfBus> buses;

    private final Set<LfBranch> branches;

    private double activePowerLoss;

    public LfContingency(Contingency contingency, Set<LfBus> buses, Set<LfBranch> branches) {
        this.contingency = Objects.requireNonNull(contingency);
        this.buses = Objects.requireNonNull(buses);
        this.branches = Objects.requireNonNull(branches);
        double lose = 0;
        for (LfBus bus : buses) {
            lose += bus.getGenerationTargetP() - bus.getLoadTargetP();
        }
        this.activePowerLoss = lose;
    }

    public Contingency getContingency() {
        return contingency;
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

    public static List<LfContingency> createContingencies(List<PropagatedContingency> propagatedContingencies, LfNetwork network,
                                                          GraphDecrementalConnectivity<LfBus> connectivity) {
        List<LfContingency> contingencies = new ArrayList<>();
        Iterator<PropagatedContingency> contingencyContextIt = propagatedContingencies.iterator();
        while (contingencyContextIt.hasNext()) {
            PropagatedContingency propagatedContingency = contingencyContextIt.next();

            // find contingency branches that are part of this network
            Set<LfBranch> branches = new HashSet<>(1);
            Iterator<String> branchIt = propagatedContingency.getBranchIdsToOpen().iterator();
            while (branchIt.hasNext()) {
                String branchId = branchIt.next();
                LfBranch branch = network.getBranchById(branchId);
                if (branch != null) {
                    branches.add(branch);
                    branchIt.remove();
                }
            }

            // if no more branch in the contingency, remove contingency from the list because
            // it won't be part of another network
            if (propagatedContingency.getBranchIdsToOpen().isEmpty()) {
                contingencyContextIt.remove();
            }

            // check if contingency split this network into multiple components
            if (branches.isEmpty()) {
                continue;
            }

            // update connectivity with triggered branches
            for (LfBranch branch : branches) {
                connectivity.cut(branch.getBus1(), branch.getBus2());
            }

            // add to contingency description buses and branches that won't be part of the main connected
            // component in post contingency state
            Set<LfBus> buses = connectivity.getSmallComponents().stream().flatMap(Set::stream).collect(Collectors.toSet());
            buses.forEach(b -> branches.addAll(b.getBranches()));

            // reset connectivity to discard triggered branches
            connectivity.reset();

            contingencies.add(new LfContingency(propagatedContingency.getContingency(), buses, branches));
        }

        return contingencies;
    }

    public void writeJson(Writer writer) {
        Objects.requireNonNull(writer);
        try (JsonGenerator jsonGenerator = new JsonFactory()
                .createGenerator(writer)
                .useDefaultPrettyPrinter()) {
            jsonGenerator.writeStartObject();

            jsonGenerator.writeStringField("id", contingency.getId());

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
