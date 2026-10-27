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
        assertEquivalentBranch(equivalentBranches.getFirst(), "VL2_0", "VL3_0", 0.03706868414355851, -0.3706868414355851);

        assertEquals(2, equivalentShunts.size());
        assertEquivalentShunt(equivalentShunts.get(0), "VL2_0", 9.499904001808446e-7, -9.499904001870618e-6);
        assertEquivalentShunt(equivalentShunts.get(1), "VL3_0", 1.4799864002679187e-6, -1.4799864002554841e-5);

        assertEquals(2, equivalentInjections.size());
        assertEquivalentInjection(equivalentInjections.get(0), "VL2_0", -40.84205181085534, -4.907325835486706);
        assertEquivalentInjection(equivalentInjections.get(1), "VL3_0", -58.71864983277945, -6.8562553822088965);
    }

    @Test
    void testWardEquivalentWithIEEE118() {
        WardEquivalent wardEquivalent = getWardEquivalent(IeeeCdfNetworkFactory.create118(), getIEEE118InternalVlIds());

        List<EquivalentBranch> equivalentBranches = wardEquivalent.getEquivalentBranches();
        List<EquivalentShunt> equivalentShunts = wardEquivalent.getEquivalentShunts();
        List<EquivalentInjection> equivalentInjections = wardEquivalent.getEquivalentInjections();

        assertEquals(6, equivalentBranches.size());
        assertEquivalentBranch(equivalentBranches.get(0), "VL24_0", "VL37_0", 0.004359714862768916, -0.027338654195683317);
        assertEquivalentBranch(equivalentBranches.get(1), "VL24_0", "VL43_0", 8.467339836730728e-5, -3.053779328070469e-4);
        assertEquivalentBranch(equivalentBranches.get(2), "VL24_0", "VL65_0", 3.0487843117066316e-4, -0.002162604409181745);
        assertEquivalentBranch(equivalentBranches.get(3), "VL37_0", "VL43_0", 0.006830849795535841, -0.027885658139961092);
        assertEquivalentBranch(equivalentBranches.get(4), "VL37_0", "VL65_0", 8.468611717275745e-4, -0.015057076988353004);
        assertEquivalentBranch(equivalentBranches.get(5), "VL43_0", "VL65_0", 6.956550425595119e-6, -3.991423264750824e-5);

        assertEquals(4, equivalentShunts.size());
        assertEquivalentShunt(equivalentShunts.get(0), "VL24_0", 4.280962278602085e-4, 0.007619568245764463);
        assertEquivalentShunt(equivalentShunts.get(1), "VL37_0", 0.002478067936384306, 4.192000700081709e-4);
        assertEquivalentShunt(equivalentShunts.get(2), "VL43_0", 5.119036312427471e-6, 2.824197926388051e-4);
        assertEquivalentShunt(equivalentShunts.get(3), "VL65_0", -6.332125183276761E-4, 0.011133909977946662);

        assertEquals(4, equivalentInjections.size());
        assertEquivalentInjection(equivalentInjections.get(0), "VL24_0", 65.95999312336845, -208.76011607358197);
        assertEquivalentInjection(equivalentInjections.get(1), "VL37_0", -145.76247079967044, -376.1775947471858);
        assertEquivalentInjection(equivalentInjections.get(2), "VL43_0", -24.434300573304355, -12.761481349961166);
        assertEquivalentInjection(equivalentInjections.get(3), "VL65_0", 422.40080197642396, 22.1790145472137);
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

    private void assertEquivalentBranch(EquivalentBranch branch, String bus1, String bus2, double g, double b) {
        assertEquals(bus1, branch.bus1().getId());
        assertEquals(bus2, branch.bus2().getId());
        assertEquals(g, branch.g(), 1e-10);
        assertEquals(b, branch.b(), 1e-10);
    }

    private void assertEquivalentShunt(EquivalentShunt shunt, String bus, double g, double b) {
        assertEquals(bus, shunt.bus().getId());
        assertEquals(g, shunt.g(), 1e-15);
        assertEquals(b, shunt.b(), 1e-15);
    }

    private void assertEquivalentInjection(EquivalentInjection injection, String bus, double p, double q) {
        assertEquals(bus, injection.bus().getId());
        assertEquals(p, injection.p(), 1e-15);
        assertEquals(q, injection.q(), 1e-15);
    }

}
