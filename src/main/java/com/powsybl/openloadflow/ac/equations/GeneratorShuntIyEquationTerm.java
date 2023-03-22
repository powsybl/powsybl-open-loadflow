package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;

public class GeneratorShuntIyEquationTerm extends AbstractEquivalentShuntIyEquationTerm {

    protected final LfGenerator gen;

    public GeneratorShuntIyEquationTerm(LfGenerator gen, LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
        this.gen = gen;
    }

    @Override
    protected double g() {
        return 1000;
    } // TODO : check acceptable large value for target V close to zero

    @Override
    protected double b() {
        return 1000;
    } // TODO : check acceptable large value for target V close to zero

    @Override
    protected String getName() {
        return "ac_iy_gen_shunt";
    }

    @Override
    public int getElementNum() {
        return gen.getBus().getNum(); // TODO : check if acceptable
    }
}
