/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.acdc;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ServiceParameterResolver;
import com.powsybl.openloadflow.network.AcDcNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static java.lang.Math.abs;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Baptiste Perreyon {@literal <bapstiste.perreyon at supergrid-institute.com>}
 */
@ExtendWith(ServiceParameterResolver.class)
class AcDcLoadFlowWithDisconnectionTest {

    private final CommonTestConfig commonTestConfig;

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

    /**
     * Verify the DC node voltage is coherent with the nominal voltage
     *
     * @param dcNode the DC node which voltage is verified
     */
    void assertVoltageRealistic(DcNode dcNode) {
        // Tolerance is the nominal voltage itself. So this includes real voltage equal to zero or to half of the
        // nominal voltage which are quite common values. But unrealistic values will fail.
        assertEquals(dcNode.getNominalV(), abs(dcNode.getV()), dcNode.getNominalV());
    }

    /*
     * DC line disconnection
     */

    static Stream<Arguments> sidesToDisconnectAndExpectedCurrent() {
        return Stream.of(
            Arguments.of(List.of(TwoSides.ONE), 0.),
            Arguments.of(List.of(TwoSides.TWO), 0.),
            Arguments.of(List.of(TwoSides.ONE, TwoSides.TWO), Double.NaN)
        );
    }

    /**
     * Check the DC current of the disconnected DC line and impacted elements is zero. Checks that the DC voltage are
     * in a realistic range.
     *
     * @param network                       The network with load flow result
     * @param disconnectedDcLineId          id of the disconnected DC line. Its current should be zero (partially disconnected)
     *                                      or NaN (if fully disconnected)
     * @param expectedCurrent               Expected current in the disconnected DC line. Should be zero or NaN (see above).
     * @param otherLineIdsWithZeroCurrent   Ids of other DC lines which current should be zero.
     * @param convertersIdWithZeroDcCurrent Ids of converter which DC current should be zero. Their absorbed AC power
     *                                      should correspond to their idle loss.
     */
    void checkResult(Network network, String disconnectedDcLineId, double expectedCurrent, List<String> otherLineIdsWithZeroCurrent, List<String> convertersIdWithZeroDcCurrent) {
        // We must use a 1e-3 tolerance because Java considers 0 and -0 different values otherwise

        // Verify the disconnected DC line has the expected urrent (0 or NaN)
        assertEquals(expectedCurrent, network.getDcLine(disconnectedDcLineId).getDcTerminal1().getI(), 1e-3);
        assertEquals(expectedCurrent, network.getDcLine(disconnectedDcLineId).getDcTerminal2().getI(), 1e-3);

        // Verify other DC lines which current should be zero due to the disconnection
        for (String dcLineId : otherLineIdsWithZeroCurrent) {
            assertEquals(0., network.getDcLine(dcLineId).getDcTerminal1().getI(), 1e-3);
            assertEquals(0., network.getDcLine(dcLineId).getDcTerminal2().getI(), 1e-3);
        }

        // Verify converters have no DC current and AC power simply matches idle losses
        for (String converterId : convertersIdWithZeroDcCurrent) {
            VoltageSourceConverter converter = network.getVoltageSourceConverter(converterId);
            assertEquals(0., converter.getDcTerminal1().getI(), 1e-3);
            assertEquals(0., converter.getDcTerminal2().getI(), 1e-3);
            assertEquals(converter.getIdleLoss(), converter.getTerminal1().getP(), 1e-3);
        }

        // Verify DC nodes voltage is realistic
        network.getDcNodes().forEach(this::assertVoltageRealistic);
    }

    @ParameterizedTest
    @MethodSource("sidesToDisconnectAndExpectedCurrent")
    void testDcLineDisconnectionAsymmetricalMonopole(List<TwoSides> sidesToDisconnect, double expectedCurrent) {
        /// Removing the DC line creates two separated DC components. It is expected that :
        /// - The DC current in the line is zero
        /// - The only power that goes into VSC is to compensate their idle losses
        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        network.getDcLine("dl34").getDcTerminals().stream()
            .filter(dcTerminal -> sidesToDisconnect.contains(dcTerminal.getSide()))
            .forEach(DcTerminal::disconnect);
        // We also update the control mode of the VSC since it is in a different DC component now, and this component
        // requires a converter in V_DC mode
        network.getVoltageSourceConverter("conv23").setTargetVdc(400).setControlMode(AcDcConverter.ControlMode.V_DC);

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        checkResult(network, "dl34", expectedCurrent, List.of(), List.of("conv23", "conv45"));
    }

