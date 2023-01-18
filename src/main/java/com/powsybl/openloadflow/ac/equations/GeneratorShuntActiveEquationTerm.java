package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;

public class GeneratorShuntActiveEquationTerm extends AbstractEquivalentShuntActiveEquationTerm {

    protected final LfGenerator gen;

    public GeneratorShuntActiveEquationTerm(LfGenerator gen, LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
        this.gen = gen;
    }

    @Override
    protected double g() {
        return 1.;
    } // TODO : check acceptable large value for target V close to zero

    @Override
    protected String getName() {
        return "ac_p_gen_shunt";
    }

    @Override
    public int getElementNum() {
        return gen.getBus().getNum(); // TODO : check if acceptable
    }
}
