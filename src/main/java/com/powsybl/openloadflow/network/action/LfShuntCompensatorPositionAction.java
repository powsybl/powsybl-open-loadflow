package com.powsybl.openloadflow.network.action;

import com.powsybl.action.ShuntCompensatorPositionAction;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfShuntImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LfShuntCompensatorPositionAction extends AbstractLfAction<ShuntCompensatorPositionAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfShuntCompensatorPositionAction.class);

    public LfShuntCompensatorPositionAction(String id, ShuntCompensatorPositionAction action) {
        super(id, action);
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters, GraphConnectivity<LfBus, LfBranch> connectivity) {
        LfShunt shunt = network.getShuntById(action.getShuntCompensatorId());
        if (shunt instanceof LfShuntImpl) { // no svc here
            if (shunt.getVoltageControl().isPresent()) {
                LOGGER.warn("Shunt compensator position action: voltage control is present on the shunt, section could be overridden.");
            }
            shunt.getControllers().stream().filter(controller -> controller.getId().equals(action.getShuntCompensatorId())).findAny()
                .ifPresentOrElse(controller -> controller.updateSectionB(action.getSectionCount()),
                    () -> LOGGER.warn("No section change: shunt {} not present", action.getShuntCompensatorId()));
            return true;
        }
        return false;
    }
}
