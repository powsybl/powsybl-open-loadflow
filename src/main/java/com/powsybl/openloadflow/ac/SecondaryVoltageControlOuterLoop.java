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
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfSecondaryVoltageControl;
import com.powsybl.openloadflow.util.PerUnit;
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

    static class SensitivityContext {

        private final Map<Integer, Integer> busNumToSensiColumn;

        private final DenseMatrix sensitivities;

        SensitivityContext(Map<Integer, Integer> busNumToSensiColumn, DenseMatrix sensitivities) {
            this.busNumToSensiColumn = Objects.requireNonNull(busNumToSensiColumn);
            this.sensitivities = Objects.requireNonNull(sensitivities);
        }

        static SensitivityContext create(List<LfBus> buses, AcLoadFlowContext context) {
            var busNumToSensiColumn = buildBusIndex(buses);

            DenseMatrix sensitivities = calculateSensitivityValues(buses, busNumToSensiColumn, context.getEquationSystem(),
                                                                   context.getJacobianMatrix());

            return new SensitivityContext(busNumToSensiColumn, sensitivities);
        }

        private static DenseMatrix calculateSensitivityValues(List<LfBus> controllerBuses, Map<Integer, Integer> busNumToSensiColumn,
                                                              EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                              JacobianMatrix<AcVariableType, AcEquationType> j) {
            DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBuses.size());
            for (LfBus controlledBus : controllerBuses) {
                equationSystem.getEquation(controlledBus.getNum(), AcEquationType.BUS_TARGET_V)
                        .ifPresent(equation -> rhs.set(equation.getColumn(), busNumToSensiColumn.get(controlledBus.getNum()), 1d));
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

        /**
         * Calculate controlled bus voltage to controller bus reactive power injection sensitivity.
         */
        double calculateSensiVq(LfBus controllerBus) {
            LfBus controlledBus = controllerBus.getVoltageControl().orElseThrow().getControlledBus();
            int controlledBusSensiColumn = busNumToSensiColumn.get(controlledBus.getNum());

            double sq = 0;
            for (LfBranch branch : controllerBus.getBranches()) {
                if (branch.getBus1() == controllerBus) {
                    sq += getQ1(branch).calculateSensi(sensitivities, controlledBusSensiColumn);
                } else if (branch.getBus2() == controllerBus) {
                    sq += getQ2(branch).calculateSensi(sensitivities, controlledBusSensiColumn);
                }
                // disconnected at the other side, we can skip
            }
            // FIXME: take into account shunts?
            return sq;
        }

        /**
         * Calculate controlled buses voltage to pilot bus voltage sensitivities.
         */
        double[] calculateSensiVv(List<LfBus> controlledBuses, LfBus pilotBus) {
            double[] sensiVv = new double[controlledBuses.size()];
            for (int i = 0; i < controlledBuses.size(); i++) {
                LfBus controlledBus = controlledBuses.get(i);
                int controlledBusSensiColumn = busNumToSensiColumn.get(controlledBus.getNum());
                sensiVv[i] = getCalculatedV(pilotBus).calculateSensi(sensitivities, controlledBusSensiColumn);
            }
            return sensiVv;
        }
    }

    static class SensiVq {

        final double[] sqi;

        final double[] si;

        final Map<Integer, Integer> controllerBusIndex;

        SensiVq(double[] sqi, double[] si, Map<Integer, Integer> controllerBusIndex) {
            this.sqi = sqi;
            this.si = si;
            this.controllerBusIndex = controllerBusIndex;
        }

        double getSqi(LfBus controllerBus) {
            return sqi[controllerBusIndex.get(controllerBus.getNum())];
        }
    }

    /**
     * Calculate:
     *  - controlled bus voltage to controller bus reactive power injection sensitivity.
     *  - pilot bus voltage to controller bus reactive power injection sensitivity.
     */
    private static SensiVq calculateSensiVq(SensitivityContext sensitivityContext, List<LfBus> controlledBuses, double[] svi) {
        // get list of all controllers of the zone
        List<LfBus> allControllerBuses = controlledBuses.stream()
                .flatMap(controlledBus -> getControllerBuses(controlledBus).stream())
                .collect(Collectors.toList());
        var controllerBusIndex = buildBusIndex(controlledBuses);

        double[] si = new double[allControllerBuses.size()];
        double[] sqi = new double[allControllerBuses.size()];
        for (int i = 0; i < controlledBuses.size(); i++) {
            LfBus controlledBus = controlledBuses.get(i);
            // we need to filter very small sensitivity to avoid large target v shift
            if (Math.abs(svi[i]) > SENSI_V_EPS) {
                for (LfBus controllerBus : getControllerBuses(controlledBus)) {
                    double sq = sensitivityContext.calculateSensiVq(controllerBus);
                    int j = controllerBusIndex.get(controllerBus.getNum());
                    if (sq != 0) {
                        sqi[j] = sq;
                        si[j] = svi[i] / sq;
                    }
                }
            }
        }

        return new SensiVq(sqi, si, controllerBusIndex);
    }

    /**
     * <pre>
     * pilot_dv = sum_i(svi * dvi) where i are all controlled buses
     * dvi: controlled bus i (primary voltage control) voltage variation, needed to reach pilot bus target voltage
     * svi: the sensitivity of controlled bus i voltage to pilot bus
     *
     * in the following equations i are controller buses and all svi and dvi of controlled buses are used for their
     * corresponding controller buses
     * dqi = dvi * sqi
     * sqi: the sensitivity of a controlled bus voltage to its controller bus i reactive power injection
     * pilot_dv = sum_i(svi * (dqi / sqi))
     * pilot_dv = sum_i(si * dqi)
     * si = svi / sqi
     * si: the sensitivity of controller bus i reactive power injection to a pilot bus voltage
     *
     * we want all generators of the zone to provide same reactive power to reach pilot bus target voltage
     * dq = dq1 = dq2 = ...
     * pilot_dv = sum_i(si) * dq
     * dq = pilot_dv / sum_i(si)
     *
     * we can now compute new target v shift for all controlled buses (primary voltage control new target v)
     * dvi = dq / sqi
     * </pre>
     */
    private static void adjustPrimaryVoltageControlTargets(String zoneName, SensitivityContext sensitivityContext,
                                                           List<LfBus> controlledBuses, LfBus pilotBus, double pilotDv) {
        // calculate sensitivity of controlled buses voltage to pilot bus voltage
        double[] svi = sensitivityContext.calculateSensiVv(controlledBuses, pilotBus);

        // calculate sensitivity of controlled buses voltage to controller buses reactive power injection
        // and sensitivity of controller bus reactive power injection to pilot bus voltage
        var sensiVq = calculateSensiVq(sensitivityContext, controlledBuses, svi);

        // supposing we want all controllers to shift to same amount of reactive power
        double dq = pilotDv / Arrays.stream(sensiVq.si).sum();

        LOGGER.trace("Control units of zone '{}' need to be adjusted of {} MW", zoneName, dq * PerUnit.SB);

        for (LfBus controlledBus : controlledBuses) {
            double pvcDv = 0;
            for (LfBus controllerBus : getControllerBuses(controlledBus)) {
                pvcDv += dq / sensiVq.getSqi(controllerBus);
            }
            var pvc = controlledBus.getVoltageControl().orElseThrow();
            double newPvcTargetV = pvc.getTargetValue() + pvcDv;
            LOGGER.trace("Adjust primary voltage control target of bus '{}': {} -> {}",
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

        SensitivityContext sensitivityContext = SensitivityContext.create(allBusList, context.getAcLoadFlowContext());

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
                adjustPrimaryVoltageControlTargets(secondaryVoltageControl.getZoneName(), sensitivityContext, controlledBuses,
                                                   pilotBus, svcTargetDv);
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
