/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper methods to validate a DcComponent configuration and, when possible, automatically resolve an
 * otherwise invalid one.
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public final class LfDcComponentValidator {

    private LfDcComponentValidator() {
    }

    public static List<LfAcDcConverter> resolve(LfNetwork lfNetwork, int numDCC) {
        // Compute DC subcomponents graph
        GraphConnectivity<LfDcBus, LfDcLine> graph = buildDcSubComponentGraph(lfNetwork, numDCC);

        // Check all converters are indirectly connected to a DC ground
        for (LfAcDcConverter converter : getAcDcConverters(lfNetwork, numDCC)) {
            if (!isSubComponentConnectedToGround(graph.getConnectedComponent(converter.getDcBus1())) &&
                    !isSubComponentConnectedToGround(graph.getConnectedComponent(converter.getDcBus2()))) {
                throw new PowsyblException(String.format("Converter %s is not indirectly connected to a DC ground", converter.getId()));
            }
        }

        // Check each DC subcomponent has an element imposing voltage
        List<LfAcDcConverter> convertersToUpdate = new ArrayList<>();
        Set<String> visitedBusIds = new HashSet<>();
        for (LfDcBus dcBus : getDcBuses(lfNetwork, numDCC)) {
            if (visitedBusIds.contains(dcBus.getId())) {
                continue;
            }

            Set<LfDcBus> dcBusesInSubComponent = graph.getConnectedComponent(dcBus);
            visitedBusIds.addAll(dcBusesInSubComponent.stream().map(LfDcBus::getId).collect(Collectors.toSet()));
            resolveDcSubComponentIfNeeded(lfNetwork, numDCC, dcBusesInSubComponent, convertersToUpdate);
        }

        return convertersToUpdate;
    }

    /**
     * Add all converters of the DC subcomponent to V_DC control if the subcomponent has no element already imposing DC
     * voltage; do nothing otherwise.
     *
     * @throws PowsyblException If the DC subcomponent has no element imposing voltage and no converter can be promoted.
     */
    private static void resolveDcSubComponentIfNeeded(LfNetwork lfNetwork, int numDCC, Set<LfDcBus> dcBusesInSubComponent, List<LfAcDcConverter> convertersToUpdate) {
        List<LfAcDcConverter> convertersInSubComponent = getAcDcConvertersInSubComponent(lfNetwork, numDCC, dcBusesInSubComponent);
        if (isSubComponentConnectedToGround(dcBusesInSubComponent) ||
                convertersInSubComponent.stream().anyMatch(conv ->
                        // Tells whether a converter settles the DC voltage: V_DC does it directly, DC_DROOP does it through the droop law.
                        conv.getControlMode() == AcDcConverter.ControlMode.V_DC || conv.getControlMode() == AcDcConverter.ControlMode.DC_DROOP
                                || convertersToUpdate.contains(conv))
        ) {
            return; // already has (or was just given, while resolving an adjacent DC subcomponent) an element imposing DC voltage
        } else {
            if (convertersInSubComponent.isEmpty()) {
                throw new PowsyblException("DC component " + numDCC + " has a DC subcomponent with no DC ground "
                        + "and no AC-DC converter able to settle the DC voltage");
            }
            convertersToUpdate.addAll(convertersInSubComponent);
        }
    }

    /**
     * Tells whether a DC subcomponent has o grounded DC bus.
     */
    private static boolean isSubComponentConnectedToGround(Set<LfDcBus> dcBusesInSubComponent) {
        return dcBusesInSubComponent.stream().anyMatch(LfDcBus::isGrounded);
    }

    /**
     * @return All AC/DC converters in the DC component.
     */
    private static List<LfAcDcConverter> getAcDcConverters(LfNetwork lfNetwork, int numDCC) {
        return lfNetwork.getVoltageSourceConverters().stream().filter(vsc -> vsc.getNumDCC() == numDCC).map(LfAcDcConverter.class::cast).toList();
    }

    /**
     * @return All DC buses in the DC component.
     */
    private static List<LfDcBus> getDcBuses(LfNetwork lfNetwork, int numDCC) {
        return lfNetwork.getDcBuses().stream().filter(b -> b.getNumDCC() == numDCC).toList();
    }

    /**
     * Find all AC/DC converters connected to the DC subcomponent.
     *
     * @param dcBusesInSubComponent DC buses in the DC subcomponent.
     * @return The list of AC/DC converter in which one of the buses is part of the DC subcomponent.
     */
    private static List<LfAcDcConverter> getAcDcConvertersInSubComponent(LfNetwork lfNetwork, int numDCC, Set<LfDcBus> dcBusesInSubComponent) {
        List<LfAcDcConverter> convertersInSubComponent = new ArrayList<>();
        for (LfAcDcConverter converter : getAcDcConverters(lfNetwork, numDCC)) {
            if (dcBusesInSubComponent.contains(converter.getDcBus1()) || dcBusesInSubComponent.contains(converter.getDcBus2())) {
                convertersInSubComponent.add(converter);
            }
        }
        return convertersInSubComponent;
    }

    /**
     * Build DC subcomponent connectivity graph. Only LfDcLines are considered as edges, so each DC pole is in a
     * distinct connected component
     *
     * @return A graph with LfDcBuses as vertices and LfDcLines as edges.
     */
    private static GraphConnectivity<LfDcBus, LfDcLine> buildDcSubComponentGraph(LfNetwork lfNetwork, int numDCC) {
        // The function to get the vertex number from the LfDcBus cannot be LfDcBus::getNum because that number refers
        // to its numbering in the LfNetwork. However,the LfDcNetwork does not necessarily contain all DC buses of a
        // LfNetwork (i.e. there might be several DC components).
        // Therefore, we use List.indexOf as a ToIntFunction for the connectivity graph.
        List<LfDcBus> dcBusesInSubComponent = getDcBuses(lfNetwork, numDCC);
        GraphConnectivity<LfDcBus, LfDcLine> graph = new NaiveGraphConnectivityFactory<LfDcBus, LfDcLine>(dcBusesInSubComponent::indexOf).create();
        dcBusesInSubComponent.forEach(graph::addVertex);

        // All DC lines in the DC component.
        List<LfDcLine> dcLinesInSubComponent = lfNetwork.getDcLines().stream().filter(l -> l.getNumDCC() == numDCC).toList();
        dcLinesInSubComponent.stream()
                .filter(l -> l.getDcBus1() != null && l.getDcBus2() != null)  // DC line must be fully connected
                .forEach(l -> graph.addEdge(l.getDcBus1(), l.getDcBus2(), l));

        graph.startTemporaryChanges(false); // We need to call this to start using connectivity graph
        return graph;
    }

}
