package com.powsybl.openloadflow.reduction.equations;

import com.google.common.collect.ImmutableList;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;

import java.util.List;
import java.util.Objects;
import java.util.*;

/**
 * @author Jean-Baptiste Heyberger <jean-baptiste.heyberger at rte-france.com>
 */
public class ClosedBranchReductionEquationTerm extends AbstractNamedEquationTerm {
    protected final LfBranch branch;

    protected final Variable v1rVar;

    protected final Variable v1iVar;

    protected final Variable v2rVar;

    protected final Variable v2iVar;

    protected final int typeTerm;

    protected final List<Variable> variables;

    protected Map<Variable, Double> varCoefs;

    public static ClosedBranchReductionEquationTerm create(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet, int typeTerm) {
        Objects.requireNonNull(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);
        return new ClosedBranchReductionEquationTerm(branch, bus1, bus2, variableSet, typeTerm);
    }

    protected ClosedBranchReductionEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet variableSet, int typeTerm) {
        this.branch = Objects.requireNonNull(branch);
        this.typeTerm = typeTerm;
        varCoefs = new HashMap<>(); //TODO: check when it is updated, check if empty? Maybe use List<var,coef> instead of List<var>
        PiModel piModel = branch.getPiModel();
        if (piModel.getX() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has reactance equal to zero");
        }

        v1rVar = variableSet.getVariable(bus1.getNum(), VariableType.BUS_VR);
        v2rVar = variableSet.getVariable(bus2.getNum(), VariableType.BUS_VR);
        v1iVar = variableSet.getVariable(bus1.getNum(), VariableType.BUS_VI);
        v2iVar = variableSet.getVariable(bus2.getNum(), VariableType.BUS_VI);

        //set admittance values for each equation term
        setVarCoefs();
        ImmutableList.Builder<Variable> variablesBuilder = ImmutableList.<Variable>builder().add(v1rVar, v2rVar, v1iVar, v2iVar);
        variables = variablesBuilder.build();
    }

    protected void setVarCoefs() {

        double g12 = 0;
        double b12 = 0;
        double g1g12sum = 0;
        double b1b12sum = 0;
        double g21 = 0;
        double b21 = 0;
        double g2g21sum = 0;
        double b2b21sum = 0;

        PiModel piM = branch.getPiModel();
        double rho = piM.getR1();
        if (piM.getZ() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has Z equal to zero");
        }
        double zInvSquare = 1 / (piM.getZ() * piM.getZ());
        double r = piM.getR();
        double x = piM.getX();
        double alpha = piM.getA1();
        double cosA = Math.cos(Math.toRadians(alpha));
        double sinA = Math.sin(Math.toRadians(alpha));
        double gPi1 = piM.getG1();
        double bPi1 = piM.getB1();
        double gPi2 = piM.getG2();
        double bPi2 = piM.getB2();

        g12 = rho * zInvSquare * (r * cosA + x * sinA);
        b12 = -rho * zInvSquare * (x * cosA + r * sinA);
        g1g12sum = rho * rho * (gPi1 + r * zInvSquare);
        b1b12sum = rho * rho * (bPi1 - x * zInvSquare);

        g21 = g12;
        b21 = rho * zInvSquare * (r * sinA - x * cosA);
        g2g21sum = r * zInvSquare + gPi2;
        b2b21sum = -x * zInvSquare + bPi2;

        if (typeTerm == 1) {
            varCoefs.put(v1rVar, g1g12sum);
            varCoefs.put(v2rVar, -g12);
            varCoefs.put(v1iVar, -b1b12sum);
            varCoefs.put(v2iVar, b12);
        } else if (typeTerm == 2) {
            varCoefs.put(v1rVar, b1b12sum);
            varCoefs.put(v2rVar, -b12);
            varCoefs.put(v1iVar, g1g12sum);
            varCoefs.put(v2iVar, -g12);
        } else if (typeTerm == 3) {
            varCoefs.put(v1rVar, -g21);
            varCoefs.put(v2rVar, g2g21sum);
            varCoefs.put(v1iVar, b21);
            varCoefs.put(v2iVar, -b2b21sum);
        } else if (typeTerm == 4) {
            varCoefs.put(v1rVar, -b21);
            varCoefs.put(v2rVar, b2b21sum);
            varCoefs.put(v1iVar, -g21);
            varCoefs.put(v2iVar, g2g21sum);
        }
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BRANCH;
    }

    @Override
    public int getElementNum() {
        return branch.getNum();
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    @Override
    protected String getName() {
        return "y_p_1";
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        /*check if implmentation is necessary*/
    }

    @Override
    public double eval() {
        return 0.;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        return varCoefs.get(variable);
    }

    @Override
    public double rhs() {
        return 0.;
    }

}
