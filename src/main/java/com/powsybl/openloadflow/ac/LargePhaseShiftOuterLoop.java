/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LargePhaseShiftOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(LargePhaseShiftOuterLoop.class);

    private static final double MAX_SHIFT = Math.toRadians(20);

    private static class ContextData {

        private final Map<String, Double> finalA1ByBranchId = new HashMap<>();
    }

    @Override
    public String getType() {
        return "Large phase shift";
    }

    @Override
    public void initialize(OuterLoopContext context) {
        ContextData data = new ContextData();
        context.setData(data);
        List<String> largeShiftBranchesIds = new ArrayList<>();
        for (var branch : context.getNetwork().getBranches()) {
            if (!branch.isDisabled()) {
                var piModel = branch.getPiModel();
                if (Math.abs(piModel.getA1()) > MAX_SHIFT) {
                    data.finalA1ByBranchId.put(branch.getId(), piModel.getA1());
                    piModel.setA1(Math.copySign(MAX_SHIFT, piModel.getA1()));
                    largeShiftBranchesIds.add(branch.getId());
                }
            }
        }
        if (!largeShiftBranchesIds.isEmpty()) {
            LOGGER.warn("{} branches have a large phase shift: {}", largeShiftBranchesIds.size(), largeShiftBranchesIds);
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        ContextData data = (ContextData) context.getData();
        List<String> branchesIdsNotYetToFinalA1 = new ArrayList<>();
        for (var e : data.finalA1ByBranchId.entrySet()) {
            String branchId = e.getKey();
            double finalA1 = e.getValue();
            var branch = context.getNetwork().getBranchById(branchId);
            var piModel = branch.getPiModel();
            double remainingA1 = finalA1 - piModel.getA1();
            if (remainingA1 != 0) {
                double newA1 = piModel.getA1();
                if (Math.abs(remainingA1) < MAX_SHIFT) {
                    newA1 += remainingA1;
                } else {
                    newA1 += Math.copySign(MAX_SHIFT, remainingA1);
                }
                branchesIdsNotYetToFinalA1.add(branchId);
                piModel.setA1(newA1);
                status = OuterLoopStatus.UNSTABLE;
            }
        }
        if (!branchesIdsNotYetToFinalA1.isEmpty()) {
            LOGGER.debug("{} branches not yet to final shift: {}",
                    branchesIdsNotYetToFinalA1.size(), branchesIdsNotYetToFinalA1);
        }
        return status;
    }
}
