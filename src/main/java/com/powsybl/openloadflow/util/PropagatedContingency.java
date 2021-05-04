/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.*;

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

    private final Set<String> hvdcIdsToOpen = new HashSet<>();

    public Contingency getContingency() {
        return contingency;
    }

    public int getIndex() {
        return index;
    }

    public Set<String> getBranchIdsToOpen() {
        return branchIdsToOpen;
    }

    public Set<String> getHvdcIdsToOpen() {
        return hvdcIdsToOpen;
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
                        // branch check is done inside branch tripping
                        ContingencyTripping.createBranchTripping(network, element.getId())
                            .traverse(switchesToOpen, terminalsToDisconnect);
                        propagatedContingency.getBranchIdsToOpen().add(element.getId());
                        break;
                    case HVDC_LINE:
                        HvdcLine hvdcLine = network.getHvdcLine(element.getId());
                        if (hvdcLine == null) {
                            throw new PowsyblException("HVDC line '" + element.getId() + "' not found in the network");
                        }
                        propagatedContingency.getHvdcIdsToOpen().add(element.getId());
                        break;
                    case DANGLING_LINE:
                        ContingencyTripping.createDanglingLineTripping(network, element.getId())
                            .traverse(switchesToOpen, terminalsToDisconnect);
                        propagatedContingency.getBranchIdsToOpen().add(element.getId());
                        break;
                    default:
                        //TODO: support all kinds of contingencies
                        throw new UnsupportedOperationException("TODO");
                }
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
