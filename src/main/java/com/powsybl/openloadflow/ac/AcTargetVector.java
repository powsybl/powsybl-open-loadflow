/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.SingleEquation;
import com.powsybl.openloadflow.equations.EquationArray;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.*;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcTargetVector extends TargetVector<AcVariableType, AcEquationType> {

    private static double getBusTargetV(LfBus bus) {
        Objects.requireNonNull(bus);
        double targetV = bus.getHighestPriorityTargetV()
                .orElseThrow(() -> new IllegalStateException("No active voltage control has been found for bus '" + bus.getId() + "'"));
        if (bus.hasGeneratorsWithSlope()) {
            // take first generator with slope: network loading ensures that there's only one generator with slope
            double slope = bus.getGeneratorsControllingVoltageWithSlope().get(0).getSlope();
            targetV -= slope * (bus.getLoadTargetQ() - bus.getGenerationTargetQ());
        }
        return targetV;
    }

    private static double getGeneratorReactivePowerDistributionTarget(LfNetwork network, int busNum) {
        LfBus controllerBus = network.getBus(busNum);
        double target = (controllerBus.getRemoteControlReactivePercent() - 1) * controllerBus.getTargetQ();
        List<LfBus> mergedControllerBuses;
        if (controllerBus.getGeneratorVoltageControl().isPresent()) {
            mergedControllerBuses = controllerBus.getGeneratorVoltageControl().orElseThrow().getMergedControllerElements();
        } else if (controllerBus.hasGeneratorReactivePowerControl()) {
            mergedControllerBuses = controllerBus.getGeneratorReactivePowerControl().orElseThrow().getControllerBuses();
        } else {
            throw new PowsyblException("Controller bus '" + controllerBus.getId() + "' has no voltage or reactive remote control");
        }
        return updateReactivePowerDistributionTarget(target, controllerBus, mergedControllerBuses);
    }

    private static double updateReactivePowerDistributionTarget(double target, LfBus controllerBus, List<LfBus> controllerBuses) {
        double updatedTarget = target;
        for (LfBus otherControllerBus : controllerBuses) {
            if (otherControllerBus != controllerBus) {
                updatedTarget += controllerBus.getRemoteControlReactivePercent() * otherControllerBus.getTargetQ();
            }
        }
        return updatedTarget;
    }

    private static double getReactivePowerControlTarget(LfBranch branch) {
        Objects.requireNonNull(branch);
        return branch.getGeneratorReactivePowerControl().map(GeneratorReactivePowerControl::getTargetValue)
                .orElseThrow(() -> new PowsyblException("Branch '" + branch.getId() + "' has no target in for generator reactive remote control"));
    }

    public static void init(SingleEquation<AcVariableType, AcEquationType> equation, LfNetwork network, double[] targets) {
        init(equation.getType(), equation.getColumn(), equation.getElementNum(), network, targets);
        targets[equation.getColumn()] -= equation.rhs();
    }

    public static void init(AcEquationType equationType, int column, int elementNum, LfNetwork network, double[] targets) {
        switch (equationType) {
            case BUS_TARGET_P :
                targets[column] = network.getBus(elementNum).getTargetP();
                break;

            case BUS_DISTR_SLACK_P :
                targets[column] = network.getBus(elementNum).getTargetP() - network.getSlackBuses().get(0).getTargetP();
                break;

            case BUS_TARGET_Q:
                targets[column] = network.getBus(elementNum).getTargetQ();
                break;

            case BUS_TARGET_V:
                targets[column] = getBusTargetV(network.getBus(elementNum));
                break;

            case BUS_TARGET_PHI:
                targets[column] = 0;
                break;

            case SHUNT_TARGET_B:
                targets[column] = network.getShunt(elementNum).getB();
                break;

            case BRANCH_TARGET_P:
                targets[column] = LfBranch.getDiscretePhaseControlTarget(network.getBranch(elementNum), TransformerPhaseControl.Unit.MW);
                break;

            case BRANCH_TARGET_Q:
                targets[column] = getReactivePowerControlTarget(network.getBranch(elementNum));
                break;

            case BRANCH_TARGET_ALPHA1:
                targets[column] = network.getBranch(elementNum).getPiModel().getA1();
                break;

            case BRANCH_TARGET_RHO1:
                targets[column] = network.getBranch(elementNum).getPiModel().getR1();
                break;

            case DISTR_Q:
                targets[column] = getGeneratorReactivePowerDistributionTarget(network, elementNum);
                break;

            case ZERO_V:
                targets[column] = 0;
                break;

            case ZERO_PHI:
                targets[column] = LfBranch.getA(network.getBranch(elementNum));
                break;

            case AC_CONV_TARGET_P_REF:
                targets[column] = network.getVoltageSourceConverter(equation.getElementNum()).getTargetP();
                break;

            case AC_CONV_TARGET_Q_REF:
                targets[column] = network.getVoltageSourceConverter(equation.getElementNum()).getTargetQ();
                break;

            case DC_NODE_TARGET_V_REF:
                targets[column] = network.getVoltageSourceConverter(equation.getElementNum()).getTargetVdc();
                break;

            case BUS_TARGET_V_REF:
                targets[column] = network.getVoltageSourceConverter(equation.getElementNum()).getTargetVac();
                break;

            case DISTR_RHO,
                 DISTR_SHUNT_B,
                 DUMMY_TARGET_P,
                 DUMMY_TARGET_Q,
                 BUS_TARGET_IX_ZERO,
                 BUS_TARGET_IY_ZERO,
                 BUS_TARGET_IX_NEGATIVE,
                 BUS_TARGET_IY_NEGATIVE,
                 DC_NODE_TARGET_I,
                 DC_NODE_GROUND:
                targets[column] = 0;
                break;

            default:
                throw new IllegalStateException("Unknown state variable type: " + equationType);
        }
    }

    public static void init(EquationArray<AcVariableType, AcEquationType> equationArray, LfNetwork network, double[] targets) {
        for (int elementNum = 0; elementNum < equationArray.getElementCount(); elementNum++) {
            if (equationArray.isElementActive(elementNum)) {
                int column = equationArray.getElementNumToColumn(elementNum);
                init(equationArray.getType(), column, elementNum, network, targets);
            }
        }
    }

    public AcTargetVector(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        super(network, equationSystem, new Initializer<>() {
            @Override
            public void initialize(SingleEquation<AcVariableType, AcEquationType> equation, LfNetwork network, double[] targets) {
                AcTargetVector.init(equation, network, targets);
            }

            @Override
            public void initialize(EquationArray<AcVariableType, AcEquationType> equationArray, LfNetwork network, double[] targets) {
                AcTargetVector.init(equationArray, network, targets);
            }
        });
    }
}
