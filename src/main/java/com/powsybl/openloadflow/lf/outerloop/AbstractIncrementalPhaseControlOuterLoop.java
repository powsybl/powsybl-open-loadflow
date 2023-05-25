/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractIncrementalPhaseControlOuterLoop<V extends Enum<V> & Quantity,
                                                               E extends Enum<E> & Quantity,
                                                               P extends AbstractLoadFlowParameters,
                                                               C extends LoadFlowContext<V, E, P>,
                                                               O extends OuterLoopContext<V, E, P, C>>
        extends AbstractPhaseControlOuterLoop<V, E, P, C, O> {

    public static final int MAX_DIRECTION_CHANGE = 2;
    public static final int MAX_TAP_SHIFT = Integer.MAX_VALUE;
    public static final double MIN_TARGET_DEADBAND = 1 / PerUnit.SB; // 1 MW
    public static final double SENSI_EPS = 1e-6;
    public static final double PHASE_SHIFT_CROSS_IMPACT_MARGIN = 0.75;

    @Override
    public void initialize(O context) {
        var contextData = new IncrementalContextData();
        context.setData(contextData);

        List<LfBranch> controllerBranches = getControllerBranches(context.getNetwork());
        for (LfBranch controllerBranch : controllerBranches) {
            contextData.getControllersContexts().put(controllerBranch.getId(), new IncrementalContextData.ControllerContext(MAX_DIRECTION_CHANGE));
        }

        fixPhaseShifterNecessaryForConnectivity(context.getNetwork(), controllerBranches);
    }

    public static double getHalfTargetDeadband(TransformerPhaseControl phaseControl) {
        return Math.max(phaseControl.getTargetDeadband(), MIN_TARGET_DEADBAND) / 2;
    }

}
