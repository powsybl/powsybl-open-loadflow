/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class LfSwitchTest {

    Network network;

    LfNetwork lfNetwork;

    LfSwitch lfSwitch;

    AcLoadFlowParameters acLoadFlowParameters;

    @BeforeEach
    void setUp() {
        network = NodeBreakerNetworkFactory.create();
        acLoadFlowParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), new LoadFlowParameters(),
                new OpenLoadFlowParameters(), true);
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), acLoadFlowParameters.getNetworkParameters(), Reporter.NO_OP);
        assertEquals(1, lfNetworks.size());
        lfNetwork = lfNetworks.get(0);
        lfSwitch = (LfSwitch) lfNetwork.getBranchById("B3");
    }

    @Test
    void getterTest() {
        assertEquals("B3", lfSwitch.getId());
        assertEquals(false, lfSwitch.hasPhaseControlCapability());
        assertEquals(Double.NaN, lfSwitch.getP1().eval());
        assertEquals(Double.NaN, lfSwitch.getP2().eval());
        assertEquals(Double.NaN, lfSwitch.getI1().eval());
        assertEquals(Double.NaN, lfSwitch.getI2().eval());
        assertEquals(Collections.emptyList(), lfSwitch.getLimits1(LimitType.CURRENT));
        assertEquals(Collections.emptyList(), lfSwitch.getLimits2(LimitType.CURRENT));
    }

    @Test
    void setterTest() {
        lfSwitch.getPiModel().setX(LfNetwork.LOW_IMPEDANCE_THRESHOLD); //FIXME

        EquationTerm p1 = new ClosedBranchSide1ActiveFlowEquationTerm(lfSwitch, lfSwitch.getBus1(), lfSwitch.getBus2(), new VariableSet(), false, false);
        EquationTerm p2 = new ClosedBranchSide2ActiveFlowEquationTerm(lfSwitch, lfSwitch.getBus1(), lfSwitch.getBus2(), new VariableSet(), false, false);
        lfSwitch.setP1(p1);
        assertEquals(Double.NaN, lfSwitch.getP1().eval());
        lfSwitch.setP2(p2);
        assertEquals(Double.NaN, lfSwitch.getP2().eval());

        EquationTerm i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(lfSwitch, lfSwitch.getBus1(), lfSwitch.getBus2(), new VariableSet(), false, false);
        EquationTerm i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(lfSwitch, lfSwitch.getBus1(), lfSwitch.getBus2(), new VariableSet(), false, false);
        lfSwitch.setI1(i1);
        assertEquals(Double.NaN, lfSwitch.getP1().eval());
        lfSwitch.setI2(i2);
        assertEquals(Double.NaN, lfSwitch.getP2().eval());
    }

}
