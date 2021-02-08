package com.powsybl.openloadflow.util;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.openloadflow.sa.BranchTripping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContingencyContext {

    final Contingency contingency;

    final Set<String> branchIdsToOpen = new HashSet<>();

    public Contingency getContingency() {
        return contingency;
    }

    public Set<String> getBranchIdsToOpen() {
        return branchIdsToOpen;
    }

    public ContingencyContext(Contingency contingency) {
        this.contingency = contingency;
    }

    public static List<ContingencyContext> getContingencyContexts(Network network, List<Contingency> contingencies, Set<Switch> allSwitchesToOpen) {
        List<ContingencyContext> contingencyContexts = new ArrayList<>();
        for (Contingency contingency : contingencies) {
            ContingencyContext contingencyContext = new ContingencyContext(contingency);
            contingencyContexts.add(contingencyContext);

            Set<Switch> switchesToOpen = new HashSet<>();
            Set<Terminal> terminalsToDisconnect =  new HashSet<>();
            for (ContingencyElement element : contingency.getElements()) {
                switch (element.getType()) {
                    case BRANCH:
                        contingencyContext.getBranchIdsToOpen().add(element.getId());
                        break;
                    default:
                        //TODO: support all kinds of contingencies
                        throw new UnsupportedOperationException("TODO");
                }
                new BranchTripping(element.getId(), null)
                    .traverse(network, null, switchesToOpen, terminalsToDisconnect);
            }

            for (Switch sw : switchesToOpen) {
                contingencyContext.getBranchIdsToOpen().add(sw.getId());
                allSwitchesToOpen.add(sw);
            }

            for (Terminal terminal : terminalsToDisconnect) {
                if (terminal.getConnectable() instanceof Branch) {
                    contingencyContext.getBranchIdsToOpen().add(terminal.getConnectable().getId());
                }
            }

        }
        return contingencyContexts;
    }
}
