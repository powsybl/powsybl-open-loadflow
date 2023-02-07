/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Importers;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VoltageMagnitudeInitializerTest {

    public static void assertBusVoltage(LfNetwork network, VoltageInitializer initializer, String busId, double vRef, double angleRef) {
        LfBus bus = network.getBusById(busId);
        double v = initializer.getMagnitude(bus);
        double angle = initializer.getAngle(bus);
        assertNotNull(bus);
        assertEquals(vRef, v, 1E-6d);
        assertEquals(angleRef, angle, 1E-2d);
    }

    @Test
    void testEsgTuto1() {
        Network network = EurostagTutorialExample1Factory.create();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        VoltageMagnitudeInitializer initializer = new VoltageMagnitudeInitializer(false, new DenseMatrixFactory(), LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "VLGEN_0", 1.020833, 0);
        assertBusVoltage(lfNetwork, initializer, "VLHV1_0", 1.074561, 0);
        assertBusVoltage(lfNetwork, initializer, "VLHV2_0", 1.074561, 0);
        assertBusVoltage(lfNetwork, initializer, "VLLOAD_0", 1.075994, 0);
    }

    @Test
    void testIeee14() {
        Network network = IeeeCdfNetworkFactory.create14();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        VoltageMagnitudeInitializer initializer = new VoltageMagnitudeInitializer(false, new DenseMatrixFactory(), LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "VL1_0", 1.06, 0);
        assertBusVoltage(lfNetwork, initializer, "VL2_0", 1.045, 0);
        assertBusVoltage(lfNetwork, initializer, "VL3_0", 1.01, 0);
        assertBusVoltage(lfNetwork, initializer, "VL4_0", 1.081485, 0);
        assertBusVoltage(lfNetwork, initializer, "VL5_0", 1.064609, 0);
        assertBusVoltage(lfNetwork, initializer, "VL6_0", 1.07, 0);
        assertBusVoltage(lfNetwork, initializer, "VL7_0", 1.2489781, 0);
        assertBusVoltage(lfNetwork, initializer, "VL8_0", 1.09, 0);
        assertBusVoltage(lfNetwork, initializer, "VL9_0", 1.320475, 0);
        assertBusVoltage(lfNetwork, initializer, "VL10_0", 1.275961, 0);
        assertBusVoltage(lfNetwork, initializer, "VL11_0", 1.174779, 0);
        assertBusVoltage(lfNetwork, initializer, "VL12_0", 1.089793, 0);
        assertBusVoltage(lfNetwork, initializer, "VL13_0", 1.105257, 0);
        assertBusVoltage(lfNetwork, initializer, "VL14_0", 1.226377, 0);
    }

    @Test
    void testZeroImpedanceBranch() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getLine("L9-14-1").setX(0);
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        VoltageMagnitudeInitializer initializer = new VoltageMagnitudeInitializer(false, new DenseMatrixFactory(), LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "VL1_0", 1.06, 0);
        assertBusVoltage(lfNetwork, initializer, "VL2_0", 1.045, 0);
        assertBusVoltage(lfNetwork, initializer, "VL3_0", 1.01, 0);
        assertBusVoltage(lfNetwork, initializer, "VL4_0", 1.078661, 0);
        assertBusVoltage(lfNetwork, initializer, "VL5_0", 1.062842, 0);
        assertBusVoltage(lfNetwork, initializer, "VL6_0", 1.07, 0);
        assertBusVoltage(lfNetwork, initializer, "VL7_0", 1.240216, 0);
        assertBusVoltage(lfNetwork, initializer, "VL8_0", 1.09, 0);
        assertBusVoltage(lfNetwork, initializer, "VL9_0", 1.300261, 0); // equals VL14_0
        assertBusVoltage(lfNetwork, initializer, "VL10_0", 1.25934, 0);
        assertBusVoltage(lfNetwork, initializer, "VL11_0", 1.166324, 0);
        assertBusVoltage(lfNetwork, initializer, "VL12_0", 1.099144, 0);
        assertBusVoltage(lfNetwork, initializer, "VL13_0", 1.121916, 0);
        assertBusVoltage(lfNetwork, initializer, "VL14_0", 1.300261, 0); // equals VL9_0
    }

    @Test
    void testZeroImpedanceBranchConnectedToPvBus() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getLine("L6-11-1").setX(0);
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        VoltageMagnitudeInitializer initializer = new VoltageMagnitudeInitializer(false, new DenseMatrixFactory(), LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "VL1_0", 1.06, 0);
        assertBusVoltage(lfNetwork, initializer, "VL2_0", 1.045, 0);
        assertBusVoltage(lfNetwork, initializer, "VL3_0", 1.01, 0);
        assertBusVoltage(lfNetwork, initializer, "VL4_0", 1.076690, 0);
        assertBusVoltage(lfNetwork, initializer, "VL5_0", 1.061609, 0);
        assertBusVoltage(lfNetwork, initializer, "VL6_0", 1.07, 0); // equals target
        assertBusVoltage(lfNetwork, initializer, "VL7_0", 1.234098, 0);
        assertBusVoltage(lfNetwork, initializer, "VL8_0", 1.09, 0);
        assertBusVoltage(lfNetwork, initializer, "VL9_0", 1.286148, 0);
        assertBusVoltage(lfNetwork, initializer, "VL10_0", 1.220109, 0);
        assertBusVoltage(lfNetwork, initializer, "VL11_0", 1.07, 0); // equals VL6_0
        assertBusVoltage(lfNetwork, initializer, "VL12_0", 1.08708, 0);
        assertBusVoltage(lfNetwork, initializer, "VL13_0", 1.100426, 0);
        assertBusVoltage(lfNetwork, initializer, "VL14_0", 1.204946, 0);
    }

    @Test
    void testParallelBranch() {
        Network network = IeeeCdfNetworkFactory.create14();
        Line l9101 = network.getLine("L9-10-1");
        double newX = l9101.getX() * 2; // so that result is the same as initial case when doubling line
        l9101.setX(newX);
        network.newLine()
                .setId("L9-10-2")
                .setVoltageLevel1("VL9")
                .setConnectableBus1("B9")
                .setBus1("B9")
                .setVoltageLevel2("VL10")
                .setConnectableBus2("B10")
                .setBus2("B10")
                .setR(0)
                .setX(newX)
                .add();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        VoltageMagnitudeInitializer initializer = new VoltageMagnitudeInitializer(false, new DenseMatrixFactory(), LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "VL1_0", 1.06, 0);
        assertBusVoltage(lfNetwork, initializer, "VL2_0", 1.045, 0);
        assertBusVoltage(lfNetwork, initializer, "VL3_0", 1.01, 0);
        assertBusVoltage(lfNetwork, initializer, "VL4_0", 1.081485, 0);
        assertBusVoltage(lfNetwork, initializer, "VL5_0", 1.064609, 0);
        assertBusVoltage(lfNetwork, initializer, "VL6_0", 1.07, 0);
        assertBusVoltage(lfNetwork, initializer, "VL7_0", 1.248978, 0);
        assertBusVoltage(lfNetwork, initializer, "VL8_0", 1.09, 0);
        assertBusVoltage(lfNetwork, initializer, "VL9_0", 1.320475, 0);
        assertBusVoltage(lfNetwork, initializer, "VL10_0", 1.275961, 0);
        assertBusVoltage(lfNetwork, initializer, "VL11_0", 1.17478, 0);
        assertBusVoltage(lfNetwork, initializer, "VL12_0", 1.089793, 0);
        assertBusVoltage(lfNetwork, initializer, "VL13_0", 1.105258, 0);
        assertBusVoltage(lfNetwork, initializer, "VL14_0", 1.226377, 0);
    }

    @Test
    void testZeroImpedanceLoop() {
        Network network = Importers.importData("XIIDM", new ResourceDataSource("init_v_zero_imp_loop", new ResourceSet("/", "init_v_zero_imp_loop.xiidm")), null);
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        VoltageMagnitudeInitializer initializer = new VoltageMagnitudeInitializer(false, new DenseMatrixFactory(), LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "B_0", 0.982318, 0);
        assertBusVoltage(lfNetwork, initializer, "D_0", 0.982318, 0);
        assertBusVoltage(lfNetwork, initializer, "F_0", 0.982315, 0);
        assertBusVoltage(lfNetwork, initializer, "H_0", 0.975, 0);
        assertBusVoltage(lfNetwork, initializer, "K_0", 0.953125, 0);
        assertBusVoltage(lfNetwork, initializer, "N_0", 0.97817, 0);
        assertBusVoltage(lfNetwork, initializer, "P_0", 0.982318, 0);
        assertBusVoltage(lfNetwork, initializer, "R_0", 0.982318, 0);
    }

    @Test
    void testWithTransformerVoltageControl() {
        Network network = IeeeCdfNetworkFactory.create14();
        var twt49 = network.getTwoWindingsTransformer("T4-9-1");
        twt49.newRatioTapChanger()
                .beginStep()
                    .setRho(1)
                .endStep()
                .setTapPosition(0)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(true)
                .setTargetV(1.1 * twt49.getTerminal2().getVoltageLevel().getNominalV())
                .setTargetDeadband(0)
                .setRegulationTerminal(twt49.getTerminal2())
                .add();
        LfNetworkParameters networkParameters = new LfNetworkParameters()
                .setTransformerVoltageControl(true);
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), networkParameters).get(0);
        VoltageMagnitudeInitializer initializer = new VoltageMagnitudeInitializer(true, new DenseMatrixFactory(), LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE);
        initializer.prepare(lfNetwork);
        assertBusVoltage(lfNetwork, initializer, "VL1_0", 1.06, 0);
        assertBusVoltage(lfNetwork, initializer, "VL2_0", 1.045, 0);
        assertBusVoltage(lfNetwork, initializer, "VL3_0", 1.01, 0);
        assertBusVoltage(lfNetwork, initializer, "VL4_0", 1.050687, 0);
        assertBusVoltage(lfNetwork, initializer, "VL5_0", 1.045337, 0);
        assertBusVoltage(lfNetwork, initializer, "VL6_0", 1.07, 0);
        assertBusVoltage(lfNetwork, initializer, "VL7_0", 1.153402, 0);
        assertBusVoltage(lfNetwork, initializer, "VL8_0", 1.09, 0);
        assertBusVoltage(lfNetwork, initializer, "VL9_0", 1.1, 0); // this is tha transformer voltage control target!
        assertBusVoltage(lfNetwork, initializer, "VL10_0", 1.094668, 0);
        assertBusVoltage(lfNetwork, initializer, "VL11_0", 1.082549, 0);
        assertBusVoltage(lfNetwork, initializer, "VL12_0", 1.07237, 0);
        assertBusVoltage(lfNetwork, initializer, "VL13_0", 1.074222, 0);
        assertBusVoltage(lfNetwork, initializer, "VL14_0", 1.088729, 0);
    }
}
