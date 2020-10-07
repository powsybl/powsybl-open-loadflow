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
                        // Current switch is just after the contingency
                        if (isOpenable(sw)) {
                            // An openable switch just after a contingency does not need to be retained and the
                            // traverser can stop
                            return false;
                        }
                        if (isBeforeOtherOpenedOrOpenableSwitches(sw, nodeAfter)) {
                            // As all paths after current switch do start with an opened or openable switch, the
                            // traverser can stop and no switches are retained
                            return false;
                        }
                    }

                    if (isOpenable(sw)) {
                        // The current switch is openable: the traverser could stop and the switch could be retained;
                        // but, to avoid unnecessary retained switches, the traverser does not retain it in two cases
                        if (isBeforeOtherOpenedOrOpenableSwitches(sw, nodeAfter)) {
                            // This might lead in some cases to more retained switches, but in practice the switches
                            // after are often opened and sometimes followed by an end node
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
            boolean endNodeAfter = connectableAfter == ConnectableType.GENERATOR
                || connectableAfter == ConnectableType.LOAD
                || connectableAfter == ConnectableType.DANGLING_LINE
                || connectableAfter == ConnectableType.STATIC_VAR_COMPENSATOR
                || connectableAfter == ConnectableType.SHUNT_COMPENSATOR;

            if (endNodeAfter) { // check that there isn't another (closed) switch or internal connection at node after
                VoltageLevel.NodeBreakerView nbv = sw.getVoltageLevel().getNodeBreakerView();
                return nbv.getInternalConnectionStream().noneMatch(ic -> ic.getNode1() == nodeAfter || ic.getNode2() == nodeAfter)
                    && nbv.getSwitchStream().noneMatch(s -> s != sw && switchAtNode(s, nodeAfter, nbv)  && !s.isOpen());
            }
        }
        return false;
    }

    private boolean isBeforeOtherOpenedOrOpenableSwitches(Switch aSwitch, int nodeAfter) {
        VoltageLevel.NodeBreakerView nbv = aSwitch.getVoltageLevel().getNodeBreakerView();

        // Check that node after is a junction of switches only
        if (!nbv.getOptionalTerminal(nodeAfter).isPresent()
            && nbv.getInternalConnectionStream().noneMatch(ic -> ic.getNode1() == nodeAfter || ic.getNode2() == nodeAfter)) {
            // Find all switches connected to node after
            List<Switch> openableSwitchesAtNodeAfter = nbv.getSwitchStream()
                .filter(s -> s != aSwitch && switchAtNode(s, nodeAfter, nbv))
                .collect(Collectors.toList());
            return !openableSwitchesAtNodeAfter.isEmpty()
                && openableSwitchesAtNodeAfter.stream().allMatch(LfBranchTripping::isOpenOrOpenable);
        }

        return false;
    }

    private static boolean switchAtNode(Switch s, int nodeAfter, VoltageLevel.NodeBreakerView nbv) {
        return s != null && (nbv.getNode1(s.getId()) == nodeAfter || nbv.getNode2(s.getId()) == nodeAfter);
    }

    private static boolean isOpenable(Switch aSwitch) {
        return !aSwitch.isOpen() &&
            !aSwitch.isFictitious() &&
            aSwitch.getKind() == SwitchKind.BREAKER;
    }

    private static boolean isOpenOrOpenable(Switch aSwitch) {
        return aSwitch.isOpen() ||
            (!aSwitch.isFictitious() && aSwitch.getKind() == SwitchKind.BREAKER);
    }

}
