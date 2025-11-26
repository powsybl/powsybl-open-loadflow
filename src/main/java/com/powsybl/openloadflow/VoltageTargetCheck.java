/*
 * Copyright (c) 2024-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class VoltageTargetCheck {

    private VoltageTargetCheck() {
    }

    public record LfIncompatibleTarget(LfBus controlledBus1, LfBus controlledBus2, double targetVoltagePlausibilityIndicator) {
        public IncompatibleTarget toIidm() {
            return new IncompatibleTarget(controlledBus1.getId(),
                    controlledBus2.getId(),
                    controlledBus1.getHighestPriorityTargetV().orElseThrow() * controlledBus1.getNominalV(),
                    controlledBus2.getHighestPriorityTargetV().orElseThrow() * controlledBus2.getNominalV(),
                    targetVoltagePlausibilityIndicator);
        }
    }

    public record IncompatibleTarget(String controlledBus1Id, String controlledBus2Id, double controlledBus1IdTargetV, double controlledBus2IdTargetV, double targetVoltagePlausibilityIndicator) {
    }

    public record LfIncompatibleTargetResolution(LfBus controlledBusToFix, Set<? extends LfElement> elementsToDisable, LfIncompatibleTarget largestLfIncompatibleTarget) {
        public IncompatibleTargetResolution toIidm() {
            Set<String> elementsToDisableIds = new TreeSet<>();
            controlledBusToFix().getGeneratorVoltageControl()
                    .ifPresent(vc -> elementsToDisableIds.addAll(vc.getControllerElements().stream()
                            .flatMap(c -> c.getGenerators().stream())
                            .map(LfGenerator::getOriginalId)
                            .toList()));
            controlledBusToFix().getShuntVoltageControl()
                    .ifPresent(vc -> elementsToDisableIds.addAll(vc.getControllerElements().stream()
                            .flatMap(s -> s.getOriginalIds().stream())
                            .toList()));
            controlledBusToFix().getTransformerVoltageControl()
                    .ifPresent(vc -> elementsToDisableIds.addAll(vc.getControllerElements().stream()
                            .flatMap(t -> t.getOriginalIds().stream())
                            .toList()));
            return new IncompatibleTargetResolution(controlledBusToFix.getId(), elementsToDisableIds, largestLfIncompatibleTarget.toIidm());
        }
    }

    /**
     * @param controlledBusToFixId  id of the busview bus that has an incompatible control
     * @param elementsToDisableIds  ids of elements controlling controlledBusToFixId. To fix the problem, their target should be adjusted. Elements can be generators, shunts or two winding transformers
     * @param largestIncompatibleTarget the strongest incompatible target in wich the controlledBusToFixId is involved
     */
    public record IncompatibleTargetResolution(String controlledBusToFixId, Set<String> elementsToDisableIds, IncompatibleTarget largestIncompatibleTarget) {
    }


    record LfResult(List<LfIncompatibleTarget> lfIncompatibleTarget) {
        public LfResult() {
            this(new ArrayList<>());
        }
    }

    public record Result(List<IncompatibleTargetResolution> incompatibleTargetResolutions) {
    }
}
