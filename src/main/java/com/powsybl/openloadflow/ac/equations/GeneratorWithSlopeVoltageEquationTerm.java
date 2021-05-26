/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class GeneratorWithSlopeVoltageEquationTerm extends AbstractNamedEquationTerm {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratorWithSlopeVoltageEquationTerm.class);

    private final List<LfGenerator> generators;

    private double slope; //only one generator with slope supported.

    private final LfBus bus;

    private final EquationSystem equationSystem;

    private final Variable vVar;

    private final Variable phiVar;

    private final List<Variable> variables;

    private double calculatedTargetV;

    private double dcalculatedTargetVdv;

    private double dcalculatedTargetVdph;

    private double sumTargetQGenerators;

    public GeneratorWithSlopeVoltageEquationTerm(List<LfGenerator> generators, LfBus bus, VariableSet variableSet, EquationSystem equationSystem, double initialCalculatedTargetV) {
        this.generators = generators;
        this.bus = bus;
        this.equationSystem = equationSystem;

        if (generators.size() > 1) {
            throw new PowsyblException("Several generators with a non-zero slope are controlling the bus " + bus.getId() + ": not supported yet");
        } else {
            slope = generators.get(0).getSlope();
        }

        vVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_V);
        phiVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_PHI);
        variables = Arrays.asList(vVar, phiVar);

        calculatedTargetV = initialCalculatedTargetV;

        sumTargetQGenerators = getSumTargetQGenerators();
    }

    @Override
    protected String getName() {
        return null;
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BUS;
    }

    @Override
    public int getElementNum() {
        return bus.getNum();
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);

        Equation qBus = (Equation) bus.getQ();

        EvalAndDerOnTermsFromEquationBUSQ evalAndDerOnTermsFromEquationBUSQ = evalAndDerOnTermsFromEquationBUSQ(x, qBus);

        // given : QbusMinusShunts = Qsvcs - QloadsAndBatteries + Qgenerators
        // then : Q(U, theta) = Qsvcs =  QbusMinusShunts + QloadsAndBatteries - Qgenerators
        double qSvc = evalAndDerOnTermsFromEquationBUSQ.qBusMinusShunts + bus.getLoadTargetQ() - sumTargetQGenerators;
        // f(U, theta) = U + lambda * Q(U, theta)
        calculatedTargetV = x[vVar.getRow()] + slope * qSvc;
        // dfdU = 1 + lambda dQdU
        // Q remains constant for loads, batteries and generators, then derivative of Q is zero for this items
        dcalculatedTargetVdv = 1 + slope * evalAndDerOnTermsFromEquationBUSQ.dQdVbusMinusShunts;
        // dfdtheta = lambda * dQdtheta
        dcalculatedTargetVdph = slope * evalAndDerOnTermsFromEquationBUSQ.dQdPHbusMinusShunts;
    }

    @Override
    public double eval() {
        return calculatedTargetV;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dcalculatedTargetVdv;
        } else if (variable.equals(phiVar)) {
            return dcalculatedTargetVdph;
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

    private boolean hasToEvalAndDerTerm(EquationTerm equationTerm) {
        return equationTerm.isActive() &&
                (equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                        || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm
                        || equationTerm instanceof OpenBranchSide1ReactiveFlowEquationTerm
                        || equationTerm instanceof OpenBranchSide2ReactiveFlowEquationTerm
                        || equationTerm instanceof ShuntCompensatorReactiveFlowEquationTerm);
    }

    private boolean hasPhiVar(EquationTerm equationTerm) {
        return equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm;
    }

    private EvalAndDerOnTermsFromEquationBUSQ evalAndDerOnTermsFromEquationBUSQ(double[] x, Equation qBus) {
        double qBusMinusShunts = 0;
        double dQdVbusMinusShunts = 0;
        double dQdPHbusMinusShunts = 0;

        for (EquationTerm equationTerm : qBus.getTerms()) {
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

    private double getSumTargetQGenerators() {
        double sumTargetQGenerators = 0;
        for (LfGenerator lfGenerator : bus.getGenerators()) {
            if (!lfGenerator.hasVoltageControl()) {
                sumTargetQGenerators += lfGenerator.getTargetQ();
            }
        }
        return sumTargetQGenerators;
    }
}
