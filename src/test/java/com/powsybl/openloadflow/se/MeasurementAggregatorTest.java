/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.powsybl.commons.PowsyblException;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.extensions.Measurement;
import com.powsybl.iidm.network.extensions.Measurements;
import com.powsybl.iidm.network.extensions.MeasurementsAdder;
import com.powsybl.iidm.network.test.BoundaryLineNetworkFactory;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.SvcTestCaseFactory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.PerUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
class MeasurementAggregatorTest {

    private Network network;

    private LfNetwork lfNetwork;

    @BeforeEach
    void setUp() {
        network = IeeeCdfNetworkFactory.create14();
        lfNetwork = Networks.load(network, new LfNetworkParameters()).get(0);
    }

    private static void meter(Connectable<?> connectable, String id, Measurement.Type type,
                              ThreeSides side, double value, double standardDeviation) {
        Measurements<?> extension = (Measurements<?>) connectable.getExtensionByName(Measurements.NAME);
        if (extension == null) {
            connectable.newExtension(MeasurementsAdder.class).add();
            extension = (Measurements<?>) connectable.getExtensionByName(Measurements.NAME);
        }
        extension.newMeasurement()
                .setId(id)
                .setType(type)
                .setSide(side)
                .setValue(value)
                .setStandardDeviation(standardDeviation)
                .setValid(true)
                .add();
    }

    private List<LfMeasurement> aggregate(MeasurementAggregation settings) {
        return new MeasurementAggregator(lfNetwork, settings).aggregate(network);
    }

    private static Optional<LfMeasurement> of(
            List<LfMeasurement> readings, AcEquationType type) {
        return readings.stream().filter(m -> m.type() == type).findFirst();
    }

    @Test
    void readsNothingFromANetworkWithNoMeters() {
        assertTrue(aggregate(new MeasurementAggregation()
                .setZeroInjectionMode(MeasurementAggregation.ZeroInjectionMode.IGNORED)).isEmpty());
    }

    @Test
    void aVoltageReadingIsPerUnitOfTheNominalVoltage() {
        var load = network.getLoad("B2-L");
        double nominalV = load.getTerminal().getVoltageLevel().getNominalV();
        meter(load, "v", Measurement.Type.VOLTAGE, null, 1.03 * nominalV, 0.01 * nominalV);

        var reading = of(aggregate(new MeasurementAggregation().setBusInjectionPolicy(
                MeasurementAggregation.BusInjectionPolicy.SUM_METERED)), AcEquationType.BUS_TARGET_V).orElseThrow();

        assertEquals(1.03, reading.valuePu(), 1e-12);
        assertEquals(0.01, reading.sigmaPu(), 1e-12);
    }

    @Test
    void aBusInjectionFlipsTheTerminalSignToGenerationPositive() {
        // IIDM terminal convention: a load consuming 21.7 MW reads +21.7 at its terminal
        var load = network.getLoad("B2-L");
        meter(load, "p", Measurement.Type.ACTIVE_POWER, null, 21.7, 0.5);

        var reading = of(aggregate(new MeasurementAggregation().setBusInjectionPolicy(
                MeasurementAggregation.BusInjectionPolicy.SUM_METERED)), AcEquationType.BUS_TARGET_P).orElseThrow();

        assertEquals(-21.7 / PerUnit.SB, reading.valuePu(), 1e-12,
                "consumption at a terminal is a negative injection into the network");
        assertEquals(0.5 / PerUnit.SB, reading.sigmaPu(), 1e-12);
    }

