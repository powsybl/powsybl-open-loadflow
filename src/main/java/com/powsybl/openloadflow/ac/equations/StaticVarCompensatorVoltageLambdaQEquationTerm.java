package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final double sumQgeneratorsWithoutVoltageRegulator;

    public StaticVarCompensatorVoltageLambdaQEquationTerm(List<LfStaticVarCompensatorImpl> lfStaticVarCompensators, LfBus bus, VariableSet variableSet, EquationSystem equationSystem) {
        this.lfStaticVarCompensators = lfStaticVarCompensators;
        this.bus = bus;
        this.equationSystem = equationSystem;

        if (lfStaticVarCompensators.size() > 1) {
            throw new PowsyblException("Bus PVLQ (" + bus.getId() + ") not supported yet as it contains more than one staticVarCompensator");
        }

        vVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_V);
        phiVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_PHI);
        variables = Arrays.asList(vVar, phiVar);

        sumQgeneratorsWithoutVoltageRegulator = getSumQgeneratorsWithoutVoltageRegulator();
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
        LfStaticVarCompensatorImpl lfStaticVarCompensator = lfStaticVarCompensators.get(0);
        double slope = lfStaticVarCompensator.getSlope();
        Equation reactiveEquation = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);

        EvalAndDerOnTermsFromEquationBUSQ evalAndDerOnTermsFromEquationBUSQ = evalAndDerOnTermsFromEquationBUSQ(x, reactiveEquation);

        // given : QbusMinusShunts = Qsvcs - QloadsAndBatteries + Qgenerators
        // then : Q(U, theta) = Qsvcs =  QbusMinusShunts + QloadsAndBatteries - Qgenerators
        double qSvc = evalAndDerOnTermsFromEquationBUSQ.qBusMinusShunts + bus.getLoadTargetQ() - sumQgeneratorsWithoutVoltageRegulator;
        // f(U, theta) = U + lambda * Q(U, theta)
        targetV = x[vVar.getRow()] + slope * qSvc;
        // dfdU = 1 + lambda dQdU
        // Q remains constant for loads, batteries and generators, then derivative of Q is zero for this items
        dfdv = 1 + slope * evalAndDerOnTermsFromEquationBUSQ.dQdVbusMinusShunts;
        // dfdtheta = lambda * dQdtheta
        dfdph = slope * evalAndDerOnTermsFromEquationBUSQ.dQdPHbusMinusShunts;
        LOGGER.trace("x = {} ; evalQbus = {} ; targetV = {} ; evalQbus - bus.getLoadTargetQ() = {}",
                x[vVar.getRow()] * bus.getNominalV(), evalAndDerOnTermsFromEquationBUSQ.qBusMinusShunts * PerUnit.SB, targetV * bus.getNominalV(),
                (evalAndDerOnTermsFromEquationBUSQ.qBusMinusShunts - bus.getLoadTargetQ()) * PerUnit.SB);
    }

    public boolean hasToEvalAndDerTerm(EquationTerm equationTerm) {
        return equationTerm.isActive() &&
                (equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                        || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm
                        || equationTerm instanceof OpenBranchSide1ReactiveFlowEquationTerm
                        || equationTerm instanceof OpenBranchSide2ReactiveFlowEquationTerm
                        || equationTerm instanceof ShuntCompensatorReactiveFlowEquationTerm);
    }

    public boolean hasPhiVar(EquationTerm equationTerm) {
        return equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm;
    }

    private class EvalAndDerOnTermsFromEquationBUSQ {
        private final double qBusMinusShunts;
        private final double dQdVbusMinusShunts;
        private final double dQdPHbusMinusShunts;

        public EvalAndDerOnTermsFromEquationBUSQ(double qBusMinusShunts, double dQdVbusMinusShunts, double dQdPHbusMinusShunts) {
            this.qBusMinusShunts = qBusMinusShunts;
            this.dQdVbusMinusShunts = dQdVbusMinusShunts;
            this.dQdPHbusMinusShunts = dQdPHbusMinusShunts;
        }
    }

    /**
     *
     * @param x vector of variable values initialized with a VoltageInitializer
     * @return sum evaluation and derivatives on branch and shunt terms from BUS_Q equation
     */
    private EvalAndDerOnTermsFromEquationBUSQ evalAndDerOnTermsFromEquationBUSQ(double[] x, Equation reactiveEquation) {
        double qBusMinusShunts = 0;
        double dQdVbusMinusShunts = 0;
        double dQdPHbusMinusShunts = 0;

        for (EquationTerm equationTerm : reactiveEquation.getTerms()) {
            if (hasToEvalAndDerTerm(equationTerm)) {
                equationTerm.update(x);
                qBusMinusShunts += equationTerm.eval();
                dQdVbusMinusShunts += equationTerm.der(vVar);
                if (hasPhiVar(equationTerm)) {
                    dQdPHbusMinusShunts += equationTerm.der(phiVar);
                }
            }
        }
        return new EvalAndDerOnTermsFromEquationBUSQ(qBusMinusShunts, dQdVbusMinusShunts, dQdPHbusMinusShunts);
    }

    private double getSumQgeneratorsWithoutVoltageRegulator() {
        double sumQgenerators = 0;
        for (LfGenerator lfGenerator : bus.getGenerators()) {
            if (!lfGenerator.hasVoltageControl()) {
                LOGGER.trace("lfGenerator.getTargetQ() = {} ; lfGenerator.getCalculatedQ() = {}", lfGenerator.getTargetQ(), lfGenerator.getCalculatedQ());
                sumQgenerators += lfGenerator.getTargetQ();
            }
        }
        return sumQgenerators;
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
        return "ac_static_var_compensator_with_slope";
    }
}
