/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.openloadflow.network.AbstractLfGenerator;
import com.powsybl.openloadflow.network.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfGeneratorImpl extends AbstractLfGenerator {

    private final Generator generator;

    private boolean participating;

    private double participationFactor;

    private LfGeneratorImpl(Generator generator) {
        super(generator.getTargetP());
        this.generator = generator;
        participating = true;
        participationFactor = 4; // why not
        // get participation factor from extension
        if (Math.abs(generator.getTargetP()) > 0) {
            ActivePowerControl<Generator> activePowerControl = generator.getExtension(ActivePowerControl.class);
            if (activePowerControl != null) {
                participating = activePowerControl.isParticipate() && activePowerControl.getDroop() != 0;
                if (activePowerControl.getDroop() != 0) {
                    participationFactor = generator.getMaxP() / activePowerControl.getDroop();
                }
            }
        } else {
            participating = false;
        }
    }

    public static LfGeneratorImpl create(Generator generator) {
        Objects.requireNonNull(generator);
        return new LfGeneratorImpl(generator);
    }

    @Override
    public String getId() {
        return generator.getId();
    }

    @Override
    public boolean hasVoltageControl() {
        return generator.isVoltageRegulatorOn();
    }

    @Override
    public double getTargetQ() {
        return generator.getTargetQ() / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        return generator.getMinP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return generator.getMaxP() / PerUnit.SB;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(generator.getReactiveLimits());
    }

    @Override
    public boolean isParticipating() {
        return participating;
    }

    @Override
    public double getParticipationFactor() {
        return participationFactor;
    }

    @Override
    public void updateState() {
        generator.getTerminal()
                .setP(-targetP)
                .setQ(Double.isNaN(calculatedQ) ? -generator.getTargetQ() : -calculatedQ);
    }
}
