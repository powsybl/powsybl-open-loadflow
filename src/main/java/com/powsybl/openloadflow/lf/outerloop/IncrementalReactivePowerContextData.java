/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
// TODO : add mother class for IncrementalContextData and this class
public class IncrementalReactivePowerContextData {

    public static final class ControllerContext {

        private final int maxDirectionChange;

        public ControllerContext(int maxDirectionChange) {
            this.maxDirectionChange = maxDirectionChange;
        }

        private final MutableInt directionChangeCount = new MutableInt();

        private AllowedDirection allowedDirection = AllowedDirection.BOTH;

        public AllowedDirection getAllowedDirection() {
            return allowedDirection;
        }

        public void updateAllowedDirection(Direction direction) {
            if (directionChangeCount.getValue() <= maxDirectionChange) {
                if (allowedDirection != direction.getAllowedDirection()) {
                    // both vs increase or decrease
                    // increase vs decrease
                    // decrease vs increase
                    directionChangeCount.increment();
                }
                allowedDirection = direction.getAllowedDirection();
            }
        }
    }

    private final Map<String, ControllerContext> controllersContexts = new HashMap<>();

    private final List<LfBranch> candidateControlledBranches;

    public Map<String, ControllerContext> getControllersContexts() {
        return controllersContexts;
    }

    public List<LfBranch> getCandidateControlledBranches() {
        return candidateControlledBranches;
    }

    public IncrementalReactivePowerContextData(LfNetwork network) {
        candidateControlledBranches = network.getBranches().stream()
                .filter(LfBranch::isTransformerReactivePowerControlled)
                .collect(Collectors.toList());

    }

    public IncrementalReactivePowerContextData() {
        candidateControlledBranches = Collections.emptyList();
    }
}
