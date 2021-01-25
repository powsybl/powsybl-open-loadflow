package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.ac.util.NetworkBuilder;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;
import com.powsybl.openloadflow.util.LoadFlowTestTools;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

class StaticVarCompensatorVoltageLambdaQEquationTermTest {
    private LoadFlowTestTools loadFlowTestToolsSvcVoltageWithSlope;
    private LfBus lfBus1;
    private LfBus lfBus2VoltageWithSlope;
    private VariableSet variableSet;
    private EquationSystem equationSystem;
    private StaticVarCompensatorVoltageLambdaQEquationTerm staticVarCompensatorVoltageLambdaQEquationTerm;

    public StaticVarCompensatorVoltageLambdaQEquationTermTest() {
        loadFlowTestToolsSvcVoltageWithSlope = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().addBus2Load().addBus2Gen().addBus2Sc().addBus1OpenLine().addBus2OpenLine().build());
        lfBus1 = loadFlowTestToolsSvcVoltageWithSlope.getLfNetwork().getBusById("vl1_0");
        lfBus2VoltageWithSlope = loadFlowTestToolsSvcVoltageWithSlope.getLfNetwork().getBusById("vl2_0");
        variableSet = loadFlowTestToolsSvcVoltageWithSlope.getVariableSet();
        equationSystem = loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem();
        staticVarCompensatorVoltageLambdaQEquationTerm =
                loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().getEquationTerm(SubjectType.BUS, lfBus2VoltageWithSlope.getNum(), StaticVarCompensatorVoltageLambdaQEquationTerm.class);
    }

    @Test
    void staticVarCompensatorVoltageLambdaQEquationTermTest() {
        List<LfStaticVarCompensatorImpl> lfStaticVarCompensators = lfBus2VoltageWithSlope.getGenerators().stream()
                .filter(lfGenerator -> lfGenerator instanceof LfStaticVarCompensatorImpl)
                .map(LfStaticVarCompensatorImpl.class::cast)
                .collect(Collectors.toList());
        lfStaticVarCompensators.add(lfStaticVarCompensators.get(0));
        Assertions.assertThrows(PowsyblException.class, () -> new StaticVarCompensatorVoltageLambdaQEquationTerm(lfStaticVarCompensators, lfBus2VoltageWithSlope, variableSet, equationSystem));
    }

    @Test
    void updateTest() {
        // getVariable and call update on term with vector of variable values
        Variable vVar = variableSet.getVariable(lfBus2VoltageWithSlope.getNum(), VariableType.BUS_V);
        Variable phiVar = variableSet.getVariable(lfBus2VoltageWithSlope.getNum(), VariableType.BUS_PHI);
        Variable nimpVar = variableSet.getVariable(-1, VariableType.BRANCH_RHO1);
        staticVarCompensatorVoltageLambdaQEquationTerm.update(new double[]{1, 0, 1, 0});

        // assertions
        Assertions.assertEquals(0.995842, staticVarCompensatorVoltageLambdaQEquationTerm.eval(), 1E-6d);
        Assertions.assertEquals(2.199184, staticVarCompensatorVoltageLambdaQEquationTerm.der(vVar), 1E-6d);
        Assertions.assertEquals(-0.4, staticVarCompensatorVoltageLambdaQEquationTerm.der(phiVar), 1E-6d);
        Assertions.assertThrows(IllegalStateException.class, () -> staticVarCompensatorVoltageLambdaQEquationTerm.der(nimpVar));
    }

    @Test
    void hasToEvalAndDerTermTest() {
        // build or get equation terms
        Equation equation = loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().createEquation(lfBus2VoltageWithSlope.getNum(), EquationType.BUS_Q);
        ShuntCompensatorReactiveFlowEquationTerm shuntCompensatorReactiveFlowEquationTerm = equation.getTerms().stream().filter(equationTerm -> equationTerm instanceof ShuntCompensatorReactiveFlowEquationTerm).map(ShuntCompensatorReactiveFlowEquationTerm.class::cast).findFirst().get();
        ClosedBranchSide1ReactiveFlowEquationTerm closedBranchSide1ReactiveFlowEquationTerm =
                new ClosedBranchSide1ReactiveFlowEquationTerm(lfBus1.getBranches().get(0), lfBus1, lfBus2VoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        ClosedBranchSide2ReactiveFlowEquationTerm closedBranchSide2ReactiveFlowEquationTerm =
                new ClosedBranchSide2ReactiveFlowEquationTerm(lfBus1.getBranches().get(0), lfBus1, lfBus2VoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        OpenBranchSide1ReactiveFlowEquationTerm openBranchSide1ReactiveFlowEquationTerm =
                new OpenBranchSide1ReactiveFlowEquationTerm(lfBus1.getBranches().get(0), lfBus2VoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        OpenBranchSide2ReactiveFlowEquationTerm openBranchSide2ReactiveFlowEquationTerm =
                new OpenBranchSide2ReactiveFlowEquationTerm(lfBus1.getBranches().get(0), lfBus1, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        BusVoltageEquationTerm busVoltageEquationTerm = new BusVoltageEquationTerm(lfBus1, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet());

        // assertions
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(closedBranchSide1ReactiveFlowEquationTerm));
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(closedBranchSide2ReactiveFlowEquationTerm));
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(openBranchSide1ReactiveFlowEquationTerm));
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(openBranchSide2ReactiveFlowEquationTerm));
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(shuntCompensatorReactiveFlowEquationTerm));
        shuntCompensatorReactiveFlowEquationTerm.setActive(false);
        Assertions.assertFalse(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(shuntCompensatorReactiveFlowEquationTerm));
        Assertions.assertFalse(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(busVoltageEquationTerm));
    }

    @Test
    void hasPhiVarTest() {
        // build equation terms
        ClosedBranchSide1ReactiveFlowEquationTerm closedBranchSide1ReactiveFlowEquationTerm =
                new ClosedBranchSide1ReactiveFlowEquationTerm(lfBus1.getBranches().get(0), lfBus1, lfBus2VoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        ClosedBranchSide2ReactiveFlowEquationTerm closedBranchSide2ReactiveFlowEquationTerm =
                new ClosedBranchSide2ReactiveFlowEquationTerm(lfBus1.getBranches().get(0), lfBus1, lfBus2VoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        ShuntCompensatorReactiveFlowEquationTerm shuntCompensatorReactiveFlowEquationTerm =
                new ShuntCompensatorReactiveFlowEquationTerm(lfBus2VoltageWithSlope.getShunts().get(0), lfBus2VoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet());

        // assertions
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasPhiVar(closedBranchSide1ReactiveFlowEquationTerm));
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasPhiVar(closedBranchSide2ReactiveFlowEquationTerm));
        Assertions.assertFalse(staticVarCompensatorVoltageLambdaQEquationTerm.hasPhiVar(shuntCompensatorReactiveFlowEquationTerm));
    }

    @Test
    void rhsTest() {
        Assertions.assertEquals(0, staticVarCompensatorVoltageLambdaQEquationTerm.rhs());
    }

    @Test
    void getNameTest() {
        Assertions.assertEquals("ac_static_var_compensator_with_slope", staticVarCompensatorVoltageLambdaQEquationTerm.getName());
    }
}
