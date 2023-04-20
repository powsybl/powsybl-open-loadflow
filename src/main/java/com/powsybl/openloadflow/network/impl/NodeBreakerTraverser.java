/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.math.graph.TraverseResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class NodeBreakerTraverser implements VoltageLevel.NodeBreakerView.TopologyTraverser {

    private final Set<Switch> switchesToOpen;
    private final Set<Terminal> traversedTerminals;
    private final List<Terminal> nextTerminals;
    private final VoltageLevel.NodeBreakerView nodeBreakerView;

    public NodeBreakerTraverser(Set<Switch> switchesToOpen, Set<Terminal> traversedTerminals, List<Terminal> nextTerminals,
                                VoltageLevel.NodeBreakerView nodeBreakerView) {
        this.switchesToOpen = switchesToOpen;
        this.traversedTerminals = traversedTerminals;
        this.nextTerminals = nextTerminals;
        this.nodeBreakerView = nodeBreakerView;
    }

    @Override
    public TraverseResult traverse(int nodeBefore, Switch sw, int nodeAfter) {
        if (sw != null && sw.isOpen()) {
            return TraverseResult.TERMINATE_PATH;
        }

        if (hasAttachedEquipment(nodeBefore) && traverserStopsAtOtherEdges(sw, nodeBefore, nodeAfter)) {
            // Switch is just after a traversed terminal which will be disconnected, and traverser stops at other start edges
            if (isOpenable(sw)) {
                // The traverser can stop now and no need to retain current switch
                return TraverseResult.TERMINATE_PATH;
            }
            if (!hasAttachedEquipment(nodeAfter) && traverserStopsAtOtherEdges(sw, nodeAfter, nodeBefore)) {
                // As the traverser would stop just after, it can stop now (without retaining current switch)
                return TraverseResult.TERMINATE_PATH;
            }
        }

        if (isOpenable(sw)) {
            // The current switch is openable: the traverser could stop and the switch could be retained,
            // but, to avoid unnecessary retained switches, the traverser does not retain it in two cases
            if (!hasAttachedEquipment(nodeAfter) && traverserStopsAtOtherEdges(sw, nodeAfter, nodeBefore)) {
                // Continuing traversing might lead in some cases to more retained switches, but in practice the
                // switches after are often opened and sometimes followed by an end node
                return TraverseResult.CONTINUE;
            }
            if (isEquivalentToStopAfterSwitch(sw, nodeAfter)) {
                // Retaining the switch is equivalent to stop at the node after if the node after the switch is an end node (e.g. load or generator)
                nodeBreakerView.getOptionalTerminal(nodeAfter).ifPresent(this::terminalTraversed);
                return TraverseResult.TERMINATE_PATH;
            }
            switchesToOpen.add(sw);
            return TraverseResult.TERMINATE_PATH;
        }

        // The traverser continues, hence nodeAfter is traversed
        nodeBreakerView.getOptionalTerminal(nodeAfter).ifPresent(this::terminalTraversed);
        return TraverseResult.CONTINUE;
    }

    private boolean hasAttachedEquipment(int nodeBefore) {
        return nodeBreakerView.getOptionalTerminal(nodeBefore).isPresent();
    }

    private void terminalTraversed(Terminal terminal) {
        traversedTerminals.add(terminal);
        ((Connectable<?>) terminal.getConnectable()).getTerminals().stream()
                .filter(t -> t != terminal)
                .forEach(nextTerminals::add);
    }

    private boolean isEquivalentToStopAfterSwitch(Switch sw, int nodeAfter) {
        Terminal terminal2 = nodeBreakerView.getTerminal(nodeAfter);
        if (terminal2 != null) {
            IdentifiableType connectableAfter = terminal2.getConnectable().getType();
            boolean endNodeAfter = connectableAfter == IdentifiableType.GENERATOR
                || connectableAfter == IdentifiableType.LOAD
                || connectableAfter == IdentifiableType.DANGLING_LINE
                || connectableAfter == IdentifiableType.STATIC_VAR_COMPENSATOR
                || connectableAfter == IdentifiableType.SHUNT_COMPENSATOR;

            if (endNodeAfter) { // check that there isn't another (closed) switch or internal connection at node after
                return noInternalConnectionAtNode(nodeAfter)
                    && nodeBreakerView.getSwitchStream(nodeAfter).noneMatch(s -> s != sw && !s.isOpen());
            }
        }
        return false;
    }

    /**
     * Return if propagation would stop at all edges starting from nodeStart, but different from the edge defined by
     * nodeStart ---aSwitch--- nodeEnd,
     */
    private boolean traverserStopsAtOtherEdges(Switch aSwitch, int nodeStart, int nodeEnd) {
        // The traverser stops at other start edges if node is a direct or indirect junction of switches only, with all
        // other switches either opened or openable.
        // An indirect junction means through internal connections.
        return internalConnectionsEndOnOpenOrOpenableSwitches(nodeStart, nodeEnd)
            && allOtherSwitchesOpenOrOpenable(aSwitch, nodeStart);
    }

    private boolean allOtherSwitchesOpenOrOpenable(Switch aSwitch, int node) {
        return nodeBreakerView.getSwitchStream(node).filter(s -> s != aSwitch).allMatch(NodeBreakerTraverser::isOpenOrOpenable);
    }

    private boolean noInternalConnectionAtNode(int node) {
        return nodeBreakerView.getNodeInternalConnectedToStream(node).findFirst().isEmpty();
    }

    private boolean internalConnectionsEndOnOpenOrOpenableSwitches(int node, int dismissedNode) {
        Set<Integer> visitedNodes = new HashSet<>();
        visitedNodes.add(dismissedNode);
        return internalConnectionsEndOnOpenOrOpenableSwitches(node, visitedNodes);
    }

    private boolean internalConnectionsEndOnOpenOrOpenableSwitches(int nStart, Set<Integer> visitedNodes) {
        if (!visitedNodes.contains(nStart)) {
            visitedNodes.add(nStart);
            for (int n : nodeBreakerView.getNodesInternalConnectedTo(nStart)) {
                if (!visitedNodes.contains(n) && (hasAttachedEquipment(n)
                                || nodeBreakerView.getSwitchStream(n).anyMatch(s -> !NodeBreakerTraverser.isOpenOrOpenable(s))
                                || !internalConnectionsEndOnOpenOrOpenableSwitches(n, visitedNodes))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isOpenable(Switch aSwitch) {
        return aSwitch != null && !aSwitch.isFictitious() && aSwitch.getKind() == SwitchKind.BREAKER;
    }

    private static boolean isOpenOrOpenable(Switch aSwitch) {
        return aSwitch.isOpen() || isOpenable(aSwitch);
    }
}
