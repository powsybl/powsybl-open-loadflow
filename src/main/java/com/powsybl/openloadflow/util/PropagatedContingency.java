/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class PropagatedContingency {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropagatedContingency.class);

    private final Contingency contingency;

    private final int index;

    private final Set<String> branchIdsToOpen;

    private final Set<Switch> switchesToOpen;

    private final Set<String> hvdcIdsToOpen;

    private final Set<String> generatorIdsToLose;

    public Contingency getContingency() {
        return contingency;
    }

    public int getIndex() {
        return index;
    }

    public Set<String> getBranchIdsToOpen() {
        return branchIdsToOpen;
    }

    public Set<String> getHvdcIdsToOpen() {
        return hvdcIdsToOpen;
    }

    public Set<String> getGeneratorIdsToLose() {
        return generatorIdsToLose;
    }

    public PropagatedContingency(Contingency contingency, int index, Set<String> branchIdsToOpen, Set<String> hvdcIdsToOpen,
                                 Set<Switch> switchesToOpen, Set<Terminal> terminalsToDisconnect, Set<String> generatorIdsToLose) {
        this.contingency = Objects.requireNonNull(contingency);
        this.index = index;
        this.branchIdsToOpen = Objects.requireNonNull(branchIdsToOpen);
        this.hvdcIdsToOpen = Objects.requireNonNull(hvdcIdsToOpen);
        this.switchesToOpen = Objects.requireNonNull(switchesToOpen);
        this.generatorIdsToLose = Objects.requireNonNull(generatorIdsToLose);

        for (Switch sw : switchesToOpen) {
            branchIdsToOpen.add(sw.getId());
        }

        for (Terminal terminal : terminalsToDisconnect) {
            if (terminal.getConnectable() instanceof Branch) {
                branchIdsToOpen.add(terminal.getConnectable().getId());
            }
            if (terminal.getConnectable() instanceof Generator) {
                generatorIdsToLose.add(terminal.getConnectable().getId());
            }
        }
    }

    public static List<PropagatedContingency> createListForSensitivityAnalysis(Network network, List<Contingency> contingencies) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency = PropagatedContingency.create(network, contingency, index);
            Optional<Switch> coupler = propagatedContingency.switchesToOpen.stream().filter(PropagatedContingency::isCoupler).findFirst();
            if (coupler.isEmpty()) {
                propagatedContingencies.add(propagatedContingency);
            } else {
                // Sensitivity analysis works in bus view, it cannot deal (yet)  with contingencies whose propagation encounters a coupler
                LOGGER.warn("Propagated contingency '{}' not processed: coupler '{}' has been encountered while propagating the contingency",
                    contingency.getId(), coupler.get().getId());
            }
        }
        return propagatedContingencies;
    }

    public static List<PropagatedContingency> createListForSecurityAnalysis(Network network, List<Contingency> contingencies, Set<Switch> allSwitchesToOpen) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency = PropagatedContingency.create(network, contingency, index);
            propagatedContingencies.add(propagatedContingency);
            allSwitchesToOpen.addAll(propagatedContingency.switchesToOpen);
        }
        return propagatedContingencies;
    }

    private static PropagatedContingency create(Network network, Contingency contingency, int index) {
        Set<Switch> switchesToOpen = new LinkedHashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();
        Set<String> branchIdsToOpen = new HashSet<>();
        Set<String> hvdcIdsToOpen = new HashSet<>();
        Set<String> generatorIdsToLose = new HashSet<>();
        for (ContingencyElement element : contingency.getElements()) {
            switch (element.getType()) {
                case BRANCH:
                case LINE:
                case TWO_WINDINGS_TRANSFORMER:
                    // branch check is done inside tripping
                    ContingencyTripping.createBranchTripping(network, element.getId())
                        .traverse(switchesToOpen, terminalsToDisconnect);
                    branchIdsToOpen.add(element.getId());
                    break;
                case HVDC_LINE:
                    HvdcLine hvdcLine = network.getHvdcLine(element.getId());
                    if (hvdcLine == null) {
                        throw new PowsyblException("HVDC line '" + element.getId() + "' not found in the network");
                    }
                    hvdcIdsToOpen.add(element.getId());
                    break;
                case DANGLING_LINE:
                    // dangling line check is done inside tripping
                    ContingencyTripping.createDanglingLineTripping(network, element.getId())
                        .traverse(switchesToOpen, terminalsToDisconnect);
                    branchIdsToOpen.add(element.getId());
                    break;
                case GENERATOR:
                    Generator generator = network.getGenerator(element.getId());
                    if (generator == null) {
                        throw new PowsyblException("Generator '" + element.getId() + "' not found in the network");
                    }
                    generatorIdsToLose.add(element.getId());
                    break;
                default:
                    //TODO: support all kinds of contingencies
                    throw new UnsupportedOperationException("TODO");
            }
        }

        return new PropagatedContingency(contingency, index, branchIdsToOpen, hvdcIdsToOpen, switchesToOpen, terminalsToDisconnect, generatorIdsToLose);
    }

    private static boolean isCoupler(Switch s) {
        // Note that we can safely get the node breaker view. Indeed, in bus breaker view, there is no switch to open,
        // as fault is not propagated (for each switch we have no idea what kind it was in the initial node breaker topology)
        VoltageLevel.NodeBreakerView nbv = s.getVoltageLevel().getNodeBreakerView();
        return connectedToBusbars(nbv.getNode1(s.getId()), s) && connectedToBusbars(nbv.getNode2(s.getId()), s);
    }

    private static boolean connectedToBusbars(int node, Switch swStart) {
        VoltageLevel.NodeBreakerView nbv = swStart.getVoltageLevel().getNodeBreakerView();

        // Testing terminal connected to switch
        Optional<Terminal> t0 = nbv.getOptionalTerminal(node);
        if (t0.isPresent()) {
            return t0.get().getConnectable().getType() == ConnectableType.BUSBAR_SECTION;
        }

        // If no terminal connected to switch, traverser is needed to see whether the node is connected to busbars only
        boolean[] connectedToNonBusbarTerminal = new boolean[1];
        boolean[] connectedToBusbarTerminal = new boolean[1];
        nbv.traverse(node, (nodeBefore, sw, nodeAfter) -> {
            if (connectedToNonBusbarTerminal[0]) {
                // No need to continue: found a terminal which does not correspond to a busbar, hence starting switch is not a coupler
                return false;
            }
            if (sw == swStart) {
                return false; // no need to go back to starting switch
            }
            if (sw != null && sw.isOpen()) { // sw == null <=> internal connection, which is always closed
                return false; // not connected to nodeAfter if switch is opened
            }
            Optional<Terminal> t = nbv.getOptionalTerminal(nodeAfter);
            if (t.isPresent()) {
                if (t.get().getConnectable().getType() != ConnectableType.BUSBAR_SECTION) {
                    connectedToNonBusbarTerminal[0] = true;
                } else {
                    connectedToBusbarTerminal[0] = true;
                }
                return false; // stop current traversing at first terminal encountered
            }
            return true;
        });
        return !connectedToNonBusbarTerminal[0] && connectedToBusbarTerminal[0];
    }
}