    @ParameterizedTest
    @MethodSource("sidesToDisconnectAndExpectedCurrent")
    void testDcLineDisconnectionRigidBipole(List<TwoSides> sidesToDisconnect, double expectedCurrent) {
        /// Removing the DC line keeps one DC component because there is another DC line in parallel.
        /// It is expected that no current flows into the disconnected line and in the two converters connected to this
        /// line
        Network network = AcDcNetworkFactory.createBipolarModelWithoutMetallicReturn();
        network.getDcLine("dl34p").getDcTerminals().stream()
            .filter(dcTerminal -> sidesToDisconnect.contains(dcTerminal.getSide()))
            .forEach(DcTerminal::disconnect);

        // We also need to change conv23p control mode, otherwise the DC voltage of dn3p would not be imposed.
        network.getVoltageSourceConverter("conv23p").setTargetVdc(200).setControlMode(AcDcConverter.ControlMode.V_DC);

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        checkResult(network, "dl34p", expectedCurrent, List.of(), List.of("conv23p", "conv45p"));

        // Check current flows in the connected DC line
        assertNotEquals(0., network.getDcLine("dl34n").getDcTerminal1().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl34n").getDcTerminal2().getI(), 1e-3);
    }

    @ParameterizedTest
    @MethodSource("sidesToDisconnectAndExpectedCurrent")
    void testDcLineDisconnectionBipoleWithMetallicReturn(List<TwoSides> sidesToDisconnect, double expectedCurrent) {
        /// Removing the DC line keeps one DC component because there are other DC lines in parallel.
        /// It is expected that no current flows into the disconnected line and in the two converters connected to this
        /// line
        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModel();
        network.getDcLine("dl34p").getDcTerminals().stream()
            .filter(dcTerminal -> sidesToDisconnect.contains(dcTerminal.getSide()))
            .forEach(DcTerminal::disconnect);

        // We also need to change conv23p control mode, otherwise the DC voltage of dn3p would not be imposed.
        network.getVoltageSourceConverter("conv23p").setTargetVdc(200).setControlMode(AcDcConverter.ControlMode.V_DC);

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        checkResult(network, "dl34p", expectedCurrent, List.of(), List.of("conv23p", "conv45p"));

        // Check current flows in the other DC lines
        assertNotEquals(0., network.getDcLine("dl3Gr").getDcTerminal1().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl3Gr").getDcTerminal2().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dlG4r").getDcTerminal1().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dlG4r").getDcTerminal2().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl34n").getDcTerminal1().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl34n").getDcTerminal2().getI(), 1e-3);
    }

