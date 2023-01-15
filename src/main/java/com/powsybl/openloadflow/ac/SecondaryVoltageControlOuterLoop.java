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
import com.powsybl.openloadflow.network.LfBranch;
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

    private static double calculateSensiVq(DenseMatrix sensitivities, LfBus controllerBus, int controlledBusSensiColumn) {
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
     * Calculate pilot bus voltage to controlled bus voltage sensitivity.
     */
    private static double[] calculateSensiVv(Map<Integer, Integer> busNumToSensiColumn, DenseMatrix sensitivities,
                                             List<LfBus> controlledBuses, LfBus pilotBus) {
        double[] sensiVv = new double[controlledBuses.size()];
        for (int i = 0; i < controlledBuses.size(); i++) {
            LfBus controlledBus = controlledBuses.get(i);
            int controlledBusSensiColumn = busNumToSensiColumn.get(controlledBus.getNum());
            sensiVv[i] = getCalculatedV(pilotBus).calculateSensi(sensitivities, controlledBusSensiColumn);
        }
        return sensiVv;
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

        double getSi(LfBus controllerBus) {
            return si[controllerBusIndex.get(controllerBus.getNum())];
        }

        double sumSi(Set<Integer> excludeControllerNums) {
            Set<Integer> excludeControllerIndexes = excludeControllerNums.stream()
                    .map(controllerBusIndex::get)
                    .collect(Collectors.toSet());
            double sum = 0;
            for (int i = 0; i < si.length; i++) {
                if (!excludeControllerIndexes.contains(i)) {
                    sum += si[i];
                }
            }
            return sum;
        }

        public Map<Integer, Integer> getControllerBusIndex() {
            return controllerBusIndex;
        }
    }

    /**
     * Calculate:
     *  - controlled bus voltage to controller bus reactive power injection sensitivity.
     *  - pilot bus voltage to controller bus reactive power injection sensitivity.
     */
    private static SensiVq calculateSensiVq(Map<Integer, Integer> busNumToSensiColumn, DenseMatrix sensitivities,
                                            List<LfBus> controlledBuses, double[] svi) {
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
                int controlledBusSensiColumn = busNumToSensiColumn.get(controlledBus.getNum());
                for (LfBus controllerBus : getControllerBuses(controlledBus)) {
                    double sq = calculateSensiVq(sensitivities, controllerBus, controlledBusSensiColumn);
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

    static class ReactiveLimitStop {

        private final LfBus controllerBus;

        private final double dqLim;

        ReactiveLimitStop(LfBus controllerBus, double dqLim) {
            this.controllerBus = controllerBus;
            this.dqLim = dqLim;
        }

        LfBus getControllerBus() {
            return controllerBus;
        }

        double getDqLim() {
            return dqLim;
        }
    }

    private static Map<Integer, ReactiveLimitStop> checkControllersReactiveLimits(List<LfBus> controlledBuses, double dq) {
        Map<Integer, ReactiveLimitStop> reactiveLimitStopByControllerNum = new HashMap<>();
        for (LfBus controlledBus : controlledBuses) {
            for (LfBus controllerBus : getControllerBuses(controlledBus)) {
                double q = controllerBus.getQ().eval() + controllerBus.getLoadTargetQ();
                double newQ = q + dq;
                if (newQ < controllerBus.getMinQ()) {
                    LOGGER.debug("Controller bus '{}' reached min q limit", controllerBus.getId());
                    reactiveLimitStopByControllerNum.put(controllerBus.getNum(), new ReactiveLimitStop(controllerBus, q - controllerBus.getMinQ()));
                } else if (newQ > controllerBus.getMaxQ()) {
                    LOGGER.debug("Controller bus '{}' reached max q limit", controllerBus.getId());
                    reactiveLimitStopByControllerNum.put(controllerBus.getNum(), new ReactiveLimitStop(controllerBus, controllerBus.getMaxQ() - q));
                }
            }
        }
        return reactiveLimitStopByControllerNum;
    }

    private static double calculateDq(double pilotDv, SensiVq sensiVq, Map<Integer, ReactiveLimitStop> reactiveLimitStopByControllerNum) {
        double pilotDvLimit = 0; // part of pilot bus voltage change because of controller bus to limit
        for (ReactiveLimitStop reactiveLimitStop : reactiveLimitStopByControllerNum.values()) {
            pilotDvLimit += reactiveLimitStop.getDqLim() * sensiVq.getSi(reactiveLimitStop.getControllerBus());
        }
        double siSum = sensiVq.sumSi(reactiveLimitStopByControllerNum.keySet());
        return siSum != 0 ? (pilotDv - pilotDvLimit) / siSum : 0;
    }

    /**
     * <pre>
     * pilot_dv = sum_i(svi * dvi) where i are all controlled buses
     * dvi: controlled bus i (primary voltage control) voltage variation, needed to each pilot bus target voltage
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
     * for each bus, check qi + dq is out of reactive limit
     * if yes remove from dv_pilot: (qlim - qi) * si
     * then recompute dq with remaining buses and new dv_pilot
     * until there is no more reactive limit violation
     *
     * at the end:
     * dvi = dq / sqi
     * </pre>
     */
    private static void adjustPrimaryVoltageControlTargets(Map<Integer, Integer> busNumToSensiColumn, DenseMatrix sensitivities,
                                                           List<LfBus> controlledBuses, LfBus pilotBus, double pilotDv) {
        // calculate sensitivity of controlled buses voltage to pilot bus voltage
        double[] svi = calculateSensiVv(busNumToSensiColumn, sensitivities, controlledBuses, pilotBus);

        // calculate sensitivity of controlled buses voltage to controller buses reactive power injection
        // and sensitivity of controller bus reactive power injection to pilot bus voltage
        var sensiVq = calculateSensiVq(busNumToSensiColumn, sensitivities, controlledBuses, svi);

        // supposing we want all controllers to shift to same amount of reactive power
        double dq = calculateDq(pilotDv, sensiVq, Collections.emptyMap());

        Map<Integer, ReactiveLimitStop> reactiveLimitStopByControllerNum = checkControllersReactiveLimits(controlledBuses, dq);
        if (!reactiveLimitStopByControllerNum.isEmpty()) {
            dq = calculateDq(pilotDv, sensiVq, reactiveLimitStopByControllerNum);
        }

        for (LfBus controlledBus : controlledBuses) {
            double pvcDv = 0;
            for (LfBus controllerBus : getControllerBuses(controlledBus)) {
                ReactiveLimitStop reactiveLimitStop = reactiveLimitStopByControllerNum.get(controllerBus.getNum());
                if (reactiveLimitStop != null) {
                    pvcDv += reactiveLimitStop.dqLim / sensiVq.getSqi(controllerBus);
                } else {
                    pvcDv += dq / sensiVq.getSqi(controllerBus);
                }
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

        var busNumToSensiColumn = buildBusIndex(allBusList);

        // calculate primary voltage control target voltage sensitivity to pilot bus voltage
        DenseMatrix sensitivities = calculateSensitivityValues(allBusList, busNumToSensiColumn,
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
                adjustPrimaryVoltageControlTargets(busNumToSensiColumn, sensitivities, controlledBuses, pilotBus, svcTargetDv);
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
