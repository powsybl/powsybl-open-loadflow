/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfShuntImpl;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */

public class IncrementalShuntVoltageControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalShuntVoltageControlOuterLoop.class);

    private static final int MAX_TAP_SHIFT = 3;
    private static final int MAX_DIRECTION_CHANGE = 2;

    private static final class ControllerContext {

        private final MutableInt directionChangeCount = new MutableInt();

        private LfShuntImpl.Controller.AllowedDirection allowedDirection = LfShuntImpl.Controller.AllowedDirection.BOTH;

        public MutableInt getDirectionChangeCount() {
            return directionChangeCount;
        }

        private LfShuntImpl.Controller.AllowedDirection getAllowedDirection() {
            return allowedDirection;
        }

        private void setAllowedDirection(LfShuntImpl.Controller.AllowedDirection allowedDirection) {
            this.allowedDirection = Objects.requireNonNull(allowedDirection);
        }
    }

    private static final class ContextData {

        private final Map<String, ControllerContext> controllersContexts = new HashMap<>();

        private Map<String, ControllerContext> getControllersContexts() {
            return controllersContexts;
        }
    }

    private static List<LfShunt> getControllerShunts(LfNetwork network) {
        return network.getBuses().stream()
                .flatMap(bus -> bus.getControllerShunt().stream())
                .filter(controllerShunt -> !controllerShunt.isDisabled() && controllerShunt.hasVoltageControlCapability())
                .collect(Collectors.toList());
    }

    protected static boolean checkTargetDeadband(Double targetDeadband, double difference) {
        return (targetDeadband != null && Math.abs(difference) > targetDeadband / 2) || targetDeadband == null;
    }

    @Override
    public String getType() {
        return "Incremental Shunt voltage control";
    }

    @Override
    public void initialize(OuterLoopContext context) {
        var contextData = new ContextData();
        context.setData(contextData);

        // All shunt voltage control are disabled for the first equation system resolution.
        for (LfShunt shunt : getControllerShunts(context.getNetwork())) {
            shunt.getVoltageControl().ifPresent(voltageControl -> shunt.setVoltageControlEnabled(false));
            // FIX ME: not safe casting
            for (LfShuntImpl.Controller lfShuntController : ((LfShuntImpl) shunt).getControllers()) {
                contextData.getControllersContexts().put(lfShuntController.getId(), new ControllerContext());
            }
        }
    }

    private static void updateAllowedDirection(ControllerContext controllerContext, LfShuntImpl.Controller.Direction direction) {
        if (controllerContext.getDirectionChangeCount().getValue() <= MAX_DIRECTION_CHANGE) {
            if (!controllerContext.getAllowedDirection().equals(direction.getAllowedDirection()))
            {
                // both vs increase or decrease
                // increase vs decrease or decrease vs increase
                controllerContext.getDirectionChangeCount().increment();
            }
            controllerContext.setAllowedDirection(direction.getAllowedDirection());
        }
    }

    private void adjustWithOneController(LfShuntImpl controllingShunt, LfBus controlledBus, ContextData contextData, int ShuntId,
                                         DenseMatrix sensitivities, double diffV, MutableObject<OuterLoopStatus> status) {
        // only one shunt controls a bus
        LfShuntImpl.Controller controllerShunt = controllingShunt.getControllers().get(0);
        var controllerContext = contextData.getControllersContexts().get(controllerShunt.getId());
        Double targetDeadband = controllingShunt.getShuntVoltageControlTargetDeadband().orElse(null);
        if (checkTargetDeadband(targetDeadband, diffV)) {
            double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) controlledBus.getCalculatedV())
                    .calculateSensi(sensitivities, ShuntId);
            double previousB = controllerShunt.getB();
            double deltaB = diffV / sensitivity;
            controllerShunt.updateTapPositionB(deltaB, MAX_TAP_SHIFT, controllerContext.getAllowedDirection()).ifPresent(direction -> {
                updateAllowedDirection(controllerContext, direction);
                LOGGER.debug("Increment shunt susceptance of '{}': {} -> {}", controllerShunt.getId(), previousB, controllerShunt.getB());
                status.setValue(OuterLoopStatus.UNSTABLE);
                controllingShunt.updateB();
                for (LfNetworkListener listener : controllingShunt.getNetwork().getListeners()) {
                    listener.onShuntTargetBChange(controllingShunt, controllingShunt.getB());
                }
            });
        } else {
            LOGGER.trace("Controlling shunt '{}' is in its deadband: deadband {} vs voltage difference {}", controllingShunt.getId(), targetDeadband, Math.abs(diffV));
        }
    }

    private void adjustWithSeveralControllers(LfShuntImpl controllingShunt, LfBus controlledBus, ContextData contextData, int ShuntId,
                                              DenseMatrix sensitivities, double diffV, MutableObject<OuterLoopStatus> status) {
        // several shunts control the same bus
        double remainingDiffV = diffV;
        boolean hasChanged = true;
        while (hasChanged) {
            hasChanged = false;
            double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) controlledBus.getCalculatedV())
                    .calculateSensi(sensitivities, ShuntId);
            double targetDeadband = controllingShunt.getShuntVoltageControlTargetDeadband().orElse(null);
            for (LfShuntImpl.Controller controllerShunt : controllingShunt.getControllers()) {
                var controllerContext = contextData.getControllersContexts().get(controllerShunt.getId());
                if (checkTargetDeadband(targetDeadband, remainingDiffV)) {
                    double previousB = controllerShunt.getB();
                    double deltaB = remainingDiffV / sensitivity;
                    LfShuntImpl.Controller.Direction direction = controllerShunt.updateTapPositionB(deltaB, 1, controllerContext.getAllowedDirection()).orElse(null);
                    if (direction != null) {
                        updateAllowedDirection(controllerContext, direction);
                        remainingDiffV -= (controllerShunt.getB() - previousB) * sensitivity;
                        hasChanged = true;
                        status.setValue(OuterLoopStatus.UNSTABLE);
                        LOGGER.debug("[Shared control] increment shunt susceptance of '{}': {} -> {}", controllerShunt.getId(), previousB, controllerShunt.getB());
                    }
                } else {
                    LOGGER.trace("Controlling shunt '{}' is in its deadband: deadband {} vs voltage difference {}", controllingShunt.getId(), targetDeadband, Math.abs(diffV));
                }
            }
        }
    }


    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        if (context.getIteration() == 0) {
            for (LfShunt controllerShunt : getControllerShunts(context.getNetwork())) {
                controllerShunt.setVoltageControlEnabled(false);

                // round the susceptance to the closest section
                double b = controllerShunt.getB();
                controllerShunt.dispatchB();
                LOGGER.trace("Round susceptance of '{}': {} -> {}", controllerShunt.getId(), b, controllerShunt.getB());

                status = OuterLoopStatus.UNSTABLE;
            }
        }
        return status;
    }
}
