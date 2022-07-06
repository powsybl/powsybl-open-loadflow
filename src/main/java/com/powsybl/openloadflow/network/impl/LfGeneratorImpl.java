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
import com.powsybl.iidm.network.extensions.CoordinatedReactiveControl;
import com.powsybl.iidm.network.extensions.RemoteReactivePowerControl;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfGeneratorImpl extends AbstractLfGenerator {

    private final Generator generator;

    private boolean participating;

    private double droop;

    private LfGeneratorImpl(Generator generator, boolean breakers, double plausibleActivePowerLimit, boolean reactiveLimits,
                            LfNetworkLoadingReport report) {
        super(generator.getTargetP());
        this.generator = generator;
        participating = true;
        droop = DEFAULT_DROOP;
        // get participation factor from extension
        ActivePowerControl<Generator> activePowerControl = generator.getExtension(ActivePowerControl.class);
        if (activePowerControl != null) {
            participating = activePowerControl.isParticipate() && activePowerControl.getDroop() != 0;
            if (activePowerControl.getDroop() != 0) {
                droop = activePowerControl.getDroop();
            }
        }

        if (!checkActivePowerControl(generator.getTargetP(), generator.getMinP(), generator.getMaxP(), plausibleActivePowerLimit, report)) {
            participating = false;
        }

        if (generator.isVoltageRegulatorOn()) {
            setVoltageControl(generator.getTargetV(), generator.getTerminal(), generator.getRegulatingTerminal(), breakers, reactiveLimits, report);
        }

        RemoteReactivePowerControl reactivePowerControl = generator.getExtension(RemoteReactivePowerControl.class);
        if (reactivePowerControl != null && reactivePowerControl.isEnabled() && !generator.isVoltageRegulatorOn()) {
            setReactivePowerControl(reactivePowerControl.getRegulatingTerminal(), reactivePowerControl.getTargetQ());
        }
    }

    public static LfGeneratorImpl create(Generator generator, boolean breakers, double plausibleActivePowerLimit,
                                         boolean reactiveLimits, LfNetworkLoadingReport report) {
        Objects.requireNonNull(generator);
        Objects.requireNonNull(report);
        return new LfGeneratorImpl(generator, breakers, plausibleActivePowerLimit, reactiveLimits, report);
    }

    @Override
    public String getId() {
        return generator.getId();
    }

    @Override
    public boolean isFictitious() {
        return generator.isFictitious();
    }

    @Override
    public OptionalDouble getRemoteControlReactiveKey() {
        CoordinatedReactiveControl coordinatedReactiveControl = generator.getExtension(CoordinatedReactiveControl.class);
        if (coordinatedReactiveControl == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(coordinatedReactiveControl.getQPercent());
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
    public void setParticipating(boolean participating) {
        this.participating = participating;
    }

    @Override
    public double getDroop() {
        return droop;
    }

    @Override
    public void updateState() {
        generator.getTerminal()
                .setP(-targetP)
                .setQ(Double.isNaN(calculatedQ) ? -generator.getTargetQ() : -calculatedQ);
    }
}
