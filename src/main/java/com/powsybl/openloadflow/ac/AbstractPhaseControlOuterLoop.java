
/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractPhaseControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPhaseControlOuterLoop.class);

    protected static List<LfBranch> getControllerBranches(LfNetwork network) {
        return network.getBranches().stream()
                .filter(branch -> !branch.isDisabled() && branch.isPhaseController())
                .collect(Collectors.toList());
    }

    protected static void fixPhaseShifterNecessaryForConnectivity(LfNetwork network) {
        List<LfBranch> controllerBranches = getControllerBranches(network);
        if (!controllerBranches.isEmpty()) {
            List<LfBranch> disabledBranches = network.getBranches().stream()
                    .filter(LfElement::isDisabled)
                    .collect(Collectors.toList());
            for (LfBranch controllerBranch : controllerBranches) {
                var phaseControl = controllerBranch.getDiscretePhaseControl().orElseThrow();
                var controlledBranch = phaseControl.getControlled();
                var connectivity = network.getConnectivity();
                connectivity.startTemporaryChanges();

                // apply contingency (in case we are inside a security analysis)
                disabledBranches.stream()
                        .filter(b -> b.getBus1() != null && b.getBus2() != null)
                        .forEach(connectivity::removeEdge);
                int componentsCountBeforePhaseShifterLoss = connectivity.getNbConnectedComponents();

                // then the phase shifter controlled branch
                if (controlledBranch.getBus1() != null && controlledBranch.getBus2() != null) {
                    connectivity.removeEdge(controlledBranch);
                }

                if (connectivity.getNbConnectedComponents() != componentsCountBeforePhaseShifterLoss) {
                    // phase shifter controlled branch necessary for connectivity, we switch off control
                    LOGGER.warn("Phase shifter '{}' control branch '{}' phase but is necessary for connectivity: switch off phase control",
                            controllerBranch.getId(), controlledBranch.getId());
                    controllerBranch.setPhaseControlEnabled(false);
                }

                connectivity.undoTemporaryChanges();
            }
        }
    }
}
