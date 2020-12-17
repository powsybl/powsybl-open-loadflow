package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class StaticVarCompensatorVoltageLambdaQEquationTerm extends AbstractNamedEquationTerm {

    private final List<LfStaticVarCompensatorImpl> lfStaticVarCompensators;

    private final LfBus bus;

    private final Variable vVar;

    private final List<Variable> variables;

    private double v;

    private double dvdq;

    // TODO : peut il y avoir plusieurs StaticVarCompensator dans un bus ?
    public StaticVarCompensatorVoltageLambdaQEquationTerm(List<LfStaticVarCompensatorImpl> lfStaticVarCompensators, LfBus bus, VariableSet variableSet) {
        this.lfStaticVarCompensators = Objects.requireNonNull(lfStaticVarCompensators);
        this.bus = Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);
        vVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_V);
        variables = Collections.singletonList(vVar);
    }

    @Override
    public SubjectType getSubjectType() {
        return SubjectType.BUS;
    }

    @Override
    public int getSubjectNum() {
        // TODO : devons nous créer la méthode getNum dans LfStaticVarCompensatorImpl ? si plusieurs LfStaticVarCompensatorImpl dans le bus ?
        return bus.getNum();
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        v = 0;
        // TODO : comment calculer v et lambda sur le bus si il y a plusieurs StaticVarCompensator dans le bus
        for (LfStaticVarCompensatorImpl lfStaticVarCompensator : lfStaticVarCompensators) {
            // TODO : Q(U, theta)
            double q = 0;
            // TODO : f(U, theta) = U + lambda * Q(U, theta)
            // TODO : pour lambda * Q(U, theta), utiliser EquationTerm.multiply(terme Q(U, theta), lambda)
            v += x[vVar.getRow()] + lfStaticVarCompensator.getVoltagePerReactivePowerControl().getSlope() * q;
        }
        // TODO : dfdU = 1 + lambda dQdU
        dvdq = 1;
        // TODO : faut il modifier les équations sur les branches ? dfdtheta = lambda * dQdtheta ?
    }

    @Override
    public double eval() {
        return v;
    }

    @Override
    public double der(Variable variable) {
        return dvdq;
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
