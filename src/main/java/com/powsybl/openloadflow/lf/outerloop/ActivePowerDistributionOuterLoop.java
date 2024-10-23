package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;

public interface ActivePowerDistributionOuterLoop<V extends Enum<V> & Quantity,
            E extends Enum<E> & Quantity,
            P extends AbstractLoadFlowParameters,
            C extends LoadFlowContext<V, E, P>,
            O extends OuterLoopContext<V, E, P, C>>
        extends OuterLoop<V, E, P, C, O> {

    double getDistributedActivePower(O context);

    double getSlackBusActivePowerMismatch(O context);

    OpenLoadFlowParameters.SlackDistributionFailureBehavior getSlackDistributionFailureBehavior(O context);

    OuterLoopResult handleDistributionFailure(O context, DistributedSlackContextData contextData, boolean movedBuses, double totalDistributedActivePower, double remainingMismatch, String errorMessage);

}
