/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfDanglingLineGenerator extends AbstractLfGenerator {

    private final DanglingLine danglingLine;

    public LfDanglingLineGenerator(DanglingLine danglingLine, String controlledLfBusId, boolean reactiveLimits, LfNetworkLoadingReport report,
                                   double minPlausibleTargetVoltage, double maxPlausibleTargetVoltage) {
        super(danglingLine.getGeneration().getTargetP());
        this.danglingLine = danglingLine;

        // local control only
        if (danglingLine.getGeneration().isVoltageRegulationOn() && checkVoltageControlConsistency(reactiveLimits, report)) {
            // The controlled bus cannot be reached from the DanglingLine parameters (there is no terminal in DanglingLine.Generation)
            if (checkTargetV(danglingLine.getGeneration().getTargetV() / danglingLine.getTerminal().getVoltageLevel().getNominalV(),
                    report, minPlausibleTargetVoltage, maxPlausibleTargetVoltage)) {
                this.controlledBusId = Objects.requireNonNull(controlledLfBusId);
                this.targetV = danglingLine.getGeneration().getTargetV() / danglingLine.getTerminal().getVoltageLevel().getNominalV();
                this.generatorControlType = GeneratorControlType.VOLTAGE;
            }
        }
    }

    @Override
    public String getId() {
        return danglingLine.getId() + "_GEN";
    }

    @Override
    public String getOriginalId() {
        return danglingLine.getId();
    }

    @Override
    public OptionalDouble getRemoteControlReactiveKey() {
        return OptionalDouble.empty();
    }

    @Override
    public double getTargetQ() {
        return danglingLine.getGeneration().getTargetQ() / PerUnit.SB;
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
