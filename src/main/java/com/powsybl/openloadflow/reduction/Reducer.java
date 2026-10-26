/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfLegBranch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The Reduction engine.
 *
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class Reducer {

    /**
     * A constant used for labeling equivalent elements within the reduced network.
     */
    public static final String EQUIVALENT = "-equivalent";
    private final Network network;
    private final LfNetwork lfNetwork;
    private final ZoneDecomposition zones;

    /**
     * Create a new Reducer instance for performing network reduction.
     *
     * @param network The {@link Network} to be reduced.
     * @param lfNetwork The {@link LfNetwork} used for the reduction operation.
     * @param zones The {@link ZoneDecomposition} describing the part to keep (internal) and the part to reduce (external)
     */
    Reducer(Network network, LfNetwork lfNetwork, ZoneDecomposition zones) {
        this.network = network;
        this.lfNetwork = lfNetwork;
        this.zones = zones;
    }

    /**
     * Reduce the network, removing the external part and replacing it with equivalents
     * calculated using the Ward reduction method.
     *
     * @param wardEquivalent The {@link WardEquivalent} model.
     * @return a {@link ReductionChanges} record containing the list of removed voltage levels and added equivalents.
     */
    public ReductionChanges reduceWithWardEquivalent(WardEquivalent wardEquivalent) {
        // Init list of reduction changes
        // Retrieve now the list of voltage levels and substations as the following operations may invalidate buses
        List<VoltageLevel> externalVoltageLevels = zones.getExternalBuses().stream()
            .map(b -> network.getVoltageLevel(b.getVoltageLevelId()))
            .toList();
        List<VoltageLevel> boundaryVoltageLevels = zones.getBoundaryBuses().stream()
            .map(b -> network.getVoltageLevel(b.getVoltageLevelId()))
            .toList();
        List<Substation> externalSubstations = externalVoltageLevels.stream()
            .map(VoltageLevel::getNullableSubstation)
            .filter(Objects::nonNull)
            // because an external and a boundary voltage level may be in the same substation
            .filter(s -> s.getVoltageLevelStream().noneMatch(boundaryVoltageLevels::contains))
            .distinct()
            .toList();

        // Create a new voltage level for each boundary bus
        for (LfBus boundaryBus : zones.getBoundaryBuses()) {
            String newVlId = boundaryBus.getVoltageLevelId() + EQUIVALENT;
            VoltageLevel newVl = network.getVoltageLevel(newVlId);
            if (newVl == null) {
                Substation boundarySubstation = network.getVoltageLevel(boundaryBus.getVoltageLevelId()).getNullableSubstation();
                VoltageLevelAdder vlAdder;
                if (boundarySubstation != null) {
                    vlAdder = boundarySubstation.newVoltageLevel();
                } else {
                    vlAdder = network.newVoltageLevel();
                }
                newVl = vlAdder.setId(newVlId)
                    .setNominalV(boundaryBus.getNominalV())
                    .setTopologyKind(TopologyKind.BUS_BREAKER)
                    .add();
            }

            Bus newBus = newVl.getBusBreakerView().newBus()
                .setId(getBusId(boundaryBus))
                .add();

            // Move the tie-lines to the new voltage level and bus
            for (LfBranch lfBranch : boundaryBus.getBranches()) {
                Branch<?> branch = network.getBranch(lfBranch.getId());
                if (zones.getInternalBuses().contains(lfBranch.getBus1())) {
                    branch.getTerminal2().getBusBreakerView().moveConnectable(newBus.getId(), true);
                } else if (zones.getInternalBuses().contains(lfBranch.getBus2())) {
                    branch.getTerminal1().getBusBreakerView().moveConnectable(newBus.getId(), true);
                }
            }
        }

        // Remove external branches, voltage levels and substations
        lfNetwork.getBranches().stream()
            .filter(b -> !zones.getInternalBuses().contains(b.getBus1())
                && !zones.getInternalBuses().contains(b.getBus2()))
            .forEach(this::removeBranch);
        externalVoltageLevels.forEach(VoltageLevel::remove);
        boundaryVoltageLevels.forEach(VoltageLevel::remove);
        externalSubstations.forEach(Substation::remove);

        // Add equivalents
        List<Line> addedLines = addEquivalentBranches(wardEquivalent.getEquivalentBranches());
        List<ShuntCompensator> addedShuntCompensators = addEquivalentShunts(wardEquivalent.getEquivalentShunts());
        List<Generator> addedGenerators = addEquivalentInjections(wardEquivalent.getEquivalentInjections());

        return new ReductionChanges(externalVoltageLevels, addedLines, addedShuntCompensators, addedGenerators);
    }

    private String getBusId(LfBus lfBus) {
        return lfBus.getId() + EQUIVALENT;
    }

    private void removeBranch(LfBranch lfBranch) {
        // Remove the given branch from the network
        if (lfBranch instanceof LfLegBranch legBranch) {
            ThreeWindingsTransformer tw3 = network.getThreeWindingsTransformer(legBranch.getTwt().getId());
            // The three-winding transformer may have already been removed from another leg branch
            if (tw3 != null) {
                tw3.remove();
            }
        } else {
            Connectable<?> branch = network.getConnectable(lfBranch.getId());
            branch.remove();
        }
    }

    private List<Line> addEquivalentBranches(List<EquivalentBranch> equivalentBranches) {
        // Add equivalent branches to the network
        List<Line> addedLines = new ArrayList<>();

        for (EquivalentBranch equivalentBranch : equivalentBranches) {
            double ySquare = equivalentBranch.g * equivalentBranch.g + equivalentBranch.b * equivalentBranch.b;

            Bus bus1 = network.getBusBreakerView().getBus(getBusId(equivalentBranch.bus1));
            Bus bus2 = network.getBusBreakerView().getBus(getBusId(equivalentBranch.bus2));

            Line line = network.newLine()
                .setId(equivalentBranch.bus1.getId() + "-" + equivalentBranch.bus2.getId() + "-equivalent-branch")
                .setName("Equivalent branch " + equivalentBranch.bus1.getId() + "-" + equivalentBranch.bus2.getId())
                .setEquivalent(true)
                .setR(equivalentBranch.g / ySquare)
                .setX(-equivalentBranch.b / ySquare)
                .setG1(equivalentBranch.g1)
                .setB1(equivalentBranch.b1)
                .setG2(equivalentBranch.g2)
                .setB2(equivalentBranch.b2)
                .setVoltageLevel1(bus1.getVoltageLevel().getId())
                .setBus1(bus1.getId())
                .setVoltageLevel2(bus2.getVoltageLevel().getId())
                .setBus2(bus2.getId())
                .add();

            addedLines.add(line);
        }

        return addedLines;
    }

    private List<ShuntCompensator> addEquivalentShunts(List<EquivalentShunt> equivalentShunts) {
        // Add equivalent shunts to the network
        List<ShuntCompensator> addedShuntCompensators = new ArrayList<>();

        for (EquivalentShunt equivalentShunt : equivalentShunts) {
            Bus bus = network.getBusBreakerView().getBus(getBusId(equivalentShunt.bus));

            ShuntCompensator shunt = bus.getVoltageLevel().newShuntCompensator()
                .setId(equivalentShunt.bus.getId() + "-equivalent-shunt")
                .setName("Equivalent shunt " + equivalentShunt.bus.getId())
                .setEquivalent(true)
                .setSectionCount(1)
                .newLinearModel()
                    .setMaximumSectionCount(1)
                    .setBPerSection(equivalentShunt.b)
                    .setGPerSection(equivalentShunt.g)
                    .add()
                .setBus(bus.getId())
                .add();

            addedShuntCompensators.add(shunt);
        }

        return addedShuntCompensators;
    }

    private List<Generator> addEquivalentInjections(List<EquivalentInjection> equivalentInjections) {
        // Add equivalent injections to the network
        List<Generator> addedGenerators = new ArrayList<>();

        for (EquivalentInjection equivalentInjection : equivalentInjections) {
            Bus bus = network.getBusBreakerView().getBus(getBusId(equivalentInjection.bus));

            Generator generator = bus.getVoltageLevel().newGenerator()
                .setId(equivalentInjection.bus.getId() + "-equivalent-injection")
                .setName("Equivalent injection " + equivalentInjection.bus.getId())
                .setEquivalent(true)
                .setMinP(-Double.MAX_VALUE)
                .setMaxP(Double.MAX_VALUE)
                .setTargetP(equivalentInjection.p)
                .setLocalTargetQ(equivalentInjection.q)
                .setBus(bus.getId())
                .add();

            addedGenerators.add(generator);
        }

        return addedGenerators;
    }

    /**
     * An equivalent branch in the network.
     *
     * @param bus1 The first bus of the branch.
     * @param bus2 The second bus of the branch.
     * @param g The series conductance of the branch.
     * @param b The series susceptance of the branch.
     * @param g1 The shunt conductance of the first bus.
     * @param b1 The shunt susceptance of the first bus.
     * @param g2 The shunt conductance of the second bus.
     * @param b2 The shunt susceptance of the second bus.
     */
    public record EquivalentBranch(LfBus bus1, LfBus bus2, double g, double b, double g1, double b1, double g2, double b2) {
    }

    /**
     * An equivalent shunt in the network.
     *
     * @param bus The bus of the shunt.
     * @param g The shunt conductance.
     * @param b The shunt susceptance.
     */
    public record EquivalentShunt(LfBus bus, double g, double b) {
    }

    /**
     * An equivalent injection in the network.
     *
     * @param bus The bus of the injection.
     * @param p The active power of the injection.
     * @param q The reactive power of the injection.
     */
    public record EquivalentInjection(LfBus bus, double p, double q) {
    }

    /**
     * The changes made to the network during the reduction.
     *
     * @param removedVoltageLevels The list of {@link VoltageLevel} removed from the network.
     * @param addedLines The list of {@link Line} added to the network.
     * @param addedShuntCompensators The list of {@link ShuntCompensator} added to the network.
     * @param addedGenerators The list of {@link Generator} added to the network.
     */
    public record ReductionChanges(List<VoltageLevel> removedVoltageLevels, List<Line> addedLines, List<ShuntCompensator> addedShuntCompensators, List<Generator> addedGenerators) {
    }

}
