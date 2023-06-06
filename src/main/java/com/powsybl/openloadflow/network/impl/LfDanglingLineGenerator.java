/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfDanglingLineGenerator extends AbstractLfGenerator {

    private final Ref<DanglingLine> danglingLineRef;

    private LfDanglingLineGenerator(DanglingLine danglingLine, LfNetwork network, String controlledLfBusId, LfNetworkParameters parameters,
                                    LfNetworkLoadingReport report) {
        super(network, danglingLine.getGeneration().getTargetP() / PerUnit.SB);
        this.danglingLineRef = Ref.create(danglingLine, parameters.isCacheEnabled());

        // local control only
        if (danglingLine.getGeneration().isVoltageRegulationOn() && checkVoltageControlConsistency(parameters, report)) {
            // The controlled bus cannot be reached from the DanglingLine parameters (there is no terminal in DanglingLine.Generation)
            if (checkTargetV(danglingLine.getGeneration().getTargetV() / danglingLine.getTerminal().getVoltageLevel().getNominalV(),
                    parameters, report)) {
                this.controlledBusId = Objects.requireNonNull(controlledLfBusId);
                this.targetV = danglingLine.getGeneration().getTargetV() / danglingLine.getTerminal().getVoltageLevel().getNominalV();
                this.generatorControlType = GeneratorControlType.VOLTAGE;
            }
        }
    }

    public static LfDanglingLineGenerator create(DanglingLine danglingLine, LfNetwork network, String controlledLfBusId, LfNetworkParameters parameters,
                                                 LfNetworkLoadingReport report) {
        Objects.requireNonNull(danglingLine);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(report);
        return new LfDanglingLineGenerator(danglingLine, network, controlledLfBusId, parameters, report);
    }

    private DanglingLine getDanglingLine() {
        return danglingLineRef.get();
    }

    @Override
    public String getId() {
        return getDanglingLine().getId() + "_GEN";
    }

    @Override
    public String getOriginalId() {
        return getDanglingLine().getId();
    }

    @Override
    public OptionalDouble getRemoteControlReactiveKey() {
        return OptionalDouble.empty();
    }

    @Override
    public double getTargetQ() {
        return getDanglingLine().getGeneration().getTargetQ() / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        return getDanglingLine().getGeneration().getMinP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return getDanglingLine().getGeneration().getMaxP() / PerUnit.SB;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.ofNullable(getDanglingLine().getGeneration().getReactiveLimits());
    }

    @Override
    public void updateState() {
        // nothing to update
    }
}
