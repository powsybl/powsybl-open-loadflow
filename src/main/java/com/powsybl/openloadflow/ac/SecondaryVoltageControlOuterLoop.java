/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfSecondaryVoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SecondaryVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecondaryVoltageControlOuterLoop.class);

    private static final double TARGET_V_DIFF_EPS = 10e-3; // in PU, so 0.1 Kv

    @Override
    public String getType() {
        return "SecondaryVoltageControl";
    }

    private static void findActiveSecondaryVoltageControls(LfNetwork network, Map<LfSecondaryVoltageControl, List<LfBus>> activeSecondaryVoltageControls, Set<LfBus> allControlledBusSet) {
        List<LfSecondaryVoltageControl> secondaryVoltageControls = network.getSecondaryVoltageControls().stream()
                .filter(control -> !control.getPilotBus().isDisabled())
                .collect(Collectors.toList());
        for (LfSecondaryVoltageControl secondaryVoltageControl : secondaryVoltageControls) {
            List<LfBus> activeControlledBuses = secondaryVoltageControl.getControlledBuses().stream()
                    .filter(b -> !b.isDisabled() && b.isVoltageControlEnabled())
                    .collect(Collectors.toList());
            if (!activeControlledBuses.isEmpty()) {
                activeSecondaryVoltageControls.put(secondaryVoltageControl, activeControlledBuses);
                for (LfBus activeControlledBus : activeControlledBuses) {
                    if (!allControlledBusSet.add(activeControlledBus)) {
                        throw new IllegalStateException("Non disjoint secondary voltage control zones");
                    }
                }
            }
        }
    }

    private static int[] buildControlledBusIndex(LfNetwork network, List<LfBus> allControlledBusList) {
        int[] controlledBusIndex = new int[network.getBuses().size()];
        Arrays.fill(controlledBusIndex, -1);
        for (int i = 0; i < allControlledBusList.size(); i++) {
            var controlledBus = allControlledBusList.get(i);
            controlledBusIndex[controlledBus.getNum()] = i;
        }
        return controlledBusIndex;
    }

    private static DenseMatrix calculateSensitivityValues(List<LfBus> controllerBuses, int[] controlledBusIndex,
                                                          EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                          JacobianMatrix<AcVariableType, AcEquationType> j) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBuses.size());
        for (LfBus controlledBus : controllerBuses) {
            equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                    .ifPresent(equation -> rhs.set(equation.getColumn(), controlledBusIndex[controlledBus.getNum()], 1d));
        }
        j.solveTransposed(rhs);
        return rhs;
    }

    @SuppressWarnings("unchecked")
    private static EquationTerm<AcVariableType, AcEquationType> getCalculatedV(LfBus pilotBus) {
        return (EquationTerm<AcVariableType, AcEquationType>) pilotBus.getCalculatedV(); // this is safe
    }

    private static void adjustPrimaryVoltageControlTargets(int[] controlledBusIndex, DenseMatrix sensitivities,
                                                           List<LfBus> controlledBuses, LfBus pilotBus, double svcTargetDv) {
        for (LfBus controlledBus : controlledBuses) {
            double sensitivity = getCalculatedV(pilotBus)
                    .calculateSensi(sensitivities, controlledBusIndex[controlledBus.getNum()]);
            // each primary voltage control proportionally participate to pilot bus voltage adjustment
            double pvcTargetDv = svcTargetDv / controlledBuses.size() / sensitivity;
            var primaryVoltageControl = controlledBus.getVoltageControl().orElseThrow();
            double newPvcTargetV = primaryVoltageControl.getTargetValue() + pvcTargetDv;
            LOGGER.trace("Adjust primary voltage control target of bus {}: {} -> {}",
                    controlledBus.getId(), primaryVoltageControl.getTargetValue(), newPvcTargetV);
            primaryVoltageControl.setTargetValue(newPvcTargetV);
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        LfNetwork network = context.getNetwork();

        // find active secondary voltage controls
        //  - pilot bus should be enabled
        //  - at least one primary control controlled bus should be enabled
        Map<LfSecondaryVoltageControl, List<LfBus>> activeSecondaryVoltageControls = new LinkedHashMap<>();
        Set<LfBus> allControlledBusSet = new LinkedHashSet<>();
        findActiveSecondaryVoltageControls(network, activeSecondaryVoltageControls, allControlledBusSet);

        if (activeSecondaryVoltageControls.isEmpty()) {
            return OuterLoopStatus.STABLE;
        }

        List<LfBus> allControlledBusList = new ArrayList<>(allControlledBusSet);

        int[] controlledBusIndex = buildControlledBusIndex(network, allControlledBusList);

        // calculate primary voltage control target voltage sensitivity to pilot bus voltage
        DenseMatrix sensitivities = calculateSensitivityValues(allControlledBusList, controlledBusIndex,
                                                               context.getAcLoadFlowContext().getEquationSystem(),
                                                               context.getAcLoadFlowContext().getJacobianMatrix());

        OuterLoopStatus status = OuterLoopStatus.STABLE;

        List<String> adjustedZoneNames = new ArrayList<>();
        for (var e : activeSecondaryVoltageControls.entrySet()) {
            var secondaryVoltageControl = e.getKey();
            var controlledBuses = e.getValue();
            var pilotBus = secondaryVoltageControl.getPilotBus();
            double svcTargetDv = secondaryVoltageControl.getTargetValue() - pilotBus.getV();
            if (Math.abs(svcTargetDv) > TARGET_V_DIFF_EPS) {
                LOGGER.debug("Secondary voltage control of zone '{}' needs a pilot point voltage adjustment: {} -> {}",
                        secondaryVoltageControl.getZoneName(), pilotBus.getV(), secondaryVoltageControl.getTargetValue());
                adjustPrimaryVoltageControlTargets(controlledBusIndex, sensitivities, controlledBuses, pilotBus, svcTargetDv);
                adjustedZoneNames.add(secondaryVoltageControl.getZoneName());
                status = OuterLoopStatus.UNSTABLE;
            }
        }
        if (!adjustedZoneNames.isEmpty()) {
            LOGGER.info("{} secondary voltage control zones have been adjusted: {}",
                    adjustedZoneNames.size(), adjustedZoneNames);
        }

        return status;
    }
}
