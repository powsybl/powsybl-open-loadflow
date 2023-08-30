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
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SecondaryVoltageControlOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecondaryVoltageControlOuterLoop.class);

    private static final double DV_EPS = 1E-4;

    @Override
    public String getType() {
        return "SecondaryVoltageControl";
    }

    private static List<LfBus> findControllerBuses(LfBus controlledBus) {
        return controlledBus.getGeneratorVoltageControl().orElseThrow()
                .getMergedControllerElements().stream()
                .filter(bus -> !bus.isDisabled() && bus.isGeneratorVoltageControlEnabled())
                .toList();
    }

    private void findActiveSecondaryVoltageControls(LfNetwork network, Map<LfSecondaryVoltageControl, List<LfBus>> activeSecondaryVoltageControls,
                                                    Set<LfBus> allControlledBusSet) {
        List<LfSecondaryVoltageControl> secondaryVoltageControls = network.getSecondaryVoltageControls().stream()
                .filter(control -> !control.getPilotBus().isDisabled())
                .toList();
        for (LfSecondaryVoltageControl secondaryVoltageControl : secondaryVoltageControls) {
            List<LfBus> activeControlledBuses = secondaryVoltageControl.getControlledBuses().stream()
                    .filter(controlledBus -> {
                        GeneratorVoltageControl voltageControl = controlledBus.getGeneratorVoltageControl().orElseThrow();
                        return !controlledBus.isDisabled()
                                && !findControllerBuses(controlledBus).isEmpty()
                                && !voltageControl.isHidden()
                                && voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN;
                    })
                    .toList();
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

        double calculateSensiK(LfBus controllerBus, LfBus controlledBus) {
            return 2 * calculateSensiQ(controllerBus, controlledBus)
                    / (controllerBus.getMaxQ() - controllerBus.getMinQ());
        }

        /**
         * Calculate controlled bus voltage to controller bus reactive power injection sensitivity.
         */
        double calculateSensiQ(LfBus controllerBus, LfBus controlledBus) {
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
        double calculateSensiVpp(LfBus controlledBus, LfBus pilotBus) {
            int controlledBusSensiColumn = busNumToSensiColumn.get(controlledBus.getNum());
            return getCalculatedV(pilotBus).calculateSensi(sensitivities, controlledBusSensiColumn);
        }
    }

    private static void printMatrix(String name, DenseMatrix m) {
        if (LOGGER.isDebugEnabled()) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try (PrintStream ps = new PrintStream(os)) {
                m.print(ps);
                LOGGER.trace("{}=\n{}", name, new String(os.toByteArray(), StandardCharsets.UTF_8));
            }
        }
    }

    private static double qToK(double q, LfBus controllerBus) {
        return (2 * q - controllerBus.getMaxQ() - controllerBus.getMinQ())
                / (controllerBus.getMaxQ() - controllerBus.getMinQ());
    }

    private static double calculateKi(LfBus controllerBus) {
        double q = controllerBus.getQ().eval() + controllerBus.getLoadTargetQ();
        return qToK(q, controllerBus);
    }

    private static DenseMatrix createA(List<LfBus> controllerBuses, Map<Integer, Integer> controllerBusIndex) {
        int n = controllerBuses.size();
        DenseMatrix a = new DenseMatrix(n, n);
        for (LfBus controllerBusI : controllerBuses) {
            for (LfBus controllerBusJ : controllerBuses) {
                int i = controllerBusIndex.get(controllerBusI.getNum());
                int j = controllerBusIndex.get(controllerBusJ.getNum());
                a.set(i, j, i == j ? 1d - (1d / n) : -1d / n);
            }
        }
        return a;
    }

    private static DenseMatrix createK0(List<LfBus> controllerBuses, Map<Integer, Integer> controllerBusIndex) {
        int n = controllerBuses.size();
        DenseMatrix k0 = new DenseMatrix(n, 1);
        for (LfBus controllerBus : controllerBuses) {
            int i = controllerBusIndex.get(controllerBus.getNum());
            k0.set(i, 0, calculateKi(controllerBus));
        }
        return k0;
    }

    private static DenseMatrix createJk(SensitivityContext sensitivityContext, List<LfBus> controllerBuses, Map<Integer, Integer> controllerBusIndex) {
        int n = controllerBuses.size();
        DenseMatrix jK = new DenseMatrix(n, n);
        for (LfBus controllerBusI : controllerBuses) {
            for (LfBus controllerBusJ : controllerBuses) {
                int i = controllerBusIndex.get(controllerBusI.getNum());
                int j = controllerBusIndex.get(controllerBusJ.getNum());
                LfBus controlledBus2 = controllerBusJ.getGeneratorVoltageControl().orElseThrow().getControlledBus();
                jK.set(i, j, sensitivityContext.calculateSensiK(controllerBusI, controlledBus2));
            }
        }
        return jK;
    }

    private static DenseMatrix createJvpp(SensitivityContext sensitivityContext, LfBus pilotBus, List<LfBus> controllerBuses, Map<Integer, Integer> controllerBusIndex) {
        int n = controllerBuses.size();
        DenseMatrix jVpp = new DenseMatrix(n, 1);
        for (LfBus controllerBus : controllerBuses) {
            int i = controllerBusIndex.get(controllerBus.getNum());
            LfBus controlledBus = controllerBus.getGeneratorVoltageControl().orElseThrow().getControlledBus();
            jVpp.set(i, 0, sensitivityContext.calculateSensiVpp(controlledBus, pilotBus));
        }
        return jVpp;
    }

    private static double calculateNorm2(DenseMatrix vect) {
        double norm2Dv = 0;
        for (int i = 0; i < vect.getRowCount(); i++) {
            norm2Dv += FastMath.pow(vect.get(i, 0), 2);
        }
        norm2Dv = FastMath.sqrt(norm2Dv);
        return norm2Dv;
    }

    private boolean adjustPrimaryVoltageControlTargets(String zoneName, SensitivityContext sensitivityContext,
                                                       List<LfBus> controlledBuses, LfBus pilotBus, double pilotDv) {
        boolean adjusted = false;

        List<LfBus> controllerBuses = controlledBuses.stream()
                .flatMap(controlledBus -> findControllerBuses(controlledBus).stream())
                .toList();

        var controllerBusIndex = buildBusIndex(controllerBuses);

        DenseMatrix a = createA(controllerBuses, controllerBusIndex);
        printMatrix("a", a);

        DenseMatrix k0 = createK0(controllerBuses, controllerBusIndex);
        printMatrix("k0", k0);

        DenseMatrix rhs = a.times(k0, -1);
        printMatrix("rhs", rhs);

        DenseMatrix jK = createJk(sensitivityContext, controllerBuses, controllerBusIndex);
        printMatrix("jK", jK);

        DenseMatrix jVpp = createJvpp(sensitivityContext, pilotBus, controllerBuses, controllerBusIndex);
        printMatrix("jVpp", jVpp);

        DenseMatrix jVppT = jVpp.transpose();

        DenseMatrix b = a.times(jK);
        printMatrix("b", b);

        // replace last row
        for (int j = 0; j < b.getColumnCount(); j++) {
            b.set(b.getRowCount() - 1, j, jVppT.get(0, j));
        }
        rhs.set(rhs.getRowCount() - 1, 0, pilotDv);
        printMatrix("b (modified)", b);
        printMatrix("rhs (modified)", rhs);

        try (LUDecomposition luDecomposition = b.decomposeLU()) {
            luDecomposition.solve(rhs);
        }
        @SuppressWarnings("UnnecessaryLocalVariable")
        DenseMatrix dv = rhs;
        printMatrix("dv", dv);

        double norm2Dv = calculateNorm2(dv);
        LOGGER.trace("||dv||2={}", norm2Dv);

        if (norm2Dv > DV_EPS * dv.getRowCount()) {
            for (LfBus controllerBus : controllerBuses) {
                int i = controllerBusIndex.get(controllerBus.getNum());
                var pvc = controllerBus.getGeneratorVoltageControl().orElseThrow();
                LfBus controlledBus = pvc.getControlledBus();
                double newPvcTargetV = pvc.getTargetValue() + dv.get(i, 0);
                LOGGER.info("Adjust target voltage of controlled bus '{}': {} -> {}",
                        controlledBus.getId(), pvc.getTargetValue() * controlledBus.getNominalV(),
                        newPvcTargetV * controlledBus.getNominalV());
                pvc.setTargetValue(newPvcTargetV);
                adjusted = true;
            }
        }

        return adjusted;
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
            boolean adjusted = adjustPrimaryVoltageControlTargets(secondaryVoltageControl.getZoneName(), sensitivityContext, controlledBuses,
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
