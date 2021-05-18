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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class PropagatedContingency {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropagatedContingency.class);

    private final Contingency contingency;

    private final int index;

    private final Set<String> branchIdsToOpen;

    private final Set<Switch> switchesToOpen;

    private final Set<String> hvdcIdsToOpen;

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

    public PropagatedContingency(Contingency contingency, int index, Set<String> branchIdsToOpen, Set<String> hvdcIdsToOpen,
                                 Set<Switch> switchesToOpen, Set<Terminal> terminalsToDisconnect) {
        this.contingency = Objects.requireNonNull(contingency);
        this.index = index;
        this.branchIdsToOpen = Objects.requireNonNull(branchIdsToOpen);
        this.hvdcIdsToOpen = Objects.requireNonNull(hvdcIdsToOpen);
        this.switchesToOpen = Objects.requireNonNull(switchesToOpen);

        for (Switch sw : switchesToOpen) {
            branchIdsToOpen.add(sw.getId());
        }

        for (Terminal terminal : terminalsToDisconnect) {
            if (terminal.getConnectable() instanceof Branch) {
                branchIdsToOpen.add(terminal.getConnectable().getId());
            }
        }
    }

    public static List<PropagatedContingency> createListForSensitivityAnalysis(Network network, List<Contingency> contingencies) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency = PropagatedContingency.create(network, contingency, index);
            Optional<Switch> coupler = propagatedContingency.switchesToOpen.stream().filter(PropagatedContingency::isCoupler).findFirst();
            if (coupler.isEmpty()) {
                propagatedContingencies.add(propagatedContingency);
            } else {
                // Sensitivity analysis works in bus view, it cannot deal (yet)  with contingencies whose propagation encounters a coupler
                LOGGER.warn("Propagated contingency '{}' not processed: coupler '{}' has been encountered while propagating the contingency",
                    contingency.getId(), coupler.get().getId());
            }
        }
        return propagatedContingencies;
    }

    public static List<PropagatedContingency> createListForSecurityAnalysis(Network network, List<Contingency> contingencies, Set<Switch> allSwitchesToOpen) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency = PropagatedContingency.create(network, contingency, index);
            propagatedContingencies.add(propagatedContingency);
            allSwitchesToOpen.addAll(propagatedContingency.switchesToOpen);
        }
        return propagatedContingencies;
    }

    private static PropagatedContingency create(Network network, Contingency contingency, int index) {
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect =  new HashSet<>();
        Set<String> branchIdsToOpen = new HashSet<>();
        Set<String> hvdcIdsToOpen = new HashSet<>();
        for (ContingencyElement element : contingency.getElements()) {
            switch (element.getType()) {
                case BRANCH:
                case LINE:
                case TWO_WINDINGS_TRANSFORMER:
                    // branch check is done inside tripping
                    ContingencyTripping.createBranchTripping(network, element.getId())
                        .traverse(switchesToOpen, terminalsToDisconnect);
                    branchIdsToOpen.add(element.getId());
                    break;
                case HVDC_LINE:
                    HvdcLine hvdcLine = network.getHvdcLine(element.getId());
                    if (hvdcLine == null) {
                        throw new PowsyblException("HVDC line '" + element.getId() + "' not found in the network");
                    }
                    hvdcIdsToOpen.add(element.getId());
                    break;
                case DANGLING_LINE:
                    // dangling line check is done inside tripping
                    ContingencyTripping.createDanglingLineTripping(network, element.getId())
                        .traverse(switchesToOpen, terminalsToDisconnect);
                    branchIdsToOpen.add(element.getId());
                    break;
                default:
                    //TODO: support all kinds of contingencies
                    throw new UnsupportedOperationException("TODO");
            }
        }

        return new PropagatedContingency(contingency, index, branchIdsToOpen, hvdcIdsToOpen, switchesToOpen, terminalsToDisconnect);
    }

    private static boolean isCoupler(Switch s) {
        VoltageLevel.NodeBreakerView nbv = s.getVoltageLevel().getNodeBreakerView();
        Connectable<?> c1 = nbv.getTerminal1(s.getId()).getConnectable();
        Connectable<?> c2 = nbv.getTerminal2(s.getId()).getConnectable();
        return c1 != c2 && c1.getType() == ConnectableType.BUSBAR_SECTION && c2.getType() == ConnectableType.BUSBAR_SECTION;
    }
}
