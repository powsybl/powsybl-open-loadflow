/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcTargetVector extends TargetVector<AcVariableType, AcEquationType> {

    private static double getBusTargetV(LfBus bus) {
        Objects.requireNonNull(bus);
        double targetV = bus.getHighestPriorityMainVoltageControl()
                .map(Control::getTargetValue)
                .orElseThrow(() -> new IllegalStateException("No active voltage control has been found for bus '" + bus.getId() + "'"));
        if (bus.hasGeneratorsWithSlope()) {
            // take first generator with slope: network loading ensures that there's only one generator with slope
            double slope = bus.getGeneratorsControllingVoltageWithSlope().get(0).getSlope();
            targetV -= slope * (bus.getLoadTargetQ() - bus.getGenerationTargetQ());
        }
        return targetV;
    }

    private static double getReactivePowerDistributionTarget(LfNetwork network, int busNum) {
        LfBus controllerBus = network.getBus(busNum);
        double target = (controllerBus.getRemoteVoltageControlReactivePercent() - 1) * controllerBus.getTargetQ();
        for (LfBus otherControllerBus : controllerBus.getGeneratorVoltageControl().orElseThrow().getMergedControllerElements()) {
            if (otherControllerBus != controllerBus) {
                target += controllerBus.getRemoteVoltageControlReactivePercent() * otherControllerBus.getTargetQ();
            }
        }
        return target;
    }

    private static double getReactivePowerControlTarget(LfBranch branch) {
        Objects.requireNonNull(branch);
        return branch.getReactivePowerControl().map(ReactivePowerControl::getTargetValue)
                .orElseThrow(() -> new PowsyblException("Branch '" + branch.getId() + "' has no target in for reactive remote control"));
    }

    public static void init(Equation<AcVariableType, AcEquationType> equation, LfNetwork network, double[] targets) {
        switch (equation.getType()) {
            case BUS_TARGET_P:
                // at this stage getTargetP() returns both generation and load effects at bus in case of a balanced load flow
                // but in the case of an unbalanced load flow it contains only the generation effects
                // because loads depends on variables with the Fortescue transform and might be handled as equation terms instead
                // However, in some specific cases, additional targets terms might be necessary:

                // in the case the bus is modeled with ABC variables, the load is of constant current type,
                // and supposing the nodal balances are modeled using current for the positive sequence,
                // then the targed is a current fixed value
                targets[equation.getColumn()] = network.getBus(equation.getElementNum()).getTargetP()
                        + getFortescueTarget(network.getBus(equation.getElementNum()), ComplexPart.REAL, Fortescue.SequenceType.POSITIVE);
                break;

            case BUS_TARGET_Q:
                // see the comment for BUS_TARGET_P
                targets[equation.getColumn()] = network.getBus(equation.getElementNum()).getTargetQ()
                        + getFortescueTarget(network.getBus(equation.getElementNum()), ComplexPart.IMAGINARY, Fortescue.SequenceType.POSITIVE);
                break;

            case BUS_TARGET_V:
                targets[equation.getColumn()] = getBusTargetV(network.getBus(equation.getElementNum()));
                break;

            case BUS_TARGET_PHI:
                targets[equation.getColumn()] = 0;
                break;

            case SHUNT_TARGET_B:
                targets[equation.getColumn()] = network.getShunt(equation.getElementNum()).getB();
                break;

            case BRANCH_TARGET_P:
                targets[equation.getColumn()] = LfBranch.getDiscretePhaseControlTarget(network.getBranch(equation.getElementNum()), TransformerPhaseControl.Unit.MW);
                break;

            case BRANCH_TARGET_Q:
                targets[equation.getColumn()] = getReactivePowerControlTarget(network.getBranch(equation.getElementNum()));
                break;

            case BRANCH_TARGET_ALPHA1:
                targets[equation.getColumn()] = network.getBranch(equation.getElementNum()).getPiModel().getA1();
                break;

            case BRANCH_TARGET_RHO1:
                targets[equation.getColumn()] = network.getBranch(equation.getElementNum()).getPiModel().getR1();
                break;

            case DISTR_Q:
                targets[equation.getColumn()] = getReactivePowerDistributionTarget(network, equation.getElementNum());
                break;

            case ZERO_V:
                targets[equation.getColumn()] = 0;
                break;

            case ZERO_PHI:
                targets[equation.getColumn()] = LfBranch.getA(network.getBranch(equation.getElementNum()));
                break;

            case BUS_TARGET_IX_ZERO:
                // see the comment for BUS_TARGET_P
                targets[equation.getColumn()] = getFortescueTarget(network.getBus(equation.getElementNum()), ComplexPart.REAL, Fortescue.SequenceType.ZERO);
                break;

            case BUS_TARGET_IY_ZERO:
                // see the comment for BUS_TARGET_P
                targets[equation.getColumn()] = getFortescueTarget(network.getBus(equation.getElementNum()), ComplexPart.IMAGINARY, Fortescue.SequenceType.ZERO);
                break;

            case BUS_TARGET_IX_NEGATIVE:
                // see the comment for BUS_TARGET_P
                targets[equation.getColumn()] = getFortescueTarget(network.getBus(equation.getElementNum()), ComplexPart.REAL, Fortescue.SequenceType.NEGATIVE);
                break;

            case BUS_TARGET_IY_NEGATIVE:
                // see the comment for BUS_TARGET_P
                targets[equation.getColumn()] = getFortescueTarget(network.getBus(equation.getElementNum()), ComplexPart.IMAGINARY, Fortescue.SequenceType.NEGATIVE);
                break;

            case DISTR_RHO:
            case DISTR_SHUNT_B:
            case DUMMY_TARGET_P:
            case DUMMY_TARGET_Q:
            case BUS_DISTR_SLACK_P:
                targets[equation.getColumn()] = 0;
                break;

            default:
                throw new IllegalStateException("Unknown state variable type: " + equation.getType());
        }

        targets[equation.getColumn()] -= equation.rhs();
    }

    public static double getFortescueTarget(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType) {
        LfAsymBus asymBus = bus.getAsym();
        Complex target = new Complex(0., 0.);
        if (asymBus != null) {
            // we use the detection of the asymmetry extension at bus to check if we are in asymmetrical calculation
            // in this case, load target is set to zero and the constant-balanced load model (in 3 phased representation) is replaced by a model depending on v1, v2, v0 (equivalent fortescue representation)
            LegConnectionType legConnectionType = null;
            if (asymBus.getLoadWye1() != null) {
                legConnectionType = LegConnectionType.Y_GROUNDED;
            } else if (asymBus.getLoadDelta1() != null) {
                legConnectionType = LegConnectionType.DELTA;
            }

            if (legConnectionType != null) {
                if (sequenceType == Fortescue.SequenceType.ZERO) {
                    target = asymBus.getIzeroTarget(legConnectionType).multiply(-1.);
                } else if (sequenceType == Fortescue.SequenceType.POSITIVE) {
                    target = asymBus.getIpositiveTarget(legConnectionType).multiply(-1.);
                } else {
                    target = asymBus.getInegativeTarget(legConnectionType).multiply(-1.);
                }
            }
        }

        if (complexPart == ComplexPart.REAL) {
            return target.getReal();
        } else {
            return target.getImaginary();
        }
    }

    public AcTargetVector(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        super(network, equationSystem, AcTargetVector::init);
    }
}
