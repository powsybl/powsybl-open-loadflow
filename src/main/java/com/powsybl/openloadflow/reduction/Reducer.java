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

import java.util.List;
import java.util.Objects;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class Reducer {

    record EquivalentBranch(LfBus bus1, LfBus bus2, double g, double b) { }
    record EquivalentShunt(LfBus bus, double g, double b) { }
    record EquivalentInjection(LfBus bus, double p, double q) { }

    private final Network network;
    private final LfNetwork lfNetwork;
    private final ZoneDecomposition zones;
    private final List<EquivalentBranch> equivalentBranches;
    private final List<EquivalentShunt> equivalentShunts;
    private final List<EquivalentInjection> equivalentInjections;
    public static final String EQUIVALENT = "-equivalent";

    Reducer(Network network, LfNetwork lfNetwork, ZoneDecomposition zones, List<EquivalentBranch> equivalentBranches, List<EquivalentShunt> equivalentShunts, List<EquivalentInjection> equivalentInjections) {
        this.network = network;
        this.lfNetwork = lfNetwork;
        this.zones = zones;
        this.equivalentBranches = equivalentBranches;
        this.equivalentShunts = equivalentShunts;
        this.equivalentInjections = equivalentInjections;
    }

    public void reduce() {
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
            // because an external and boundary voltage level may be in the same substation
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
        equivalentBranches.forEach(this::addEquivalentBranch);
        equivalentShunts.forEach(this::addEquivalentShunt);
        equivalentInjections.forEach(this::addEquivalentInjection);
    }

    private String getBusId(LfBus lfBus) {
        return lfBus.getId() + EQUIVALENT;
    }

    private void removeBranch(LfBranch lfBranch) {
        if (lfBranch instanceof LfLegBranch legBranch) {
            ThreeWindingsTransformer tw3 = network.getThreeWindingsTransformer(legBranch.getTwt().getId());
            if (tw3 != null) {
                tw3.remove();
            }
        } else {
            Connectable<?> branch = network.getConnectable(lfBranch.getId());
            branch.remove();
        }
    }

    private void addEquivalentBranch(EquivalentBranch equivalentBranch) {
        double ySquare = equivalentBranch.g * equivalentBranch.g + equivalentBranch.b * equivalentBranch.b;

        Bus bus1 = network.getBusBreakerView().getBus(getBusId(equivalentBranch.bus1));
        Bus bus2 = network.getBusBreakerView().getBus(getBusId(equivalentBranch.bus2));

        network.newLine()
            .setId(equivalentBranch.bus1.getId() + "-" + equivalentBranch.bus2.getId() + "-equivalent-branch")
            .setName("Equivalent branch " + equivalentBranch.bus1.getId() + "-" + equivalentBranch.bus2.getId())
            .setEquivalent(true)
            .setR(equivalentBranch.g / ySquare)
            .setX(-equivalentBranch.b / ySquare)
            .setVoltageLevel1(bus1.getVoltageLevel().getId())
            .setBus1(bus1.getId())
            .setVoltageLevel2(bus2.getVoltageLevel().getId())
            .setBus2(bus2.getId())
            .add();
    }

    private void addEquivalentShunt(EquivalentShunt equivalentShunt) {
        Bus bus = network.getBusBreakerView().getBus(getBusId(equivalentShunt.bus));

        bus.getVoltageLevel().newShuntCompensator()
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
    }

    private void addEquivalentInjection(EquivalentInjection equivalentInjection) {
        Bus bus = network.getBusBreakerView().getBus(getBusId(equivalentInjection.bus));

        bus.getVoltageLevel().newGenerator()
            .setId(equivalentInjection.bus.getId() + "-equivalent-injection")
            .setName("Equivalent injection " + equivalentInjection.bus.getId())
            .setEquivalent(true)
            .setMinP(-Double.MAX_VALUE)
            .setMaxP(Double.MAX_VALUE)
            .setTargetP(equivalentInjection.p)
            .setLocalTargetQ(equivalentInjection.q)
            .setBus(bus.getId())
            .add();
    }

}
