package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.tasks.BranchTripping;
import com.powsybl.iidm.network.*;

import java.util.Objects;
import java.util.Set;

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
                findSwitchesToOpen(branch.getTerminal1(), switchesToOpen, terminalsToDisconnect);
            } else if (voltageLevelId.equals(branch.getTerminal2().getVoltageLevel().getId())) {
                findSwitchesToOpen(branch.getTerminal2(), switchesToOpen, terminalsToDisconnect);
            } else {
                throw new PowsyblException("VoltageLevel '" + voltageLevelId + "' not connected to branch '" + branchId + "'");
            }
        } else {
            findSwitchesToOpen(branch.getTerminal1(), switchesToOpen, terminalsToDisconnect);
            findSwitchesToOpen(branch.getTerminal2(), switchesToOpen, terminalsToDisconnect);
        }
    }

    private void findSwitchesToOpen(Terminal terminal, Set<Switch> switchesToOpen, Set<Terminal> terminalsToDisconnect) {
        if (terminal.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER) {
            int initNode = terminal.getNodeBreakerView().getNode();
            VoltageLevel.NodeBreakerView nodeBreakerView = terminal.getVoltageLevel().getNodeBreakerView();
            nodeBreakerView.traverse(initNode, (nodeBefore, sw, nodeAfter) -> {
                if (sw != null) {
                    if (isOpenable(sw) || sw.isOpen()) {
                        if (!sw.isOpen() && isSwitchLfNeeded(sw, nodeBefore, initNode)) {
                            switchesToOpen.add(sw);
                        }
                        return false;
                    }
                }
                // nodeAfter is traversed and its terminal is therefore disconnected
                nodeBreakerView.getOptionalTerminal(nodeAfter).ifPresent(terminalsToDisconnect::add);
                return true;
            });
        } else {
            // FIXME: Traverser yet to implement for bus breaker view
            throw new PowsyblException("Traverser yet to implement for bus breaker view");
        }
    }

    private static boolean isSwitchLfNeeded(Switch sw, int nodeBefore, int initNode) {
        // TODO: find other rules to identify switches which don't need to be retained
        return nodeBefore != initNode
            && !isLineBeforeSwitch(sw, nodeBefore);
    }

    private static boolean isLineBeforeSwitch(Switch sw, int nodeBefore) {
        Terminal terminal1 = sw.getVoltageLevel().getNodeBreakerView().getTerminal(nodeBefore);
        return terminal1 != null && terminal1.getConnectable().getType() == ConnectableType.LINE;
    }

    private static boolean isOpenable(Switch aSwitch) {
        return !aSwitch.isOpen() &&
            !aSwitch.isFictitious() &&
            aSwitch.getKind() == SwitchKind.BREAKER;
    }

}
