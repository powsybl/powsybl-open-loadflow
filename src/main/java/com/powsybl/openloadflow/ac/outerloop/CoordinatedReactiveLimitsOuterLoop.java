/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.google.common.base.Stopwatch;
import com.google.ortools.Loader;
import com.google.ortools.modelbuilder.*;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.SecondaryVoltageControlOuterLoop.SensitivityContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.VoltageControl;
import com.powsybl.openloadflow.util.PerUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.ac.outerloop.SecondaryVoltageControlOuterLoop.buildBusIndex;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class CoordinatedReactiveLimitsOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoordinatedReactiveLimitsOuterLoop.class);

    public static final String NAME = "CoordinatedReactiveLimits";

    private static final double Q_LIMIT_EPSILON = 1E-6; // 10-3 MVar in PU
    private static final double DV_MAX = 0.2;
    private static final double SENSI_EPS = 1e-9;
    private static final double Q_ADJUST_EPS = 1e-3; // 1 MVar in PU
    private static final int MAX_CONTROLLED_BUSES = 5;
    private static final double L1_WEIGHT = 0.1; // weight for L1 term
    private static final double DV_MAX_WEIGHT = 1; // weight for max term (higher = more spreading)

    public record ControllerBusToLimit(LfBus controllerBus, double q, double minQ, double maxQ, LfBus.QLimitType limitType) {

        double getLimit() {
            return this.limitType == LfBus.QLimitType.MIN_Q ? minQ : maxQ;
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        Loader.loadNativeLibraries();
    }

    private static ModelBuilder createModelBuilder(List<ControllerBusToLimit> controllerBusesToAdjust,
                                                   List<LfBus> allControlledBuses,
                                                   SensitivityContext sensitivityContext,
                                                   List<Variable> dvVars,
                                                   int[] controlledBusNumToDvVarIndex) {
        ModelBuilder modelBuilder = new ModelBuilder();

        // find for each controller buses most influential controlled buses
        List<List<Pair<LfBus, Double>>> mostInfluentialControlledBuses = new ArrayList<>();
        for (ControllerBusToLimit toLimit : controllerBusesToAdjust) {
            LfBus controllerBus = toLimit.controllerBus();
            List<Pair<LfBus, Double>> controlledBusesWithSensi = new ArrayList<>();
            for (LfBus controlledBus : allControlledBuses) {
                double dqDv = sensitivityContext.calculateSensiQ(controllerBus, controlledBus);
                controlledBusesWithSensi.add(Pair.of(controlledBus, dqDv));
            }
            mostInfluentialControlledBuses.add(controlledBusesWithSensi.stream()
                    .sorted(Comparator.comparing((Pair<LfBus, Double> o) -> Math.abs(o.getRight())).reversed())
                    .limit(MAX_CONTROLLED_BUSES)
                    .toList());
        }

        // create one variable per controlled bus we want to be adjustable
        List<Pair<LfBus, Double>> controlledBusesToAdjust = mostInfluentialControlledBuses
                .stream()
                .flatMap(List::stream)
                .toList();

        for (int iDvVar = 0; iDvVar < controlledBusesToAdjust.size(); iDvVar++) {
            dvVars.add(modelBuilder.newNumVar(-DV_MAX, DV_MAX, "dv_" + iDvVar));
            controlledBusNumToDvVarIndex[controlledBusesToAdjust.get(iDvVar).getLeft().getNum()] = iDvVar;
        }

        // for each controller bus, add constraint to force finding a global voltage adjustment (on all controller buses)
        // that allows to go inside the reactive limits of the controlled buses
        //
        // q + dqdv1 * dv1 + dqdv2 * dv2 + ... + dqdvn * dvn <= qLimitMax - epsilon
        // or
        // q + dqdv1 * dv1 / dqdv2 * dv2 + ... + dqdvn * dvn >= qLimitMin + epsilon
        //
        for (int iController = 0; iController < controllerBusesToAdjust.size(); iController++) {
            ControllerBusToLimit toLimit = controllerBusesToAdjust.get(iController);
            var exprBuilder = LinearExpr.newBuilder();
            // only add in the equation already selected most influential controlled buses
            for (var p : mostInfluentialControlledBuses.get(iController)) {
                LfBus controlledBus = p.getLeft();
                double dqDv = p.getRight();
                if (Math.abs(dqDv) > SENSI_EPS) {
                    int iDvVar = controlledBusNumToDvVarIndex[controlledBus.getNum()];
                    Variable dvVar = dvVars.get(iDvVar);
                    exprBuilder.addTerm(dvVar, dqDv);
                }
            }
            modelBuilder.addGreaterOrEqual(exprBuilder.build(), toLimit.minQ() + Q_LIMIT_EPSILON - toLimit.q());
            modelBuilder.addLessOrEqual(exprBuilder.build(), toLimit.maxQ() - Q_LIMIT_EPSILON - toLimit.q());
        }

        LOGGER.debug("Model built with {} variables", dvVars.size());

        // max deviation variable to force spreading voltage deviation over all controlled buses
        Variable dvMax = modelBuilder.newNumVar(0, DV_MAX, "dv_max");

        // minimize the voltage adjustments
        //
        // abs(dv1) + abs(dv2) + ... + abs(dvn)
        // equivalent to abs_dv1 + abs_dv2 + ... + abs_dvn
        // with abs_dvn >= dvn and abs_dvn >= -dvn
        //
        var objectiveBuilder = LinearExpr.newBuilder();
        for (int i = 0; i < dvVars.size(); i++) {
            var dv = dvVars.get(i);
            var absDv = modelBuilder.newNumVar(0, Double.POSITIVE_INFINITY, "abs_dv_" + i);
            modelBuilder.addGreaterOrEqual(absDv, dv); // abs_dv >= dv
            modelBuilder.addLessOrEqual(LinearExpr.newBuilder().addTerm(dv, -1.0).build(), absDv); // abs_dv >= -dv
            modelBuilder.addLessOrEqual(absDv, dvMax);
            objectiveBuilder.addTerm(absDv, L1_WEIGHT);
        }
        objectiveBuilder.addTerm(dvMax, DV_MAX_WEIGHT); // minimize also the max deviation variable
        modelBuilder.minimize(objectiveBuilder.build());

//        System.out.println(modelBuilder.exportToLpString(false));

        return modelBuilder;
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        List<LfBus> allControllerBuses = context.getNetwork().<LfBus>getControllerElements(VoltageControl.Type.GENERATOR)
                .stream()
                .filter(LfBus::isGeneratorVoltageControlEnabled)
                .toList();
        List<ControllerBusToLimit> controllerBusesToAdjust = new ArrayList<>();
        allControllerBuses.forEach(bus -> {
            double minQ = bus.getMinQ();
            double maxQ = bus.getMaxQ();
            double q = bus.getQ().eval() + bus.getLoadTargetQ();
            if (q < minQ) {
                LOGGER.debug("Need to adjust controller bus '{}' from {} to min limit {}", bus.getId(), q * PerUnit.SB, minQ * PerUnit.SB);
                controllerBusesToAdjust.add(new ControllerBusToLimit(bus, q, minQ, maxQ, LfBus.QLimitType.MIN_Q));
            } else if (q > maxQ) {
                LOGGER.debug("Need to adjust controller bus '{}' from {} to max limit {}", bus.getId(), q * PerUnit.SB, maxQ * PerUnit.SB);
                controllerBusesToAdjust.add(new ControllerBusToLimit(bus, q, minQ, maxQ, LfBus.QLimitType.MAX_Q));
            }
        });

        var outerLoopStatus = OuterLoopStatus.STABLE;
        if (!controllerBusesToAdjust.isEmpty()) {
            double adjustSumQ = controllerBusesToAdjust.stream().mapToDouble(value -> Math.abs(value.q() - value.getLimit())).sum();
            if (Math.abs(adjustSumQ) > Q_ADJUST_EPS) {
                LOGGER.debug("{} controller buses to adjust {} MVar", controllerBusesToAdjust.size(), adjustSumQ * PerUnit.SB);

                List<LfBus> allControlledBuses = allControllerBuses.stream()
                        .map(b -> b.getGeneratorVoltageControl().orElseThrow().getControlledBus())
                        .distinct()
                        .toList();

                var allControlledBusIndex = buildBusIndex(allControlledBuses);
                SensitivityContext sensitivityContext = SensitivityContext.create(allControlledBuses,
                        allControlledBusIndex,
                        context.getLoadFlowContext());

                List<Variable> dvVars = new ArrayList<>();
                int[] controlledBusNumToDvVarIndex = new int[context.getNetwork().getBuses().size()];
                Arrays.fill(controlledBusNumToDvVarIndex, -1);
                var modelBuilder = createModelBuilder(controllerBusesToAdjust, allControlledBuses, sensitivityContext,
                        dvVars, controlledBusNumToDvVarIndex);

                ModelSolver solver = new ModelSolver("highs");
                Stopwatch stopwatch = Stopwatch.createStarted();
                SolveStatus solverStatus = solver.solve(modelBuilder);
                stopwatch.stop();
                LOGGER.debug("Model solved with status {} in {} ms", solverStatus, stopwatch.elapsed(TimeUnit.MILLISECONDS));
                if (solverStatus != SolveStatus.OPTIMAL) {
                    throw new PowsyblException("Solver failed: " + solverStatus);
                }

                for (LfBus controlledBus : allControlledBuses) {
                    int iDvVar = controlledBusNumToDvVarIndex[controlledBus.getNum()];
                    if (iDvVar != -1) {
                        var dvVar = dvVars.get(iDvVar);
                        double dv = solver.getValue(dvVar);
                        if (Math.abs(dv) > 0) {
                            var vc = controlledBus.getGeneratorVoltageControl().orElseThrow();
                            var newTargetValue = vc.getTargetValue() + dv;
                            LOGGER.debug("Adjust target voltage of controlled bus '{}': {} -> {}",
                                    controlledBus.getId(), vc.getTargetValue() * controlledBus.getNominalV(),
                                    newTargetValue * controlledBus.getNominalV());
                            vc.setTargetValue(newTargetValue);
                            outerLoopStatus = OuterLoopStatus.UNSTABLE;
                        }
                    }
                }
            }
        }

        return new OuterLoopResult(NAME, outerLoopStatus);
    }

    @Override
    public boolean canFixUnrealisticState() {
        return true;
    }
}
