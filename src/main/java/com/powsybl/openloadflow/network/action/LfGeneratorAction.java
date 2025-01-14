/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.GeneratorAction;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public class LfGeneratorAction extends AbstractLfAction<GeneratorAction> {

    private record GeneratorChange(double change, boolean isRelative) { }

    private GeneratorChange generatorChange;

    public LfGeneratorAction(String id, GeneratorAction action, LfNetwork lfNetwork) {
        super(id, action);
        LfGenerator generator = lfNetwork.getGeneratorById(action.getGeneratorId());
        if (generator != null) {
            OptionalDouble activePowerValue = action.getActivePowerValue();
            Optional<Boolean> relativeValue = action.isActivePowerRelativeValue();
            if (relativeValue.isEmpty() || activePowerValue.isEmpty()) {
                throw new UnsupportedOperationException("Generator action on " + action.getGeneratorId() + " : configuration not supported yet.");
            } else {
                generatorChange = new GeneratorChange(activePowerValue.getAsDouble() / PerUnit.SB, relativeValue.get());
            }
        }
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        LfGenerator generator = network.getGeneratorById(action.getGeneratorId());
        if (generator != null && !generator.isDisabled()) {
            double newTargetP = generatorChange.isRelative() ? generator.getTargetP() + generatorChange.change() : generatorChange.change();
            generator.setTargetP(newTargetP);
            generator.setInitialTargetP(newTargetP);
            generator.reApplyActivePowerControlChecks(networkParameters, null);
            return true;
        }
        return false;
    }
}
