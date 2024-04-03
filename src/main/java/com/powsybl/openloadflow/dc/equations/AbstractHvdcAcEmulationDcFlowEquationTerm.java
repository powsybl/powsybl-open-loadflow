package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.List;

public abstract class AbstractHvdcAcEmulationDcFlowEquationTerm extends AbstractElementEquationTerm<LfHvdc, DcVariableType, DcEquationType> {

    protected final Variable<DcVariableType> ph1Var;
    protected final Variable<DcVariableType> ph2Var;
    protected final List<Variable<DcVariableType>> variables;
    protected final LfHvdc hvdc;
    protected final double k;

    protected AbstractHvdcAcEmulationDcFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet) {
        super(hvdc);
        ph1Var = variableSet.getVariable(bus1.getNum(), DcVariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), DcVariableType.BUS_PHI);
        variables = List.of(ph1Var, ph2Var);
        this.hvdc = hvdc;
        k = this.hvdc.getDroop() * 180 / Math.PI;
    }

    @Override
    public List<Variable<DcVariableType>> getVariables() {
        return variables;
    }

    protected double ph1() {
        return ph1(sv);
    }

    protected double ph1(StateVector sv) {
        return sv.get(ph1Var.getRow());
    }

    protected double ph2() {
        return ph2(sv);
    }

    protected double ph2(StateVector sv) {
        return sv.get(ph2Var.getRow());
    }

}
