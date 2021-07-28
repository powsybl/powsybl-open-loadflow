/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
class PropagatedContingencyTest {

    @Test
    void testOnOpenLine() {
        Network network = NodeBreakerNetworkFactory.create();
        List<Contingency> contingencies = Collections.singletonList(new Contingency("l1", new BranchContingency("L1")));
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSensitivityAnalysis(network, contingencies);
        assertEquals(0, propagatedContingencies.size());
    }

    @Test
    void testCouplerNextToOpenDisconnector() {
        Network network = NodeBreakerNetworkFactory.create();

        // Open disconnector "D" and arrange network so that "C1" is a retained switch for branch "L2" contingency
        network.getSwitch("D").setOpen(true);
        network.getSwitch("B1").setFictitious(true);
        network.getVoltageLevel("VL1").getNodeBreakerView().newInternalConnection().setNode1(0).setNode2(6).add();

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l2", new BranchContingency("L2")));
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSensitivityAnalysis(network, contingencies);
        assertEquals(0, propagatedContingencies.size());
    }

    @Test
    void testSwitchToOpenDirectlyOn2wt() {
        Network network = NodeBreakerNetworkFactory.create();

        // Add 2wt surrounded by two breakers (note that marking the 2wt as a terminal to disconnect instead of retaining the 2 switches might be a feature added in the future)
        network.getVoltageLevel("VL1").getNodeBreakerView().newInternalConnection().setNode1(3).setNode2(7).add();
        network.getVoltageLevel("VL1").getNodeBreakerView().newSwitch().setId("B5").setNode1(7).setNode2(8).setKind(SwitchKind.BREAKER).add();
        network.getVoltageLevel("VL2").getNodeBreakerView().newSwitch().setId("B6").setNode1(0).setNode2(4).setKind(SwitchKind.BREAKER).add();
        network.getSubstation("S").newTwoWindingsTransformer().setId("TR1").setVoltageLevel1("VL1").setNode1(8).setVoltageLevel2("VL2").setNode2(4)
            .setRatedU1(400).setRatedU2(400).setR(1).setX(30).setG(0).setB(0).add();

        List<Contingency> contingencies = List.of(new Contingency("l2", new BranchContingency("L2")));
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSensitivityAnalysis(network, contingencies);
        assertEquals(1, propagatedContingencies.size());
        assertEquals(Set.of("B1", "B5", "L2"), propagatedContingencies.get(0).getBranchIdsToOpen());
    }

    @Test
    void testCouplerConnectedTo3Bars() {
        Network network = NodeBreakerNetworkFactory.create3Bars();

        // Add coupler "C3" connected to BBS1, BBS2 and BBS3
        VoltageLevel.NodeBreakerView nbv1 = network.getVoltageLevel("VL1").getNodeBreakerView();
        nbv1.newInternalConnection().setNode1(1).setNode2(8).add();
        nbv1.newBreaker().setId("C3").setNode1(8).setNode2(9).setKind(SwitchKind.BREAKER).add();
        nbv1.newInternalConnection().setNode1(9).setNode2(0).add();
        nbv1.newInternalConnection().setNode1(9).setNode2(2).add();

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l2", new BranchContingency("L2")));
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSensitivityAnalysis(network, contingencies);
        assertEquals(0, propagatedContingencies.size());
    }

}
