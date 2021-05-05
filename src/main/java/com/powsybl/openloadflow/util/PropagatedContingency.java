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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class PropagatedContingency {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropagatedContingency.class);

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

    public static List<PropagatedContingency> createListForSensitivityAnalysis(Network network, List<Contingency> contingencies) {
        // Sensitivity analysis works in bus view, hence
        //   - it cannot deal with contingencies whose propagation encounters a coupler
        //   - it does not need the set of switches to open
        return createList(network, contingencies, new HashSet<>(), true);
    }

    public static List<PropagatedContingency> createListForSecurityAnalysis(Network network, List<Contingency> contingencies, Set<Switch> allSwitchesToOpen) {
        // Security analysis works in bus breaker view, hence needs to know all switches to retain. Couplers are not a problem.
        return createList(network, contingencies, allSwitchesToOpen, false);
    }

    private static List<PropagatedContingency> createList(Network network, List<Contingency> contingencies, Set<Switch> allSwitchesToOpen,
                                                         boolean removeContingenciesEncounteringCouplers) {
        List<PropagatedContingency> propagatedContingencies = new ArrayList<>();
        for (int index = 0; index < contingencies.size(); index++) {
            Contingency contingency = contingencies.get(index);
            PropagatedContingency propagatedContingency = new PropagatedContingency(contingency, index);

            Set<Switch> switchesToOpen = new HashSet<>();
            Set<Terminal> terminalsToDisconnect =  new HashSet<>();
            for (ContingencyElement element : contingency.getElements()) {
                switch (element.getType()) {
                    case BRANCH:
                    case LINE:
                    case TWO_WINDINGS_TRANSFORMER:
                        // branch check is done inside branch tripping
                        new BranchTripping(element.getId(), null)
                            .traverse(network, null, switchesToOpen, terminalsToDisconnect);
                        propagatedContingency.getBranchIdsToOpen().add(element.getId());
                        break;
                    case HVDC_LINE:
                        HvdcLine hvdcLine = network.getHvdcLine(element.getId());
                        if (hvdcLine == null) {
                            throw new PowsyblException("HVDC line '" + element.getId() + "' not found");
                        }
                        propagatedContingency.getHvdcIdsToOpen().add(element.getId());
                        break;
                    default:
                        //TODO: support all kinds of contingencies
                        throw new UnsupportedOperationException("TODO");
                }
            }

            if (!removeContingenciesEncounteringCouplers || switchesToOpen.stream().noneMatch(PropagatedContingency::isCoupler)) {
                propagatedContingencies.add(propagatedContingency);

                for (Switch sw : switchesToOpen) {
                    propagatedContingency.getBranchIdsToOpen().add(sw.getId());
                    allSwitchesToOpen.add(sw);
                }

                for (Terminal terminal : terminalsToDisconnect) {
                    if (terminal.getConnectable() instanceof Branch) {
                        propagatedContingency.getBranchIdsToOpen().add(terminal.getConnectable().getId());
                    }
                }

            } else {
                LOGGER.error("Contingency '{}' removed from list, as a coupler switch has been encountered while propagating the contingency", contingency.getId());
            }

        }
        return propagatedContingencies;
    }

    private static boolean isCoupler(Switch s) {
        VoltageLevel.NodeBreakerView nbv = s.getVoltageLevel().getNodeBreakerView();
        Connectable<?> c1 = nbv.getTerminal1(s.getId()).getConnectable();
        Connectable<?> c2 = nbv.getTerminal2(s.getId()).getConnectable();
        return c1 != c2 && c1.getType() == ConnectableType.BUSBAR_SECTION && c2.getType() == ConnectableType.BUSBAR_SECTION;
    }
}
