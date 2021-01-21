package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.openloadflow.ac.util.NetworkBuilder;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.LoadFlowTestTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReactiveLimitsOuterLoopTest {
    private LoadFlowTestTools loadFlowTestToolsSvcReactive;
    private LfBus lfBusReactive;
    private LoadFlowTestTools loadFlowTestToolsSvcSlope;
    private LfBus lfBusVoltageSlope;
    private ReactiveLimitsOuterLoop reactiveLimitsOuterLoop;

    public ReactiveLimitsOuterLoopTest() {
        loadFlowTestToolsSvcReactive = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcRegulationMode(StaticVarCompensator.RegulationMode.REACTIVE_POWER).build());
        lfBusReactive = loadFlowTestToolsSvcReactive.getLfNetwork().getBusById("vl2_0");
        loadFlowTestToolsSvcSlope = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().build());
        lfBusVoltageSlope = loadFlowTestToolsSvcSlope.getLfNetwork().getBusById("vl2_0");
        reactiveLimitsOuterLoop = new ReactiveLimitsOuterLoop();
    }

    @Test
    void getVTest() {
        lfBusReactive.setV(0.975);
        assertEquals(0.975, reactiveLimitsOuterLoop.getV(lfBusReactive, loadFlowTestToolsSvcReactive.getEquationSystem()));
        loadFlowTestToolsSvcSlope.getEquationSystem().updateEquations(new double[]{0d, 0d, 1d, 0d});
        assertEquals(2.2d, reactiveLimitsOuterLoop.getV(lfBusVoltageSlope, loadFlowTestToolsSvcSlope.getEquationSystem()));
    }
}
