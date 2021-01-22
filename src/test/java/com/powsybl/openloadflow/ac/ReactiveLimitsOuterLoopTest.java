package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.openloadflow.ac.equations.StaticVarCompensatorVoltageLambdaQEquationTerm;
import com.powsybl.openloadflow.ac.util.NetworkBuilder;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationType;
import com.powsybl.openloadflow.equations.SubjectType;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.LoadFlowTestTools;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReactiveLimitsOuterLoopTest {
    private LoadFlowTestTools loadFlowTestToolsSvcReactive;
    private LfBus lfBus2Reactive;
    private LoadFlowTestTools loadFlowTestToolsSvcVoltageWithSlope;
    private LfBus lfBus2VoltageWithSlope;
    private ReactiveLimitsOuterLoop reactiveLimitsOuterLoop;

    public ReactiveLimitsOuterLoopTest() {
        loadFlowTestToolsSvcReactive = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcRegulationMode(StaticVarCompensator.RegulationMode.REACTIVE_POWER).build());
        lfBus2Reactive = loadFlowTestToolsSvcReactive.getLfNetwork().getBusById("vl2_0");
        loadFlowTestToolsSvcVoltageWithSlope = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().build());
        lfBus2VoltageWithSlope = loadFlowTestToolsSvcVoltageWithSlope.getLfNetwork().getBusById("vl2_0");
        reactiveLimitsOuterLoop = new ReactiveLimitsOuterLoop();
    }

    @Test
    void getVTest() {
        // no equation with type EquationType.BUS_VLQ
        lfBus2Reactive.setV(0.975);
        assertEquals(0.975, reactiveLimitsOuterLoop.getV(lfBus2Reactive, loadFlowTestToolsSvcReactive.getEquationSystem()));

        // there is an equation with type EquationType.BUS_VLQ and a StaticVarCompensatorVoltageLambdaQEquationTerm
        loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().updateEquations(new double[]{0d, 0d, 1d, 0d});
        assertEquals(2.2d, reactiveLimitsOuterLoop.getV(lfBus2VoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem()));

        // there is an EquationType.BUS_VLQ but StaticVarCompensatorVoltageLambdaQEquationTerm is missing
        lfBus2VoltageWithSlope.setV(0.95);
        Optional<Equation> equation = loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().getEquation(lfBus2VoltageWithSlope.getNum(), EquationType.BUS_VLQ);
        if (equation.isPresent()) {
            StaticVarCompensatorVoltageLambdaQEquationTerm staticVarCompensatorVoltageLambdaQEquationTerm = loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().getEquationTerm(SubjectType.BUS, lfBus2VoltageWithSlope.getNum(), StaticVarCompensatorVoltageLambdaQEquationTerm.class);
            equation.get().getTerms().remove(staticVarCompensatorVoltageLambdaQEquationTerm);
        }
        assertEquals(0.95, reactiveLimitsOuterLoop.getV(lfBus2VoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem()));
    }

    @Test
    void updateControlledBusTest() {
        int previousEquationCount = loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().getEquations().size();
        reactiveLimitsOuterLoop.updateControlledBus(lfBus2VoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem(), new VariableSet());
        assertEquals(previousEquationCount + 1, loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().getEquations().size(), "should create an equation with type EquationType.BUS_V");
    }
}
