/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class LfBoundaryLineGenerator extends AbstractLfGenerator {

    private final Ref<BoundaryLine> boundaryLineRef;

    private LfBoundaryLineGenerator(BoundaryLine boundaryLine, LfNetwork network, String controlledLfBusId, LfNetworkParameters parameters,
                                    LfNetworkLoadingReport report) {
        super(network, boundaryLine.getGeneration().getTargetP() / PerUnit.SB, parameters);
        this.boundaryLineRef = Ref.create(boundaryLine, parameters.isCacheEnabled());

        // local control only
        if (boundaryLine.getGeneration().isVoltageRegulationOn() && checkVoltageControlConsistency(parameters, report)) {
            // The controlled bus cannot be reached from the BoundaryLine parameters (there is no terminal in BoundaryLine.Generation)
            if (checkTargetV(getId(), boundaryLine.getGeneration().getTargetV() / boundaryLine.getTerminal().getVoltageLevel().getNominalV(),
                    boundaryLine.getTerminal().getVoltageLevel().getNominalV(), parameters, report)) {
                this.controlledBusId = Objects.requireNonNull(controlledLfBusId);
                this.targetV = boundaryLine.getGeneration().getTargetV() / boundaryLine.getTerminal().getVoltageLevel().getNominalV();
                this.generatorControlType = GeneratorControlType.VOLTAGE;
            }
        }
    }

    public static LfBoundaryLineGenerator create(BoundaryLine boundaryLine, LfNetwork network, String controlledLfBusId, LfNetworkParameters parameters,
                                                 LfNetworkLoadingReport report) {
        Objects.requireNonNull(boundaryLine);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(report);
        return new LfBoundaryLineGenerator(boundaryLine, network, controlledLfBusId, parameters, report);
    }

    private BoundaryLine getBoundaryLine() {
        return boundaryLineRef.get();
    }

    @Override
    public String getId() {
        return getBoundaryLine().getId() + "_GEN";
    }

    @Override
    public String getOriginalId() {
        return getBoundaryLine().getId();
    }

    @Override
    public OptionalDouble getRemoteControlReactiveKey() {
        return OptionalDouble.empty();
    }

    @Override
    public double getTargetQ() {
        return Networks.zeroIfNan(getBoundaryLine().getGeneration().getTargetQ()) / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        return getBoundaryLine().getGeneration().getMinP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return getBoundaryLine().getGeneration().getMaxP() / PerUnit.SB;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.ofNullable(getBoundaryLine().getGeneration().getReactiveLimits());
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        // nothing to update
    }
}
