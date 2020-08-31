package com.powsybl.openloadflow;

import com.powsybl.iidm.network.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class LfContingencyTopologyTraverser implements VoltageLevel.TopologyTraverser {

    private Set<Switch> switchesToOpen;
    private Set<Terminal> terminalsToDisconnect;
    private Set<Terminal> traversedTerminals;

    private static boolean isOpenable(Switch aSwitch) {
        return !aSwitch.isOpen() &&
            !aSwitch.isFictitious() &&
            aSwitch.getKind() == SwitchKind.BREAKER;
    }

    public void traverse(Terminal terminal, Set<Switch> switchesToOpen, Set<Terminal> terminalsToDisconnect) {
        Objects.requireNonNull(terminal);
        Objects.requireNonNull(switchesToOpen);
        Objects.requireNonNull(terminalsToDisconnect);
        setSwitchesToOpen(switchesToOpen);
        setTerminalsToDisconnect(terminalsToDisconnect);
        setTraversedTerminals(new HashSet<>());
        terminal.traverse(this);
    }

    private void setTraversedTerminals(Set<Terminal> traversedTerminals) {
        this.traversedTerminals = traversedTerminals;
    }

    @Override
    public boolean traverse(Terminal terminal, boolean connected) {
        traversedTerminals.add(terminal);
        if (terminal.getVoltageLevel().getTopologyKind() == TopologyKind.BUS_BREAKER) {
            // we have no idea what kind of switch it was in the initial node/breaker topology
            // so to keep things simple we do not propagate the fault
            if (connected) {
                terminalsToDisconnect.add(terminal);
            }
            return false;
        }
        // in node/breaker topology propagation is decided only based on switch position
        return true;
    }

    @Override
    public boolean traverse(Switch aSwitch) {
        boolean traverse = false;

        if (isOpenable(aSwitch)) {
            switchesToOpen.add(aSwitch);
        } else if (!aSwitch.isOpen()) {
            traverse = true;
        }

        return traverse;
    }

    public void setSwitchesToOpen(Set<Switch> switchesToOpen) {
        this.switchesToOpen = switchesToOpen;
    }

    public void setTerminalsToDisconnect(Set<Terminal> terminalsToDisconnect) {
        this.terminalsToDisconnect = terminalsToDisconnect;
    }

}
