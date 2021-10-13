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
import com.powsybl.openloadflow.network.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfGeneratorImpl extends AbstractLfGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfGeneratorImpl.class);

    private static final double DEFAULT_DROOP = 4; // why not

    private static final double TARGET_P_EPSILON = 1e-2;

    private final Generator generator;

    private boolean participating;

    private double droop;

    private LfGeneratorImpl(Generator generator, boolean breakers, LfNetworkLoadingReport report, double plausibleActivePowerLimit) {
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
        if (Math.abs(generator.getTargetP()) < TARGET_P_EPSILON) {
            LOGGER.trace("Discard generator '{}' from active power control because targetP ({}) equals 0",
                    generator.getId(), generator.getTargetP());
            report.generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero++;
            participating = false;
        }
        if (generator.getTargetP() > generator.getMaxP()) {
            LOGGER.trace("Discard generator '{}' from active power control because targetP ({}) > maxP ({})",
                    generator.getId(), generator.getTargetP(), generator.getMaxP());
            report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP++;
            participating = false;
        }
        if (generator.getMaxP() > plausibleActivePowerLimit) {
            LOGGER.trace("Discard generator '{}' from active power control because maxP ({}) > {}} MW",
                    generator.getId(), generator.getMaxP(), plausibleActivePowerLimit);
            report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible++;
            participating = false;
        }
        if ((generator.getMaxP() - generator.getMinP()) < TARGET_P_EPSILON) {
            LOGGER.trace("Discard generator '{}' from active power control because maxP ({} MW) equals minP ({} MW)",
                generator.getId(), generator.getMaxP(), generator.getMinP());
            report.generatorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP++;
            participating = false;
        }

        if (generator.isVoltageRegulatorOn()) {
            setVoltageControl(generator.getTargetV(), generator.getTerminal(), generator.getRegulatingTerminal(), breakers, report);
        }

        RemoteReactivePowerControl reactivePowerControl = generator.getExtension(RemoteReactivePowerControl.class);
        if (reactivePowerControl != null && reactivePowerControl.isEnabled() && !generator.isVoltageRegulatorOn()) {
            setReactivePowerControl(reactivePowerControl.getRegulatingTerminal(), reactivePowerControl.getTargetQ());
        }
    }

    public static LfGeneratorImpl create(Generator generator, boolean breakers, LfNetworkLoadingReport report, double plausibleActivePowerLimit) {
        Objects.requireNonNull(generator);
        Objects.requireNonNull(report);
        return new LfGeneratorImpl(generator, breakers, report, plausibleActivePowerLimit);
    }

    @Override
    public String getId() {
        return generator.getId();
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
