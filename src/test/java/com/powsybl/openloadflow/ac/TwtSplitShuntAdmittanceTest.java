/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.PiModelArray;
import com.powsybl.openloadflow.network.PstFactory;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.openloadflow.util.PerUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
* @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
*/
class TwtSplitShuntAdmittanceTest {

    @Test
    void testSplitImpactOnPST() {

        // With a ratio of 1, splitting the shunt has an impact on the result

        Network n = PstFactory.createPSTWithLineAtEachSide();

        TwoWindingsTransformer pst = n.getTwoWindingsTransformer("pst");
        assertEquals(pst.getRatedU1(), pst.getRatedU2());

        System.out.println("No split");
        LoadFlowResult r = LoadFlow.find("OpenLoadFlow").run(n, new LoadFlowParameters().setTwtSplitShuntAdmittance(false));

        assertTrue(r.isFullyConverged());
        assertEquals(403.395, pst.getTerminal1().getQ(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-291.439, pst.getTerminal2().getQ(), LoadFlowAssert.DELTA_POWER);

        // With split
        r = LoadFlow.find("OpenLoadFlow").run(n, new LoadFlowParameters().setTwtSplitShuntAdmittance(true));

        assertTrue(r.isFullyConverged());
        assertEquals(404.622, pst.getTerminal1().getQ(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-292.680, pst.getTerminal2().getQ(), LoadFlowAssert.DELTA_POWER);

    }

    @Test
    void testPiModelOnTransformer() {
        // Test that the shunt split is correct
        Network n = VoltageControlNetworkFactory.createNetworkWithVoltageRegulatingT2wtTapChangeRationOnly();

        TwoWindingsTransformer twt = n.getTwoWindingsTransformer("T2wT");

        // Create pi model array and split transformer shunt admittance
        LfNetworkParameters params = new LfNetworkParameters()
                .setTwtSplitShuntAdmittance(true)
                .setTransformerVoltageControl(true);

        List<LfNetwork> lfNetworks = new LfNetworkLoaderImpl().load(n, new LfTopoConfig(), params, ReportNode.NO_OP);
        assertEquals(1, lfNetworks.size());

        LfNetwork lfNetwork = lfNetworks.get(0);
        LfBranch lfTwt = lfNetwork.getBranchById("T2wT");

        assertNotEquals(0, lfTwt.getPiModel().getB2());
        assertEquals(lfTwt.getPiModel().getB1(), lfTwt.getPiModel().getB2());

        // If we move B2 to the left, the result should be the same as TWT (in Per Unit)
        double ratio = twt.getRatedU2() / twt.getRatedU1();
        double bLeft = lfTwt.getPiModel().getB1() + lfTwt.getPiModel().getB2() / ratio;
        assertEquals(twt.getB() * PerUnit.zb(twt.getTerminal2().getVoltageLevel().getNominalV()), bLeft, 1e-5);

        assertNotEquals(0, lfTwt.getPiModel().getG2());
        assertEquals(lfTwt.getPiModel().getG1(), lfTwt.getPiModel().getG2());
        // If we move G2 to the left the result should be the same as TWT (in Per Unit)
        double gLeft = lfTwt.getPiModel().getG1() + lfTwt.getPiModel().getG2() / ratio;
        assertEquals(twt.getG() * PerUnit.zb(twt.getTerminal2().getVoltageLevel().getNominalV()), gLeft, 1e-5);

        // g and b should be the same for all tap positions
        double b1 = lfTwt.getPiModel().getB1();
        double g1 = lfTwt.getPiModel().getG1();
        PiModelArray piModelArray = (PiModelArray) lfTwt.getPiModel();
        for (int p = twt.getRatioTapChanger().getLowTapPosition(); p <= twt.getRatioTapChanger().getHighTapPosition(); p++) {
            piModelArray.setTapPosition(p);
            assertEquals(b1, piModelArray.getB1());
            assertEquals(b1, piModelArray.getB2());
            assertEquals(g1, piModelArray.getG1());
            assertEquals(g1, piModelArray.getG2());
        }
    }

}
