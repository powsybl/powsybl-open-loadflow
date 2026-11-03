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
final class DcComponentValidator {

    private DcComponentValidator() {
    }

    /**
     * Check that a DC component is a configuration Open Load Flow can solve, and resolve any DC subcomponent (i.e. set
     * of DC buses connected through DC lines and closed DC switches) that has no element imposing the DC voltage (no connected DC
     * ground and no fully connected V_DC/DC_DROOP converter), by selecting all fully connected P_PCC
     * converters of that subcomponent to be promoted to V_DC control internally. Disconnected converters are
     * ignored: only fully connected ones take part in the checks/resolution.
     *
     * @param dcBuses        All DC buses found in the DC component.
     * @param acDcConverters All AC-DC converters found in the DC component.
     * @param dcSwitches     DC switches to follow, in addition to DC lines, when exploring DC subcomponents.
     * @param numDcc         The DC component's id, used in error messages.
     * @return A list of AC-DC converter promoted to V_DC control, to use instead of the converter's own IIDM value;
     *         empty if no promotion was necessary.
     * @throws PowsyblException If the DC component configuration is invalid and cannot be resolved automatically.
     */
    static List<AcDcConverter<?>> resolveDcComponent(List<DcBus> dcBuses, Collection<AcDcConverter<?>> acDcConverters,
                                                     Collection<DcSwitch> dcSwitches, int numDcc) {
        // bus id -> connected buses through resistive DcSwitch
        Map<String, List<DcBus>> dcSwitchNeighbours = indexClosedDcSwitches(dcSwitches);
        List<AcDcConverter<?>> connectedConverters = acDcConverters.stream()
                .filter(DcComponentValidator::isFullyConnected)
                .toList();
        checkAllConvertersAreIndirectlyConnectedToADcGround(connectedConverters, dcSwitchNeighbours);

        List<AcDcConverter<?>> convertersToUpdate = new ArrayList<>();
        Set<String> visitedBusIds = new HashSet<>();
        List<DcBus> sortedDcBuses = dcBuses.stream().sorted(Comparator.comparing(DcBus::getId)).toList();
        for (DcBus dcBus : sortedDcBuses) {
            if (visitedBusIds.contains(dcBus.getId())) {
                continue;
            }
            DcSubComponent dcSubComponent = DcSubComponent.around(dcBus, dcSwitchNeighbours);
            visitedBusIds.addAll(dcSubComponent.getVisitedBusIds());
            resolveDcSubComponentIfNeeded(dcSubComponent, convertersToUpdate, numDcc);
        }
        return convertersToUpdate;
    }

    /**
     * Index, for each DC bus, the DC buses it is linked to through a closed DC switch. Only closed switches with a
     * non-zero resistance link two distinct buses: zero-resistance ones are already merged by the DC topology.
     */
    private static Map<String, List<DcBus>> indexClosedDcSwitches(Collection<DcSwitch> dcSwitches) {
        Map<String, List<DcBus>> neighbors = new HashMap<>();
        for (DcSwitch dcSwitch : dcSwitches) {
            DcBus dcBus1 = dcSwitch.getDcNode1().getDcBus();
            DcBus dcBus2 = dcSwitch.getDcNode2().getDcBus();
            if (!dcSwitch.isOpen() && dcBus1 != null && dcBus2 != null && !dcBus1.getId().equals(dcBus2.getId())) {
                neighbors.computeIfAbsent(dcBus1.getId(), k -> new ArrayList<>()).add(dcBus2);
                neighbors.computeIfAbsent(dcBus2.getId(), k -> new ArrayList<>()).add(dcBus1);
            }
        }
        return neighbors;
    }

    /**
     * Add all converters of the DC subcomponent to V_DC control if the subcomponent has no element already imposing DC
     * voltage; do nothing otherwise.
     *
     * @throws PowsyblException If the DC subcomponent has no element imposing voltage and no converter can be promoted.
     */
    private static void resolveDcSubComponentIfNeeded(DcSubComponent dcSubComponent, List<AcDcConverter<?>> convertersToUpdate, int numDcc) {
        if (dcSubComponent.hasConnectedDcGround()
                || dcSubComponent.hasConverterMatching(c -> controlsDcVoltage(c) || convertersToUpdate.contains(c))) {
            return; // already has (or was just given, while resolving an adjacent dcSubComponent) an element imposing DC voltage
        }
        Set<AcDcConverter<?>> dcSubComponentConverters = dcSubComponent.getConverters();
        if (dcSubComponentConverters.isEmpty()) {
            throw new PowsyblException("DC component " + numDcc + " has a DC subcomponent with no DC ground "
                    + "and no AC-DC converter able to settle the DC voltage");
        }
        convertersToUpdate.addAll(dcSubComponentConverters);
    }

