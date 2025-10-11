/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

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

import static com.powsybl.openloadflow.ac.outerloop.SecondaryVoltageControlOuterLoop.buildBusIndex;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class CoordinatedReactiveLimitsOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoordinatedReactiveLimitsOuterLoop.class);

    public static final String NAME = "CoordinatedReactiveLimits";

    private static final double Q_LIMIT_EPSILON = 1E-6;
    private static final double DV_MAX = 0.2;

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
        // q + dqdv1 * dv1 + dqdv2 * dv2 + ... + dqdvn * dvn <= qlimit + epsilon
        //
        for (ControllerBusToPqBus pq : controllerBusesToAdjust) {
            LfBus controllerBus = pq.getControllerBus();
            var exprBuilder = LinearExpr.newBuilder();
            for (int i = 0; i < controlledBusesToAdjust.size(); i++) {
                LfBus controlledBus = controlledBusesToAdjust.get(i);
                Variable dv = dvs.get(i);
                double dqDv = sensitivityContext.calculateSensiQ(controllerBus, controlledBus);
                exprBuilder.addTerm(dv, dqDv);
            }
            if (pq.getLimitType() == LfBus.QLimitType.MIN_Q) {
                modelBuilder.addLessOrEqual(exprBuilder.build(), pq.getqLimit() - pq.getQ() + Q_LIMIT_EPSILON);
            } else {
                modelBuilder.addGreaterOrEqual(exprBuilder.build(), pq.getqLimit() - pq.getQ() - Q_LIMIT_EPSILON);
            }
        }

        LOGGER.debug("Model built with {} variables", dvs.size());

        // minimize the voltage adjustments
        var objectiveBuilder = LinearExpr.newBuilder();
        for (var dv : dvs) {
            objectiveBuilder.addTerm(dv, 1.0);
        }
        modelBuilder.minimize(objectiveBuilder.build());

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
        LOGGER.debug("{} controller buses to adjust", controllerBusesToAdjust.size());

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
            SolveStatus solverStatus = solver.solve(modelBuilder);
            LOGGER.debug("Solver status: {}", solverStatus);
            if (solverStatus != SolveStatus.OPTIMAL) {
                throw new PowsyblException("Solver failed: " + solverStatus);
            }

            for (int i = 0; i < controlledBusesToAdjust.size(); i++) {
                LfBus controlledBus = controlledBusesToAdjust.get(i);
                double dv = solver.getValue(dvs.get(i));
                var vc = controlledBus.getGeneratorVoltageControl().orElseThrow();
                var newTargetValue = vc.getTargetValue() + dv;
                LOGGER.debug("Adjust target voltage of controlled bus '{}': {} -> {}",
                        controlledBus.getId(), vc.getTargetValue() * controlledBus.getNominalV(),
                        newTargetValue * controlledBus.getNominalV());
                vc.setTargetValue(newTargetValue);
                outerLoopStatus = OuterLoopStatus.UNSTABLE;
            }
        }

        return new OuterLoopResult(NAME, outerLoopStatus);
    }
}
