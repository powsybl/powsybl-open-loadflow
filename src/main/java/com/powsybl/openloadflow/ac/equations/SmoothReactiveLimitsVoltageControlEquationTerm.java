/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.AbstractEquationTerm;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.SingleEquationTerm;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Smooth Q/V characteristic for generator voltage control with reactive limits.
 *
 * <p>The residual is:</p>
 * <pre>
 * Q - Qmid - Qhalf * s((Vtarget - V) / Vscale) = 0
 * </pre>
 *
 * <p>{@code s} is a normalized tanh clipped to {@code [-1, 1]} at a finite
 * voltage error. At a root, Q is inside {@code [Qmin, Qmax]} by construction.
 * Near the target voltage, the curve behaves like a steep voltage-control
 * characteristic. Near reactive limits, it becomes a fixed reactive-power
 * equation and lets voltage move away from the target.</p>
 */
public class SmoothReactiveLimitsVoltageControlEquationTerm extends AbstractEquationTerm<AcVariableType, AcEquationType> {

    private static final double DEFAULT_VOLTAGE_SCALE = 0.0075;

    private static final double TANH_SATURATION_X = 2.0;

    private static final double TANH_SATURATION_VALUE = FastMath.tanh(TANH_SATURATION_X);

    private static final double MIN_Q_RANGE = 0.10;

    private final LfBus controlledBus;

    private final SingleEquationTerm<AcVariableType, AcEquationType> controlledV;

    private final List<ControllerQ> controllers;

    private final List<SingleEquationTerm<AcVariableType, AcEquationType>> children;

    private final List<Variable<AcVariableType>> variables;

    private final double minQ;

    private final double maxQ;

    private final double voltageScale;

    public SmoothReactiveLimitsVoltageControlEquationTerm(LfBus controlledBus,
                                                          SingleEquationTerm<AcVariableType, AcEquationType> controlledV,
                                                          List<ControllerQ> controllers) {
        this(controlledBus, controlledV, controllers, DEFAULT_VOLTAGE_SCALE);
    }

    SmoothReactiveLimitsVoltageControlEquationTerm(LfBus controlledBus,
                                                   SingleEquationTerm<AcVariableType, AcEquationType> controlledV,
                                                   List<ControllerQ> controllers,
                                                   double voltageScale) {
        super(!Objects.requireNonNull(controlledBus).isDisabled());
        this.controlledBus = controlledBus;
        this.controlledV = Objects.requireNonNull(controlledV);
        this.controllers = List.copyOf(Objects.requireNonNull(controllers));
        if (this.controllers.isEmpty()) {
            throw new IllegalArgumentException("At least one controller bus is required");
        }
        this.children = List.of(this.controlledV);
        this.variables = createVariables(this.controlledV, this.controllers);
        QRange qRange = computeQRange(this.controllers);
        this.minQ = qRange.minQ();
        this.maxQ = qRange.maxQ();
        if (maxQ <= minQ) {
            throw new IllegalArgumentException("Inconsistent reactive limits for controlled bus '" + controlledBus.getId() + "'");
        }
        this.voltageScale = voltageScale;
    }

    private static List<Variable<AcVariableType>> createVariables(SingleEquationTerm<AcVariableType, AcEquationType> controlledV,
                                                                  List<ControllerQ> controllers) {
        Set<Variable<AcVariableType>> variables = new LinkedHashSet<>();
        variables.addAll(controlledV.getVariables());
        for (ControllerQ controller : controllers) {
            for (EquationTerm<AcVariableType, AcEquationType> term : controller.controllerQEquation().getTerms()) {
                variables.addAll(term.getVariables());
            }
        }
        return List.copyOf(variables);
    }

