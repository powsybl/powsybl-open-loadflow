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
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractPhaseControlOuterLoop<V extends Enum<V> & Quantity,
                                                    E extends Enum<E> & Quantity,
                                                    P extends AbstractLoadFlowParameters,
                                                    C extends LoadFlowContext<V, E, P>,
                                                    O extends OuterLoopContext<V, E, P, C>> implements OuterLoop<V, E, P, C, O> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPhaseControlOuterLoop.class);

    private static final String TYPE = "PhaseControl";

    protected AbstractPhaseControlOuterLoop() {
    }

    @Override
    public String getType() {
        return TYPE;
    }

    protected static List<LfBranch> getControllerBranches(LfNetwork network) {
        return network.getBranches().stream()
                .filter(branch -> !branch.isDisabled() && branch.isPhaseController() && branch.isConnectedAtBothSides())
                .collect(Collectors.toList());
    }

    protected static void fixPhaseShifterNecessaryForConnectivity(LfNetwork network, List<LfBranch> controllerBranches) {
        if (!controllerBranches.isEmpty()) {
            List<LfBranch> disabledBranches = network.getBranches().stream()
                    .filter(LfElement::isDisabled)
                    .collect(Collectors.toList());
            for (LfBranch controllerBranch : controllerBranches) {
                var phaseControl = controllerBranch.getPhaseControl().orElseThrow();
                var controlledBranch = phaseControl.getControlledBranch();
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
