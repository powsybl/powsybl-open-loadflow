/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import net.jafama.FastMath;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SecondaryVoltageControlOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecondaryVoltageControlOuterLoop.class);

    public static final double SENSI_V_V_EPS_DEFAULT_VALUE = 1e-2;

    private final double sensiVvEps;

    private final double minPlausibleTargetVoltage;

    private final double maxPlausibleTargetVoltage;

    public SecondaryVoltageControlOuterLoop(double sensiVvEps, double minPlausibleTargetVoltage, double maxPlausibleTargetVoltage) {
        this.sensiVvEps = sensiVvEps;
        this.minPlausibleTargetVoltage = minPlausibleTargetVoltage;
        this.maxPlausibleTargetVoltage = maxPlausibleTargetVoltage;
    }

    @Override
    public String getType() {
        return "SecondaryVoltageControl";
    }

    private static List<LfBus> findControllerBuses(LfBus controlledBus) {
        return controlledBus.getGeneratorVoltageControl().orElseThrow()
                .getMergedControllerElements().stream()
                .filter(bus -> !bus.isDisabled() && bus.isGeneratorVoltageControlEnabled())
                .collect(Collectors.toList());
    }

    private void findActiveSecondaryVoltageControls(LfNetwork network, Map<LfSecondaryVoltageControl, List<LfBus>> activeSecondaryVoltageControls,
                                                    Set<LfBus> allControlledBusSet) {
        List<LfSecondaryVoltageControl> secondaryVoltageControls = network.getSecondaryVoltageControls().stream()
                .filter(control -> !control.getPilotBus().isDisabled())
                .collect(Collectors.toList());
        for (LfSecondaryVoltageControl secondaryVoltageControl : secondaryVoltageControls) {
            List<LfBus> activeControlledBuses = secondaryVoltageControl.getControlledBuses().stream()
                    .filter(controlledBus -> {
                        GeneratorVoltageControl voltageControl = controlledBus.getGeneratorVoltageControl().orElseThrow();
                        double targetV = voltageControl.getTargetValue();
                        return !controlledBus.isDisabled()
                                && !findControllerBuses(controlledBus).isEmpty()
                                && !voltageControl.isHidden()
                                && voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN
                                && targetV > minPlausibleTargetVoltage && targetV < maxPlausibleTargetVoltage;
                    })
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

        static SensitivityContext create(List<LfBus> controlledBuses, AcLoadFlowContext context) {
            var busNumToSensiColumn = buildBusIndex(controlledBuses);

            DenseMatrix sensitivities = calculateSensitivityValues(controlledBuses, busNumToSensiColumn, context.getEquationSystem(),
                                                                   context.getJacobianMatrix());

            return new SensitivityContext(busNumToSensiColumn, sensitivities);
        }

        private static DenseMatrix calculateSensitivityValues(List<LfBus> controlledBuses, Map<Integer, Integer> busNumToSensiColumn,
                                                              EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                              JacobianMatrix<AcVariableType, AcEquationType> j) {
            DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controlledBuses.size());
            for (LfBus controlledBus : controlledBuses) {
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

        @SuppressWarnings("unchecked")
        private static EquationTerm<AcEquationType, AcEquationType> getQ(LfShunt shunt) {
            return (EquationTerm<AcEquationType, AcEquationType>) shunt.getQ();
        }

        /**
         * Calculate controlled bus voltage to controller bus reactive power injection sensitivity.
         */
        double calculateSensiVq(LfBus controllerBus, LfBus controlledBus) {
            int controlledBusSensiColumn = busNumToSensiColumn.get(controlledBus.getNum());

            MutableDouble sq = new MutableDouble();
            for (LfBranch branch : controllerBus.getBranches()) {
                // we can skip branches disconnected at the other side
                if (branch.getBus1() == controllerBus && branch.getBus2() != null) {
                    sq.add(getQ1(branch).calculateSensi(sensitivities, controlledBusSensiColumn));
                } else if (branch.getBus2() == controllerBus && branch.getBus1() != null) {
                    sq.add(getQ2(branch).calculateSensi(sensitivities, controlledBusSensiColumn));
                }
            }

            controllerBus.getShunt().ifPresent(shunt -> sq.add(getQ(shunt).calculateSensi(sensitivities, controlledBusSensiColumn)));
            controllerBus.getControllerShunt().ifPresent(shunt -> sq.add(getQ(shunt).calculateSensi(sensitivities, controlledBusSensiColumn)));
            controllerBus.getSvcShunt().ifPresent(shunt -> sq.add(getQ(shunt).calculateSensi(sensitivities, controlledBusSensiColumn)));

            return sq.getValue();
        }

        /**
         * Calculate controlled buses voltage to pilot bus voltage sensitivities.
         */
        double calculateSensiVv(LfBus controlledBus, LfBus pilotBus) {
            int controlledBusSensiColumn = busNumToSensiColumn.get(controlledBus.getNum());
            return getCalculatedV(pilotBus).calculateSensi(sensitivities, controlledBusSensiColumn);
        }
    }

    private boolean adjustPrimaryVoltageControlTargets2(String zoneName, SensitivityContext sensitivityContext,
                                                       List<LfBus> controlledBuses, LfBus pilotBus, double pilotDv) {
        boolean adjusted = false;

        List<LfBus> controllerBuses = controlledBuses.stream()
                .flatMap(controlledBus -> findControllerBuses(controlledBus).stream())
                .collect(Collectors.toList());

        var controllerBusIndex = buildBusIndex(controllerBuses);

        int n = controllerBuses.size();
        DenseMatrix a = new DenseMatrix(n, n);
        for (LfBus controllerBus : controllerBuses) {
            for (LfBus controllerBus2 : controllerBuses) {
                int i = controllerBusIndex.get(controllerBus.getNum());
                int j = controllerBusIndex.get(controllerBus2.getNum());
                a.set(i, j, i == j ? 1d - (1d / n) : - 1d / n);
            }
        }
//        System.out.println("a=");
//        a.print(System.out);

        DenseMatrix k0 = new DenseMatrix(n, 1);
        for (LfBus controllerBus : controllerBuses) {
            int i = controllerBusIndex.get(controllerBus.getNum());
            k0.set(i, 0, calculateKi(controllerBus));
        }
        System.out.println("k0=");
        k0.print(System.out);

        DenseMatrix rhs = a.times(k0, -1);
//        System.out.println("rhs=");
//        rhs.print(System.out);

        DenseMatrix jK = new DenseMatrix(n, n);
        for (LfBus controllerBus : controllerBuses) {
            for (LfBus controllerBus2 : controllerBuses) {
                LfBus controlledBus2 = controllerBus2.getGeneratorVoltageControl().orElseThrow().getControlledBus();
                int i = controllerBusIndex.get(controllerBus.getNum());
                int j = controllerBusIndex.get(controlledBus2.getNum());
                jK.set(i, j, qToK(sensitivityContext.calculateSensiVq(controllerBus, controlledBus2), controllerBus));
            }
        }
//        System.out.println("jK=");
//        jK.print(System.out);

        DenseMatrix jVpp = new DenseMatrix(n, 1);
        for (LfBus controllerBus : controllerBuses) {
            LfBus controlledBus = controllerBus.getGeneratorVoltageControl().orElseThrow().getControlledBus();
            int i = controllerBusIndex.get(controllerBus.getNum());
            jVpp.set(i, 0, sensitivityContext.calculateSensiVv(controlledBus, pilotBus));
        }
//        System.out.println("jVpp=");
//        jVpp.print(System.out);

        DenseMatrix jVppT = jVpp.transpose();
//        System.out.println("jVppT=");
//        jVppT.print(System.out);

        DenseMatrix bt = a.times(jK).transpose();
//        System.out.println("bt=");
//        bt.print(System.out);

        // replace last row
        for (int j = 0; j < bt.getColumnCount(); j++) {
            bt.set(bt.getRowCount() - 1, j, jVppT.get(0, j));
        }
        rhs.set(rhs.getRowCount() - 1, 0, pilotDv);
//        System.out.println("bt (modified)=");
//        bt.print(System.out);
//        System.out.println("rhs (modified)=");
//        rhs.print(System.out);

        try (LUDecomposition luDecomposition = bt.decomposeLU()) {
            luDecomposition.solve(rhs);
            System.out.println("dv=");
            rhs.print(System.out);
        }
        DenseMatrix dv = rhs;

        double norm2Dv = 0;
        for (int i = 0; i < dv.getRowCount(); i++) {
            norm2Dv += FastMath.pow(dv.get(i, 0), 2);
        }
        norm2Dv = FastMath.sqrt(norm2Dv);
        System.out.println("norm2Dv=" + norm2Dv);

        if (norm2Dv > 0.01) {
            for (LfBus controllerBus : controllerBuses) {
                int i = controllerBusIndex.get(controllerBus.getNum());
                var pvc = controllerBus.getGeneratorVoltageControl().orElseThrow();
                LfBus controlledBus = pvc.getControlledBus();
                double newPvcTargetV = pvc.getTargetValue() + dv.get(i, 0);
                String plausibleTargetInfos = "";
                if (newPvcTargetV > maxPlausibleTargetVoltage) {
                    newPvcTargetV = maxPlausibleTargetVoltage;
                    plausibleTargetInfos = " (cut to max plausible target voltage)";
                }
                if (newPvcTargetV < minPlausibleTargetVoltage) {
                    newPvcTargetV = minPlausibleTargetVoltage;
                    plausibleTargetInfos = " (cut to min plausible target voltage)";
                }
                LOGGER.info("Adjust target voltage of controlled bus '{}': {} -> {}{}",
                        controlledBus.getId(), pvc.getTargetValue() * controlledBus.getNominalV(),
                        newPvcTargetV * controlledBus.getNominalV(), plausibleTargetInfos);
                pvc.setTargetValue(newPvcTargetV);
                adjusted = true;
            }
        }

        return adjusted;
    }

    private static double qToK(double q, LfBus controllerBus) {
        return (2 * q - controllerBus.getMaxQ() - controllerBus.getMinQ())
                / (controllerBus.getMaxQ() - controllerBus.getMinQ());
    }

    private static double calculateKi(LfBus controllerBus) {
        double q = controllerBus.getQ().eval() + controllerBus.getLoadTargetQ();
        return qToK(q, controllerBus);
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
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

        SensitivityContext sensitivityContext = SensitivityContext.create(allControlledBusList, context.getLoadFlowContext());

        OuterLoopStatus status = OuterLoopStatus.STABLE;

        List<String> adjustedZoneNames = new ArrayList<>();
        for (var e : activeSecondaryVoltageControls.entrySet()) {
            var secondaryVoltageControl = e.getKey();
            var controlledBuses = e.getValue();
            var pilotBus = secondaryVoltageControl.getPilotBus();
            double svcTargetDv = secondaryVoltageControl.getTargetValue() - pilotBus.getV();
            LOGGER.debug("Secondary voltage control of zone '{}' needs a pilot point voltage adjustment: {} -> {}",
                    secondaryVoltageControl.getZoneName(), pilotBus.getV() * pilotBus.getNominalV(),
                    secondaryVoltageControl.getTargetValue() * pilotBus.getNominalV());
            boolean adjusted = adjustPrimaryVoltageControlTargets2(secondaryVoltageControl.getZoneName(), sensitivityContext, controlledBuses,
                                                                  pilotBus, svcTargetDv);
            if (adjusted) {
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
