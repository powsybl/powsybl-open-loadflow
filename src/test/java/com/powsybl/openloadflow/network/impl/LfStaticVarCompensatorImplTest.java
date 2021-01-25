package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.openloadflow.ac.util.NetworkBuilder;
import com.powsybl.openloadflow.util.LoadFlowTestTools;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LfStaticVarCompensatorImplTest {
    private LfStaticVarCompensatorImpl lfStaticVarCompensatorReactive;
    private LfStaticVarCompensatorImpl lfStaticVarCompensatorVoltageSlope;

    public LfStaticVarCompensatorImplTest() {
        LoadFlowTestTools loadFlowTestToolsSvcReactive = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcRegulationMode(StaticVarCompensator.RegulationMode.REACTIVE_POWER).build());
        lfStaticVarCompensatorReactive = (LfStaticVarCompensatorImpl) loadFlowTestToolsSvcReactive.getLfNetwork().getBusById("vl2_0").getGenerators().get(0);
        LoadFlowTestTools loadFlowTestToolsSvcSlope = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().build());
        lfStaticVarCompensatorVoltageSlope = (LfStaticVarCompensatorImpl) loadFlowTestToolsSvcSlope.getLfNetwork().getBusById("vl2_0").getGenerators().get(0);
    }

    @Test
    void getSlopeTest() {
        // case 1 : no slope defined
        Assertions.assertEquals(0, lfStaticVarCompensatorReactive.getSlope());

        // case 2 : add a VoltagePerReactivePowerControlAdder with a slope on StaticVarCompensator
        Assertions.assertEquals(0.0025, lfStaticVarCompensatorVoltageSlope.getSlope());
    }

    @Test
    void hasVoltageControlTest() {
        Assertions.assertFalse(lfStaticVarCompensatorReactive.hasVoltageControl());
        Assertions.assertTrue(lfStaticVarCompensatorVoltageSlope.hasVoltageControl());
    }

    @Test
    void getTargetQTest() {
        Assertions.assertEquals(-3.0, lfStaticVarCompensatorReactive.getTargetQ());
    }

    @Test
    void getMaxPTest() {
        Assertions.assertEquals(Double.MAX_VALUE, lfStaticVarCompensatorReactive.getMaxP());
    }

    @Test
    void getParticipationFactorTest() {
        Assertions.assertEquals(0, lfStaticVarCompensatorReactive.getParticipationFactor());
    }

    @Test
    void updateStateTest() {
        lfStaticVarCompensatorReactive.updateState();
        Assertions.assertEquals(300, lfStaticVarCompensatorReactive.getSvc().getReactivePowerSetpoint());
    }
}
