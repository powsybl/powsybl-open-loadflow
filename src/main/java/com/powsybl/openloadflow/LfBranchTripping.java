package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.tasks.BranchTripping;
import com.powsybl.iidm.network.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class LfBranchTripping extends BranchTripping {

    private final String branchId;
    private final String voltageLevelId;
    private final LfContingencyTopologyTraverser traverser;

    public LfBranchTripping(String branchId, String voltageLevelId) {
        super(branchId, voltageLevelId);
        this.branchId = Objects.requireNonNull(branchId);
        this.voltageLevelId = voltageLevelId;
        this.traverser = new LfContingencyTopologyTraverser();
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
                traverser.traverse(branch.getTerminal1(), switchesToOpen, terminalsToDisconnect);
            } else if (voltageLevelId.equals(branch.getTerminal2().getVoltageLevel().getId())) {
                traverser.traverse(branch.getTerminal2(), switchesToOpen, terminalsToDisconnect);
            } else {
                throw new PowsyblException("VoltageLevel '" + voltageLevelId + "' not connected to branch '" + branchId + "'");
            }
        } else {
            List<Switch> uselessSwitchForLf = new ArrayList<>();
            findUselessRetainedSwitch(branch.getTerminal1(), uselessSwitchForLf);
            findUselessRetainedSwitch(branch.getTerminal2(), uselessSwitchForLf);
            traverser.traverse(branch.getTerminal1(), switchesToOpen, terminalsToDisconnect);
            traverser.traverse(branch.getTerminal2(), switchesToOpen, terminalsToDisconnect);
            switchesToOpen.removeAll(uselessSwitchForLf);
        }
    }

    private void findUselessRetainedSwitch(Terminal terminal, List<Switch> uselessSwitchForLf) {
        if (terminal.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER) {
            int node = terminal.getNodeBreakerView().getNode();
            terminal.getVoltageLevel().getNodeBreakerView().traverse(node, (node1, sw, node2) -> {
                if ((node1 == node || node2 == node) && sw != null && sw.getKind() == SwitchKind.BREAKER) {
                    uselessSwitchForLf.add(sw);
                }
                return false;
            });
        }
    }

}
