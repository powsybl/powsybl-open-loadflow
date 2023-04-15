package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractNamedEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.extensions.AsymBus;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public abstract class AbstractShuntFortescueEquationTerm extends AbstractNamedEquationTerm<AcVariableType, AcEquationType> {

    protected final LfBus bus;

    protected final Variable<AcVariableType> vVar;

    protected final Variable<AcVariableType> phVar;

    protected final Fortescue.SequenceType sequenceType;

    protected AbstractShuntFortescueEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        super(true);
        this.bus = bus;
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);
        AcVariableType vType;
        AcVariableType phType;
        this.sequenceType = sequenceType;
        switch (sequenceType) {
            case ZERO:
                vType = AcVariableType.BUS_V_ZERO;
                phType = AcVariableType.BUS_PHI_ZERO;
                break;

            case NEGATIVE:
                vType = AcVariableType.BUS_V_NEGATIVE;
                phType = AcVariableType.BUS_PHI_NEGATIVE;
                break;

            default:
                throw new IllegalStateException("Unknown or unadapted sequence type " + sequenceType);
        }
        vVar = variableSet.getVariable(bus.getNum(), vType);
        phVar = variableSet.getVariable(bus.getNum(), phType);

    }

    @Override
    public ElementType getElementType() {
        return ElementType.BUS;
    }

    @Override
    public int getElementNum() {
        return bus.getNum();
    }

    protected double v() {
        return sv.get(vVar.getRow());
    }

    protected double ph() {
        return sv.get(phVar.getRow());
    }

    protected double b() {
        AsymBus asymBus = (AsymBus) bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (sequenceType == Fortescue.SequenceType.ZERO) {
            return asymBus.getbZeroEquivalent();
        }
        return asymBus.getbNegativeEquivalent();
    }

    protected double g() {
        AsymBus asymBus = (AsymBus) bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (sequenceType == Fortescue.SequenceType.ZERO) {
            return asymBus.getgZeroEquivalent();
        }
        return asymBus.getgNegativeEquivalent();
    }

}
