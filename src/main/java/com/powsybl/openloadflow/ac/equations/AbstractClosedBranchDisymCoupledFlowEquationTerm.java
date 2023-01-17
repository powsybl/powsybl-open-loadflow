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

    // direct
    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final Variable<AcVariableType> ph1Var;

    protected final Variable<AcVariableType> ph2Var;

    // inverse
    protected final Variable<AcVariableType> v1VarInv;

    protected final Variable<AcVariableType> v2VarInv;

    protected final Variable<AcVariableType> ph1VarInv;

    protected final Variable<AcVariableType> ph2VarInv;

    // homopolar
    protected final Variable<AcVariableType> v1VarHom;

    protected final Variable<AcVariableType> v2VarHom;

    protected final Variable<AcVariableType> ph1VarHom;

    protected final Variable<AcVariableType> ph2VarHom;

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

        v1VarInv = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V_INVERSE);
        v2VarInv = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V_INVERSE);
        ph1VarInv = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI_INVERSE);
        ph2VarInv = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI_INVERSE);

        v1VarHom = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V_HOMOPOLAR);
        v2VarHom = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V_HOMOPOLAR);
        ph1VarHom = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI_HOMOPOLAR);
        ph2VarHom = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI_HOMOPOLAR);

        a1Var = deriveA1 ? variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_ALPHA1) : null;
        r1Var = deriveR1 ? variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_RHO1) : null;

        variables.add(v1Var);
        variables.add(v2Var);
        variables.add(ph1Var);
        variables.add(ph2Var);
        variables.add(v1VarInv);
        variables.add(v2VarInv);
        variables.add(ph1VarInv);
        variables.add(ph2VarInv);
        variables.add(v1VarHom);
        variables.add(v2VarHom);
        variables.add(ph1VarHom);
        variables.add(ph2VarHom);

        if (a1Var != null) {
            variables.add(a1Var);
        }
        if (r1Var != null) {
            variables.add(r1Var);
        }

    }

    /*protected double v1() {
        return sv.get(v1Var.getRow());
    }

    protected double v2() {
        return sv.get(v2Var.getRow());
    }

    protected double ph1() {
        return sv.get(ph1Var.getRow());
    }

    protected double ph2() {
        return sv.get(ph2Var.getRow());
    }

    protected double v1Inv() {
        return sv.get(v1VarInv.getRow());
    }

    protected double v2Inv() {
        return sv.get(v2VarInv.getRow());
    }

    protected double ph1Inv() {
        return sv.get(ph1VarInv.getRow());
    }

    protected double ph2Inv() {
        return sv.get(ph2VarInv.getRow());
    }

    protected double v1Hom() {
        return sv.get(v1VarHom.getRow());
    }

    protected double v2Hom() {
        return sv.get(v2VarHom.getRow());
    }

    protected double ph1Hom() {
        return sv.get(ph1VarHom.getRow());
    }

    protected double ph2Hom() {
        return sv.get(ph2VarHom.getRow());
    }*/

    protected double v(int g, int i) {
        switch (g) {
            case 0: // homopolar
                return i == 1 ? sv.get(v1VarHom.getRow()) : sv.get(v2VarHom.getRow());

            case 1: // direct
                return i == 1 ? sv.get(v1Var.getRow()) : sv.get(v2Var.getRow());

            case 2: // inverse
                return i == 1 ? sv.get(v1VarInv.getRow()) : sv.get(v2VarInv.getRow());

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    protected double ph(int g, int i) {
        switch (g) {
            case 0: // homopolar
                return i == 1 ? sv.get(ph1VarHom.getRow()) : sv.get(ph2VarHom.getRow());

            case 1: // direct
                return i == 1 ? sv.get(ph1Var.getRow()) : sv.get(ph2Var.getRow());

            case 2: // inverse
                return i == 1 ? sv.get(ph1VarInv.getRow()) : sv.get(ph2VarInv.getRow());

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

    /*public static double theta1(double ph1, double a1, double ph2) {
        return -a1 + A2 - ph1 + ph2;
    }*/

    /*public static double theta2(double ph1, double a1, double ph2) {
        return a1 - A2 + ph1 - ph2;
    }*/

    //protected abstract double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1);

    /*@Override
    public double calculateSensi(DenseMatrix dx, int column) {
        Objects.requireNonNull(dx);
        double dph1 = dx.get(ph1Var.getRow(), column);
        double dph2 = dx.get(ph2Var.getRow(), column);
        double dv1 = dx.get(v1Var.getRow(), column);
        double dv2 = dx.get(v2Var.getRow(), column);
        double da1 = a1Var != null ? dx.get(a1Var.getRow(), column) : branch.getPiModel().getA1();
        double dr1 = r1Var != null ? dx.get(r1Var.getRow(), column) : branch.getPiModel().getR1();
        return calculateSensi(dph1, dph2, dv1, dv2, da1, dr1);
    }*/

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
