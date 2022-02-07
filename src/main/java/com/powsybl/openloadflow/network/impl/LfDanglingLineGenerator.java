/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.openloadflow.network.PerUnit;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineGenerator extends AbstractLfGenerator {

    private final DanglingLine danglingLine;

    public LfDanglingLineGenerator(DanglingLine danglingLine, String controlledLfBusId, boolean reactiveLimits, LfNetworkLoadingReport report) {
        super(danglingLine.getGeneration().getTargetP());
        this.danglingLine = danglingLine;

        // local control only
        if (danglingLine.getGeneration().isVoltageRegulationOn() && checkVoltageControlConsistency(reactiveLimits, report)) {
            // The controlled bus cannot be reached from the DanglingLine parameters (there is no terminal in DanglingLine.Generation)
            this.controlledBusId = Objects.requireNonNull(controlledLfBusId);
            setTargetV(danglingLine.getGeneration().getTargetV() / danglingLine.getTerminal().getVoltageLevel().getNominalV(), report);
            this.generatorControlType = GeneratorControlType.VOLTAGE;
        }

        targetQ = danglingLine.getGeneration().getTargetQ() / PerUnit.SB;
    }

    @Override
    public String getId() {
        return danglingLine.getId() + "_GEN";
    }

    @Override
    public OptionalDouble getRemoteControlReactiveKey() {
        return OptionalDouble.empty();
    }

    @Override
    public double getTargetQ() {
        return targetQ;
    }

    @Override
    public double getMinP() {
        return danglingLine.getGeneration().getMinP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return danglingLine.getGeneration().getMaxP() / PerUnit.SB;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.ofNullable(danglingLine.getGeneration().getReactiveLimits());
    }

    @Override
    public void updateState() {
        // nothing to update
    }
}
