package com.powsybl.openloadflow;

import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.List;

public abstract class AbstractHvdcAcEmulationFlowEquationTerm<T extends Enum<T> & Quantity, U extends Enum<U> & Quantity> extends AbstractElementEquationTerm<LfHvdc, T, U> {




    protected final Variable<T> ph1Var;

    protected final Variable<T> ph2Var;

    protected final List<Variable<T>> variables;

    protected final double k;

    protected final double p0;

    protected final double lossFactor1;

    protected final double lossFactor2;

    /**
     * @return TODO documentation
     */
    protected abstract T getBusPhi();
    protected AbstractHvdcAcEmulationFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<T> variableSet) {
        super(hvdc);
        ph1Var = variableSet.getVariable(bus1.getNum(), getBusPhi());
        ph2Var = variableSet.getVariable(bus2.getNum(), getBusPhi());
        variables = List.of(ph1Var, ph2Var);
        k = hvdc.getDroop() * 180 / Math.PI;
        p0 = hvdc.getP0();
        lossFactor1 = hvdc.getConverterStation1().getLossFactor() / 100;
        lossFactor2 = hvdc.getConverterStation2().getLossFactor() / 100;
    }

    protected double ph1() {
        return sv.get(ph1Var.getRow());
    }

    protected double ph2() {
        return sv.get(ph2Var.getRow());
    }

    protected static double getLossMultiplier(double lossFactor1, double lossFactor2) {
        return (1 - lossFactor1) * (1 - lossFactor2);
    }

    @Override
    public List<Variable<T>> getVariables() {
        return variables;
    }

    @Override
    public boolean hasRhs() {
        return false;
    }
}
