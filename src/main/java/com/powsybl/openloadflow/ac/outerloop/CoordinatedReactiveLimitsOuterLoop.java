/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.SecondaryVoltageControlOuterLoop.SensitivityContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.VoltageControl;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class CoordinatedReactiveLimitsOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoordinatedReactiveLimitsOuterLoop.class);

    public static final String NAME = "CoordinatedReactiveLimits";

    private static final double Q_LIMIT_EPSILON = 1E-6; // 10-3 MVar in PU
    private static final double SENSI_EPS = 1e-6;
    private static final double DV_EPS = 1e-8;

    private final MatrixFactory matrixFactory;

    public CoordinatedReactiveLimitsOuterLoop() {
        this(new SparseMatrixFactory());
    }

    public CoordinatedReactiveLimitsOuterLoop(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public OuterLoopResult check(AcOuterLoopContext context, ReportNode reportNode) {
        var outerLoopStatus = OuterLoopStatus.STABLE;

        // check if some controller buses are out of their limits
        List<String> controllerBusIdsToAdjust = new ArrayList<>();
        for (LfBus controllerBus : context.getNetwork().<LfBus>getControllerElements(VoltageControl.Type.GENERATOR)) {
            double minQ = controllerBus.getMinQ();
            double maxQ = controllerBus.getMaxQ();
            double q = controllerBus.getQ().eval() + controllerBus.getLoadTargetQ();
            if (q < minQ - Q_LIMIT_EPSILON) {
                LOGGER.trace("Need to adjust controller bus '{}' from {} to min limit {}",
                        controllerBus.getId(), q * PerUnit.SB, minQ * PerUnit.SB);
                controllerBusIdsToAdjust.add(controllerBus.getId());
            } else if (q > maxQ + Q_LIMIT_EPSILON) {
                LOGGER.trace("Need to adjust controller bus '{}' from {} to max limit {}",
                        controllerBus.getId(), q * PerUnit.SB, maxQ * PerUnit.SB);
                controllerBusIdsToAdjust.add(controllerBus.getId());
            }
        }
        LOGGER.debug("{} controller buses need to be adjusted", controllerBusIdsToAdjust.size());

        if (!controllerBusIdsToAdjust.isEmpty()) {
            List<LfBus> controlledBuses = context.getNetwork().<LfBus>getControlledBuses(VoltageControl.Type.GENERATOR)
                    .stream()
                    .filter(LfBus::isGeneratorVoltageControlEnabled)
                    .toList();

            if (!controlledBuses.isEmpty()) {
                List<LfBus> firstControllerBuses = controlledBuses.stream()
                        .map(b -> {
                            var controllerBuses = b.getGeneratorVoltageControl().orElseThrow().getControllerElements();
                            if (controllerBuses.size() > 1) {
                                throw new PowsyblException("Shared remote voltage control not supported: "
                                        + controllerBuses.stream().map(LfBus::getId).toList());
                            }
                            return controllerBuses.getFirst();
                        })
                        .distinct()
                        .toList();

                var controlledBusIndex = LfBus.buildIndex(controlledBuses);
                var controllerBusIndex = LfBus.buildIndex(firstControllerBuses);
                SensitivityContext sensitivityContext = SensitivityContext.create(controlledBuses,
                        controlledBusIndex,
                        context.getLoadFlowContext());

                // q + dqdv1 * dv1 + dqdv2 * dv2 + ... + dqdvn * dvn = qLimitMax - epsilon
                // or
                // q + dqdv1 * dv1 / dqdv2 * dv2 + ... + dqdvn * dvn = qLimitMin + epsilon
                Matrix j = matrixFactory.create(firstControllerBuses.size(), controlledBuses.size(), firstControllerBuses.size());
                for (LfBus controlledBus : controlledBuses) {
                    for (LfBus firstControllerBus : firstControllerBuses) {
                        double s = sensitivityContext.calculateSensiQ(firstControllerBus, controlledBus);
                        int row = controllerBusIndex.get(firstControllerBus.getNum());
                        int col = controlledBusIndex.get(controlledBus.getNum());
                        if (Math.abs(s) >= SENSI_EPS) {
                            j.add(row, col, s);
                        }
                    }
                }

                double[] dq = new double[firstControllerBuses.size()];
                for (LfBus firstControllerBus : firstControllerBuses) {
                    double minQ = firstControllerBus.getMinQ();
                    double maxQ = firstControllerBus.getMaxQ();
                    double q = firstControllerBus.getQ().eval() + firstControllerBus.getLoadTargetQ();
                    int row = controllerBusIndex.get(firstControllerBus.getNum());
                    if (q < minQ) {
                        dq[row] = minQ - q;
                    } else if (q > maxQ) {
                        dq[row] = maxQ - q;
                    } else {
                        dq[row] = 0;
                    }
                }

                try (LUDecomposition luDecomposition = j.decomposeLU()) {
                    luDecomposition.solve(dq);
                }

                List<String> controlledBusIdsToAdjust = new ArrayList<>();
                for (LfBus controlledBus : controlledBuses) {
                    int row = controlledBusIndex.get(controlledBus.getNum());
                    double dv = dq[row];
                    if (Math.abs(dv) > DV_EPS) {
                        var vc = controlledBus.getGeneratorVoltageControl().orElseThrow();
                        var newTargetValue = vc.getTargetValue() + dv;
                        LOGGER.trace("Adjust target voltage of controlled bus '{}': {} -> {}",
                                controlledBus.getId(), vc.getTargetValue() * controlledBus.getNominalV(),
                                newTargetValue * controlledBus.getNominalV());
                        vc.setTargetValue(newTargetValue);
                        controlledBusIdsToAdjust.add(controlledBus.getId());
                    }
                }
                LOGGER.debug("{} controlled buses have been adjusted", controlledBusIdsToAdjust.size());
            }

            outerLoopStatus = OuterLoopStatus.UNSTABLE;
        }
        return new OuterLoopResult(NAME, outerLoopStatus);
    }

    @Override
    public boolean canFixUnrealisticState() {
        return true;
    }
}
