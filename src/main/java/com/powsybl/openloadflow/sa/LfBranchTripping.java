/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.tasks.BranchTripping;
import com.powsybl.iidm.network.*;

import java.util.*;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class LfBranchTripping extends BranchTripping {

    private final String branchId;
    private final String voltageLevelId;

    public LfBranchTripping(String branchId, String voltageLevelId) {
        super(branchId, voltageLevelId);
        this.branchId = Objects.requireNonNull(branchId);
        this.voltageLevelId = voltageLevelId;
    }

    @Override
    public void traverse(Network network, ComputationManager computationManager, Set<Switch> switchesToOpen, Set<Terminal> terminalsToDisconnect) {
        Objects.requireNonNull(network);

        Branch branch = network.getBranch(branchId);
        if (branch == null) {
            throw new PowsyblException("Branch '" + branchId + "' not found");
        }
        if (voltageLevelId != null) {
            if (voltageLevelId.equals(branch.getTerminal1().getVoltageLevel().getId())) {
                traverseFromTerminal(branch.getTerminal1(), switchesToOpen, terminalsToDisconnect, new HashSet<>());
            } else if (voltageLevelId.equals(branch.getTerminal2().getVoltageLevel().getId())) {
                traverseFromTerminal(branch.getTerminal2(), switchesToOpen, terminalsToDisconnect, new HashSet<>());
            } else {
                throw new PowsyblException("VoltageLevel '" + voltageLevelId + "' not connected to branch '" + branchId + "'");
            }
        } else {
            Set<Terminal> traversedTerminals = new HashSet<>();
            traverseFromTerminal(branch.getTerminal1(), switchesToOpen, terminalsToDisconnect, traversedTerminals);
            traverseFromTerminal(branch.getTerminal2(), switchesToOpen, terminalsToDisconnect, traversedTerminals);
        }
    }

    private void traverseFromTerminal(Terminal terminal, Set<Switch> switchesToOpen, Set<Terminal> terminalsToDisconnect, Set<Terminal> traversedTerminals) {

        if (traversedTerminals.contains(terminal)) {
            return;
        }
        traversedTerminals.add(terminal);

        if (terminal.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER) {

            int initNode = terminal.getNodeBreakerView().getNode();
            VoltageLevel.NodeBreakerView nodeBreakerView = terminal.getVoltageLevel().getNodeBreakerView();

            LfNodeBreakerTraverser traverser = new LfNodeBreakerTraverser(switchesToOpen, initNode, nodeBreakerView);
            nodeBreakerView.traverse(initNode, traverser);

            // Recursive call to continue the traverser in affected neighbouring voltage levels
            List<Terminal> nextTerminals = new ArrayList<>();
            traverser.getTraversedTerminals().forEach(t -> {
                addNextTerminals(t, nextTerminals);
                traversedTerminals.add(t);
                terminalsToDisconnect.add(t);
            });
            nextTerminals.forEach(t -> traverseFromTerminal(t, switchesToOpen, terminalsToDisconnect, traversedTerminals));

        } else {
            // TODO: Traverser yet to implement for bus breaker view
            throw new UnsupportedOperationException("Traverser yet to implement for bus breaker view");
        }
    }

    private static void addNextTerminals(Terminal otherTerminal, List<Terminal> nextTerminals) {
        Objects.requireNonNull(otherTerminal);
        Objects.requireNonNull(nextTerminals);
        Connectable otherConnectable = otherTerminal.getConnectable();
        if (otherConnectable instanceof Branch) {
            Branch branch = (Branch) otherConnectable;
            if (branch.getTerminal1() == otherTerminal) {
                nextTerminals.add(branch.getTerminal2());
            } else if (branch.getTerminal2() == otherTerminal) {
                nextTerminals.add(branch.getTerminal1());
            } else {
                throw new AssertionError();
            }
        } else if (otherConnectable instanceof ThreeWindingsTransformer) {
            ThreeWindingsTransformer ttc = (ThreeWindingsTransformer) otherConnectable;
            if (ttc.getLeg1().getTerminal() == otherTerminal) {
                nextTerminals.add(ttc.getLeg2().getTerminal());
                nextTerminals.add(ttc.getLeg3().getTerminal());
            } else if (ttc.getLeg2().getTerminal() == otherTerminal) {
                nextTerminals.add(ttc.getLeg1().getTerminal());
                nextTerminals.add(ttc.getLeg3().getTerminal());
            } else if (ttc.getLeg3().getTerminal() == otherTerminal) {
                nextTerminals.add(ttc.getLeg1().getTerminal());
                nextTerminals.add(ttc.getLeg2().getTerminal());
            } else {
                throw new AssertionError();
            }
        }
    }
}
