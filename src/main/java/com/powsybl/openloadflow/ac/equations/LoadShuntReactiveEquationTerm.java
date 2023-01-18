package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

public class LoadShuntReactiveEquationTerm extends AbstractEquivalentShuntReactiveEquationTerm {

    public LoadShuntReactiveEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
    }

    @Override
    protected double b() {
        return 0.1;
    } // TODO : check acceptable value for equivalent load

    @Override
    protected String getName() {
        return "ac_q_load_shunt";
    }

    @Override
    public int getElementNum() {
        return bus.getNum(); // TODO : check if acceptable
    }
}
