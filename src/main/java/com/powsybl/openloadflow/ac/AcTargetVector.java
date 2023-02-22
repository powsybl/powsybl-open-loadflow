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

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcTargetVector extends TargetVector<AcVariableType, AcEquationType> {

    private static double getBusTargetV(LfBus bus) {
        Objects.requireNonNull(bus);
        return bus.getShuntVoltageControl().filter(dvc -> bus.isShuntVoltageControlled())
                .map(ShuntVoltageControl::getTargetValue)
                .orElse(bus.getTransformerVoltageControl().filter(dvc -> bus.isTransformerVoltageControlled())
                        .map(TransformerVoltageControl::getTargetValue)
                        .orElse(getVoltageControlledTargetValue(bus).orElse(Double.NaN)));
    }

    private static Optional<Double> getVoltageControlledTargetValue(LfBus bus) {
        return bus.getGeneratorVoltageControl().filter(vc -> bus.isGeneratorVoltageControlled()).map(vc -> {
            if (vc.getControllerElements().stream().noneMatch(LfBus::isGeneratorVoltageControlEnabled)) {
                throw new IllegalStateException("None of the controller buses of bus '" + bus.getId() + "'has voltage control on");
            }
            return vc.getTargetValue();
        });
    }

    private static double getReactivePowerDistributionTarget(LfNetwork network, int busNum) {
        LfBus controllerBus = network.getBus(busNum);
        double target = (controllerBus.getRemoteVoltageControlReactivePercent() - 1) * controllerBus.getTargetQ();
        for (LfBus otherControllerBus : controllerBus.getGeneratorVoltageControl().orElseThrow().getControllerElements()) {
            if (otherControllerBus != controllerBus) {
                target += controllerBus.getRemoteVoltageControlReactivePercent() * otherControllerBus.getTargetQ();
            }
        }
        return target;
    }

    private static double createBusWithSlopeTarget(LfBus bus) {
        // take first generator with slope: network loading ensures that there's only one generator with slope
        double slope = bus.getGeneratorsControllingVoltageWithSlope().get(0).getSlope();
        return getBusTargetV(bus) - slope * (bus.getLoadTargetQ() - bus.getGenerationTargetQ());
    }

    private static double getReactivePowerControlTarget(LfBranch branch) {
        Objects.requireNonNull(branch);
        return branch.getReactivePowerControl().map(ReactivePowerControl::getTargetValue)
                .orElseThrow(() -> new PowsyblException("Branch '" + branch.getId() + "' has no target in for reactive remote control"));
    }

    private static double getBusTargetP(Equation<AcVariableType, AcEquationType> equation, LfNetwork network) {
        LfBus bus = network.getBus(equation.getElementNum());
        return bus.getLoadModel() != null ? bus.getGenerationTargetP() : bus.getTargetP();
    }

    private static double getBusTargetQ(Equation<AcVariableType, AcEquationType> equation, LfNetwork network) {
        LfBus bus = network.getBus(equation.getElementNum());
        return bus.getLoadModel() != null ? bus.getGenerationTargetQ() : bus.getTargetQ();
    }

    public static void init(Equation<AcVariableType, AcEquationType> equation, LfNetwork network, double[] targets) {
        switch (equation.getType()) {
            case BUS_TARGET_P:
                targets[equation.getColumn()] = getBusTargetP(equation, network);
                break;

            case BUS_TARGET_Q:
                targets[equation.getColumn()] = getBusTargetQ(equation, network);
                break;

            case BUS_TARGET_V:
                targets[equation.getColumn()] = getBusTargetV(network.getBus(equation.getElementNum()));
                break;

            case BUS_TARGET_V_WITH_SLOPE:
                targets[equation.getColumn()] = createBusWithSlopeTarget(network.getBus(equation.getElementNum()));
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

    public AcTargetVector(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        super(network, equationSystem, AcTargetVector::init);
    }
}
