package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.ac.util.NetworkBuilder;
import com.powsybl.openloadflow.equations.SubjectType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.equations.VariableType;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;
import com.powsybl.openloadflow.util.LoadFlowTestTools;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

public class StaticVarCompensatorVoltageLambdaQEquationTermTest {
    private LoadFlowTestTools loadFlowTestToolsSvcVoltageWithSlope;
    private LfBus lfBusVoltageWithSlope;
    private VariableSet variableSet;
    private StaticVarCompensatorVoltageLambdaQEquationTerm staticVarCompensatorVoltageLambdaQEquationTerm;

    public StaticVarCompensatorVoltageLambdaQEquationTermTest() {
        loadFlowTestToolsSvcVoltageWithSlope = new LoadFlowTestTools(new NetworkBuilder().addNetworkBus1GenBus2Svc().setBus2SvcVoltageAndSlope().build());
        lfBusVoltageWithSlope = loadFlowTestToolsSvcVoltageWithSlope.getLfNetwork().getBusById("vl2_0");
        variableSet = loadFlowTestToolsSvcVoltageWithSlope.getVariableSet();
        staticVarCompensatorVoltageLambdaQEquationTerm =
                loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().getEquationTerm(SubjectType.BUS, lfBusVoltageWithSlope.getNum(), StaticVarCompensatorVoltageLambdaQEquationTerm.class);
    }

    @Test
    void staticVarCompensatorVoltageLambdaQEquationTermTest() {
        List<LfStaticVarCompensatorImpl> lfStaticVarCompensators = lfBusVoltageWithSlope.getGenerators().stream()
                .filter(lfGenerator -> lfGenerator instanceof LfStaticVarCompensatorImpl)
                .map(LfStaticVarCompensatorImpl.class::cast)
                .collect(Collectors.toList());
        lfStaticVarCompensators.add(lfStaticVarCompensators.get(0));
        Assertions.assertThrows(PowsyblException.class, () -> new StaticVarCompensatorVoltageLambdaQEquationTerm(lfStaticVarCompensators, lfBusVoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem()));
    }

    @Test
    void updateTest() {
        Variable vVar = variableSet.getVariable(lfBusVoltageWithSlope.getNum(), VariableType.BUS_V);
        Variable phiVar = variableSet.getVariable(lfBusVoltageWithSlope.getNum(), VariableType.BUS_PHI);
        Variable nimpVar = variableSet.getVariable(-1, VariableType.BRANCH_RHO1);
        staticVarCompensatorVoltageLambdaQEquationTerm.update(new double[]{1, 0, 1, 0});

        Assertions.assertEquals(1, staticVarCompensatorVoltageLambdaQEquationTerm.eval());
        Assertions.assertEquals(2.2, staticVarCompensatorVoltageLambdaQEquationTerm.der(vVar));
        Assertions.assertEquals(-0.4, staticVarCompensatorVoltageLambdaQEquationTerm.der(phiVar), 1E-6d);
        Assertions.assertThrows(IllegalStateException.class, () -> staticVarCompensatorVoltageLambdaQEquationTerm.der(nimpVar));
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
