/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.GeneratorAction;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
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
