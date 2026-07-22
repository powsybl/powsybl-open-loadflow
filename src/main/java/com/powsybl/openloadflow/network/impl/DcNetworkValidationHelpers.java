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
final class DcNetworkValidationHelpers {

    private DcNetworkValidationHelpers() {
    }

    /**
     * Check that at least one of the DC terminal of each AC-DC converter is indirectly connected (i.e. through DC
     * lines) to a DC ground.
     *
     * @param acDcConverters A list of AC-DC converters to check.
     * @throws PowsyblException If at least one AC-DC converter is not indirectly connected to a DC ground
     */
    public static void checkAllConvertersAreIndirectlyConnectedToADcGround(List<AcDcConverter<?>> acDcConverters) {
        for (AcDcConverter<?> converter : acDcConverters) {
            boolean isTerminal1IndirectlyConnectedToGround = isConnectedToGround(converter.getDcTerminal1());
            boolean isTerminal2IndirectlyConnectedToGround = isConnectedToGround(converter.getDcTerminal2());
            if (!isTerminal1IndirectlyConnectedToGround && !isTerminal2IndirectlyConnectedToGround) {
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
    public static void checkAtLeastOneConverterControlsVdc(List<AcDcConverter<?>> acDcConverters, int numDcc) {
        // Ensure at least one converter controls the DC voltage. V_DC controls it directly; P_PCC_DROOP also counts
        // as DC-voltage control since the droop law settles the DC voltage.
        boolean isVdcControlled = false;
        for (AcDcConverter<?> converter : acDcConverters) {
            if (converter.getControlMode() == AcDcConverter.ControlMode.V_DC || converter.getControlMode() == AcDcConverter.ControlMode.P_PCC_DROOP) {
                isVdcControlled = true;
                break;
            }
        }
        if (!isVdcControlled) {
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
    public static void checkPccConverterAreIndirectlyConnectedToElementImposingVoltage(List<AcDcConverter<?>> acDcConverters) {
        for (AcDcConverter<?> converter : acDcConverters) {
            if (converter.getControlMode() == AcDcConverter.ControlMode.P_PCC) {
                boolean isConverterTerminal1Fine = isConnectedToNonPccConverterOrGround(converter.getDcTerminal1());
                if (!isConverterTerminal1Fine) {
                    throw new PowsyblException(String.format("Converter %s is in P_PCC control mode but its first DC bus is not connected to an element imposing voltage", converter.getId()));
                }

                boolean isConverterTerminal2Fine = isConnectedToNonPccConverterOrGround(converter.getDcTerminal2());
                if (!isConverterTerminal2Fine) {
                    throw new PowsyblException(String.format("Converter %s is in P_PCC control mode but its second DC bus is not connected to an element imposing voltage", converter.getId()));
                }
            }
        }
    }

    /**
     * Perform topological search to find out if the terminal of a AC-DC converter in P_PCC mode is indirectly connected
     * (i.e. through DC lines) to an element imposing voltage (a DC ground or a AC-DC converter not in P_PCC mode)
     *
     * @param startTerminal The terminal of the AC-DC converter used as a starting point.
     * @return true if the terminal is indirectly connected to an element imposing voltage, false otherwise.
     */
    private static boolean isConnectedToNonPccConverterOrGround(DcTerminal startTerminal) {
        return isIndirectlyConnectedToVoltageImposingElement(startTerminal, converter ->
                converter.getControlMode() != AcDcConverter.ControlMode.P_PCC
                        && converter.getDcTerminal1().isConnected() && converter.getDcTerminal2().isConnected()
                        && converter.getTerminal1().isConnected());
    }

    /**
     * Perform topological search to find out if the terminal of a AC-DC converter is indirectly connected (i.e.
     * through DC lines) to a DC ground.
     *
     * @param startTerminal The terminal of the AC-DC converter used as a starting point.
     * @return true if the terminal is indirectly connected to a DC ground, false otherwise.
     */
    private static boolean isConnectedToGround(DcTerminal startTerminal) {
        return isIndirectlyConnectedToVoltageImposingElement(startTerminal, converter -> false);
    }

    /**
     * Perform a breadth-first topological search, following only DC lines, to find out if the terminal of a AC-DC
     * converter is indirectly connected to an element imposing voltage: a connected DC ground, or an AC-DC converter
     * satisfying {@code isVoltageImposingConverter}.
     *
     * @param startTerminal              The terminal of the AC-DC converter used as a starting point.
     * @param isVoltageImposingConverter Tells whether an encountered AC-DC converter should, on its own, be
     *                                    considered as imposing voltage. Pass a predicate always returning false to
     *                                    only look for a DC ground.
     * @return true if the terminal is indirectly connected to an element imposing voltage, false otherwise.
     */
    private static boolean isIndirectlyConnectedToVoltageImposingElement(DcTerminal startTerminal,
                                                                          Predicate<AcDcConverter<?>> isVoltageImposingConverter) {
        DcBus startBus = startTerminal.getDcBus();
        if (startBus == null) {
            return false; // startTerminal not connected
        }

        Set<String> visitedBusIds = new HashSet<>();
        Deque<DcBus> queue = new ArrayDeque<>();
        visitedBusIds.add(startBus.getId());
        queue.add(startBus);

        while (!queue.isEmpty()) {
            DcBus currentBus = queue.poll();
            boolean[] found = {false};

            currentBus.visitConnectedEquipments(new DcTopologyVisitor() {
                @Override
                public void visitAcDcConverter(AcDcConverter<?> converter, TerminalNumber terminalNumber) {
                    if (isVoltageImposingConverter.test(converter)) {
                        found[0] = true;
                    }
                }

                @Override
                public void visitDcLine(DcLine dcLine, TwoSides side) {
                    // side is connected to currentBus; check the other side
                    TwoSides otherSide = (side == TwoSides.ONE) ? TwoSides.TWO : TwoSides.ONE;
                    DcTerminal otherTerminal = dcLine.getDcTerminal(otherSide);
                    if (otherTerminal.isConnected()) {
                        DcBus otherBus = otherTerminal.getDcBus();
                        if (otherBus != null && visitedBusIds.add(otherBus.getId())) {
                            queue.add(otherBus);
                        }
                    }
                }

                @Override
                public void visitDcGround(DcGround dcGround) {
                    if (dcGround.getDcTerminal().isConnected()) {
                        found[0] = true;
                    }
                }
            });

            if (found[0]) {
                return true;
            }
        }

        return false;
    }
}
