package com.powsybl.openloadflow.network.action;

import com.powsybl.action.BoundaryLineAction;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alice Caron {@literal <alice.caron at rte-france.com>}
 * @author Alexandre LE JEAN {@literal <alexandre.le-jean@artelys.com>}
 */
public class LfBoundaryLineAction extends AbstractLfAction<BoundaryLineAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfBoundaryLineAction.class);

    private final String boundaryLineId;
    private final LfBranch lfBranch;
    private final LfLoad lfBoundaryLoad;
    private final PowerShift powerShift;

    public LfBoundaryLineAction(BoundaryLineAction action, Network network, LfNetwork lfNetwork) {
        super(action);
        boundaryLineId = action.getBoundaryLineId();
        lfBranch = lfNetwork.getBranchById(action.getBoundaryLineId());
        lfBoundaryLoad = lfBranch != null ? lfBranch.getBus2().getLoads().stream().findFirst().orElse(null) : null;
        BoundaryLine branch = network.getBoundaryLine(action.getBoundaryLineId());
        powerShift = createPowerShift(branch, action);
    }

    private static PowerShift createPowerShift(BoundaryLine branch, BoundaryLineAction loadAction) {
        double activePowerShift = loadAction.getActivePowerValue().stream().map(a -> loadAction.isRelativeValue() ? a : a - branch.getP0()).findAny().orElse(0);
        double reactivePowerShift = loadAction.getReactivePowerValue().stream().map(r -> loadAction.isRelativeValue() ? r : r - branch.getQ0()).findAny().orElse(0);
        return new PowerShift(activePowerShift / PerUnit.SB,
                0,
                reactivePowerShift / PerUnit.SB);
    }

    @Override
    public boolean isValid() {
        return lfBranch != null && LfBranch.BranchType.BOUNDARY_LINE == lfBranch.getBranchType();
    }

    @Override
    public boolean apply(LfNetwork lfNetwork, LfContingency lfContingency, LfNetworkParameters lfNetworkParameters) {
        if (!isValid()) {
            if(lfBranch == null) {
                LOGGER.warn("Boundary line action {}: branch matching boundary line id {} not found", action.getId(), boundaryLineId);
            }
            else if (LfBranch.BranchType.BOUNDARY_LINE != lfBranch.getBranchType()) {
                LOGGER.warn("Boundary line action {}: branch matching boundary line id {} is not a boundary line", action.getId(), boundaryLineId);
            }
            return false;
        }
        if (lfBoundaryLoad == null) {
            // No load on this boundary line: nothing to do
            return true;
        }
        if (action.getReactivePowerValue().isPresent()) {
            // Case the boundary line has a generation part that regulates in voltage and the action modifies the load reactive power
            LfGenerator boundaryActionGenerator = lfBranch.getBus2().getGenerators().stream().findFirst().orElse(null);
            if (boundaryActionGenerator != null && !Double.isNaN(boundaryActionGenerator.getTargetV())) {
                LOGGER.warn("The boundary line action {} on {} will modify the load reactive power but the boundary line has generation part regulating in voltage.", action.getId(), boundaryLineId);
            }
        }
        lfBoundaryLoad.setTargetP(lfBoundaryLoad.getTargetP() + powerShift.getActive());
        lfBoundaryLoad.setTargetQ(lfBoundaryLoad.getTargetQ() + powerShift.getReactive());
        return true;
    }
}