    @Test
    void devicesOnOneBusSumAndTheirVariancesAddUp() {
        // bus VL2 carries both a load and a generator in the IEEE 14-bus case
        var load = network.getLoad("B2-L");
        var generator = network.getGenerator("B2-G");
        meter(load, "p1", Measurement.Type.ACTIVE_POWER, null, 21.7, 0.3);
        meter(generator, "p2", Measurement.Type.ACTIVE_POWER, null, -40.0, 0.4);

        var reading = of(aggregate(new MeasurementAggregation()), AcEquationType.BUS_TARGET_P).orElseThrow();

        assertEquals(-(21.7 - 40.0) / PerUnit.SB, reading.valuePu(), 1e-12);
        assertEquals(Math.hypot(0.3, 0.4) / PerUnit.SB, reading.sigmaPu(), 1e-12,
                "independent readings sum in variance, so sigma is the root of the sum of squares");
    }

    @Test
    void requireAllMeteredFormsNoInjectionWhenADeviceHasNoMeter() {
        // only the load is metered; the generator on the same bus is not
        meter(network.getLoad("B2-L"), "p1", Measurement.Type.ACTIVE_POWER, null, 21.7, 0.3);

        assertTrue(of(aggregate(new MeasurementAggregation()
                .setZeroInjectionMode(MeasurementAggregation.ZeroInjectionMode.IGNORED)), AcEquationType.BUS_TARGET_P).isEmpty());

        assertTrue(of(aggregate(new MeasurementAggregation()
                        .setBusInjectionPolicy(MeasurementAggregation.BusInjectionPolicy.SUM_METERED)
                        .setZeroInjectionMode(MeasurementAggregation.ZeroInjectionMode.IGNORED)), AcEquationType.BUS_TARGET_P).isPresent(),
                "summing the metered devices is the other policy, and it does form one");
    }

    @Test
    void aShuntReadingIsIgnoredBecauseTheModelAlreadyCarriesIt() {
        // bus VL9 carries a load and a shunt compensator
        meter(network.getShuntCompensator("B9-SH"), "q", Measurement.Type.REACTIVE_POWER, null, -19.0, 0.5);

        assertTrue(of(aggregate(new MeasurementAggregation()
                        .setBusInjectionPolicy(MeasurementAggregation.BusInjectionPolicy.SUM_METERED)
                        .setZeroInjectionMode(MeasurementAggregation.ZeroInjectionMode.IGNORED)), AcEquationType.BUS_TARGET_Q).isEmpty(),
                "a shunt sits in the network admittance, so its reading would count it twice");
    }

    @Test
    void aShuntDoesNotCountAsADeviceAwaitingAMeter() {
        // VL9 holds one real device, the load, plus a shunt that the model already carries
        meter(network.getLoad("B9-L"), "q", Measurement.Type.REACTIVE_POWER, null, 16.6, 0.4);

        var reading = of(aggregate(new MeasurementAggregation()), AcEquationType.BUS_TARGET_Q).orElseThrow();

        assertEquals(-16.6 / PerUnit.SB, reading.valuePu(), 1e-12,
                "requiring every device to be metered must not wait for a meter on the shunt");
    }

    @Test
    void aStaticVarCompensatorIsADeviceLikeAGenerator() {
        // LfNetwork carries a static var compensator as a generator, not in the network admittance
        Network svcNetwork = SvcTestCaseFactory.create();
        meter(svcNetwork.getLoad("L2"), "load", Measurement.Type.REACTIVE_POWER, null, 50, 0.5);
        meter(svcNetwork.getStaticVarCompensator("SVC2"), "svc", Measurement.Type.REACTIVE_POWER, null, -38.9, 0.5);

        var reading = of(aggregate(svcNetwork), AcEquationType.BUS_TARGET_Q).orElseThrow();

        assertEquals(-(50 - 38.9) / PerUnit.SB, reading.valuePu(), 1e-12);
        assertEquals(2, reading.sourceMeasurementIds().size());
    }

    @Test
    void aBusHoldingOnlyAStaticVarCompensatorIsNotAZeroInjection() {
        Network svcNetwork = SvcTestCaseFactory.create();
        svcNetwork.getLoad("L2").remove();

        assertTrue(aggregate(svcNetwork).isEmpty(),
                "the compensator injects reactive power, so nothing at its bus is known to be zero");
    }

