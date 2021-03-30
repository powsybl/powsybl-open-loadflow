/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.Terminal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class PropagatedContingency {

    private final Contingency contingency;

    private final int index;

    private final Set<String> branchIdsToOpen = new HashSet<>();

    public Contingency getContingency() {
        return contingency;
    }

    public int getIndex() {
        return index;
    }

    public Set<String> getBranchIdsToOpen() {
        return branchIdsToOpen;
    }

    public PropagatedContingency(Contingency contingency, int index) {
        this.contingency = contingency;
        this.index = index;
    }

    public static List<PropagatedContingency> create(Network network, List<Contingency> contingencies, Set<Switch> allSwitchesToOpen) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency = new PropagatedContingency(contingency, index);
            propagatedContingencies.add(propagatedContingency);

            Set<Switch> switchesToOpen = new HashSet<>();
            Set<Terminal> terminalsToDisconnect =  new HashSet<>();
            for (ContingencyElement element : contingency.getElements()) {
                switch (element.getType()) {
                    case BRANCH:
                        propagatedContingency.getBranchIdsToOpen().add(element.getId());
                        break;
                    default:
                        //TODO: support all kinds of contingencies
                        throw new UnsupportedOperationException("TODO");
                }
                new BranchTripping(element.getId(), null)
                    .traverse(network, null, switchesToOpen, terminalsToDisconnect);
            }

            for (Switch sw : switchesToOpen) {
                propagatedContingency.getBranchIdsToOpen().add(sw.getId());
                allSwitchesToOpen.add(sw);
            }

            for (Terminal terminal : terminalsToDisconnect) {
                if (terminal.getConnectable() instanceof Branch) {
                    propagatedContingency.getBranchIdsToOpen().add(terminal.getConnectable().getId());
                }
            }

        }
        return propagatedContingencies;
    }
}