    @ParameterizedTest
    @MethodSource("sidesToDisconnectAndExpectedCurrent")
    void testDcLineDisconnectionBipoleWithMetallicReturnDoubleConverter(List<TwoSides> sidesToDisconnect, double expectedCurrent) {
        /// Removing the DC line keeps one DC component because there are other DC lines in parallel.
        /// It is expected that no current flows into the disconnected line.
        Network network = AcDcNetworkFactory.createFourConvertersBipole("test", false, false, false, false);
        network.getDcLine("dl14").getDcTerminals().stream()
            .filter(dcTerminal -> sidesToDisconnect.contains(dcTerminal.getSide()))
            .forEach(DcTerminal::disconnect);

        // We also need to change conv5 control mode, otherwise the DC voltage of DC7 would not be imposed.
        network.getVoltageSourceConverter("conv5").setTargetVdc(500).setControlMode(AcDcConverter.ControlMode.V_DC);

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        // conv1 and conv3 both connect DC1 and DC2. Thus, current can flow inside them. Same thing goes for conv5 and conv7
        // In a realistic use case, however, the target power of these converters would typically be set to zero so that
        // no current flows between the DC buses.
        checkResult(network, "dl14", expectedCurrent, List.of("dl47"), List.of());

        // Check current in the other DC lines
        assertNotEquals(0., network.getDcLine("dl25").getDcTerminal1().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl25").getDcTerminal2().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl36").getDcTerminal1().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl36").getDcTerminal2().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl58").getDcTerminal1().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl58").getDcTerminal2().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl69").getDcTerminal1().getI(), 1e-3);
        assertNotEquals(0., network.getDcLine("dl69").getDcTerminal2().getI(), 1e-3);
    }

    @Test
    void testDoubleDcLineDisconnectionRigidBipole() {
        /// Removing the two DC lines creates two DC components. Similarly to testDcLineDisconnectionAsymmetricalMonopole(), it is expected that:
        /// - The DC current in the DC lines is zero
        /// - The only power that goes into VSC is to compensate their idle losses
        Network network = AcDcNetworkFactory.createBipolarModelWithoutMetallicReturn();
        network.getDcLine("dl34p").getDcTerminal1().disconnect();
        network.getDcLine("dl34n").getDcTerminal1().disconnect();

        // Add idle losses to converters and change their control mode to ensure DC buses voltage is imposed
        network.getVoltageSourceConverters().forEach(vsc -> vsc.setIdleLoss(0.5).setTargetVdc(400).setControlMode(AcDcConverter.ControlMode.V_DC));

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        // Check the current in the disconnected DC lines
        assertEquals(0., network.getDcLine("dl34p").getDcTerminal1().getI(), 1e-3);
        assertEquals(0., network.getDcLine("dl34p").getDcTerminal2().getI(), 1e-3);
        assertEquals(0., network.getDcLine("dl34n").getDcTerminal1().getI(), 1e-3);
        assertEquals(0., network.getDcLine("dl34n").getDcTerminal2().getI(), 1e-3);

        // Check power in VSC associated to the disconnected line
        VoltageSourceConverter conv23p = network.getVoltageSourceConverter("conv23p");
        VoltageSourceConverter conv45p = network.getVoltageSourceConverter("conv45p");
        VoltageSourceConverter conv23n = network.getVoltageSourceConverter("conv23n");
        VoltageSourceConverter conv45n = network.getVoltageSourceConverter("conv45n");
        assertEquals(conv23p.getIdleLoss(), conv23p.getTerminal1().getP(), 1e-3);
        assertEquals(conv45p.getIdleLoss(), conv45p.getTerminal1().getP(), 1e-3);
        assertEquals(conv23n.getIdleLoss(), conv23n.getTerminal1().getP(), 1e-3);
        assertEquals(conv45n.getIdleLoss(), conv45n.getTerminal1().getP(), 1e-3);

        // Check voltages are in a realistic range
        network.getDcNodes().forEach(this::assertVoltageRealistic);
    }

    /*
     * AC-DC converter disconnection
     */
    @Test
    void testAcDcConverterDisconnectionInAsymmetricalPointToPointConnection() {
        /// When disconnecting the converter, the current in the DC line should be null

        Network network = AcDcNetworkFactory.createAcDcNetwork1();
        // -- Disconnection on AC side --
        network.getVoltageSourceConverter("conv23").disconnect();

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        // Check current is 0 in the DC system
        assertEquals(0., network.getDcLine("dl34").getDcTerminal1().getI());

        // -- Disconnection on DC side --
        network.getVoltageSourceConverter("conv23").connect();
        network.getVoltageSourceConverter("conv23").disconnectDc();

        // Run load flow
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isFullyConverged());

        // Check current is 0 in the DC system
        assertEquals(0., network.getDcLine("dl34").getDcTerminal1().getI());
    }

    @Test
    void testAcDcConverterDisconnectionInBipolarWithDMRPointToPointConnection() {
        /// When disconnecting the converter, the current in the DC line at the disconnected pole (positive) should be
        /// null, but the current should still pass through the negative and neutral pole, due to the second converter.

        Network network = AcDcNetworkFactory.createAcDcNetworkBipolarModel();
        // -- Disconnection on AC side --
        network.getVoltageSourceConverter("conv23p").disconnect();

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        // Check current is 0 in the positive pole
        assertEquals(0., network.getDcLine("dl34p").getDcTerminal1().getI());
        // Check the current still flows in the negative and neutral pole
        assertNotEquals(0., network.getDcLine("dl34n").getDcTerminal1().getI());
        assertNotEquals(0., network.getDcLine("dl3Gr").getDcTerminal1().getI());
        assertNotEquals(0., network.getDcLine("dlG4r").getDcTerminal1().getI());

        // -- Disconnection on DC side --
        network.getVoltageSourceConverter("conv23p").connect();
        network.getVoltageSourceConverter("conv23p").disconnectDc();

        // Run load flow
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isFullyConverged());

        // Check current is 0 in the positive pole
        assertEquals(0., network.getDcLine("dl34p").getDcTerminal1().getI());
        // Check the current still flows in the negative and neutral pole
        assertNotEquals(0., network.getDcLine("dl34n").getDcTerminal1().getI());
        assertNotEquals(0., network.getDcLine("dl3Gr").getDcTerminal1().getI());
        assertNotEquals(0., network.getDcLine("dlG4r").getDcTerminal1().getI());
    }

    @Test
    void testAcDcConverterDisconnectionInRigidBipolarPointToPointConnection() {
        /// When disconnecting the converter, the current in the DC line at the disconnected pole (positive) should be
        /// null, but the current should still pass through the negative, due to the second converter.

        Network network = AcDcNetworkFactory.createBipolarModelWithoutMetallicReturn();
        // -- Disconnection on AC side --
        network.getVoltageSourceConverter("conv23p").disconnect();

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        // Check current is 0 in the positive pole
        assertEquals(0., network.getDcLine("dl34p").getDcTerminal1().getI());
        // Check the current still flows in the negative pole
        assertNotEquals(0., network.getDcLine("dl34n").getDcTerminal1().getI());

        // -- Disconnection on DC side --
        network.getVoltageSourceConverter("conv23p").connect();
        network.getVoltageSourceConverter("conv23p").disconnectDc();

        // Run load flow
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isFullyConverged());

        // Check current is 0 in the positive pole
        assertEquals(0., network.getDcLine("dl34p").getDcTerminal1().getI());
        // Check the current still flows in the negative pole
        assertNotEquals(0., network.getDcLine("dl34n").getDcTerminal1().getI());
    }

    @Test
    void testAcDcConverterDisconnectionLeadsInMtDcNetwork() {
        /// When disconnecting the converter, the current in the DC line next to the converter should be null, but
        /// the current should still pass through other DC lines.

        Network network = AcDcNetworkFactory.createAcDcNetworkWithThreeConverters();
        // -- Disconnection on AC side --
        network.getVoltageSourceConverter("conv45").disconnect();

        // Run load flow
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        // Check current is 0 in the DC line connected to the converter
        assertEquals(0., network.getDcLine("dl4").getDcTerminal1().getI());
        // Check the current still flows in the rest of the DC system
        assertNotEquals(0., network.getDcLine("dl3").getDcTerminal1().getI());
        assertNotEquals(0., network.getDcLine("dl7").getDcTerminal1().getI());

        // -- Disconnection on DC side --
        network.getVoltageSourceConverter("conv45").connect();
        network.getVoltageSourceConverter("conv45").disconnectDc();

        // Run load flow
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isFullyConverged());

        // Check current is 0 in the DC line connected to the converter
        assertEquals(0., network.getDcLine("dl4").getDcTerminal1().getI());
        // Check the current still flows in the rest of the DC system
        assertNotEquals(0., network.getDcLine("dl3").getDcTerminal1().getI());
        assertNotEquals(0., network.getDcLine("dl7").getDcTerminal1().getI());
    }

    // TODO: add test if the disconnected converter causes to have no V_DC converter / a P_PCC converter without
    //  connection to voltage reference
}
