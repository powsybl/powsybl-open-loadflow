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
        if (!isValid()) {
            LOGGER.warn("Boundary line action {}: branch matching boundary line id {} not found", action.getId(), boundaryLineId);
            return false;
        }
        if (!lfBranch.getBranchType().equals(LfBranch.BranchType.BOUNDARY_LINE)) {
            LOGGER.warn("Boundary line action {}: branch matching boundary line id {} is not a boundary line", action.getId(), boundaryLineId);
            return false;
        }

        LfLoad boundaryActionLoad = lfBranch.getBus2().getLoads().stream().findFirst().orElse(null);
        if (boundaryActionLoad == null) {
            // No load on this boundary line: nothing to do
            return true;
        }
        if (action.getActivePowerValue().isPresent()) {
            double activePowerValue = action.getActivePowerValue().getAsDouble() / PerUnit.SB;
            double targetValue = action.isRelativeValue() ? activePowerValue + boundaryActionLoad.getTargetP() : activePowerValue;
            boundaryActionLoad.setTargetP(targetValue);
        }
        if (action.getReactivePowerValue().isPresent()) {
            // Case the boundary line has a generation part that regulates in voltage and the action modifies the load reactive power
            LfGenerator boundaryActionGenerator = lfBranch.getBus2().getGenerators().stream().findFirst().orElse(null);
            if (boundaryActionGenerator != null && !Double.isNaN(boundaryActionGenerator.getTargetV())) {
                LOGGER.warn("The boundary line action {} on {} will modify the load reactive power but the boundary line has generation part regulating in voltage.", action.getId(), boundaryLineId);
            }

            double reactivePowerValue = action.getReactivePowerValue().getAsDouble() / PerUnit.SB;
            double targetValue = action.isRelativeValue() ? reactivePowerValue + boundaryActionLoad.getTargetQ() : reactivePowerValue;
            boundaryActionLoad.setTargetQ(targetValue);
        }
        return true;
    }
}
