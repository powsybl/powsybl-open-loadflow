package com.powsybl.openloadflow.network.action;

import com.powsybl.action.GeneratorAction;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Optional;
import java.util.OptionalDouble;

public class LfGeneratorAction extends AbstractLfAction<GeneratorAction> {

    public LfGeneratorAction(String id, GeneratorAction action) {
        super(id, action);
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters, GraphConnectivity<LfBus, LfBranch> connectivity) {
        LfGenerator generator = network.getGeneratorById(action.getGeneratorId());
        if (generator != null) {
            OptionalDouble activePowerValue = action.getActivePowerValue();
            Optional<Boolean> relativeValue = action.isActivePowerRelativeValue();
            if (relativeValue.isPresent() && activePowerValue.isPresent()) {
                if (!generator.isDisabled()) {
                    double change = activePowerValue.getAsDouble() / PerUnit.SB;
                    double newTargetP = Boolean.TRUE.equals(relativeValue.get()) ? generator.getTargetP() + change : change;
                    generator.setTargetP(newTargetP);
                    generator.setInitialTargetP(newTargetP);
                    generator.reApplyActivePowerControlChecks(networkParameters, null);
                    return true;
                }
            } else {
                throw new UnsupportedOperationException("Generator action on " + action.getGeneratorId() + " : configuration not supported yet.");
            }
        }
        return false;
    }
}
