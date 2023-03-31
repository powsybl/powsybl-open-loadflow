package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public abstract class AbstractClosedBranchDisymCoupledFlowEquationTerm extends AbstractBranchDisymFlowEquationTerm {

    // positive
    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final Variable<AcVariableType> ph1Var;

    protected final Variable<AcVariableType> ph2Var;

    // negative
    protected final Variable<AcVariableType> v1VarNegative;

    protected final Variable<AcVariableType> v2VarNegative;

    protected final Variable<AcVariableType> ph1VarNegative;

    protected final Variable<AcVariableType> ph2VarNegative;

    // zero
    protected final Variable<AcVariableType> v1VarZero;

    protected final Variable<AcVariableType> v2VarZero;

    protected final Variable<AcVariableType> ph1VarZero;

    protected final Variable<AcVariableType> ph2VarZero;

    // rho and angle
    protected final Variable<AcVariableType> a1Var;

    protected final Variable<AcVariableType> r1Var;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected AbstractClosedBranchDisymCoupledFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                                 boolean deriveA1, boolean deriveR1) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);

        v1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V);
        v2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V);
        ph1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI);

        v1VarNegative = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V_NEGATIVE);
        v2VarNegative = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V_NEGATIVE);
        ph1VarNegative = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI_NEGATIVE);
        ph2VarNegative = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI_NEGATIVE);

        v1VarZero = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V_ZERO);
        v2VarZero = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V_ZERO);
        ph1VarZero = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI_ZERO);
        ph2VarZero = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI_ZERO);

        a1Var = deriveA1 ? variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_ALPHA1) : null;
        r1Var = deriveR1 ? variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_RHO1) : null;

        variables.add(v1Var);
        variables.add(v2Var);
        variables.add(ph1Var);
        variables.add(ph2Var);
        variables.add(v1VarNegative);
        variables.add(v2VarNegative);
        variables.add(ph1VarNegative);
        variables.add(ph2VarNegative);
        variables.add(v1VarZero);
        variables.add(v2VarZero);
        variables.add(ph1VarZero);
        variables.add(ph2VarZero);

        if (a1Var != null) {
            variables.add(a1Var);
        }
        if (r1Var != null) {
            variables.add(r1Var);
        }

    }

    protected double v(int g, int i) {
        switch (g) {
            case 0: // zero
                return i == 1 ? sv.get(v1VarZero.getRow()) : sv.get(v2VarZero.getRow());

            case 1: // positive
                return i == 1 ? sv.get(v1Var.getRow()) : sv.get(v2Var.getRow());

            case 2: // negative
                return i == 1 ? sv.get(v1VarNegative.getRow()) : sv.get(v2VarNegative.getRow());

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    protected double ph(int g, int i) {
        switch (g) {
            case 0: // zero
                return i == 1 ? sv.get(ph1VarZero.getRow()) : sv.get(ph2VarZero.getRow());

            case 1: // positive
                return i == 1 ? sv.get(ph1Var.getRow()) : sv.get(ph2Var.getRow());

            case 2: // negative
                return i == 1 ? sv.get(ph1VarNegative.getRow()) : sv.get(ph2VarNegative.getRow());

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    protected double r1() {
        return r1Var != null ? sv.get(r1Var.getRow()) : element.getPiModel().getR1();
    }

    protected double a1() {
        return a1Var != null ? sv.get(a1Var.getRow()) : element.getPiModel().getA1();
    }

    protected double r(int i) {
        return i == 1 ? r1() : 1.;
    }

    protected double a(int i) {
        return i == 1 ? a1() : A2;
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
