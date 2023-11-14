/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.lf.outerloop.IncrementalContextData;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class IncrementalRatioTapChangerReactivePowerControlOuterLoop implements AcOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalRatioTapChangerReactivePowerControlOuterLoop.class);

    public static final String NAME = "IncrementalRatioTapChangerReactivePowerControl";

    private static final double MIN_TARGET_DEADBAND_MVAR = 0.1;

    private static final int MAX_DIRECTION_CHANGE = 2;
    public static final int DEFAULT_MAX_TAP_SHIFT = 3;
    private final int maxTapShift;

    public IncrementalRatioTapChangerReactivePowerControlOuterLoop(int maxTapShift) {
        this.maxTapShift = maxTapShift;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        var contextData = new IncrementalContextData();
        context.setData(contextData);

        for (LfBranch branch : context.getNetwork().getReactivePowerControllerBranches()) {
            branch.getTransformerReactivePowerControl().ifPresent(rtcReactivePowerControl -> branch.setTransformerReactivePowerControlEnabled(false));
            contextData.getControllersContexts().put(branch.getId(), new IncrementalContextData.ControllerContext(MAX_DIRECTION_CHANGE));
        }
    }

    private boolean isSensitivityReactivePowerPerR1Positive(LfBranch controllerBranch, ControlledSide controlledSide) {
        if (controlledSide == ControlledSide.ONE) {
            ClosedBranchSide1ReactiveFlowEquationTerm q1 = (ClosedBranchSide1ReactiveFlowEquationTerm) controllerBranch.getQ1();
            return q1.der(q1.getR1Var()) > 0;
        } else {
            ClosedBranchSide2ReactiveFlowEquationTerm q2 = (ClosedBranchSide2ReactiveFlowEquationTerm) controllerBranch.getQ2();
            return q2.der(q2.getR1Var()) > 0;
        }
    }

    private boolean adjustWithController(LfBranch controllerBranch, LfBranch controlledBranch, IncrementalContextData contextData, List<String> controlledBranchesWithAllItsControllersToLimit) {
        // only one transformer controls a branch
        var controllerContext = contextData.getControllersContexts().get(controllerBranch.getId());
        TransformerReactivePowerControl reactivePowerControl = controlledBranch.getTransformerReactivePowerControl().orElseThrow();
        PiModel piModel = controllerBranch.getPiModel();
        int previousTapPosition = piModel.getTapPosition();

        // Compute sensitivity to determine in which direction we should move
        ControlledSide controlledSide = reactivePowerControl.getControlledSide();
        boolean isSensitivityPositive = isSensitivityReactivePowerPerR1Positive(controllerBranch, controlledSide);

        // Compute which direction is the good one and shift tap position
        Direction direction = isSensitivityPositive ? Direction.DECREASE : Direction.INCREASE;
        controllerContext.updateAllowedDirection(direction);
        boolean hasMoved = piModel.shiftOneTapPositionToChangeR1(direction);

        if (hasMoved) {
            // Loggers
            Range<Integer> tapPositionRange = piModel.getTapPositionRange();
            LOGGER.debug("Controller branch '{}' change tap from {} to {} (full range: {})", controllerBranch.getId(),
                    previousTapPosition, piModel.getTapPosition(), tapPositionRange);
            if (piModel.getTapPosition() == tapPositionRange.getMinimum()
                    || piModel.getTapPosition() == tapPositionRange.getMaximum()) {
                controlledBranchesWithAllItsControllersToLimit.add(controlledBranch.getId());
            }
            // has been adjusted
            return true;
        }
        // has not been adjusted
        return false;
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
        MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        LfNetwork network = context.getNetwork();
        var contextData = (IncrementalContextData) context.getData();

        List<LfBranch> controllerBranches = network.getReactivePowerControllerBranches(); // TODO : do we want only rtc ?

        // for synthetics logs
        List<String> controlledBranchesOutsideOfDeadband = new ArrayList<>();
        List<String> controlledBranchesAdjusted = new ArrayList<>();
        List<String> controlledBranchesWithAllItsControllersToLimit = new ArrayList<>();

        List<LfBranch> controlledBranches = network.getReactivePowerControlledBranches(); // TODO : do we want only rtc ?

        controlledBranches.forEach(controlledBranch -> {
            TransformerReactivePowerControl reactivePowerControl = controlledBranch.getTransformerReactivePowerControl().orElseThrow();
            double diffQ = getDiffQ(reactivePowerControl);
            double halfTargetDeadband = getHalfTargetDeadband(reactivePowerControl);
            if (Math.abs(diffQ) > halfTargetDeadband) {
                controlledBranchesOutsideOfDeadband.add(controlledBranch.getId());

                // For now, case with only one controller
                LfBranch controllers = reactivePowerControl.getControllerBranch();
                LOGGER.trace("Controlled branch '{}' is outside of its deadband (half is {} MVar) and could need a reactive power adjustment of {} MVar",
                        controlledBranch.getId(), halfTargetDeadband, diffQ);
                boolean adjusted = adjustWithController(controllers, controlledBranch, contextData, controlledBranchesWithAllItsControllersToLimit);
                // If we adjusted the value, outerllop is unstable
                if (adjusted) {
                    controlledBranchesAdjusted.add(controlledBranch.getId());
                    status.setValue(OuterLoopStatus.UNSTABLE);
                }
            }
        });

        // Print some info
        if (!controlledBranchesOutsideOfDeadband.isEmpty() && LOGGER.isInfoEnabled()) {
            Map<String, Double> largestMismatches = controllerBranches.stream()
                    .map(controlledBranch -> Pair.of(controlledBranch.getId(), Math.abs(getDiffQ(controlledBranch.getTransformerReactivePowerControl().get()))))
                    .sorted((p1, p2) -> Double.compare(p2.getRight(), p1.getRight()))
                    .limit(3) // 3 largest
                    .collect(Collectors.toMap(Pair::getLeft, Pair::getRight, (key1, key2) -> key1, LinkedHashMap::new));
            LOGGER.info("{} controlled branch reactive power are outside of their target deadband, largest ones are: {}",
                    controlledBranchesOutsideOfDeadband.size(), largestMismatches);
        }
        if (!controlledBranchesAdjusted.isEmpty()) {
            LOGGER.info("{} controlled branch reactive power have been adjusted by changing at least one tap",
                    controlledBranchesAdjusted.size());
        }
        if (!controlledBranchesWithAllItsControllersToLimit.isEmpty()) {
            LOGGER.info("{} controlled branches have all its controllers to a tap limit: {}",
                    controlledBranchesWithAllItsControllersToLimit.size(), controlledBranchesWithAllItsControllersToLimit);
        }

        return status.getValue();
    }

    protected static double getHalfTargetDeadband(TransformerReactivePowerControl reactivePowerControl) {
        return reactivePowerControl.getTargetDeadband().orElse(MIN_TARGET_DEADBAND_MVAR) / 2;
    }

    private static double getDiffQ(TransformerReactivePowerControl reactivePowerControl) {
        double targetQ = reactivePowerControl.getTargetValue();
        double q = reactivePowerControl.getControlledSide() == ControlledSide.ONE ? reactivePowerControl.getControlledBranch().getQ1().eval()
                : reactivePowerControl.getControlledBranch().getQ2().eval();
        return targetQ - q;
    }
}
