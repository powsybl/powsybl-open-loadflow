package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.tasks.BranchTripping;
import com.powsybl.iidm.network.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
                    if (sw.isOpen()) {
                        return false;
                    }
                    if (nodeBefore == initNode) {
                        if (isOpenable(sw)) {
                            return false;
                        }
                        if (isBeforeOtherOpenedOrOpenableSwitches(sw, nodeAfter)) {
                            return false;
                        }
                    }
                    if (isOpenable(sw)) {
                        if (isLineBeforeSwitch(sw, nodeBefore)) {
                            return false;
                        }
                        if (isBeforeOtherOpenedOrOpenableSwitches(sw, nodeAfter)) {
                            return true;
                        }
                        if (isEndNodeAfterSwitch(sw, nodeAfter)) {
                            return false;
                        }
                        switchesToOpen.add(sw);
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

    private static boolean isEndNodeAfterSwitch(Switch sw, int nodeAfter) {
        Terminal terminal2 = sw.getVoltageLevel().getNodeBreakerView().getTerminal(nodeAfter);
        if (terminal2 != null) {
            ConnectableType connectableAfter = terminal2.getConnectable().getType();
            //TODO: check other connectable types
            return connectableAfter == ConnectableType.GENERATOR
                    || connectableAfter == ConnectableType.LOAD
                    || connectableAfter == ConnectableType.DANGLING_LINE
                    || connectableAfter == ConnectableType.STATIC_VAR_COMPENSATOR
                    || connectableAfter == ConnectableType.SHUNT_COMPENSATOR;
        }
        return false;
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

    private boolean isBeforeOtherOpenedOrOpenableSwitches(Switch aSwitch, int nodeAfter) {
        VoltageLevel.NodeBreakerView nbv = aSwitch.getVoltageLevel().getNodeBreakerView();
        if (nbv.getOptionalTerminal(nodeAfter).isPresent()) {
            return false;
        }
        List<Switch> openableSwitchesAtNodeAfter = nbv.getSwitchStream()
            .filter(s -> s != aSwitch && s != null &&  (nbv.getNode1(s.getId()) == nodeAfter || nbv.getNode2(s.getId()) == nodeAfter))
            .collect(Collectors.toList());
        return !openableSwitchesAtNodeAfter.isEmpty()
            && openableSwitchesAtNodeAfter.stream().allMatch(LfBranchTripping::isOpenOrOpenable);
    }

    private static boolean isOpenOrOpenable(Switch aSwitch) {
        return aSwitch.isOpen() || isOpenable(aSwitch);
    }

}