    @Test
    void aBoundaryLineReadingIsABranchFlow() {
        // an injection in IIDM, a branch in LfNetwork: the bus holds a generator and the boundary line
        Network boundaryNetwork = BoundaryLineNetworkFactory.create();
        meter(boundaryNetwork.getBoundaryLine("BL"), "flow", Measurement.Type.ACTIVE_POWER, null, 54.8, 0.5);
        meter(boundaryNetwork.getGenerators().iterator().next(), "generator", Measurement.Type.ACTIVE_POWER, null, -54.8, 0.5);

        List<LfMeasurement> readings = aggregate(boundaryNetwork);

        var flow = of(readings, AcEquationType.BRANCH_TARGET_P1).orElseThrow();
        assertEquals("BL", flow.element().getId());
        assertEquals(54.8 / PerUnit.SB, flow.valuePu(), 1e-12);
        var injection = of(readings, AcEquationType.BUS_TARGET_P).orElseThrow();
        assertEquals(54.8 / PerUnit.SB, injection.valuePu(), 1e-12,
                "the generator alone: the boundary line is neither summed in nor a device awaiting a meter");
    }

    @Test
    void aReadingOnHalfATieLineResolvesToTheTieLine() {
        Network tieLineNetwork = EurostagTutorialExample1Factory.createWithTieLine();
        meter(tieLineNetwork.getBoundaryLine("XNODE1_NHV2"), "flow", Measurement.Type.ACTIVE_POWER, null, -300, 0.5);

        var flow = of(aggregate(tieLineNetwork), AcEquationType.BRANCH_TARGET_P2).orElseThrow();

        assertEquals("NHV1_NHV2_1", flow.element().getId());
        assertEquals(-3, flow.valuePu(), 1e-12);
    }

