package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractNamedEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractGeneratorShuntEquationTerm extends AbstractNamedEquationTerm<AcVariableType, AcEquationType> {

    //protected final LfShunt shunt;
    protected final LfGenerator gen;

    protected final Variable<AcVariableType> vVar;

    protected AbstractGeneratorShuntEquationTerm(LfGenerator gen, LfBus bus, VariableSet<AcVariableType> variableSet, DisymAcSequenceType sequenceType) {
        super(true);
        this.gen = gen;
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = null;
        switch (sequenceType) {
            case HOMOPOLAR:
                vType = AcVariableType.BUS_V_HOMOPOLAR;
                break;

            case INVERSE:
                vType = AcVariableType.BUS_V_INVERSE;
                break;

            default:
                throw new IllegalStateException("Unknown or unadapted sequence type " + sequenceType);
        }
        vVar = variableSet.getVariable(bus.getNum(), vType);
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BUS;
    } // TODO : check if acceptable

    @Override
    public int getElementNum() {
        return gen.getBus().getNum(); // TODO : check if acceptable
    }

    protected double v() {
        return sv.get(vVar.getRow());
    }
}
