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
import com.powsybl.openloadflow.ac.outerloop.ReactiveLimitsOuterLoop.ControllerBusToPqBus;
import com.powsybl.openloadflow.ac.outerloop.SecondaryVoltageControlOuterLoop.SensitivityContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.VoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.ac.outerloop.SecondaryVoltageControlOuterLoop.buildBusIndex;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class CoordinatedReactiveLimitsOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoordinatedReactiveLimitsOuterLoop.class);

    public static final String NAME = "CoordinatedReactiveLimits";

    private static final double Q_LIMIT_EPSILON = 1E-6;
    private static final double DV_MAX = 0.2;
    private static final double SENSI_EPS = 1e-9;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        Loader.loadNativeLibraries();
    }

    private static ModelBuilder createModelBuilder(List<ControllerBusToPqBus> controllerBusesToAdjust,
                                                   List<LfBus> controlledBusesToAdjust,
                                                   SensitivityContext sensitivityContext,
                                                   List<Variable> dvs) {
        ModelBuilder modelBuilder = new ModelBuilder();

        for (int i = 0; i < controlledBusesToAdjust.size(); i++) {
            dvs.add(modelBuilder.newNumVar(-DV_MAX, DV_MAX, "dv_" + i));
        }

        // for each controller bus, add constraint to force finding a global voltage adjustment (on all controller buses)
        // that allows to go inside the reactive limits of the controlled buses
        //
        // q + dqdv1 * dv1 + dqdv2 * dv2 + ... + dqdvn * dvn <= qLimitMax - epsilon
        // or
        // q + dqdv1 * dv1 / dqdv2 * dv2 + ... + dqdvn * dvn >= qLimitMin + epsilon
        //
        for (ControllerBusToPqBus pq : controllerBusesToAdjust) {
            LfBus controllerBus = pq.getControllerBus();
            var exprBuilder = LinearExpr.newBuilder();
            for (int i = 0; i < controlledBusesToAdjust.size(); i++) {
                LfBus controlledBus = controlledBusesToAdjust.get(i);
                double dqDv = sensitivityContext.calculateSensiQ(controllerBus, controlledBus);
                if (Math.abs(dqDv) > SENSI_EPS) {
                    Variable dv = dvs.get(i);
                    exprBuilder.addTerm(dv, dqDv);
                }
            }
            if (pq.getLimitType() == LfBus.QLimitType.MIN_Q) {
                modelBuilder.addGreaterOrEqual(exprBuilder.build(), pq.getqLimit() + Q_LIMIT_EPSILON - pq.getQ());
            } else {
                modelBuilder.addLessOrEqual(exprBuilder.build(), pq.getqLimit() - Q_LIMIT_EPSILON - pq.getQ());
            }
        }

        LOGGER.debug("Model built with {} variables", dvs.size());

        // minimize the voltage adjustments
        //
        // abs(dv1) + abs(dv2) + ... + abs(dvn)
        // equivalent to abs_dv1 + abs_dv2 + ... + abs_dvn
        // with abs_dvn >= dvn and abs_dvn >= -dvn
        //
        var objectiveBuilder = LinearExpr.newBuilder();
        for (int i = 0; i < dvs.size(); i++) {
            var dv = dvs.get(i);
            var absDv = modelBuilder.newNumVar(0, Double.POSITIVE_INFINITY, "abs_dv_" + i);
            modelBuilder.addGreaterOrEqual(absDv, dv); // abs_dv >= dv
            modelBuilder.addLessOrEqual(LinearExpr.newBuilder().addTerm(dv, -1.0).build(), absDv); // abs_dv >= -dv
            objectiveBuilder.addTerm(absDv, 1.0);
        }
        modelBuilder.minimize(objectiveBuilder.build());

//        System.out.println(modelBuilder.exportToLpString(false));

        return modelBuilder;
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        List<ControllerBusToPqBus> controllerBusesToAdjust = new ArrayList<>();
        context.getNetwork().<LfBus>getControllerElements(VoltageControl.Type.GENERATOR).forEach(bus -> {
            if (bus.isGeneratorVoltageControlEnabled()) {
                double minQ = bus.getMinQ();
                double maxQ = bus.getMaxQ();
                double q = bus.getQ().eval() + bus.getLoadTargetQ();
                if (q < minQ) {
                    controllerBusesToAdjust.add(new ControllerBusToPqBus(bus, q, minQ, LfBus.QLimitType.MIN_Q));
                } else if (q > maxQ) {
                    controllerBusesToAdjust.add(new ControllerBusToPqBus(bus, q, maxQ, LfBus.QLimitType.MAX_Q));
                }
            }
        });
        double adjustSumQ = controllerBusesToAdjust.stream().mapToDouble(value -> Math.abs(value.getQ() - value.getqLimit())).sum();
        LOGGER.debug("{} controller buses to adjust {} MVar", controllerBusesToAdjust.size(), adjustSumQ);

        var outerLoopStatus = OuterLoopStatus.STABLE;
        if (!controllerBusesToAdjust.isEmpty()) {
            List<LfBus> controlledBusesToAdjust = controllerBusesToAdjust.stream()
                    .map(b -> b.getControllerBus().getGeneratorVoltageControl().orElseThrow().getControlledBus())
                    .distinct()
                    .toList();

            var controlledBusIndex = buildBusIndex(controlledBusesToAdjust);
            SensitivityContext sensitivityContext = SensitivityContext.create(controlledBusesToAdjust,
                                                                              controlledBusIndex,
                                                                              context.getLoadFlowContext());

            List<Variable> dvs = new ArrayList<>();
            var modelBuilder = createModelBuilder(controllerBusesToAdjust, controlledBusesToAdjust, sensitivityContext, dvs);

            ModelSolver solver = new ModelSolver("highs");
            Stopwatch stopwatch = Stopwatch.createStarted();
            SolveStatus solverStatus = solver.solve(modelBuilder);
            stopwatch.stop();
            LOGGER.debug("Model solved with status {} in {} ms", solverStatus, stopwatch.elapsed(TimeUnit.MILLISECONDS));
            if (solverStatus != SolveStatus.OPTIMAL) {
                throw new PowsyblException("Solver failed: " + solverStatus);
            }

            for (int i = 0; i < controlledBusesToAdjust.size(); i++) {
                LfBus controlledBus = controlledBusesToAdjust.get(i);
                double dv = solver.getValue(dvs.get(i));
                var vc = controlledBus.getGeneratorVoltageControl().orElseThrow();
                var newTargetValue = vc.getTargetValue() + dv;
                LOGGER.trace("Adjust target voltage of controlled bus '{}': {} -> {}",
                        controlledBus.getId(), vc.getTargetValue() * controlledBus.getNominalV(),
                        newTargetValue * controlledBus.getNominalV());
                vc.setTargetValue(newTargetValue);
                outerLoopStatus = OuterLoopStatus.UNSTABLE;
            }
        }

        return new OuterLoopResult(NAME, outerLoopStatus);
    }
}
