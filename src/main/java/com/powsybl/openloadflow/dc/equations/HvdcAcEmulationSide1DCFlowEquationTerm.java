package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

public class HvdcAcEmulationSide1DCFlowEquationTerm extends AbstractHvdcAcEmulationDcFlowEquationTerm {

    public HvdcAcEmulationSide1DCFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<DcVariableType> variableSet) {
        super(hvdc, bus1, bus2, variableSet);
    }

    @Override
    protected String getName() {
        return "dc_p_1_hvdc";
    }

    @Override
    public double eval() {
        return k * (ph1() - ph2()) + hvdc.getP0();
    }

    @Override
    public double der(Variable<DcVariableType> variable) {
        if (variable.equals(ph1Var)) {
            return k;
        } else if (variable.equals(ph2Var)) {
            return -k;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public boolean hasRhs() {
        return true;
    }

    @Override
    public double rhs() {
        return hvdc.getP0();
    }
}