    private static QRange computeQRange(List<ControllerQ> controllers) {
        if (controllers.size() == 1) {
            LfBus controllerBus = controllers.get(0).controllerBus();
            return normalizeQRange(controllerBus.getMinQ(), controllerBus.getMaxQ());
        }

        double minQ = Double.NEGATIVE_INFINITY;
        double maxQ = Double.POSITIVE_INFINITY;
        for (ControllerQ controller : controllers) {
            double percent = controller.controllerBus().getRemoteControlReactivePercent();
            if (percent > 0) {
                QRange qRange = normalizeQRange(controller.controllerBus().getMinQ(), controller.controllerBus().getMaxQ());
                minQ = FastMath.max(minQ, qRange.minQ() / percent);
                maxQ = FastMath.min(maxQ, qRange.maxQ() / percent);
            }
        }
        if (!Double.isFinite(minQ) || !Double.isFinite(maxQ) || maxQ <= minQ) {
            minQ = controllers.stream().map(ControllerQ::controllerBus)
                    .map(SmoothReactiveLimitsVoltageControlEquationTerm::normalizeBusQRange)
                    .mapToDouble(QRange::minQ)
                    .sum();
            maxQ = controllers.stream().map(ControllerQ::controllerBus)
                    .map(SmoothReactiveLimitsVoltageControlEquationTerm::normalizeBusQRange)
                    .mapToDouble(QRange::maxQ)
                    .sum();
        }
        return normalizeQRange(minQ, maxQ);
    }

    private static QRange normalizeBusQRange(LfBus bus) {
        return normalizeQRange(bus.getMinQ(), bus.getMaxQ());
    }

    private static QRange normalizeQRange(double q1, double q2) {
        double minQ = FastMath.min(q1, q2);
        double maxQ = FastMath.max(q1, q2);
        if (maxQ - minQ < MIN_Q_RANGE) {
            double center = (minQ + maxQ) / 2;
            return new QRange(center - MIN_Q_RANGE / 2, center + MIN_Q_RANGE / 2);
        }
        return new QRange(minQ, maxQ);
    }

    @Override
    public List<SingleEquationTerm<AcVariableType, AcEquationType>> getChildren() {
        return children;
    }

    @Override
    public void setStateVector(StateVector sv) {
        super.setStateVector(sv);
        controlledV.setStateVector(sv);
    }

    @Override
    public void setEquation(Equation<AcVariableType, AcEquationType> equation) {
        super.setEquation(equation);
        controlledV.setEquation(equation);
    }

    @Override
    public ElementType getElementType() {
        return controlledBus.getType();
    }

    @Override
    public int getElementNum() {
        return controlledBus.getNum();
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    private double q() {
        double q = 0;
        for (ControllerQ controller : controllers) {
            q += controller.controllerQEquation().eval() + controller.controllerBus().getLoadTargetQ();
        }
        return q;
    }

    private double dq(Variable<AcVariableType> variable) {
        double dq = 0;
        for (ControllerQ controller : controllers) {
            for (EquationTerm<AcVariableType, AcEquationType> term : controller.controllerQEquation().getTerms()) {
                if (term.isActive() && term.getVariables().contains(variable)) {
                    dq += term.der(variable);
                }
            }
        }
        return dq;
    }

    private double targetV() {
        return controlledBus.getHighestPriorityTargetV()
                .orElseThrow(() -> new IllegalStateException("No active voltage control has been found for bus '" + controlledBus.getId() + "'"));
    }

    private static double characteristic(double x) {
        if (x >= TANH_SATURATION_X) {
            return 1;
        } else if (x <= -TANH_SATURATION_X) {
            return -1;
        }
        return FastMath.tanh(x) / TANH_SATURATION_VALUE;
    }

    private static double dCharacteristic(double x) {
        if (x >= TANH_SATURATION_X || x <= -TANH_SATURATION_X) {
            return 0;
        }
        double tanh = FastMath.tanh(x);
        return (1 - tanh * tanh) / TANH_SATURATION_VALUE;
    }

    private double midQ() {
        return (minQ + maxQ) / 2;
    }

    private double halfQRange() {
        return (maxQ - minQ) / 2;
    }

    @Override
    public double eval() {
        double voltageError = (targetV() - controlledV.eval()) / voltageScale;
        return q() - midQ() - halfQRange() * characteristic(voltageError);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        double voltageError = (targetV() - controlledV.eval()) / voltageScale;
        double dVoltageError = controlledV.getVariables().contains(variable) ? -controlledV.der(variable) / voltageScale : 0;
        return dq(variable) - halfQRange() * dCharacteristic(voltageError) * dVoltageError;
    }

    @Override
    public double calculateSensi(DenseMatrix x, int column) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void write(Writer writer) throws IOException {
        writer.write("smoothReactiveLimitsVoltageControl(");
        writer.write(controlledBus.getId());
        writer.write(")");
    }

    public record ControllerQ(LfBus controllerBus,
                              Equation<AcVariableType, AcEquationType> controllerQEquation) {
    }

    private record QRange(double minQ, double maxQ) {
    }
}
