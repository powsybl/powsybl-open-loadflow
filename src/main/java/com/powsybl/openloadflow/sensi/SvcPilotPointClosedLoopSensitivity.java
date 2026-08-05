/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.SecondaryVoltageControlOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.SecondaryVoltageControlOuterLoop.SensitivityContext;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfSecondaryVoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Closed-loop SVC pilot voltage sensitivity coefficients.
 *
 * <p>Given a converged AC state with secondary voltage control active, computes a vector {@code w} such that
 * perturbing the queried zone's pilot-point voltage target by +1 (pu) induces, after the SVC re-coordination,
 * a shift of {@code w[c]} on controlled bus {@code c}'s {@link com.powsybl.openloadflow.ac.equations.AcEquationType#BUS_TARGET_V}
 * setpoint. Other zones' pilot targets are held fixed; the K-equalisation constraint holds in every zone.
 *
 * <p>Plugging this {@code w} into the standard OLF sensitivity RHS-builder (one nonzero per controlled bus's
 * BUS_TARGET_V equation column) and running the Jacobian transposed-solve as usual yields the closed-loop
 * d(anything) / dV_pilot* exactly — no other code path needs to change.
 *
 * <p>The coordination system ({@code A}, {@code J_K}, {@code J_Vpp}, {@code B = A·J_K^T} with the last row of
 * each zone replaced by the pilot-voltage constraint) is exactly the one assembled by
 * {@link SecondaryVoltageControlOuterLoop}; the building blocks are reused from there. The only difference is the
 * right-hand side: a unit vector with {@code 1} at the queried zone's last-row position (instead of the outer
 * loop's {@code -A·k0} / pilot-error vector), so that the solution {@code w} is the closed-loop derivative
 * w.r.t. the queried pilot target rather than a one-shot target correction.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public final class SvcPilotPointClosedLoopSensitivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(SvcPilotPointClosedLoopSensitivity.class);

    private SvcPilotPointClosedLoopSensitivity() {
    }

    /**
     * Compute the controlled-bus weights {@code w} for a unit perturbation of the queried zone's pilot voltage
     * target. Returns a map keyed by controlled bus to its weight (in pu V per pu V).
     */
    public static Map<LfBus, Double> computeControlledBusWeights(LfBus queriedPilotBus, AcLoadFlowContext context) {
        LfNetwork lfNetwork = context.getNetwork();
        List<LfSecondaryVoltageControl> svcs = lfNetwork.getSecondaryVoltageControls().stream()
                .filter(svc -> !svc.getEnabledControllerBuses().isEmpty())
                .toList();
        Optional<LfSecondaryVoltageControl> queriedZoneOpt = svcs.stream()
                .filter(svc -> svc.getPilotBus() == queriedPilotBus)
                .findFirst();
        if (queriedZoneOpt.isEmpty()) {
            // The queried zone has no enabled controller bus at the converged state (all its units
            // disconnected / out of reactive range / deactivated), so perturbing its pilot-point
            // target has no closed-loop effect: the sensitivity is identically zero. Return no
            // weights rather than failing the whole sensitivity analysis.
            LOGGER.debug("Bus {} is not a pilot bus of any active SVC zone; pilot-target sensitivity is zero",
                    queriedPilotBus.getId());
            return Map.of();
        }
        LfSecondaryVoltageControl queriedZone = queriedZoneOpt.get();

        // All-zones controller / controlled buses (same assembly as the SVC outer loop).
        List<LfBus> allControllerBuses = svcs.stream()
                .flatMap(svc -> svc.getEnabledControllerBuses().stream())
                .toList();
        List<LfBus> allControlledBuses = allControllerBuses.stream()
                .map(b -> b.getGeneratorVoltageControl().orElseThrow().getControlledBus())
                .distinct()
                .toList();
        Map<Integer, Integer> controllerBusIndex = LfBus.buildIndex(allControllerBuses);
        Map<Integer, Integer> controlledBusIndex = LfBus.buildIndex(allControlledBuses);

        // Reuse the outer loop's coordination building blocks: open-loop sensitivities, A and B = A·J_K^T.
        SensitivityContext sensitivityContext = SensitivityContext.create(allControlledBuses, controlledBusIndex, context);
        DenseMatrix a = SecondaryVoltageControlOuterLoop.createA(svcs, allControllerBuses, controllerBusIndex);
        DenseMatrix jK = SecondaryVoltageControlOuterLoop.createJk(sensitivityContext, allControllerBuses, allControlledBuses,
                controllerBusIndex, controlledBusIndex).transpose();
        DenseMatrix b = a.times(jK);

        // Replace the last row of each zone block with its pilot-voltage constraint, and put 1 at the queried
        // zone's last-row position in the RHS (unit perturbation of that zone's pilot target).
        DenseMatrix rhs = new DenseMatrix(b.getRowCount(), 1);
        for (LfSecondaryVoltageControl svc : svcs) {
            List<LfBus> controlledInZone = svc.getEnabledControllerBuses().stream()
                    .map(bus -> bus.getGeneratorVoltageControl().orElseThrow().getControlledBus())
                    .distinct()
                    .toList();
            int row = controlledBusIndex.get(controlledInZone.get(controlledInZone.size() - 1).getNum());
            DenseMatrix jVpp = SecondaryVoltageControlOuterLoop.createJvpp(sensitivityContext, svc.getPilotBus(),
                    allControlledBuses, controlledBusIndex);
            for (int j = 0; j < b.getColumnCount(); j++) {
                b.set(row, j, jVpp.get(j, 0));
            }
            if (svc == queriedZone) {
                rhs.set(row, 0, 1.0);
            }
        }

        try (LUDecomposition lu = b.decomposeLU()) {
            lu.solve(rhs);
        }

        Map<LfBus, Double> weights = new LinkedHashMap<>();
        for (LfBus controlled : allControlledBuses) {
            weights.put(controlled, rhs.get(controlledBusIndex.get(controlled.getNum()), 0));
        }
        return weights;
    }
}
