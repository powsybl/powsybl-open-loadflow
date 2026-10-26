/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.reduction.Reducer.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.powsybl.openloadflow.reduction.ReductionUtil.create4BusNetwork;
import static com.powsybl.openloadflow.reduction.ReductionUtil.getIEEE118InternalVlIds;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
class WardEquivalentTest {

    @Test
    void testWardEquivalentWith4BusNetwork() {
        WardEquivalent wardEquivalent = getWardEquivalent(create4BusNetwork(), Set.of("VL1"));

        List<EquivalentBranch> equivalentBranches = wardEquivalent.getEquivalentBranches();
        List<EquivalentShunt> equivalentShunts = wardEquivalent.getEquivalentShunts();
        List<EquivalentInjection> equivalentInjections = wardEquivalent.getEquivalentInjections();

        assertEquals(1, equivalentBranches.size());
        assertEquals(2, equivalentShunts.size());
        assertEquals(2, equivalentInjections.size());

        EquivalentBranch eqBranch = equivalentBranches.getFirst();
        assertEquals("VL2_0", eqBranch.bus1().getId());
        assertEquals("VL3_0", eqBranch.bus2().getId());
        assertEquals(0.03706868414355851, eqBranch.g(), 1e-10);
        assertEquals(-0.3706868414355851, eqBranch.b(), 1e-10);
        assertEquals(0.0, eqBranch.g1(), 1e-10);
        assertEquals(0.0, eqBranch.b1(), 1e-10);
        assertEquals(0.0, eqBranch.g2(), 1e-10);
        assertEquals(0.0, eqBranch.b2(), 1e-10);

        EquivalentShunt eqShunt = equivalentShunts.getFirst();
        assertEquals("VL2_0", eqShunt.bus().getId());
        assertEquals(9.499904001808446E-7, eqShunt.g(), 1e-15);
        assertEquals(-9.499904001870618E-6, eqShunt.b(), 1e-15);

        EquivalentInjection eqInj = equivalentInjections.getFirst();
        assertEquals("VL2_0", eqInj.bus().getId());
        assertEquals(-40.84205181085534, eqInj.p(), 1e-10);
        assertEquals(-4.907325835486706, eqInj.q(), 1e-10);
    }

    @Test
    void testWardEquivalentWithIEEE118() {
        WardEquivalent wardEquivalent = getWardEquivalent(IeeeCdfNetworkFactory.create118(), getIEEE118InternalVlIds());

        List<EquivalentBranch> equivalentBranches = wardEquivalent.getEquivalentBranches();
        List<EquivalentShunt> equivalentShunts = wardEquivalent.getEquivalentShunts();
        List<EquivalentInjection> equivalentInjections = wardEquivalent.getEquivalentInjections();

        assertEquals(6, equivalentBranches.size());
        assertEquals(4, equivalentShunts.size());
        assertEquals(4, equivalentInjections.size());

        EquivalentBranch eqBranch = equivalentBranches.getLast();
        assertEquals("VL43_0", eqBranch.bus1().getId());
        assertEquals("VL65_0", eqBranch.bus2().getId());
        assertEquals(6.956550425595119e-6, eqBranch.g(), 1e-10);
        assertEquals(-3.991423264750824e-5, eqBranch.b(), 1e-10);
        assertEquals(1.0434825638392678e-5, eqBranch.g1(), 1e-10);
        assertEquals(-5.987134897126235e-5, eqBranch.b1(), 1e-10);
        assertEquals(-4.173930255357071e-6, eqBranch.g2(), 1e-10);
        assertEquals(2.3948539588504942e-5, eqBranch.b2(), 1e-10);

        EquivalentShunt eqShunt = equivalentShunts.getLast();
        assertEquals("VL65_0", eqShunt.bus().getId());
        assertEquals(6.200517366662355e-5, eqShunt.g(), 1e-15);
        assertEquals(7.781525998373082e-4, eqShunt.b(), 1e-15);

        EquivalentInjection eqInj = equivalentInjections.getLast();
        assertEquals("VL65_0", eqInj.bus().getId());
        assertEquals(422.40080197642396, eqInj.p(), 1e-10);
        assertEquals(22.1790145472137, eqInj.q(), 1e-10);
    }

    private WardEquivalent getWardEquivalent(Network network, Set<String> internalVlIds) {
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new SparseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        loadFlowRunner.run(network, parameters);
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters());
        LfNetwork lfNetwork = lfNetworks.getFirst();

        ZoneDecomposition zones = new ZoneDecomposition(lfNetwork, internalVlIds);

        return new WardEquivalent(lfNetwork, zones, WardEquivalentType.STANDARD, new SparseMatrixFactory());
    }

}
