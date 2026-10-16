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
 * Implementation of LfDCNetwork that provides a filtered view of LfNetwork DC equipment for a single DC component.
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public class LfDcNetworkImpl implements LfDcNetwork {

    private final LfNetwork lfNetwork;

    private final int numDC;

    public LfDcNetworkImpl(LfNetwork lfNetwork, int numDC) {
        this.lfNetwork = lfNetwork;
        this.numDC = numDC;
    }

    @Override
    public int getNumDC() {
        return numDC;
    }

    @Override
    public List<LfAcDcConverter> resolve() {
        // Compute DC subcomponents graph
        GraphConnectivity<LfDcBus, LfDcLine> graph = buildDcSubComponentGraph();

        // Check all converters are indirectly connected to a DC ground
        for (LfAcDcConverter converter : getAcDcConverters()) {
            if (!isSubComponentConnectedToGround(graph.getConnectedComponent(converter.getDcBus1())) &&
                    !isSubComponentConnectedToGround(graph.getConnectedComponent(converter.getDcBus2()))) {
                throw new PowsyblException(String.format("Converter %s is not indirectly connected to a DC ground", converter.getId()));
            }
        }

        // Check each DC subcomponent has an element imposing voltage
        List<LfAcDcConverter> convertersToUpdate = new ArrayList<>();
        Set<String> visitedBusIds = new HashSet<>();
        for (LfDcBus dcBus : getDcBuses()) {
            if (visitedBusIds.contains(dcBus.getId())) {
                continue;
            }

            Set<LfDcBus> dcBusesInSubComponent = graph.getConnectedComponent(dcBus);
            visitedBusIds.addAll(dcBusesInSubComponent.stream().map(LfDcBus::getId).collect(Collectors.toSet()));
            resolveDcSubComponentIfNeeded(dcBusesInSubComponent, convertersToUpdate);
        }

        return convertersToUpdate;
    }

    /**
     * Add all converters of the DC subcomponent to V_DC control if the subcomponent has no element already imposing DC
     * voltage; do nothing otherwise.
     *
     * @throws PowsyblException If the DC subcomponent has no element imposing voltage and no converter can be promoted.
     */
    private void resolveDcSubComponentIfNeeded(Set<LfDcBus> dcBusesInSubComponent, List<LfAcDcConverter> convertersToUpdate) {
        List<LfAcDcConverter> convertersInSubComponent = getAcDcConvertersInSubComponent(dcBusesInSubComponent);
        if (isSubComponentConnectedToGround(dcBusesInSubComponent) ||
                convertersInSubComponent.stream().anyMatch(conv -> controlsDcVoltage(conv) || convertersToUpdate.contains(conv))) {
            return; // already has (or was just given, while resolving an adjacent dcSubComponent) an element imposing DC voltage
        } else {
            if (convertersInSubComponent.isEmpty()) {
                throw new PowsyblException("DC component " + numDC + " has a DC subcomponent with no DC ground "
                        + "and no AC-DC converter able to settle the DC voltage");
            }
            convertersToUpdate.addAll(convertersInSubComponent);
        }
    }

    /**
     * Tells whether a converter settles the DC voltage: V_DC does it directly, DC_DROOP does it
     * through the droop law.
     */
    private boolean controlsDcVoltage(LfAcDcConverter converter) {
        return converter.getControlMode() == AcDcConverter.ControlMode.V_DC || converter.getControlMode() == AcDcConverter.ControlMode.DC_DROOP;
    }

    /**
     * Tells whether a DC subcomponent has o grounded DC bus.
     */
    private boolean isSubComponentConnectedToGround(Set<LfDcBus> dcBusesInSubComponent) {
        return dcBusesInSubComponent.stream().anyMatch(LfDcBus::isGrounded);
    }

    @Override
    public List<LfAcDcConverter> getAcDcConverters() {
        return lfNetwork.getVoltageSourceConverters().stream().filter(vsc -> vsc.getNumDC() == numDC).map(vsc -> (LfAcDcConverter) vsc).toList();
    }

    /**
     * @return All DC buses in the DC component.
     */
    private List<LfDcBus> getDcBuses() {
        return lfNetwork.getDcBuses().stream().filter(b -> b.getNumDC() == numDC).toList();
    }

    /**
     * @return All DC lines in the DC component.
     */
    private List<LfDcLine> getDcLines() {
        return lfNetwork.getDcLines().stream().filter(l -> l.getNumDC() == numDC).toList();
    }

    /**
     * Find all AC/DC converters connected to the DC subcomponent.
     *
     * @param dcBusesInSubComponent DC buses in the DC subcomponent.
     * @return The list of AC/DC converter in which one of the buses is part of the DC subcomponent.
     */
    private List<LfAcDcConverter> getAcDcConvertersInSubComponent(Set<LfDcBus> dcBusesInSubComponent) {
        List<LfAcDcConverter> convertersInSubComponent = new ArrayList<>();
        for (LfAcDcConverter converter : getAcDcConverters()) {
            if (dcBusesInSubComponent.contains(converter.getDcBus1()) || dcBusesInSubComponent.contains(converter.getDcBus2())) {
                convertersInSubComponent.add(converter);
            }
        }
        return convertersInSubComponent;
    }

    @Override
    public List<LfAcDcConverter> getAcDcConvertersAround(LfDcBus dcBus) {
        GraphConnectivity<LfDcBus, LfDcLine> graph = buildDcSubComponentGraph();
        Set<LfDcBus> dcBusesInSubComponent = graph.getConnectedComponent(dcBus);
        return getAcDcConvertersInSubComponent(dcBusesInSubComponent);
    }

    /**
     * Build DC subcomponent connectivity graph. Only LfDcLines are considered as edges, so each DC pole is in a
     * distinct connected component
     *
     * @return A graph with LfDcBuses as vertices and LfDcLines as edges.
     */
    private GraphConnectivity<LfDcBus, LfDcLine> buildDcSubComponentGraph() {
        GraphConnectivity<LfDcBus, LfDcLine> graph = new NaiveGraphConnectivityFactory<LfDcBus, LfDcLine>(LfDcBus::getNum).create();
        getDcBuses().forEach(graph::addVertex);
        getDcLines().stream()
                .filter(l -> l.getDcBus1() != null && l.getDcBus2() != null)
                .forEach(l -> graph.addEdge(l.getDcBus1(), l.getDcBus2(), l));

        graph.startTemporaryChanges(); // We need to call this to start using connectivity graph
        return graph;
    }
}
