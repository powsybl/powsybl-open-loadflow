package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Load;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class StaticVarCompensatorVoltageLambdaQEquationTerm extends AbstractNamedEquationTerm {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticVarCompensatorVoltageLambdaQEquationTerm.class);

    private final List<LfStaticVarCompensatorImpl> lfStaticVarCompensators;

    private final LfBus bus;

    private final EquationSystem equationSystem;

    private final Variable vVar;

    private final Variable phiVar;

    private final List<Variable> variables;

    private double targetV;

    private double dfdv;

    private double dfdph;

    private final double sumQloads;

    private final double sumQgeneratorsWithoutVoltageRegulator;

    private final List<ShuntCompensatorReactiveFlowEquationTerm> shuntCompensatorReactiveFlowEquationTerms;

    public StaticVarCompensatorVoltageLambdaQEquationTerm(List<LfStaticVarCompensatorImpl> lfStaticVarCompensators, LfBus bus, VariableSet variableSet, EquationSystem equationSystem) {
        this.lfStaticVarCompensators = lfStaticVarCompensators;
        this.bus = bus;
        this.equationSystem = equationSystem;
        vVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_V);
        phiVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_PHI);
        variables = Arrays.asList(vVar, phiVar);

        sumQloads = getSumQloads();
        sumQgeneratorsWithoutVoltageRegulator = getSumQgeneratorsWithoutVoltageRegulator();

        shuntCompensatorReactiveFlowEquationTerms = new ArrayList<>();
        for (LfShunt lfShunt : bus.getShunts()) {
            LOGGER.debug("lfShunt.getB() = {}", lfShunt.getB());
            ShuntCompensatorReactiveFlowEquationTerm shuntCompensatorReactiveFlowEquationTerm = new ShuntCompensatorReactiveFlowEquationTerm(lfShunt, bus, variableSet);
            shuntCompensatorReactiveFlowEquationTerms.add(shuntCompensatorReactiveFlowEquationTerm);
        }
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
        if (lfStaticVarCompensators.size() > 1) {
            throw new PowsyblException("Bus PVLQ (" + bus.getId() + ") not supported yet as it contains more than one staticVarCompensator");
        }
        LfStaticVarCompensatorImpl lfStaticVarCompensator = lfStaticVarCompensators.get(0);
        double slope = lfStaticVarCompensator.getSlope();
        Equation reactiveEquation = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);

        double[] resultQbus = sumEvalAndDerOnBranchTermsFromEquationBUSQ(x);
        double sumEvalQbus = resultQbus[0];
        double sumDerQdVbus = resultQbus[1];
        double sumDerQdPHbus = resultQbus[2];

        double[] resultQshunt = sumEvalAndDerOnShuntTerms(x);
        double sumEvalQshunt = resultQshunt[0];
        double sumDerQdVshunt = resultQshunt[1];

        // Qbus = Qsvc - Qload + Qgenerator - Qshunt d'où : Q(U, theta) = Qsvc =  Qbus + Qload - Qgenerator + Qshunt
        // f(U, theta) = U + lambda * Q(U, theta)
        targetV = x[vVar.getRow()] + slope * (sumEvalQbus + sumQloads - sumQgeneratorsWithoutVoltageRegulator + sumEvalQshunt);
        // dfdU = 1 + lambda dQdU
        dfdv = 1 + slope * (sumDerQdVbus + sumDerQdVshunt);
        // dfdtheta = lambda * dQdtheta
        dfdph = slope * sumDerQdPHbus;
        LOGGER.debug("x = {} ; evalQbus = {} ; targetV = {} ; evalQbus - bus.getLoadTargetQ() = {}",
                x[vVar.getRow()] * bus.getNominalV(), sumEvalQbus * PerUnit.SB, targetV * bus.getNominalV(), (sumEvalQbus - bus.getLoadTargetQ()) * PerUnit.SB);
    }

    /**
     *
     * @param x variable value vector initialize with a VoltageInitializer
     * @return sum evaluation and derivatives on branch terms from BUS_Q equation
     */
    private double[] sumEvalAndDerOnBranchTermsFromEquationBUSQ(double[] x) {
        Equation reactiveEquation = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);
        double sumEvalQbus = 0;
        double sumDerQdVbus = 0;
        double sumDerQdPHbus = 0;

        for (EquationTerm equationTerm : reactiveEquation.getTerms()) {
            if (equationTerm.isActive() &&
                    (equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                            || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm)) {
                equationTerm.update(x);
                sumEvalQbus += equationTerm.eval();
                sumDerQdVbus += equationTerm.der(vVar);
                sumDerQdPHbus += equationTerm.der(phiVar);
            }
        }
        return new double[]{sumEvalQbus, sumDerQdVbus, sumDerQdPHbus};
    }

    private double getSumQloads() {
        double sumQloads = 0;
        LOGGER.trace("abstractLfBus.getTargetQ() = {} ; abstractLfBus.getCalculatedQ() = {} ; abstractLfBus.getLoadTargetQ() = {} ; abstractLfBus.getGenerationTargetQ() = {}",
                bus.getTargetQ(), bus.getCalculatedQ(), bus.getLoadTargetQ(), bus.getGenerationTargetQ());
        for (Load load : bus.getLoads()) {
            LOGGER.debug("load.getQ0() = {} ; load.getTerminal().getQ() = {}", load.getQ0(), load.getTerminal().getQ());
            sumQloads += load.getQ0();
        }
        return sumQloads / PerUnit.SB;
    }

    private double getSumQgeneratorsWithoutVoltageRegulator() {
        double sumQgenerators = 0;
        for (LfGenerator lfGenerator : bus.getGenerators()) {
            if (!lfGenerator.hasVoltageControl()) {
                LOGGER.debug("lfGenerator.getTargetQ() = {} ; lfGenerator.getCalculatedQ() = {}", lfGenerator.getTargetQ(), lfGenerator.getCalculatedQ());
                sumQgenerators += lfGenerator.getTargetQ();
            }
        }
        return sumQgenerators;
    }

    private double[] sumEvalAndDerOnShuntTerms(double[] x) {
        double sumEvalQshunt = 0;
        double sumDerQdVshunt = 0;
        for (ShuntCompensatorReactiveFlowEquationTerm shuntCompensatorReactiveFlowEquationTerm : shuntCompensatorReactiveFlowEquationTerms) {
            shuntCompensatorReactiveFlowEquationTerm.update(x);
            sumEvalQshunt += shuntCompensatorReactiveFlowEquationTerm.eval();
            sumDerQdVshunt += shuntCompensatorReactiveFlowEquationTerm.der(vVar);
        }
        return new double[]{sumEvalQshunt, sumDerQdVshunt};
    }

    @Override
    public double eval() {
        return targetV;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dfdv;
        } else if (variable.equals(phiVar)) {
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