    @Test
    void readingsOfALoadFlowLeadBackToItAcrossAStaticVarCompensator() {
        Network svcNetwork = SvcTestCaseFactory.create();
        meterWithLoadFlowResult(svcNetwork);

        WlsResult result = estimate(svcNetwork);

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus(), result.getStatusText());
        for (WlsResult.BusResult bus : result.getBusResults()) {
            assertEquals(svcNetwork.getBusView().getBus(bus.busId()).getV(), bus.v(), 0.4, "voltage at " + bus.busId());
        }
    }

    @Test
    void readingsOfALoadFlowLeadBackToItAcrossABoundaryLine() {
        Network boundaryNetwork = BoundaryLineNetworkFactory.create();
        meterWithLoadFlowResult(boundaryNetwork);

        WlsResult result = estimate(boundaryNetwork);

        assertEquals(WlsResult.Status.ESTIMATED, result.getStatus(), result.getStatusText());
        Terminal terminal = boundaryNetwork.getBoundaryLine("BL").getTerminal();
        WlsResult.BranchResult flow = result.getBranchResults().get(0);
        assertEquals("BL", flow.branchId());
        assertEquals(terminal.getP(), flow.p1(), 0.1);
        assertEquals(terminal.getQ(), flow.q1(), 0.1);
    }

    /** Puts a meter on every terminal reading what a load flow found there, so the readings agree with one another. */
    private static void meterWithLoadFlowResult(Network metered) {
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        assertTrue(runner.run(metered, new LoadFlowParameters()).isFullyConverged());
        int count = 0;
        for (Connectable<?> connectable : metered.getConnectables()) {
            List<? extends Terminal> terminals = connectable.getTerminals();
            for (int i = 0; i < terminals.size(); i++) {
                Terminal terminal = terminals.get(i);
                ThreeSides side = connectable instanceof Injection ? null : ThreeSides.valueOf(i + 1);
                double nominalV = terminal.getVoltageLevel().getNominalV();
                meter(connectable, "p" + count, Measurement.Type.ACTIVE_POWER, side, terminal.getP(), 0.5);
                meter(connectable, "q" + count, Measurement.Type.REACTIVE_POWER, side, terminal.getQ(), 0.5);
                meter(connectable, "v" + count, Measurement.Type.VOLTAGE, side, terminal.getBusView().getBus().getV(), 0.01 * nominalV);
                count++;
            }
        }
    }

    private static List<LfMeasurement> aggregate(Network other) {
        LfNetwork otherLfNetwork = Networks.load(other, new LfNetworkParameters()).get(0);
        return new MeasurementAggregator(otherLfNetwork, new MeasurementAggregation()).aggregate(other);
    }

    private static WlsResult estimate(Network other) {
        LfNetwork otherLfNetwork = Networks.load(other, new LfNetworkParameters()).get(0);
        List<LfMeasurement> readings = new MeasurementAggregator(otherLfNetwork, new MeasurementAggregation()).aggregate(other);
        return new WlsStateEstimator(otherLfNetwork, readings).estimate();
    }

    @Test
    void aBranchFlowKeepsItsSideAndIsPerUnitOnTheHundredMvaBase() {
        var line = network.getLine("L1-2-1");
        meter(line, "p", Measurement.Type.ACTIVE_POWER, ThreeSides.ONE, 156.9, 1.0);

        var reading = of(aggregate(new MeasurementAggregation()), AcEquationType.BRANCH_TARGET_P1).orElseThrow();

        assertEquals(156.9 / PerUnit.SB, reading.valuePu(), 1e-12);
        assertEquals(1.0 / PerUnit.SB, reading.sigmaPu(), 1e-12);
    }

    @Test
    void aCurrentReadingIsPerUnitOfTheBaseCurrentAtItsSide() {
        var line = network.getLine("L1-2-1");
        double nominalV = line.getTerminal1().getVoltageLevel().getNominalV();
        double amps = 400.0;
        meter(line, "i", Measurement.Type.CURRENT, ThreeSides.ONE, amps, 4.0);

        var reading = of(aggregate(new MeasurementAggregation()), AcEquationType.BRANCH_TARGET_I1).orElseThrow();

        assertEquals(amps / PerUnit.ib(nominalV), reading.valuePu(), 1e-12);
    }

    @Test
    void skipsWhatItCannotEstimate() {
        var load = network.getLoad("B2-L");
        meter(load, "invalid", Measurement.Type.ACTIVE_POWER, null, 21.7, 0.3);
        load.getExtension(Measurements.class).getMeasurement("invalid").setValid(false);

        meter(load, "noSigma", Measurement.Type.REACTIVE_POWER, null, 12.7, Double.NaN);
        meter(load, "frequency", Measurement.Type.FREQUENCY, null, 50.0, 0.01);
        meter(load, "angle", Measurement.Type.ANGLE, null, -5.0, 0.1);

        assertTrue(aggregate(new MeasurementAggregation()
                .setBusInjectionPolicy(MeasurementAggregation.BusInjectionPolicy.SUM_METERED)
                .setZeroInjectionMode(MeasurementAggregation.ZeroInjectionMode.IGNORED)).isEmpty());
    }

    @Test
    void theStandardDeviationFloorIsRelativeToTheReading() {
        var line = network.getLine("L1-2-1");
        meter(line, "p", Measurement.Type.ACTIVE_POWER, ThreeSides.ONE, 100.0, 0.01);

        var settings = new MeasurementAggregation().setRelativeStandardDeviationFloor(0.02);
        var reading = of(aggregate(settings), AcEquationType.BRANCH_TARGET_P1).orElseThrow();

        assertEquals(0.02 * (100.0 / PerUnit.SB), reading.sigmaPu(), 1e-12,
                "the declared 0.01 MW is below the 2% floor, so the floor applies");
        assertFalse(reading.sigmaPu() == 0.01 / PerUnit.SB);
    }

    @Test
    void aReadingOnAThreeWindingTransformerResolvesToTheLegItSitsOn() {
        // one connectable in IIDM, three branches in the LfNetwork
        Network twtNetwork = ThreeWindingsTransformerNetworkFactory.create();
        ThreeWindingsTransformer transformer = twtNetwork.getThreeWindingsTransformer("3WT");
        meter(transformer, "p", Measurement.Type.ACTIVE_POWER, ThreeSides.ONE, 10.0, 0.5);

        LfNetwork twtLfNetwork = Networks.load(twtNetwork, new LfNetworkParameters()).get(0);
        List<LfMeasurement> readings = new MeasurementAggregator(twtLfNetwork, new MeasurementAggregation()
                .setBusInjectionPolicy(MeasurementAggregation.BusInjectionPolicy.SUM_METERED))
                .aggregate(twtNetwork);

        LfMeasurement reading = of(readings, AcEquationType.BRANCH_TARGET_P1).orElseThrow();
        assertEquals("3WT_leg_1", reading.element().getId());
        assertEquals(10.0 / PerUnit.SB, reading.valuePu(), 1e-12);
    }

    @Test
    void aBusWithNoDeviceEntersAsAZeroInjectionPseudoMeasurement() {
        // IEEE 14 bus 7 carries no load and no generator, the textbook zero injection bus
        List<LfMeasurement> readings = aggregate(new MeasurementAggregation());

        assertEquals(2, readings.size(), "one active and one reactive pseudo-measurement");
        for (LfMeasurement reading : readings) {
            assertEquals("VL7_0", reading.element().getId());
            assertEquals(0.0, reading.valuePu(), 1e-12);
            assertEquals(0.001, reading.sigmaPu(), 1e-12);
        }
        assertTrue(of(readings, AcEquationType.BUS_TARGET_P).isPresent());
        assertTrue(of(readings, AcEquationType.BUS_TARGET_Q).isPresent());
    }

    @Test
    void aMeteredBusIsNeverTreatedAsAZeroInjection() {
        meter(network.getLoad("B2-L"), "p", Measurement.Type.ACTIVE_POWER, null, 21.7, 0.5);

        List<LfMeasurement> readings = aggregate(new MeasurementAggregation()
                .setBusInjectionPolicy(MeasurementAggregation.BusInjectionPolicy.SUM_METERED));

        assertTrue(readings.stream()
                .filter(m -> m.type() == AcEquationType.BUS_TARGET_P)
                .noneMatch(m -> m.element().getId().equals("VL2_0") && m.sigmaPu() == 0.001));
    }

    @Test
    void theZeroInjectionStandardDeviationIsConfigurable() {
        List<LfMeasurement> readings = aggregate(new MeasurementAggregation()
                .setZeroInjectionStandardDeviationPu(1e-5));

        assertEquals(1e-5, of(readings, AcEquationType.BUS_TARGET_P).orElseThrow().sigmaPu(), 1e-15);
    }

    @Test
    void twoMetersOnOneQuantityMergeByInverseVariance() {
        var load = network.getLoad("B2-L");
        double nominalV = load.getTerminal().getVoltageLevel().getNominalV();
        meter(load, "v1", Measurement.Type.VOLTAGE, null, 1.02 * nominalV, 0.01 * nominalV);
        meter(load, "v2", Measurement.Type.VOLTAGE, null, 1.04 * nominalV, 0.02 * nominalV);

        List<LfMeasurement> readings = aggregate(new MeasurementAggregation().setBusInjectionPolicy(
                MeasurementAggregation.BusInjectionPolicy.SUM_METERED));

        assertEquals(1, readings.stream().filter(m -> m.type() == AcEquationType.BUS_TARGET_V).count());
        var merged = of(readings, AcEquationType.BUS_TARGET_V).orElseThrow();
        // (10000 * 1.02 + 2500 * 1.04) / 12500, so the precise meter dominates
        assertEquals(1.024, merged.valuePu(), 1e-12);
        assertEquals(Math.sqrt(1 / 12500.0), merged.sigmaPu(), 1e-12);
        assertTrue(merged.sigmaPu() < 0.01, "merging two readings cannot widen the uncertainty");
    }

    @Test
    void voltmetersInDifferentBaysOfOneBusReadTheSameQuantity() {
        // a voltage measurement hangs off a device, and every bay of a bus sees the same voltage
        var load = network.getLoad("B2-L");
        var generator = network.getGenerator("B2-G");
        double nominalV = load.getTerminal().getVoltageLevel().getNominalV();
        meter(load, "v", Measurement.Type.VOLTAGE, null, 1.02 * nominalV, 0.01 * nominalV);
        meter(generator, "v", Measurement.Type.VOLTAGE, null, 1.04 * nominalV, 0.02 * nominalV);

        List<LfMeasurement> readings = aggregate(new MeasurementAggregation().setBusInjectionPolicy(
                MeasurementAggregation.BusInjectionPolicy.SUM_METERED));

        assertEquals(1, readings.stream().filter(m -> m.type() == AcEquationType.BUS_TARGET_V).count(),
                "two bays reporting one bus voltage are one measurement of it");
        assertEquals(1.024, of(readings, AcEquationType.BUS_TARGET_V).orElseThrow().valuePu(), 1e-12);
    }

    @Test
    void keepAllLetsEveryReadingContributeItsOwnRow() {
        var load = network.getLoad("B2-L");
        double nominalV = load.getTerminal().getVoltageLevel().getNominalV();
        meter(load, "v1", Measurement.Type.VOLTAGE, null, 1.02 * nominalV, 0.01 * nominalV);
        meter(load, "v2", Measurement.Type.VOLTAGE, null, 1.04 * nominalV, 0.02 * nominalV);

        List<LfMeasurement> readings = aggregate(new MeasurementAggregation()
                .setBusInjectionPolicy(MeasurementAggregation.BusInjectionPolicy.SUM_METERED)
                .setDuplicateMeasurementPolicy(
                        MeasurementAggregation.DuplicateMeasurementPolicy.KEEP_ALL));

        assertEquals(List.of(1.02, 1.04), readings.stream()
                .filter(m -> m.type() == AcEquationType.BUS_TARGET_V)
                .map(LfMeasurement::valuePu)
                .toList());
    }

    @Test
    void throwNamesTheQuantityAndThePlaceThatDuplicated() {
        var load = network.getLoad("B2-L");
        double nominalV = load.getTerminal().getVoltageLevel().getNominalV();
        meter(load, "v1", Measurement.Type.VOLTAGE, null, 1.02 * nominalV, 0.01 * nominalV);
        meter(load, "v2", Measurement.Type.VOLTAGE, null, 1.04 * nominalV, 0.02 * nominalV);

        MeasurementAggregation settings = new MeasurementAggregation()
                .setBusInjectionPolicy(MeasurementAggregation.BusInjectionPolicy.SUM_METERED)
                .setDuplicateMeasurementPolicy(
                        MeasurementAggregation.DuplicateMeasurementPolicy.THROW);

        PowsyblException thrown = assertThrows(PowsyblException.class, () -> aggregate(settings));
        assertTrue(thrown.getMessage().contains("BUS_TARGET_V"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(load.getTerminal().getBusView().getBus().getId()),
                thrown.getMessage());
    }

    @Test
    void twoMetersOnOneDeviceDoNotCountItsPowerTwice() {
        var load = network.getLoad("B2-L");
        meter(load, "p1", Measurement.Type.ACTIVE_POWER, null, 21.7, 0.5);
        meter(load, "p2", Measurement.Type.ACTIVE_POWER, null, 21.7, 0.5);

        var injection = of(aggregate(new MeasurementAggregation().setBusInjectionPolicy(
                MeasurementAggregation.BusInjectionPolicy.SUM_METERED)), AcEquationType.BUS_TARGET_P)
                .orElseThrow();

        // one device consuming 21.7 MW, read twice, is still one 21.7 MW device
        assertEquals(-0.217, injection.valuePu(), 1e-12);
        assertEquals(Math.sqrt(1 / 8.0) / PerUnit.SB, injection.sigmaPu(), 1e-12);
    }

    @Test
    void namesTheIidmMeasurementAReadingCameFrom() {
        var load = network.getLoad("B2-L");
        double nominalV = load.getTerminal().getVoltageLevel().getNominalV();
        meter(load, "v", Measurement.Type.VOLTAGE, null, 1.03 * nominalV, 0.01 * nominalV);

        var reading = of(aggregate(new MeasurementAggregation().setBusInjectionPolicy(
                MeasurementAggregation.BusInjectionPolicy.SUM_METERED)), AcEquationType.BUS_TARGET_V).orElseThrow();

        assertEquals(List.of("v"), reading.sourceMeasurementIds(),
                "bad data detection reports culprits by IIDM measurement identifier, so a reading has"
                        + " to carry the one it came from");
    }

    @Test
    void aBusInjectionNamesEveryDeviceReadingSummedIntoIt() {
        var load = network.getLoad("B2-L");
        var generator = network.getGenerator("B2-G");
        meter(load, "p1", Measurement.Type.ACTIVE_POWER, null, 21.7, 0.3);
        meter(generator, "p2", Measurement.Type.ACTIVE_POWER, null, -40.0, 0.4);

        var reading = of(aggregate(new MeasurementAggregation()), AcEquationType.BUS_TARGET_P).orElseThrow();

        assertEquals(List.of("p1", "p2"), reading.sourceMeasurementIds(),
                "an injection is a sum of device readings, so either of them can be the bad one");
    }

    @Test
    void aMergedReadingNamesBothMetersItCameFrom() {
        var load = network.getLoad("B2-L");
        double nominalV = load.getTerminal().getVoltageLevel().getNominalV();
        meter(load, "v1", Measurement.Type.VOLTAGE, null, 1.02 * nominalV, 0.01 * nominalV);
        meter(load, "v2", Measurement.Type.VOLTAGE, null, 1.04 * nominalV, 0.02 * nominalV);

        var merged = of(aggregate(new MeasurementAggregation().setBusInjectionPolicy(
                MeasurementAggregation.BusInjectionPolicy.SUM_METERED)), AcEquationType.BUS_TARGET_V).orElseThrow();

        assertEquals(List.of("v1", "v2"), merged.sourceMeasurementIds(),
                "merging loses which of the two is wrong, so it must at least report that it was one"
                        + " of these");
    }

    @Test
    void aZeroInjectionPseudoMeasurementNamesNoMeter() {
        List<LfMeasurement> readings = aggregate(new MeasurementAggregation());

        assertFalse(readings.isEmpty());
        for (LfMeasurement reading : readings) {
            assertTrue(reading.sourceMeasurementIds().isEmpty(),
                    "no meter produced a zero injection, so there is nothing to blame for it");
        }
    }

    @Test
    void aMeasurementWithoutAnIdentifierIsStillRead() {
        // IIDM lets a measurement go without one
        var load = network.getLoad("B2-L");
        double nominalV = load.getTerminal().getVoltageLevel().getNominalV();
        meter(load, null, Measurement.Type.VOLTAGE, null, 1.03 * nominalV, 0.01 * nominalV);
        meter(load, null, Measurement.Type.ACTIVE_POWER, null, 21.7, 0.5);

        List<LfMeasurement> readings = aggregate(new MeasurementAggregation().setBusInjectionPolicy(
                MeasurementAggregation.BusInjectionPolicy.SUM_METERED));

        var voltage = of(readings, AcEquationType.BUS_TARGET_V).orElseThrow();
        assertEquals(1.03, voltage.valuePu(), 1e-12);
        assertTrue(voltage.sourceMeasurementIds().isEmpty(), "there is no meter to name");
        assertTrue(of(readings, AcEquationType.BUS_TARGET_P).orElseThrow().sourceMeasurementIds().isEmpty());
    }
}
