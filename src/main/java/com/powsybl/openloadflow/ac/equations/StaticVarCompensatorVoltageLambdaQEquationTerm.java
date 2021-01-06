package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class StaticVarCompensatorVoltageLambdaQEquationTerm extends AbstractNamedEquationTerm {

    private final List<LfStaticVarCompensatorImpl> lfStaticVarCompensators;

    private final LfBus bus;

    private final EquationSystem equationSystem;

    private final Variable vVar;

    private final Variable phVar;

    private final List<Variable> variables;

    private double x;

    private double dfdU;

    private double dfdph;

    public StaticVarCompensatorVoltageLambdaQEquationTerm(List<LfStaticVarCompensatorImpl> lfStaticVarCompensators, LfBus bus, VariableSet variableSet, EquationSystem equationSystem) {
        this.lfStaticVarCompensators = Objects.requireNonNull(lfStaticVarCompensators);
        this.bus = Objects.requireNonNull(bus);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        Objects.requireNonNull(variableSet);
        vVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_V);
        phVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_PHI);
        variables = Arrays.asList(vVar, phVar);
    }

    @Override
    public SubjectType getSubjectType() {
        return SubjectType.BUS;
    }

    @Override
    public int getSubjectNum() {
        return bus.getNum();
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        this.x = x[vVar.getRow()];
    }

    @Override
    public double eval() {
        double v = 0;
        // TODO : comment calculer v et lambda sur le bus si il y a plusieurs StaticVarCompensator dans le bus
        for (LfStaticVarCompensatorImpl lfStaticVarCompensator : lfStaticVarCompensators) {
            double slope = lfStaticVarCompensator.getVoltagePerReactivePowerControl().getSlope();

            Equation branchReactiveEquation = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);
            System.out.println("TODO : use Q from svc terminal ? " + lfStaticVarCompensator.getSvc().getTerminal().getQ() + " ; " + lfStaticVarCompensator.getTargetQ() + " ; " + lfStaticVarCompensator.getCalculatedQ());
            double q = branchReactiveEquation.eval();

            // f(U, theta) = U + lambda * Q(U, theta)
            // TODO : pour lambda * Q(U, theta), utiliser EquationTerm.multiply(terme Q(U, theta), lambda)
            v = x + slope * q;

            double dqdv = branchReactiveEquation.getTerms().stream().filter(term -> term.getVariables().contains(vVar)).map(term -> term.der(vVar)).reduce(0d, (d1, d2) -> d1 + d2);
            // dfdU = 1 + lambda dQdU
            dfdU = 1 + slope * dqdv;
            double dqdph = branchReactiveEquation.getTerms().stream().filter(term -> term.getVariables().contains(phVar)).map(term -> term.der(phVar)).reduce(0d, (d1, d2) -> d1 + d2);
            // dfdtheta = lambda * dQdtheta
            dfdph = slope * dqdph;
        }
        return v;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dfdU;
        } else if (variable.equals(phVar)) {
            return dfdph;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    @Override
    public double rhs() {
        return 0;
    }

    @Override
    protected String getName() {
        return "ac_static_var_compensator";
    }
}
