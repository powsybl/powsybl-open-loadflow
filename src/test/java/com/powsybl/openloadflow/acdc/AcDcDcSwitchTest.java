/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.acdc;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageSourceConverter;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.AcDcNetworkFactory;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.network.AcDcNetworkFactory.*;
import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for DC switch resistance modeling in AC/DC load flow.
 * A DC switch with R > 0 must be loaded as a DC branch (LfDcBranch) in the LfNetwork.
 * A DC switch with R = 0 must be discarded: PowSyBl Core topology merges its two DC nodes
 * into the same DC bus, so OpenLoadFlow sees both ends as the same node and drops the branch.
 *
 * @author Landry Huet {@literal <landry.huet at supergrid-institute.com>}
 */
class AcDcDcSwitchTest {

    private LfNetworkParameters networkParameters;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    private void setParameters() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters).setAcDcNetwork(true);
    }

    @BeforeEach
    void setUp() {
        networkParameters = new LfNetworkParameters().setAcDcNetwork(true);
    }

    /**
     * When a DC switch has R = 0, PowSyBl Core topology merges its two DC nodes into the same
     * DC bus. OpenLoadFlow must then discard it (both ends point to the same LfDcBus),
     * leaving no DC branch for the switch.
     */
    @Test
    void testDcSwitchWithZeroResistanceIsNotLoadedAsBranch() {
        // dn3 --[sw R=0]--> dn4: PowSyBl Core merges dn3 and dn4 into one DC bus
        Network network = createAcDcNetworkWithDcSwitchOnly(0.0);

        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        // The switch is discarded because both DC terminals resolve to the same LfDcBus
        assertEquals(0, lfNetwork.getDcBranches().size());
        // dn3+dn4 are merged (1 bus) + dnDummy3 (1) + dnDummy4 (1) = 3 DC buses
        assertEquals(3, lfNetwork.getDcBuses().size());
    }

    /**
     * When a DC switch has R > 0, its two DC nodes remain distinct. OpenLoadFlow must load
     * the switch as a LfDcBranch connecting two separate LfDcBus instances.
     */
    @Test
    void testDcSwitchWithNonZeroResistanceIsLoadedAsBranch() {
        // dn3 --[sw R=0.5]--> dn4: nodes stay distinct
        Network network = createAcDcNetworkWithDcSwitchOnly(0.5);

        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        // The switch must appear as exactly one LfDcBranch
        assertEquals(1, lfNetwork.getDcBranches().size());
        // dn3 (1) + dn4 (1) + dnDummy3 (1) + dnDummy4 (1) = 4 DC buses
        assertEquals(4, lfNetwork.getDcBuses().size());
    }

    /**
     * An open DC switch must never be loaded as a branch, regardless of its resistance.
     * Core topology does not merge nodes across an open switch, so both nodes resolve to
     * distinct LfDcBus instances — but the circuit is broken and no branch should appear.
     */
    @Test
    void testOpenDcSwitchIsNotLoadedAsBranch() {
        // dn3 --[sw R=0.5, open]--> dn4: circuit breaker is open
        Network network = createAcDcNetworkWithDcSwitchOnly(0.5);
        network.getDcSwitch("sw34").setOpen(true);

        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        assertEquals(0, lfNetwork.getDcBranches().size());
    }

    /**
     * Meshed case: dl34 and an open sw34 both connect dn3 to dn4. Because dl34 keeps the DC
     * component connected, both ends of sw34 resolve to distinct non-null LfDcBuses — the guard
     * must still discard the open switch (the null-bus shortcut does not apply here).
     */
    @Test
    void testOpenDcSwitchInParallelWithDcLineIsNotLoadedAsBranch() {
        Network network = createAcDcNetworkWithParallelOpenDcSwitch();

        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        // Only dl34 must be present; the open sw34 must be discarded
        assertEquals(1, lfNetwork.getDcBranches().size());
    }

    @Disabled("Requires fix in the topology computation.")
    @Test
    void testDcSwitchInSeries() {
        final double resistance = 1.0;
        Network network = createAcDcNetworkWithSeriesDcSwitch(resistance);
        LfNetwork lfNetwork = Networks.load(network, networkParameters).getFirst();

        // We should have 3 branches
        assertEquals(3, lfNetwork.getDcBranches().size());
        assertEquals(6, lfNetwork.getDcBuses().size());

        setParameters();
        parametersExt
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        VoltageSourceConverter conv23 = network.getVoltageSourceConverter("conv23");
        double vDc = AcDcNetworkFactory.VDC;

        double i = conv23.getDcTerminal1().getI();
        double v3 = conv23.getDcTerminal1().getDcNode().getV();
        double u = v3 - vDc;
        double uOhm = -3 * resistance * i / 1000; // convert to kV
        assertEquals(uOhm, u, vDc * 1e-6);

        double delta = vDc * vDc + 12 * resistance * AcDcNetworkFactory.PAC;
        double current = (vDc - Math.sqrt(delta)) / (6 * resistance) * 1000; // conversion factor
        assertDcCurrentEquals(current, conv23.getDcTerminal1());
    }

    /**
     * Two DC components in the same connected component are loaded in the same LfNetwork, one DC component at a
     * time. Each resistive DC switch must be loaded exactly once, and not again when loading the other DC component.
     * Both DC links are identical, so both must reach the same analytical solution.
     */
    @Test
    void testDcSwitchesOfSeveralDcComponents() {
        final double resistance = 0.5;
        Network network = createAcDcNetworkWithTwoDcComponentsWithDcSwitch(resistance);

        List<LfNetwork> lfNetworks = Networks.load(network, networkParameters);
        assertEquals(1, lfNetworks.size());
        // One DC branch per DC switch
        assertEquals(2, lfNetworks.getFirst().getDcBranches().size());

        setParameters();
        parametersExt.setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        // Ideal converters: P = V_dn3 * I and V_dn3 = VDC + R * I
        double expectedVdn3 = 0.5 * (VDC + Math.sqrt(VDC * VDC + 4 * resistance * PAC));
        assertVoltageEquals(expectedVdn3, network.getDcNode("dn3"));
        assertVoltageEquals(expectedVdn3, network.getDcNode("dn3x"));
    }
}
