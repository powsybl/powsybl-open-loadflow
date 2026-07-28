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
 * Helper methods to validate a DcComponent configuration
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public final class DcComponentValidator {

    private DcComponentValidator() {
    }

    /**
     * Check that a DC component is a configuration Open Load Flow can solve. Disconnected converters
     * are ignored: only fully connected ones take part in the checks.
     *
     * @param acDcConverters All AC-DC converters found in the DC component.
     * @param numDcc         The DC component's id, used in error messages.
     * @throws PowsyblException If the DC component configuration is invalid.
     */
    static void checkDcComponentIsValid(Collection<AcDcConverter<?>> acDcConverters, int numDcc) {
        List<AcDcConverter<?>> connectedConverters = acDcConverters.stream()
            .filter(DcComponentValidator::isFullyConnected)
            .toList();
        checkAllConvertersAreIndirectlyConnectedToADcGround(connectedConverters);
        checkAtLeastOneConverterControlsVdc(connectedConverters, numDcc);
        checkPccConvertersAreIndirectlyConnectedToElementImposingVoltage(connectedConverters);
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
     * Check that at least one AC-DC converter controls the DC voltage.
     *
     * @param acDcConverters A list of AC-DC converters to check.
     * @throws PowsyblException If no AC-DC converter control the DC voltage.
     */
    private static void checkAtLeastOneConverterControlsVdc(List<AcDcConverter<?>> acDcConverters, int numDcc) {
        if (acDcConverters.stream().noneMatch(DcComponentValidator::controlsDcVoltage)) {
            throw new PowsyblException("At least one AC/DC converter control mode must be V_DC or P_PCC_DROOP in each DC component, but DC component " + numDcc + " does not have any");
        }
    }

    /**
     * Check that both DC terminals of each AC-DC converter in P_PCC mode are indirectly connected (i.e. through DC
     * lines) to an element imposing voltage (a non P_PCC AC-DC converter or a DC ground).
     *
     * @param acDcConverters A list of AC-DC converters to check.
     * @throws PowsyblException If at least one terminal of one AC-DC converter is not indirectly connected to an
     *                          element imposing voltage
     */
    private static void checkPccConvertersAreIndirectlyConnectedToElementImposingVoltage(List<AcDcConverter<?>> acDcConverters) {
        for (AcDcConverter<?> converter : acDcConverters) {
            if (converter.getControlMode() == AcDcConverter.ControlMode.P_PCC) {
                checkDcTerminalVoltageIsImposed(converter, converter.getDcTerminal1(), "first");
                checkDcTerminalVoltageIsImposed(converter, converter.getDcTerminal2(), "second");
            }
        }
    }

    /**
     * Throw an Exception if the converter DC terminal is not connected to an element imposing voltage.
     *
     * @param converter    The AC-DC converter to check.
     * @param dcTerminal   The DC terminal of the AC-DC converter to check.
     * @param terminalName The name of the DC terminal to check.
     * @throws PowsyblException If the DC terminal of the AC-DC converter is not indirectly connected to an element imposing voltage
     */
    private static void checkDcTerminalVoltageIsImposed(AcDcConverter<?> converter, DcTerminal dcTerminal, String terminalName) {
        if (!isConnectedToVoltageImposingElement(dcTerminal)) {
            throw new PowsyblException(String.format(
                "Converter %s is in P_PCC control mode but its %s DC bus is not connected to an element imposing voltage",
                converter.getId(), terminalName));
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
     * Tells whether a DC terminal is indirectly connected, through DC lines, to an element imposing
     * voltage: a connected DC ground, or a fully connected converter settling the DC voltage.
     */
    private static boolean isConnectedToVoltageImposingElement(DcTerminal startTerminal) {
        DcIsland island = DcIsland.around(startTerminal);
        return island.hasConnectedDcGround()
            || island.hasConverterMatching(c -> controlsDcVoltage(c) && isFullyConnected(c));
    }

    /**
     * The set of DC buses reachable from a DC terminal by following connected DC lines, together with
     * the connected DC grounds and the AC-DC converters attached to them. Built by breadth-first search.
     */
    private static final class DcIsland implements DcTopologyVisitor {

        private final Set<String> visitedBusIds = new HashSet<>();
        private final Deque<DcBus> busesToVisit = new ArrayDeque<>();
        private final List<DcGround> connectedGrounds = new ArrayList<>();
        private final List<AcDcConverter<?>> converters = new ArrayList<>();

        /**
         * Explores the island around {@code startTerminal}; an unconnected terminal yields an empty island.
         */
        static DcIsland around(DcTerminal startTerminal) {
            DcIsland island = new DcIsland();
            island.enqueue(startTerminal.getDcBus());
            while (!island.busesToVisit.isEmpty()) {
                island.busesToVisit.poll().visitConnectedEquipments(island);
            }
            return island;
        }

        private void enqueue(DcBus dcBus) {
            if (dcBus != null && visitedBusIds.add(dcBus.getId())) {
                busesToVisit.add(dcBus);
            }
        }

        boolean hasConnectedDcGround() {
            return !connectedGrounds.isEmpty();
        }

        boolean hasConverterMatching(Predicate<AcDcConverter<?>> predicate) {
            return converters.stream().anyMatch(predicate);
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
            converters.add(converter);
        }
    }
}
