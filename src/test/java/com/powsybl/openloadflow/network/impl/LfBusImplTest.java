package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.PerUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.DELTA_POWER;

public class LfBusImplTest {
    private Network network;
    private Bus bus;
    private StaticVarCompensator svc1;
    private StaticVarCompensator svc2;
    private StaticVarCompensator svc3;

    private Network createNetwork() {
        Network network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("S1")
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        svc1 = vl1.newStaticVarCompensator()
                .setId("svc1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.006)
                .setBmax(0.006)
                .add();
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.01)
                .add();
        svc2 = vl1.newStaticVarCompensator()
                .setId("svc2")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.001)
                .setBmax(0.001)
                .add();
        svc2.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.015)
                .add();
        svc3 = vl1.newStaticVarCompensator()
                .setId("svc3")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.00075)
                .setBmax(0.00075)
                .add();
        svc3.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.02)
                .add();
        return network;
    }

    @BeforeEach
    void setUp() {
        network = createNetwork();
    }

    @Test
    void updateGeneratorsStateTest() {
        LfBusImpl lfBus = new LfBusImpl(bus, 385, 0);
        LfNetworkLoadingReport lfNetworkLoadingReport = new LfNetworkLoadingReport();
        lfBus.addStaticVarCompensator(svc1, 1.0, lfNetworkLoadingReport);
        lfBus.addStaticVarCompensator(svc2, 1.0, lfNetworkLoadingReport);
        lfBus.addStaticVarCompensator(svc3, 1.0, lfNetworkLoadingReport);
        double generationQ = -6.412103131789854;
        lfBus.updateGeneratorsState(generationQ * PerUnit.SB, true);
        double sumQ = 0;
        for (LfGenerator lfGenerator : lfBus.getGenerators()) {
            sumQ += lfGenerator.getCalculatedQ();
        }
        Assertions.assertEquals(generationQ, sumQ, DELTA_POWER, "sum of generators calculatedQ should be equals to qToDispatch");
    }
}
