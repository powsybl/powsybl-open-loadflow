package com.powsybl.openloadflow.network.action;

import com.powsybl.action.BoundaryLineAction;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alice Caron {@literal <alice.caron at rte-france.com>}
 */
public class LfBoundaryLineAction extends AbstractLfAction<BoundaryLineAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfBoundaryLineAction.class);

    private final String boundaryLineId;
    private final LfBranch lfBranch;

    public LfBoundaryLineAction(BoundaryLineAction action, LfNetwork lfNetwork) {
        super(action);
        boundaryLineId = action.getBoundaryLineId();
        lfBranch = lfNetwork.getBranchById(action.getBoundaryLineId());
    }

    @Override
    public boolean isValid() {
        return lfBranch != null;
    }

    @Override
    public boolean apply(LfNetwork lfNetwork, LfContingency lfContingency, LfNetworkParameters lfNetworkParameters) {
        if (isValid()) {
            if (lfBranch.getBranchType().equals(LfBranch.BranchType.BOUNDARY_LINE)) {
                LfLoad boundaryActionLoad = lfBranch.getBus2().getLoads().stream().findFirst().orElse(null);

                // Case the dangling line has a generation part that regulates in voltage and the action modifies the load reactive power
                LfGenerator boundaryActionGenerator = lfBranch.getBus2().getGenerators().stream().findFirst().orElse(null);
                if (null != boundaryActionGenerator && !Double.isNaN(boundaryActionGenerator.getTargetV()) && action.getReactivePowerValue().isPresent()) {
                    LOGGER.warn("The dangling line action on {} will modify the load reactive power but the dangling line has generation part regulating in voltage.", action.getBoundaryLineId());
                }

                if (null != boundaryActionLoad) {
                    if (action.getActivePowerValue().isPresent()) {
                        double activePowerValue = action.isRelativeValue() ? action.getActivePowerValue().getAsDouble() / PerUnit.SB
                                + boundaryActionLoad.getTargetP() : action.getActivePowerValue().getAsDouble() / PerUnit.SB;
                        boundaryActionLoad.setTargetP(activePowerValue);
                    }
                    if (action.getReactivePowerValue().isPresent()) {
                        double reactivePowerValue = action.isRelativeValue() ? action.getReactivePowerValue().getAsDouble() / PerUnit.SB
                                + boundaryActionLoad.getTargetQ() : action.getReactivePowerValue().getAsDouble() / PerUnit.SB;
                        boundaryActionLoad.setTargetQ(reactivePowerValue);
                    }
                }
                return true;
            }
            LOGGER.warn("Dangling line action {}: branch matching dangling line id {} is not a dangling line", action.getId(), action.getBoundaryLineId());
            return false;
        }
        LOGGER.warn("Dangling line action {}: branch matching dangling line id {} not found", action.getId(), action.getBoundaryLineId());
        return false;
    }
}
