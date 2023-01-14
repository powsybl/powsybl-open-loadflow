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
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SecondaryVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecondaryVoltageControlOuterLoop.class);

    private static final double TARGET_V_DIFF_EPS = 1e-1; // in Kv
    private static final double SENSI_V_EPS = 1e-3;

    @Override
    public String getType() {
        return "SecondaryVoltageControl";
    }

    private static boolean isValid(LfBus bus) {
        return !bus.isDisabled() && bus.isVoltageControlEnabled();
    }

    private static List<LfBus> getControllerBuses(LfBus controlledBus) {
        return controlledBus.getVoltageControl()
                .orElseThrow()
                .getControllerBuses()
                .stream().filter(SecondaryVoltageControlOuterLoop::isValid)
                .collect(Collectors.toList());
    }

    private static void findActiveSecondaryVoltageControls(LfNetwork network, Map<LfSecondaryVoltageControl, List<LfBus>> activeSecondaryVoltageControls,
                                                           Set<LfBus> allBusSet) {
        List<LfSecondaryVoltageControl> secondaryVoltageControls = network.getSecondaryVoltageControls().stream()
                .filter(control -> !control.getPilotBus().isDisabled())
                .collect(Collectors.toList());
        for (LfSecondaryVoltageControl secondaryVoltageControl : secondaryVoltageControls) {
            List<LfBus> activeControlledBuses = secondaryVoltageControl.getControlledBuses().stream()
                    .filter(SecondaryVoltageControlOuterLoop::isValid)
                    .collect(Collectors.toList());
            if (!activeControlledBuses.isEmpty()) {
                activeSecondaryVoltageControls.put(secondaryVoltageControl, activeControlledBuses);
                for (LfBus activeControlledBus : activeControlledBuses) {
                    if (!allBusSet.add(activeControlledBus)) {
                        throw new IllegalStateException("Non disjoint secondary voltage control zones");
                    }
                    allBusSet.addAll(getControllerBuses(activeControlledBus));
                }
            }
        }
    }

    private static Map<Integer, Integer> buildBusIndex(List<LfBus> buses) {
        Map<Integer, Integer> busIndex = new LinkedHashMap<>();
        for (int i = 0; i < buses.size(); i++) {
            var bus = buses.get(i);
            busIndex.put(bus.getNum(), i);
        }
        return busIndex;
    }

    private static DenseMatrix calculateSensitivityValues(List<LfBus> controllerBuses, Map<Integer, Integer> busSensiColumn,
                                                          EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                          JacobianMatrix<AcVariableType, AcEquationType> j) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBuses.size());
        for (LfBus controlledBus : controllerBuses) {
            equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                    .ifPresent(equation -> rhs.set(equation.getColumn(), busSensiColumn.get(controlledBus.getNum()), 1d));
        }
        j.solveTransposed(rhs);
        return rhs;
    }

    @SuppressWarnings("unchecked")
    private static EquationTerm<AcVariableType, AcEquationType> getCalculatedV(LfBus pilotBus) {
        return (EquationTerm<AcVariableType, AcEquationType>) pilotBus.getCalculatedV(); // this is safe
    }

    @SuppressWarnings("unchecked")
    private static EquationTerm<AcEquationType, AcEquationType> getQ1(LfBranch branch) {
        return (EquationTerm<AcEquationType, AcEquationType>) branch.getQ1();
    }

    @SuppressWarnings("unchecked")
    private static EquationTerm<AcEquationType, AcEquationType> getQ2(LfBranch branch) {
        return (EquationTerm<AcEquationType, AcEquationType>) branch.getQ2();
    }

    private static double calculateSensiVQ(DenseMatrix sensitivities, LfBus controllerBus, int controlledBusColumn) {
        double sensiVQ = 0;
        for (LfBranch branch : controllerBus.getBranches()) {
            if (branch.getBus1() == controllerBus) {
                sensiVQ += getQ1(branch).calculateSensi(sensitivities, controlledBusColumn);
            } else if (branch.getBus2() == controllerBus) {
                sensiVQ += getQ2(branch).calculateSensi(sensitivities, controlledBusColumn);
            }
            // disconnected at the other side, we can skip
        }
        // FIXME: take into account shunts?
        return sensiVQ;
    }

    private static void adjustPrimaryVoltageControlTargets(Map<Integer, Integer> busSensiColumn, DenseMatrix sensitivities,
                                                           List<LfBus> controlledBuses, LfBus pilotBus, double pilotDv) {
        // without reactive limit:
        // pilot_dv = sv1 * dv1 + sv2 * dv2 + ...
        // dqi = dvi * sqi
        // pilot_dv = sv1 * (dq1 / sq1) + sv2 * (dq2 / sq2) + ...
        // pilot_dv = s1 * dq1 + s2 * dq2 + ...
        // si = svi / sqi
        // we want all generator of the zone to provide same reactive power
        // dq = dq1 = dq2 = ...
        // dv_pilot = (s1 + s2 + ...) * dq
        // dv_pilot = ss * dq
        // dq = pilot_dv / ss
        //
        // for each bus, check qi + dq is out of reactive limit
        // if yes remove from dv_pilot: (qlim - qi) * si
        // then recompute dq with remaining buses and new dv_pilot
        // until there is no more reactive limit violation
        //
        // at the end:
        // dvi = dq / sqi

        // get list of all controllers of the zone
        List<LfBus> controllerBuses = controlledBuses.stream()
                .flatMap(controlledBus -> getControllerBuses(controlledBus).stream())
                .collect(Collectors.toList());
        var controllerBusIndex = buildBusIndex(controlledBuses);

        double[] si = new double[controllerBuses.size()];
        double[] sqi = new double[controllerBuses.size()];
        for (LfBus controlledBus : controlledBuses) {
            int controlledBusColumn = busSensiColumn.get(controlledBus.getNum());
            double sensiVV = getCalculatedV(pilotBus).calculateSensi(sensitivities, controlledBusColumn);
            // we need to filter very small sensitivity to avoid large target v shift
            if (Math.abs(sensiVV) > SENSI_V_EPS) {
                for (LfBus controllerBus : getControllerBuses(controlledBus)) {
                    double sensiVQ = calculateSensiVQ(sensitivities, controllerBus, controlledBusColumn);
                    int i = controllerBusIndex.get(controllerBus.getNum());
                    if (sensiVQ != 0) {
                        sqi[i] = sensiVQ;
                        si[i] = sensiVV / sensiVQ;
                    }
                }
            }
        }
        double ss = Arrays.stream(si).sum();
        double dq = pilotDv / ss;

        for (LfBus controlledBus : controlledBuses) {
            double pvcDv = 0;
            for (LfBus controllerBus : getControllerBuses(controlledBus)) {
                int i = controllerBusIndex.get(controllerBus.getNum());
                // TODO check reactive limit
                pvcDv += dq / sqi[i];
            }
            var pvc = controlledBus.getVoltageControl().orElseThrow();
            double newPvcTargetV = pvc.getTargetValue() + pvcDv;
            LOGGER.debug("Adjust primary voltage control target of bus {}: {} -> {}",
                    controlledBus.getId(), pvc.getTargetValue() * controlledBus.getNominalV(),
                    newPvcTargetV * controlledBus.getNominalV());
            pvc.setTargetValue(newPvcTargetV);
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        LfNetwork network = context.getNetwork();

        // find active secondary voltage controls
        //  - pilot bus should be enabled
        //  - at least one primary control controlled bus should be enabled
        Map<LfSecondaryVoltageControl, List<LfBus>> activeSecondaryVoltageControls = new LinkedHashMap<>();
        Set<LfBus> allBusSet = new LinkedHashSet<>();
        findActiveSecondaryVoltageControls(network, activeSecondaryVoltageControls, allBusSet);

        if (activeSecondaryVoltageControls.isEmpty()) {
            return OuterLoopStatus.STABLE;
        }

        List<LfBus> allBusList = new ArrayList<>(allBusSet);

        var busSensiColumn = buildBusIndex(allBusList);

        // calculate primary voltage control target voltage sensitivity to pilot bus voltage
        DenseMatrix sensitivities = calculateSensitivityValues(allBusList, busSensiColumn,
                                                               context.getAcLoadFlowContext().getEquationSystem(),
                                                               context.getAcLoadFlowContext().getJacobianMatrix());

        OuterLoopStatus status = OuterLoopStatus.STABLE;

        List<String> adjustedZoneNames = new ArrayList<>();
        for (var e : activeSecondaryVoltageControls.entrySet()) {
            var secondaryVoltageControl = e.getKey();
            var controlledBuses = e.getValue();
            var pilotBus = secondaryVoltageControl.getPilotBus();
            double svcTargetDv = secondaryVoltageControl.getTargetValue() - pilotBus.getV();
            if (Math.abs(svcTargetDv) * pilotBus.getNominalV() > TARGET_V_DIFF_EPS) {
                LOGGER.debug("Secondary voltage control of zone '{}' needs a pilot point voltage adjustment: {} -> {}",
                        secondaryVoltageControl.getZoneName(), pilotBus.getV() * pilotBus.getNominalV(),
                        secondaryVoltageControl.getTargetValue() * pilotBus.getNominalV());
                adjustPrimaryVoltageControlTargets(busSensiColumn, sensitivities, controlledBuses, pilotBus, svcTargetDv);
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