    /**
     * Check that at least one of the DC terminal of each AC-DC converter is indirectly connected (i.e. through DC
     * lines and closed DC switches) to a DC ground.
     *
     * @param acDcConverters     A list of AC-DC converters to check.
     * @param dcSwitchNeighbours DC buses linked to each DC bus through a closed DC switch.
     * @throws PowsyblException If at least one AC-DC converter is not indirectly connected to a DC ground
     */
    private static void checkAllConvertersAreIndirectlyConnectedToADcGround(List<AcDcConverter<?>> acDcConverters,
                                                                            Map<String, List<DcBus>> dcSwitchNeighbours) {
        for (AcDcConverter<?> converter : acDcConverters) {
            if (!isConnectedToGround(converter.getDcTerminal1(), dcSwitchNeighbours)
                    && !isConnectedToGround(converter.getDcTerminal2(), dcSwitchNeighbours)) {
                throw new PowsyblException(String.format("Converter %s is not indirectly connected to a DC ground", converter.getId()));
            }
        }
    }

    /**
     * Tells whether a converter settles the DC voltage: V_DC does it directly, DC_DROOP does it
     * through the droop law.
     */
    private static boolean controlsDcVoltage(AcDcConverter<?> converter) {
        return converter.getControlMode() == AcDcConverter.ControlMode.V_DC
                || converter.getControlMode() == AcDcConverter.ControlMode.DC_DROOP;
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
     * Tells whether a DC terminal is indirectly connected, through DC lines and closed DC switches, to a connected DC ground.
     */
    private static boolean isConnectedToGround(DcTerminal startTerminal, Map<String, List<DcBus>> dcSwitchNeighbours) {
        return DcSubComponent.around(startTerminal, dcSwitchNeighbours).hasConnectedDcGround();
    }

    /**
     * The set of DC buses, connected DC grounds, and connected AC/DC converters reachable from a given DC bus by following
     * **only** connected DC lines and closed DC switches but not AC/DC converters.
     * Each DcSubComponent shall contain at least one element imposing voltage to be valid. Built using a breadth-first search.
     * <p>
     * Note: When all DC lines of the DcComponent are connected, this corresponds to a DC pole.
     */
    private static final class DcSubComponent implements DcTopologyVisitor {

        private final Set<String> visitedBusIds = new HashSet<>();
        private final Deque<DcBus> busesToVisit = new ArrayDeque<>();
        private final List<DcGround> connectedGrounds = new ArrayList<>();
        private final Set<AcDcConverter<?>> converters = new LinkedHashSet<>();

        /**
         * Explores the DC subcomponent around {@code startBus}; a null bus yields an empty DC subcomponent.
         */
        static DcSubComponent around(DcBus startBus, Map<String, List<DcBus>> dcSwitchNeighbours) {
            DcSubComponent dcSubComponent = new DcSubComponent();
            dcSubComponent.enqueue(startBus);
            while (!dcSubComponent.busesToVisit.isEmpty()) {
                DcBus dcBus = dcSubComponent.busesToVisit.poll();
                dcBus.visitConnectedEquipments(dcSubComponent);
                // DC switches are not reached by the DC topology visitor: follow closed resistive ones explicitly
                dcSwitchNeighbours.getOrDefault(dcBus.getId(), List.of()).forEach(dcSubComponent::enqueue);
            }
            return dcSubComponent;
        }

        /**
         * Explores the DC subcomponent around {@code startTerminal}; an unconnected terminal yields an empty DC DCsubcomponent.
         */
        static DcSubComponent around(DcTerminal startTerminal, Map<String, List<DcBus>> dcSwitchNeighbours) {
            return around(startTerminal.getDcBus(), dcSwitchNeighbours);
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
