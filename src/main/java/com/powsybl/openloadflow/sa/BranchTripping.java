/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.tasks.AbstractTrippingTask;
import com.powsybl.iidm.network.*;

import java.util.*;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class BranchTripping extends AbstractTrippingTask {

    private final String branchId;
    private final String voltageLevelId;

    public BranchTripping(String branchId) {
        this(branchId, null);
    }

    public BranchTripping(String branchId, String voltageLevelId) {
        this.branchId = Objects.requireNonNull(branchId);
        this.voltageLevelId = voltageLevelId;
    }

    @Override
    public void traverse(Network network, ComputationManager computationManager, Set<Switch> switchesToOpen, Set<Terminal> terminalsToDisconnect) {
        Objects.requireNonNull(network);

        Branch<?> branch = network.getBranch(branchId);
        if (branch == null) {
            throw new PowsyblException("Branch '" + branchId + "' not found");
        }

        Set<Terminal> traversedTerminals = new HashSet<>();
        if (voltageLevelId != null) {
            if (voltageLevelId.equals(branch.getTerminal1().getVoltageLevel().getId())) {
                traverseFromTerminal(branch.getTerminal1(), switchesToOpen, traversedTerminals);
            } else if (voltageLevelId.equals(branch.getTerminal2().getVoltageLevel().getId())) {
                traverseFromTerminal(branch.getTerminal2(), switchesToOpen, traversedTerminals);
            } else {
                throw new PowsyblException("VoltageLevel '" + voltageLevelId + "' not connected to branch '" + branchId + "'");
            }
        } else {
            traverseFromTerminal(branch.getTerminal1(), switchesToOpen, traversedTerminals);
            traverseFromTerminal(branch.getTerminal2(), switchesToOpen, traversedTerminals);
        }
        terminalsToDisconnect.addAll(traversedTerminals);
    }

    /**
     * Recursive method to calculate the switches to open and the traversed terminals from a terminal
     * @param terminal starting terminal
     * @param switchesToOpen set of switches which would be opened by the contingency propagation from terminal
     * @param traversedTerminals set of terminals traversed by the contingency propagation
     */
    private void traverseFromTerminal(Terminal terminal, Set<Switch> switchesToOpen, Set<Terminal> traversedTerminals) {
        Objects.requireNonNull(terminal);
        Objects.requireNonNull(switchesToOpen);
        Objects.requireNonNull(traversedTerminals);

        if (traversedTerminals.contains(terminal)) {
            return;
        }

        if (terminal.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER) {
            traversedTerminals.add(terminal);
            List<Terminal> nextTerminals = traverseNodeBreakerVoltageLevelsFromTerminal(terminal, switchesToOpen, traversedTerminals);

            // Recursive call to continue the traverser in affected neighbouring voltage levels
            nextTerminals.forEach(t -> traverseFromTerminal(t, switchesToOpen, traversedTerminals));
        } else {
            // In bus breaker view we have no idea what kind of switch it was in the initial node/breaker topology
            // so to keep things simple we do not propagate the fault
            if (terminal.isConnected()) {
                traversedTerminals.add(terminal);
            }
        }
    }

    private List<Terminal> traverseNodeBreakerVoltageLevelsFromTerminal(Terminal terminal, Set<Switch> switchesToOpen,
                                                                        Set<Terminal> traversedTerminals) {
        int initNode = terminal.getNodeBreakerView().getNode();
        VoltageLevel.NodeBreakerView nodeBreakerView = terminal.getVoltageLevel().getNodeBreakerView();

        NodeBreakerTraverser traverser = new NodeBreakerTraverser(switchesToOpen, initNode, nodeBreakerView);
        nodeBreakerView.traverse(initNode, traverser);

        List<Terminal> nextTerminals = new ArrayList<>();
        traverser.getTraversedTerminals().forEach(t -> {
            nextTerminals.addAll(t.getConnectable().getTerminals()); // the already traversed terminal are also added for the sake of simplicity
            traversedTerminals.add(t);
        });

        return nextTerminals;
    }

}
