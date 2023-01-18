package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

public class LoadShuntActiveEquationTerm extends AbstractEquivalentShuntActiveEquationTerm {

    public LoadShuntActiveEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
    }

    @Override
    protected double g() {
        return 0.1;
    } // TODO : check acceptable value for equivalent load

    @Override
    protected String getName() {
        return "ac_p_load_shunt";
    }

    @Override
    public int getElementNum() {
        return bus.getNum(); // TODO : check if acceptable
    }

}
