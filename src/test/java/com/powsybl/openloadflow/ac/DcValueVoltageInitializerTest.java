/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.ReferencePriority;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.dc.DcValueVoltageInitializer;
import com.powsybl.openloadflow.dc.equations.DcApproximationType;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class DcValueVoltageInitializerTest {

    private Network network;
    private SlackBusSelector slackBusSelector;
    private MatrixFactory matrixFactory;
    private LfNetworkParameters lfNetworkParameters;

    private static void assertBusVoltage(LfNetwork network, VoltageInitializer initializer, String busId, double angleRef) {
        LfBus bus = network.getBusById(busId);
        assertNotNull(bus);
        double v = initializer.getMagnitude(bus);
        double angle = initializer.getAngle(bus);
        assertEquals(1.0, v, 1E-6d);
        assertEquals(angleRef, angle, 1E-2d);
    }

    @BeforeEach
    void setUp() {
        network = FourBusNetworkFactory.create();
        slackBusSelector = new FirstSlackBusSelector();
        matrixFactory = new DenseMatrixFactory();
        lfNetworkParameters = new LfNetworkParameters().setSlackBusSelector(slackBusSelector);
    }

    @Test
    void testFourBusNetwork() {
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), slackBusSelector).get(0);
        VoltageInitializer initializer = new DcValueVoltageInitializer(lfNetworkParameters,
                false,
                LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX,
                true,
                DcApproximationType.IGNORE_R,
                matrixFactory,
                1);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "b1_vl_0", 0.0);
        assertBusVoltage(lfNetwork, initializer, "b2_vl_0", -0.025);
        assertBusVoltage(lfNetwork, initializer, "b3_vl_0", -0.15);
        assertBusVoltage(lfNetwork, initializer, "b4_vl_0", -0.025);
    }

    @Test
    void testFourBusNetworkReferencePriority() {
        ReferencePriority.set(network.getGenerator("g4"), 1);
        lfNetworkParameters.setReferenceBusSelector(new ReferenceBusGeneratorPrioritySelector());
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), lfNetworkParameters).get(0);
        VoltageInitializer initializer = new DcValueVoltageInitializer(lfNetworkParameters,
                false,
                LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX,
                true,
                DcApproximationType.IGNORE_R,
                matrixFactory,
                1);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "b1_vl_0", 0.025);
        assertBusVoltage(lfNetwork, initializer, "b2_vl_0", 0.0);
        assertBusVoltage(lfNetwork, initializer, "b3_vl_0", -0.125);
        assertBusVoltage(lfNetwork, initializer, "b4_vl_0", 0.0);
    }

    @Test
    void testFourBusNetworkZeroImpedanceBranch() {
        // Line l12 with zero impedance
        network.getLine("l12").setR(0).setX(0);
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), slackBusSelector).get(0);
        VoltageInitializer initializer = new DcValueVoltageInitializer(lfNetworkParameters,
                false,
                LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX,
                true,
                DcApproximationType.IGNORE_R,
                matrixFactory,
                1);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "b1_vl_0", 0.0);
        assertBusVoltage(lfNetwork, initializer, "b2_vl_0", 0.0);
        assertBusVoltage(lfNetwork, initializer, "b3_vl_0", -0.14);
        assertBusVoltage(lfNetwork, initializer, "b4_vl_0", -0.02);
    }

    @Test
    void testFourBusNetworkResistiveOnlyBranch() {
        // Line l12 only resistive
        network.getLine("l12").setR(0.1).setX(0);
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), slackBusSelector).get(0);
        VoltageInitializer initializer = new DcValueVoltageInitializer(lfNetworkParameters,
                false,
                LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX,
                true,
                DcApproximationType.IGNORE_R,
                matrixFactory,
                1);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "b1_vl_0", 0.0);
        assertBusVoltage(lfNetwork, initializer, "b2_vl_0", 0.0);
        assertBusVoltage(lfNetwork, initializer, "b3_vl_0", -0.14);
        assertBusVoltage(lfNetwork, initializer, "b4_vl_0", -0.02);
    }

}
