/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.iidm.network.*;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class NodeBreakerTraverser implements VoltageLevel.NodeBreakerView.Traverser {

    private final Set<Switch> switchesToOpen;
    private final Set<Terminal> traversedTerminals;
    private final int initNode;
    private final VoltageLevel.NodeBreakerView nodeBreakerView;

    public NodeBreakerTraverser(Set<Switch> switchesToOpen, int initNode,
                                VoltageLevel.NodeBreakerView nodeBreakerView) {
        this.switchesToOpen = switchesToOpen;
        this.traversedTerminals = new HashSet<>();
        this.initNode = initNode;
        this.nodeBreakerView = nodeBreakerView;
    }

    @Override
    public boolean traverse(int nodeBefore, Switch sw, int nodeAfter) {
        if (sw != null) {
            if (sw.isOpen()) {
                return false;
            }

            if (nodeBefore == initNode && traverserStopsAtOtherStartEdges(sw, initNode)) {
                // Switch is just after contingency and traverser stops at other start edges
                if (isOpenable(sw)) {
                    // The traverser can stop now and no need to retain current switch
                    return false;
                }
                if (traverserWouldStopAfter(sw, nodeAfter)) {
                    // As the traverser would stop just after, it can stop now (without retaining current switch)
                    return false;
                }
            }

            if (isOpenable(sw)) {
                // The current switch is openable: the traverser could stop and the switch could be retained,
                // but, to avoid unnecessary retained switches, the traverser does not retain it in two cases
                if (traverserWouldStopAfter(sw, nodeAfter)) {
                    // Continuing traversing might lead in some cases to more retained switches, but in practice the
                    // switches after are often opened and sometimes followed by an end node
                    return true;
                }
                if (isEndNodeAfterSwitch(sw, nodeAfter)) {
                    // No need to retain switch if the node after the switch is an end node (e.g. load or generator)
                    return false;
                }
                switchesToOpen.add(sw);
                return false;
            }
        }

        // The traverser continues, hence nodeAfter is traversed
        nodeBreakerView.getOptionalTerminal(nodeAfter).ifPresent(traversedTerminals::add);
        return true;
    }

    private static boolean isEndNodeAfterSwitch(Switch sw, int nodeAfter) {
        Terminal terminal2 = sw.getVoltageLevel().getNodeBreakerView().getTerminal(nodeAfter);
        if (terminal2 != null) {
            ConnectableType connectableAfter = terminal2.getConnectable().getType();
            boolean endNodeAfter = connectableAfter == ConnectableType.GENERATOR
                || connectableAfter == ConnectableType.LOAD
                || connectableAfter == ConnectableType.DANGLING_LINE
                || connectableAfter == ConnectableType.STATIC_VAR_COMPENSATOR
                || connectableAfter == ConnectableType.SHUNT_COMPENSATOR;

            if (endNodeAfter) { // check that there isn't another (closed) switch or internal connection at node after
                VoltageLevel.NodeBreakerView nbv = sw.getVoltageLevel().getNodeBreakerView();
                return noInternalConnectionAtNode(nodeAfter, nbv)
                    && nbv.getSwitchStream().noneMatch(s -> s != sw && switchAtNode(s, nodeAfter, nbv)  && !s.isOpen());
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
        return nbv.getSwitchStream().filter(s -> s != aSwitch && switchAtNode(s, node, nbv)).allMatch(NodeBreakerTraverser::isOpenOrOpenable);
    }

    private static boolean noInternalConnectionAtNode(int node, VoltageLevel.NodeBreakerView nbv) {
        return nbv.getInternalConnectionStream().noneMatch(ic -> ic.getNode1() == node || ic.getNode2() == node);
    }

    private static boolean switchAtNode(Switch s, int nodeAfter, VoltageLevel.NodeBreakerView nbv) {
        return s != null && (nbv.getNode1(s.getId()) == nodeAfter || nbv.getNode2(s.getId()) == nodeAfter);
    }

    private static boolean isOpenable(Switch aSwitch) {
        return !aSwitch.isFictitious() && aSwitch.getKind() == SwitchKind.BREAKER;
    }

    private static boolean isOpenOrOpenable(Switch aSwitch) {
        return aSwitch.isOpen() || isOpenable(aSwitch);
    }

    protected Set<Terminal> getTraversedTerminals() {
        return this.traversedTerminals;
    }
}
