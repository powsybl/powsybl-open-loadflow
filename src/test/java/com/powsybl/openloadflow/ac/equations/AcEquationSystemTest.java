package com.powsybl.openloadflow.ac.equations;

import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControl;
import com.powsybl.openloadflow.ac.util.NetworkBuilder;
import com.powsybl.openloadflow.equations.SubjectType;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;
import com.powsybl.openloadflow.util.LoadFlowTestTools;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class AcEquationSystemTest {
    private LoadFlowTestTools loadFlowTestToolsSvcVoltageWithSlope;
    private LfBus lfBus2VoltageWithSlope;
    private LfBus lfBus1;
    private StaticVarCompensatorVoltageLambdaQEquationTerm staticVarCompensatorVoltageLambdaQEquationTerm;

    public AcEquationSystemTest() {
        loadFlowTestToolsSvcVoltageWithSlope = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().addBus2Gen().build());
        lfBus2VoltageWithSlope = loadFlowTestToolsSvcVoltageWithSlope.getLfNetwork().getBusById("vl2_0");
        lfBus1 = loadFlowTestToolsSvcVoltageWithSlope.getLfNetwork().getBusById("vl1_0");
        staticVarCompensatorVoltageLambdaQEquationTerm =
                loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().getEquationTerm(SubjectType.BUS, lfBus2VoltageWithSlope.getNum(), StaticVarCompensatorVoltageLambdaQEquationTerm.class);
    }

    @Test
    void useBusPvlqTest() {
        // 1 - parameter useBusPVLQ is false
        List<LfStaticVarCompensatorImpl> lfStaticVarCompensators = new ArrayList<>();
        Assertions.assertFalse(AcEquationSystem.useBusPVLQ(lfBus2VoltageWithSlope,
                new AcEquationSystemCreationParameters(false, false, false, false),
                lfStaticVarCompensators), "should return false as parameter useBusPVLQ is false");

        // 2 - no staticVarCompensatorsWithSlope in bus
        Assertions.assertFalse(AcEquationSystem.useBusPVLQ(lfBus1,
                new AcEquationSystemCreationParameters(false, false, false, true),
                lfStaticVarCompensators), "should return false as missing StaticVarCompensator in bus");

        // 3 - there is a generator with a voltage regulator
        LoadFlowTestTools loadFlowTestToolsWithGeneratorVoltageRegulator = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().addBus2Gen().setBus2GenRegulationMode(true).build());
        LfBus lfBus2WithGeneratorVoltageRegulator = loadFlowTestToolsWithGeneratorVoltageRegulator.getLfNetwork().getBusById("vl2_0");
        Assertions.assertFalse(AcEquationSystem.useBusPVLQ(lfBus2WithGeneratorVoltageRegulator,
                new AcEquationSystemCreationParameters(false, false, false, true),
                lfStaticVarCompensators), "should return false as bus contains a generator with voltage regulator");

        // 4 - parameter useBusPVLQ is true AND there is a staticVarCompensatorsWithSlope in bus
        //     AND there isn't another generator with a voltage regulator
        Assertions.assertTrue(AcEquationSystem.useBusPVLQ(lfBus2VoltageWithSlope,
                new AcEquationSystemCreationParameters(false, false, false, true),
                lfStaticVarCompensators), "should return true as parameter useBusPVLQ is true, bus contains a StaticVarCompensator and no generator with voltage regulator");
    }

    @Test
    void getStaticVarCompensatorsWithSlopeTest() {
        // 1 - one static var compensator with a slope not null
        List<LfStaticVarCompensatorImpl> lfStaticVarCompensators = new ArrayList<>();
        AcEquationSystem.getStaticVarCompensatorsWithSlope(lfBus2VoltageWithSlope, lfStaticVarCompensators);
        Assertions.assertEquals(1, lfStaticVarCompensators.size(), "should return StaticVarCompensator with slope not zero");

        // 2 - one static var compensator without VoltagePerReactivePowerControl
        lfStaticVarCompensators = new ArrayList<>();
        LoadFlowTestTools loadFlowTestTools = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcRegulationMode(StaticVarCompensator.RegulationMode.REACTIVE_POWER).build());
        LfBus lfBus2 = loadFlowTestTools.getLfNetwork().getBusById("vl2_0");
        AcEquationSystem.getStaticVarCompensatorsWithSlope(lfBus2, lfStaticVarCompensators);
        Assertions.assertEquals(0, lfStaticVarCompensators.size(), "should return no StaticVarCompensator as VoltagePerReactivePowerControl is missing ");

        // 3 - one static var compensator with VoltagePerReactivePowerControl and a zero slope
        NetworkBuilder networkBuilder = new NetworkBuilder();
        networkBuilder.addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope();
        networkBuilder.getBus2svc().getExtension(VoltagePerReactivePowerControl.class).setSlope(0);
        loadFlowTestTools = new LoadFlowTestTools(networkBuilder.build());
        lfBus2 = loadFlowTestTools.getLfNetwork().getBusById("vl2_0");
        AcEquationSystem.getStaticVarCompensatorsWithSlope(lfBus2, lfStaticVarCompensators);
        Assertions.assertEquals(0, lfStaticVarCompensators.size(), "should return no StaticVarCompensator as slope is zero");
    }
}
