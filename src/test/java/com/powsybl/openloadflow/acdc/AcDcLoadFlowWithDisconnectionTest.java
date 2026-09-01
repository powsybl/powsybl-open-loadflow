/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.acdc;

import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageSourceConverter;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ServiceParameterResolver;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonStoppingCriteria;
import com.powsybl.openloadflow.network.AcDcNetworkFactory;
import com.powsybl.openloadflow.util.PerUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
@ExtendWith(ServiceParameterResolver.class)
class AcDcLoadFlowWithDisconnectionTest {

    private final CommonTestConfig commonTestConfig;

    private final double tol = NewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ * PerUnit.SB;  // 10^-2

    AcDcLoadFlowWithDisconnectionTest(CommonTestConfig commonTestConfig) {
        this.commonTestConfig = commonTestConfig;
    }

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(commonTestConfig.matrixFactory()));
        parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters).setAcDcNetwork(true);
    }

    /*
     * DC ground disconnection
     */
    @Test
    void testConverterNotIndirectlyConnectedToDcGround() {
        /// conv23's DC ground (dg3) is disconnected. Neither of its DC terminals is indirectly connected to a DC ground
        /// anymore. This should trigger an Exception
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        network.getDcGround("dg3").disconnectDc();

        CompletionException e = assertThrows(CompletionException.class, () -> loadFlowRunner.run(network, parameters));
        assertEquals("Converter conv23 is not indirectly connected to a DC ground", e.getCause().getMessage());
    }

    /*
     * DC line disconnection
     */

    @Test
    void dcLineDisconnection() {
        /// The studied network has two parallel DC lines with the same resistance. Therefore, the current passing through
        /// it is half the one of the converters. When disconnecting a terminal of one of the DC line, its current drops
        /// to zero and the current of the other DC line is now equal to the current in the converters. When
        /// disconnecting both terminals of the DC line, it is not even included in the load flow.
        Network network = AcDcNetworkFactory.createAcDcNetworkTwoParallelDcLines();

        // Run load flow on the complete network
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        double dcLineR = network.getDcLine("dl34").getR(); // 0.1 Ohm
        double expectedDcCurrent = 125; // 50 MW/400kV = 125A
        // Check DC current
        assertEquals(-expectedDcCurrent, network.getVoltageSourceConverter("conv23").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getVoltageSourceConverter("conv45").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent / 2, network.getDcLine("dl34").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent / 2, network.getDcLine("dl34_bis").getDcTerminal1().getI(), tol);
        // Check DC voltage, taking into account the voltage rise due to the equivalent resistance of the two DC lines
        assertEquals(400 + expectedDcCurrent / 1000 * dcLineR / 2, network.getDcNode("dn3").getV(), tol);
        assertEquals(400, network.getDcNode("dn4").getV());

        // Disconnect a DC line and run load flow. The current in the disconnected DC line should be zero, and doubled in the other DC line
        network.getDcLine("dl34_bis").getDcTerminal1().disconnect();  // Disconnect one terminal
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isFullyConverged());
        // Check DC current
        assertEquals(-expectedDcCurrent, network.getVoltageSourceConverter("conv23").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getVoltageSourceConverter("conv45").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getDcLine("dl34").getDcTerminal1().getI(), tol);
        assertEquals(0., network.getDcLine("dl34_bis").getDcTerminal1().getI(), 0);
        // Check DC voltage, taking into account the voltage rise due to resistance of ONE DC line
        assertEquals(400 + expectedDcCurrent / 1000 * dcLineR, network.getDcNode("dn3").getV(), tol);
        assertEquals(400, network.getDcNode("dn4").getV());

        // Disconnect fully the DC line and run load flow. The disconnected DC line should not appear in the load flow so its state variable should be NaN.
        // The current in the second DC line should be doubled compared to the first case (without any disconnection)
        network.getDcLine("dl34_bis").disconnectDc();  // Disconnect both terminals
        LoadFlowResult result3 = loadFlowRunner.run(network, parameters);
        assertTrue(result3.isFullyConverged());
        // Check DC current
        assertEquals(-expectedDcCurrent, network.getVoltageSourceConverter("conv23").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getVoltageSourceConverter("conv45").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getDcLine("dl34").getDcTerminal1().getI(), tol);
        assertEquals(Double.NaN, network.getDcLine("dl34_bis").getDcTerminal1().getI());
        // Check DC voltage, taking into account the voltage rise due to resistance of ONE DC line
        assertEquals(400 + expectedDcCurrent / 1000 * dcLineR, network.getDcNode("dn3").getV(), tol);
        assertEquals(400, network.getDcNode("dn4").getV());
    }

    @Test
    void dcLineDisconnectionLeadsToZeroCurrentInConverter() {
        /// In this test case, removing a DC line isolates the positive pole of conv23p. Therefore, no DC current pass
        /// into it and its only consumption on AC side corresponds to its idle loss. Additionally, Open Load Flow
        /// automatically sets it in V_DC mode to control dn3p voltage.
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModel();
        network.getDcLine("dl34p").disconnectDc();

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        // Check DC current is NaN in dl34p and zero in conv23p and conv45p. Check converter AC power is equal to their idle loss
        VoltageSourceConverter conv23p = network.getVoltageSourceConverter("conv23p");
        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        assertEquals(Double.NaN, network.getDcLine("dl34p").getDcTerminal1().getI());
        assertEquals(0, conv23p.getDcTerminal1().getI(), 0);
        assertEquals(0, conv45p.getDcTerminal1().getI(), 0);
        assertEquals(conv23p.getIdleLoss(), conv23p.getTerminal1().getP(), tol);  // No other losses than idle loss
        assertEquals(conv45p.getIdleLoss(), conv45p.getTerminal1().getP(), tol);  // No other losses than idle loss
        // The automatic promotion of conv23p to V_DC mode is purely internal: conv23p's IIDM control mode is left untouched
        assertEquals(AcDcConverter.ControlMode.P_PCC, conv23p.getControlMode());

        // Check DC current in the rest of the network
        double dcLineR = network.getDcLine("dl34n").getR(); // 0.1 Ohm. Same value everywhere
        double expectedDcCurrent = 121.8;  // (25MW - losses) / 200 kV
        assertEquals(-expectedDcCurrent, network.getVoltageSourceConverter("conv23n").getDcTerminal1().getI(), tol);
        assertEquals(-expectedDcCurrent, network.getVoltageSourceConverter("conv45n").getDcTerminal1().getI(), tol);
        assertEquals(-expectedDcCurrent, network.getDcLine("dl34n").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getDcLine("dl3Gr").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getDcLine("dlG4r").getDcTerminal1().getI(), tol);

        // Check DC node voltage, taking into account voltage drop due to DC line resistance
        assertEquals(0, network.getDcNode("dnGr").getV(), 0);
        assertEquals(-expectedDcCurrent / 1000 * dcLineR, network.getDcNode("dn4r").getV(), tol);
        assertEquals(-expectedDcCurrent / 1000 * dcLineR + 200, network.getDcNode("dn4p").getV(), tol);
        assertEquals(-expectedDcCurrent / 1000 * dcLineR - 200, network.getDcNode("dn4n").getV(), tol);
        assertEquals(-expectedDcCurrent / 1000 * dcLineR * 2 - 200, network.getDcNode("dn3n").getV(), tol);
        assertEquals(expectedDcCurrent / 1000 * dcLineR, network.getDcNode("dn3r").getV(), tol);
        // dn3p's own voltage is a gauge freedom (its island carries no current regardless of the value assigned to
        // it): the automatic promotion uses the DC component's nominal voltage (400 kV) as the target Vdc
        assertEquals(expectedDcCurrent / 1000 * dcLineR + 400, network.getDcNode("dn3p").getV(), tol);
    }

    /*
     * AC-DC converter disconnection
     */
    @Test
    void testAcDcConverterDisconnectionLeadsToZeroCurrentInConverterAndNearbyIsolatedDcLines() {
        /// When disconnection a converter, either on AC or DC side, no DC current pass through it.
        /// On a simple bipolar point-to-point connection, it implies that no current pass through one of the layer (e.g. the positive layer)

        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModel();
        // -- Disconnection on AC side --
        network.getVoltageSourceConverter("conv23p").disconnect();

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        // Check current is NaN in the disconnected converter and 0 in the positive pole
        assertEquals(Double.NaN, network.getVoltageSourceConverter("conv23p").getDcTerminal1().getI());
        assertEquals(0., network.getDcLine("dl34p").getDcTerminal1().getI());
        // Check the current still flows in the negative and neutral pole
        double expectedDcCurrent = 121.8;  // (25MW - losses) / 200 kV
        assertEquals(-expectedDcCurrent, network.getVoltageSourceConverter("conv23n").getDcTerminal1().getI(), tol);
        assertEquals(-expectedDcCurrent, network.getVoltageSourceConverter("conv45n").getDcTerminal1().getI(), tol);
        assertEquals(-expectedDcCurrent, network.getDcLine("dl34n").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getDcLine("dl3Gr").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getDcLine("dlG4r").getDcTerminal1().getI(), tol);

        // -- Disconnection on DC side --
        network.getVoltageSourceConverter("conv23p").connect();
        network.getVoltageSourceConverter("conv23p").disconnectDc();

        // Run load flow
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isFullyConverged());

        // Check current is NaN in the disconnected converter and 0 in the positive pole
        assertEquals(Double.NaN, network.getVoltageSourceConverter("conv23p").getDcTerminal1().getI());
        assertEquals(0., network.getDcLine("dl34p").getDcTerminal1().getI());
        // Check the current still flows in the negative and neutral pole
        assertEquals(-expectedDcCurrent, network.getVoltageSourceConverter("conv23n").getDcTerminal1().getI(), tol);
        assertEquals(-expectedDcCurrent, network.getVoltageSourceConverter("conv45n").getDcTerminal1().getI(), tol);
        assertEquals(-expectedDcCurrent, network.getDcLine("dl34n").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getDcLine("dl3Gr").getDcTerminal1().getI(), tol);
        assertEquals(expectedDcCurrent, network.getDcLine("dlG4r").getDcTerminal1().getI(), tol);
    }
}
