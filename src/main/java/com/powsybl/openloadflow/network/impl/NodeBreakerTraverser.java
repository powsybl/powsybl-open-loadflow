/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.math.graph.TraverseResult;

import java.util.List;
import java.util.Set;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class NodeBreakerTraverser implements VoltageLevel.NodeBreakerView.TopologyTraverser {

    private final Set<Switch> switchesToOpen;
    private final Set<Terminal> traversedTerminals;
    private final List<Terminal> nextTerminals;
    private final int initNode;
    private final VoltageLevel.NodeBreakerView nodeBreakerView;

    public NodeBreakerTraverser(Set<Switch> switchesToOpen, Set<Terminal> traversedTerminals, List<Terminal> nextTerminals,
                                int initNode, VoltageLevel.NodeBreakerView nodeBreakerView) {
        this.switchesToOpen = switchesToOpen;
        this.traversedTerminals = traversedTerminals;
        this.nextTerminals = nextTerminals;
        this.initNode = initNode;
        this.nodeBreakerView = nodeBreakerView;
    }

    @Override
    public TraverseResult traverse(int nodeBefore, Switch sw, int nodeAfter) {
        if (sw != null) {
            if (sw.isOpen()) {
                return TraverseResult.TERMINATE_PATH;
            }

            if (nodeBefore == initNode && traverserStopsAtOtherStartEdges(sw, initNode)) {
                // Switch is just after contingency and traverser stops at other start edges
                if (isOpenable(sw)) {
                    // The traverser can stop now and no need to retain current switch
                    return TraverseResult.TERMINATE_PATH;
                }
                if (traverserWouldStopAfter(sw, nodeAfter)) {
                    // As the traverser would stop just after, it can stop now (without retaining current switch)
                    return TraverseResult.TERMINATE_PATH;
                }
            }

            if (isOpenable(sw)) {
                // The current switch is openable: the traverser could stop and the switch could be retained,
                // but, to avoid unnecessary retained switches, the traverser does not retain it in two cases
                if (traverserWouldStopAfter(sw, nodeAfter)) {
                    // Continuing traversing might lead in some cases to more retained switches, but in practice the
                    // switches after are often opened and sometimes followed by an end node
                    return TraverseResult.CONTINUE;
                }
                if (isEquivalentToStopAfterSwitch(sw, nodeAfter)) {
                    // Retaining the switch is equivalent to stop at the node after if the node after the switch is an end node (e.g. load or generator)
                    sw.getVoltageLevel().getNodeBreakerView().getOptionalTerminal(nodeAfter).ifPresent(this::terminalTraversed);
                    return TraverseResult.TERMINATE_PATH;
                }
                switchesToOpen.add(sw);
                return TraverseResult.TERMINATE_PATH;
            }
        }

        // The traverser continues, hence nodeAfter is traversed
        nodeBreakerView.getOptionalTerminal(nodeAfter).ifPresent(this::terminalTraversed);
        return TraverseResult.CONTINUE;
    }

    private void terminalTraversed(Terminal terminal) {
        traversedTerminals.add(terminal);
        ((Connectable<?>) terminal.getConnectable()).getTerminals().stream()
                .filter(t -> t != terminal)
                .forEach(nextTerminals::add);
    }

    private static boolean isEquivalentToStopAfterSwitch(Switch sw, int nodeAfter) {
        Terminal terminal2 = sw.getVoltageLevel().getNodeBreakerView().getTerminal(nodeAfter);
        if (terminal2 != null) {
            IdentifiableType connectableAfter = terminal2.getConnectable().getType();
            boolean endNodeAfter = connectableAfter == IdentifiableType.GENERATOR
                || connectableAfter == IdentifiableType.LOAD
                || connectableAfter == IdentifiableType.DANGLING_LINE
                || connectableAfter == IdentifiableType.STATIC_VAR_COMPENSATOR
                || connectableAfter == IdentifiableType.SHUNT_COMPENSATOR;

            if (endNodeAfter) { // check that there isn't another (closed) switch or internal connection at node after
                VoltageLevel.NodeBreakerView nbv = sw.getVoltageLevel().getNodeBreakerView();
                return noInternalConnectionAtNode(nodeAfter, nbv)
                    && nbv.getSwitchStream(nodeAfter).noneMatch(s -> s != sw && !s.isOpen());
            }
        }
        return false;
    }

    private boolean traverserWouldStopAfter(Switch aSwitch, int nodeAfter) {
        // The traverser would stop just after current switch if node after is a junction of switches only,
        // with all other switches either opened or openable
        VoltageLevel.NodeBreakerView nbv = aSwitch.getVoltageLevel().getNodeBreakerView();
        if (!nbv.getOptionalTerminal(nodeAfter).isPresent() && noInternalConnectionAtNode(nodeAfter, nbv)) {
            // No terminal nor internal connection at node after, thus there are only switches
            return allOtherSwitchesOpenOrOpenable(aSwitch, nodeAfter, nbv);
        }
        return false;
    }

    private boolean traverserStopsAtOtherStartEdges(Switch aSwitch, int initNode) {
        // The traverser stops at other start edges if:
        //  - no internal connection at init node
        //  - and all other switches connected to init node are either open or openable
        VoltageLevel.NodeBreakerView nbv = aSwitch.getVoltageLevel().getNodeBreakerView();
        return noInternalConnectionAtNode(initNode, nbv)
            && allOtherSwitchesOpenOrOpenable(aSwitch, initNode, nbv);
    }

    private static boolean allOtherSwitchesOpenOrOpenable(Switch aSwitch, int node, VoltageLevel.NodeBreakerView nbv) {
        return nbv.getSwitchStream(node).filter(s -> s != aSwitch).allMatch(NodeBreakerTraverser::isOpenOrOpenable);
    }

    private static boolean noInternalConnectionAtNode(int node, VoltageLevel.NodeBreakerView nbv) {
        return nbv.getNodeInternalConnectedToStream(node).findFirst().isEmpty();
    }

    private static boolean isOpenable(Switch aSwitch) {
        return !aSwitch.isFictitious() && aSwitch.getKind() == SwitchKind.BREAKER;
    }

    private static boolean isOpenOrOpenable(Switch aSwitch) {
        return aSwitch.isOpen() || isOpenable(aSwitch);
    }
}
