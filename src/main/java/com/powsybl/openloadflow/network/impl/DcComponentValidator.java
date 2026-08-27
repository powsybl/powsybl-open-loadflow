/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;

import java.util.*;
import java.util.function.Predicate;

/**
 * Helper methods to validate a DcComponent configuration and, when possible, automatically resolve an
 * otherwise invalid one.
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public final class DcComponentValidator {

    private DcComponentValidator() {
    }

    /**
     * Check that a DC component is a configuration Open Load Flow can solve, and resolve any island of DC
     * buses (connected through DC lines only) that has no element imposing the DC voltage (no connected DC
     * ground and no fully connected V_DC/P_PCC_DROOP converter), by selecting all fully connected P_PCC
     * converters of that island to be promoted to V_DC control internally. Disconnected converters are
     * ignored: only fully connected ones take part in the checks/resolution.
     *
     * @param dcBuses        All DC buses found in the DC component.
     * @param acDcConverters All AC-DC converters found in the DC component.
     * @param numDcc         The DC component's id, used in error messages.
     * @return A list of AC-DC converter promoted to V_DC control, to use instead of the converter's own IIDM value;
     *         empty if no promotion was necessary.
     * @throws PowsyblException If the DC component configuration is invalid and cannot be resolved automatically.
     */
    static List<AcDcConverter<?>> resolveDcComponent(List<DcBus> dcBuses, Collection<AcDcConverter<?>> acDcConverters,
                                                     int numDcc) {
        List<AcDcConverter<?>> connectedConverters = acDcConverters.stream()
            .filter(DcComponentValidator::isFullyConnected)
            .toList();
        checkAllConvertersAreIndirectlyConnectedToADcGround(connectedConverters);

        List<AcDcConverter<?>> convertersToUpdate = new ArrayList<>();
        Set<String> visitedBusIds = new HashSet<>();
        List<DcBus> sortedDcBuses = dcBuses.stream().sorted(Comparator.comparing(DcBus::getId)).toList();
        for (DcBus dcBus : sortedDcBuses) {
            if (visitedBusIds.contains(dcBus.getId())) {
                continue;
            }
            DcIsland island = DcIsland.around(dcBus);
            visitedBusIds.addAll(island.getVisitedBusIds());
            resolveIslandIfNeeded(island, convertersToUpdate, numDcc);
        }
        return convertersToUpdate;
    }

    /**
     * Add all converters of the island to V_DC control if the island has no element already imposing DC
     * voltage; do nothing otherwise.
     *
     * @throws PowsyblException If the island has no element imposing voltage and no converter can be promoted.
     */
    private static void resolveIslandIfNeeded(DcIsland island, List<AcDcConverter<?>> convertersToUpdate, int numDcc) {
        if (island.hasConnectedDcGround()
            || island.hasConverterMatching(c -> controlsDcVoltage(c) || convertersToUpdate.contains(c))) {
            return; // already has (or was just given, while resolving an adjacent island) an element imposing DC voltage
        }
        Set<AcDcConverter<?>> islandConverters = island.getConverters();
        if (islandConverters.isEmpty()) {
            throw new PowsyblException("DC component " + numDcc + " has an island of DC buses with no DC ground"
                + " and no AC-DC converter able to settle the DC voltage");
        }
        convertersToUpdate.addAll(islandConverters);
    }

    /**
     * Check that at least one of the DC terminal of each AC-DC converter is indirectly connected (i.e. through DC
     * lines) to a DC ground.
     *
     * @param acDcConverters A list of AC-DC converters to check.
     * @throws PowsyblException If at least one AC-DC converter is not indirectly connected to a DC ground
     */
    private static void checkAllConvertersAreIndirectlyConnectedToADcGround(List<AcDcConverter<?>> acDcConverters) {
        for (AcDcConverter<?> converter : acDcConverters) {
            if (!isConnectedToGround(converter.getDcTerminal1()) && !isConnectedToGround(converter.getDcTerminal2())) {
                throw new PowsyblException(String.format("Converter %s is not indirectly connected to a DC ground", converter.getId()));
            }
        }
    }

    /**
     * Tells whether a converter settles the DC voltage: V_DC does it directly, P_PCC_DROOP does it
     * through the droop law.
     */
    private static boolean controlsDcVoltage(AcDcConverter<?> converter) {
        return converter.getControlMode() == AcDcConverter.ControlMode.V_DC
            || converter.getControlMode() == AcDcConverter.ControlMode.P_PCC_DROOP;
    }

    /**
     * Tells whether a converter has its AC terminal and both its DC terminals connected.
     */
    private static boolean isFullyConnected(AcDcConverter<?> converter) {
        return converter.getTerminal1().isConnected()
            && converter.getDcTerminal1().isConnected()
            && converter.getDcTerminal2().isConnected();
    }

    /**
     * Tells whether a DC terminal is indirectly connected, through DC lines, to a connected DC ground.
     */
    private static boolean isConnectedToGround(DcTerminal startTerminal) {
        return DcIsland.around(startTerminal).hasConnectedDcGround();
    }

    /**
     * The set of DC buses reachable from a DC bus by following connected DC lines, together with
     * the connected DC grounds and the AC-DC converters attached to them. Built by breadth-first search.
     */
    private static final class DcIsland implements DcTopologyVisitor {

        private final Set<String> visitedBusIds = new HashSet<>();
        private final Deque<DcBus> busesToVisit = new ArrayDeque<>();
        private final List<DcGround> connectedGrounds = new ArrayList<>();
        private final Set<AcDcConverter<?>> converters = new LinkedHashSet<>();

        /**
         * Explores the island around {@code startBus}; a null bus yields an empty island.
         */
        static DcIsland around(DcBus startBus) {
            DcIsland island = new DcIsland();
            island.enqueue(startBus);
            while (!island.busesToVisit.isEmpty()) {
                island.busesToVisit.poll().visitConnectedEquipments(island);
            }
            return island;
        }

        /**
         * Explores the island around {@code startTerminal}; an unconnected terminal yields an empty island.
         */
        static DcIsland around(DcTerminal startTerminal) {
            return around(startTerminal.getDcBus());
        }

        private void enqueue(DcBus dcBus) {
            if (dcBus != null && visitedBusIds.add(dcBus.getId())) {
                busesToVisit.add(dcBus);
            }
        }

        Set<String> getVisitedBusIds() {
            return visitedBusIds;
        }

        boolean hasConnectedDcGround() {
            return !connectedGrounds.isEmpty();
        }

        boolean hasConverterMatching(Predicate<AcDcConverter<?>> predicate) {
            return converters.stream().anyMatch(predicate);
        }

        Set<AcDcConverter<?>> getConverters() {
            return converters;
        }

        @Override
        public void visitDcLine(DcLine dcLine, TwoSides side) {
            // side is the one connected to the bus being visited: carry on through the other side
            DcTerminal otherTerminal = dcLine.getDcTerminal(side == TwoSides.ONE ? TwoSides.TWO : TwoSides.ONE);
            if (otherTerminal.isConnected()) {
                enqueue(otherTerminal.getDcBus());
            }
        }

        @Override
        public void visitDcGround(DcGround dcGround) {
            if (dcGround.getDcTerminal().isConnected()) {
                connectedGrounds.add(dcGround);
            }
        }

        @Override
        public void visitAcDcConverter(AcDcConverter<?> converter, TerminalNumber terminalNumber) {
            if (isFullyConnected(converter)) {
                converters.add(converter);
            }
        }
    }
}
