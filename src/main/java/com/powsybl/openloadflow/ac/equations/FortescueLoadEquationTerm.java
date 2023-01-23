package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractNamedEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class FortescueLoadEquationTerm extends AbstractNamedEquationTerm<AcVariableType, AcEquationType> {

    protected final LfBus bus;

    // direct
    protected final Variable<AcVariableType> vVar;

    protected final Variable<AcVariableType> phVar;

    // inverse
    protected final Variable<AcVariableType> vVarInv;

    protected final Variable<AcVariableType> phVarInv;

    // homopolar
    protected final Variable<AcVariableType> vVarHom;

    protected final Variable<AcVariableType> phVarHom;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    private final boolean isActive; // true if active power asked, false if reactive power asked
    private final int sequenceNum; // 0 = hompolar, 1 = direct, 2 = inverse

    public FortescueLoadEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, boolean isActive, int sequenceNum) {
        super(true);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);

        this.bus = bus;
        this.isActive = isActive;
        this.sequenceNum = sequenceNum;

        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        phVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI);

        vVarInv = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_INVERSE);
        phVarInv = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_INVERSE);

        vVarHom = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_HOMOPOLAR);
        phVarHom = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_HOMOPOLAR);

        variables.add(vVar);
        variables.add(phVar);
        variables.add(vVarInv);
        variables.add(phVarInv);
        variables.add(vVarHom);
        variables.add(phVarHom);
    }

    public double ph(int g) {
        switch (g) {
            case 0: // homopolar
                return sv.get(phVarHom.getRow());

            case 1: // direct
                return sv.get(phVar.getRow());

            case 2: // inverse
                return sv.get(phVarInv.getRow());

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    public double v(int g) {
        switch (g) {
            case 0: // homopolar
                return sv.get(vVarHom.getRow());

            case 1: // direct
                return sv.get(vVar.getRow());

            case 2: // inverse
                return sv.get(vVarInv.getRow());

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    public static double numerator(boolean isActive, int sequenceNum, FortescueLoadEquationTerm eqTerm) {
        double busP = eqTerm.bus.getLoadTargetP();
        double busQ = eqTerm.bus.getLoadTargetQ();
        if (isActive) {
            return GenericGeneratorTerm.t1g(sequenceNum, sequenceNum, sequenceNum, busP, busQ, eqTerm).getFirst() - GenericGeneratorTerm.t1g(0, 1, 2, busP, busQ, eqTerm).getFirst();
        } else {
            return GenericGeneratorTerm.t1g(sequenceNum, sequenceNum, sequenceNum, busP, busQ, eqTerm).getSecond() - GenericGeneratorTerm.t1g(0, 1, 2, busP, busQ, eqTerm).getSecond();
        }
    }

    public static double denominator(FortescueLoadEquationTerm eqTerm) {
        return GenericGeneratorTerm.denom(eqTerm);
    }

    public static double pq(boolean isActive, int sequenceNum, FortescueLoadEquationTerm eqTerm) {
        return numerator(isActive, sequenceNum, eqTerm) / denominator(eqTerm);
    }

    public static double dpq(boolean isActive, int sequenceNum, FortescueLoadEquationTerm eqTerm, Variable<AcVariableType> derVariable) {

        double busP = eqTerm.bus.getLoadTargetP();
        double busQ = eqTerm.bus.getLoadTargetQ();

        // we use the formula: (f/g)' = (f'*g-f*g')/g²
        double f = numerator(isActive, sequenceNum, eqTerm);
        double df;
        if (isActive) {
            df = GenericGeneratorTerm.dt1g(sequenceNum, sequenceNum, sequenceNum, busP, busQ, eqTerm, derVariable).getFirst() - GenericGeneratorTerm.dt1g(0, 1, 2, busP, busQ, eqTerm, derVariable).getFirst();
        } else {
            df = GenericGeneratorTerm.dt1g(sequenceNum, sequenceNum, sequenceNum, busP, busQ, eqTerm, derVariable).getSecond() - GenericGeneratorTerm.dt1g(0, 1, 2, busP, busQ, eqTerm, derVariable).getSecond();
        }

        double g = denominator(eqTerm);
        double dg = GenericGeneratorTerm.dDenom(eqTerm, derVariable);
        if (Math.abs(g) < 0.000001) {
            throw new IllegalStateException("Unexpected singularity of load derivative: ");
        }
        return (df * g - dg * f) / (g * g);
    }

    @Override
    public double eval() {
        return pq(isActive, sequenceNum, this);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return dpq(isActive, sequenceNum, this, variable);
    }

    @Override
    protected String getName() {
        return "ac_pq_load";
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BUS;
    } // TODO : check if acceptable

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    @Override
    public int getElementNum() {
        return bus.getNum(); // TODO : check if acceptable
    }
}
