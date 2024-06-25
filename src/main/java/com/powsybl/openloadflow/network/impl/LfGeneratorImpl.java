/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.LfAsymGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class LfGeneratorImpl extends AbstractLfGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfGeneratorImpl.class);

    private final Ref<Generator> generatorRef;

    private boolean participating;

    private double droop;

    private double participationFactor;

    private Double qPercent;

    private final boolean voltageControlAlways;

    private LfGeneratorImpl(Generator generator, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        super(network, generator.getTargetP() / PerUnit.SB);
        this.generatorRef = Ref.create(generator, parameters.isCacheEnabled());
        // voltageControlAlways for condensers, or for fictitious generators if FictitiousGeneratorVoltageControlMode set to ALAWYS
        voltageControlAlways = generator.isCondenser() ? true   // USing ? : syntax because style checker forbids parenthesis in a pure boolean equation condenser || (fictif and param)
                :
                generator.isFictitious() && parameters.getFictitiousGeneratorVoltageControlMode() == OpenLoadFlowParameters.FictitiousGeneratorVoltageControlMode.ALWAYS;
        participating = true;
        droop = DEFAULT_DROOP;

        setReferencePriority(ReferencePriority.get(generator));

        // get participation factor and droop from extension
        ActivePowerControl<Generator> activePowerControl = generator.getExtension(ActivePowerControl.class);
        if (activePowerControl != null) {
            participating = activePowerControl.isParticipate();
            if (!Double.isNaN(activePowerControl.getDroop())) {
                droop = activePowerControl.getDroop();
            }
            if (activePowerControl.getParticipationFactor() > 0) {
                participationFactor = activePowerControl.getParticipationFactor();
            }
        }

        if (!checkActivePowerControl(generator.getId(), generator.getTargetP(), generator.getMinP(), generator.getMaxP(),
                parameters.getPlausibleActivePowerLimit(), parameters.isUseActiveLimits(), report)) {
            participating = false;
        }

        if (generator.isVoltageRegulatorOn()) {
            setVoltageControl(generator.getTargetV(), generator.getTerminal(), generator.getRegulatingTerminal(), parameters,
                    report);
        }

        RemoteReactivePowerControl reactivePowerControl = generator.getExtension(RemoteReactivePowerControl.class);
        if (reactivePowerControl != null && reactivePowerControl.isEnabled() && !generator.isVoltageRegulatorOn() && parameters.isGeneratorReactivePowerRemoteControl()) {
            setRemoteReactivePowerControl(reactivePowerControl.getRegulatingTerminal(), reactivePowerControl.getTargetQ());
        }

        CoordinatedReactiveControl coordinatedReactiveControl = getGenerator().getExtension(CoordinatedReactiveControl.class);
        if (coordinatedReactiveControl != null) {
            if (coordinatedReactiveControl.getQPercent() == 0) {
                LOGGER.trace("Generator '{}' remote voltage control reactive power key value is zero", generator.getId());
                report.generatorsWithZeroRemoteVoltageControlReactivePowerKey++;
            } else {
                qPercent = coordinatedReactiveControl.getQPercent();
            }
        }
    }

    private static void createAsym(Generator generator, LfGeneratorImpl lfGenerator) {
        var extension = generator.getExtension(GeneratorFortescue.class);
        if (extension != null) {
            double vNom = generator.getTerminal().getVoltageLevel().getNominalV();
            double zb = vNom * vNom / PerUnit.SB;
            double r0 = extension.getRz() / zb;
            double x0 = extension.getXz() / zb;
            double r2 = extension.getRn() / zb;
            double x2 = extension.getXn() / zb;
            double z0Square = r0 * r0 + x0 * x0;
            double z2Square = r2 * r2 + x2 * x2;
            double epsilon = 0.0000000001;
            double bZero;
            double gZero;
            double bNegative;
            double gNegative;
            if (z0Square > epsilon) {
                bZero = -x0 / z0Square;
                gZero = r0 / z0Square;
            } else {
                throw new PowsyblException("Generator '" + generator.getId() + "' has fortescue zero sequence values that will bring singularity in the equation system");
            }
            if (z2Square > epsilon) {
                bNegative = -x2 / z2Square;
                gNegative = r2 / z2Square;
            } else {
                throw new PowsyblException("Generator '" + generator.getId() + "' has fortescue negative sequence values that will bring singularity in the equation system");
            }
            lfGenerator.setAsym(new LfAsymGenerator(gZero, bZero, gNegative, bNegative));
        }
    }

    public static LfGeneratorImpl create(Generator generator, LfNetwork network, LfNetworkParameters parameters,
                                         LfNetworkLoadingReport report) {
        Objects.requireNonNull(generator);
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(report);
        LfGeneratorImpl lfGenerator = new LfGeneratorImpl(generator, network, parameters, report);
        if (parameters.isAsymmetrical()) {
            createAsym(generator, lfGenerator);
        }
        return lfGenerator;
    }

    private Generator getGenerator() {
        return generatorRef.get();
    }

    @Override
    public String getId() {
        return getGenerator().getId();
    }

    @Override
    public boolean isFictitious() {
        return getGenerator().isFictitious();
    }

    @Override
    public OptionalDouble getRemoteControlReactiveKey() {
        return qPercent != null ? OptionalDouble.of(qPercent) : OptionalDouble.empty();
    }

    @Override
    public double getTargetQ() {
        return Networks.zeroIfNan(getGenerator().getTargetQ()) / PerUnit.SB;
    }

    @Override
    public double getMinP() {
        return getGenerator().getMinP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return getGenerator().getMaxP() / PerUnit.SB;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(getGenerator().getReactiveLimits());
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
    public double getParticipationFactor() {
        return participationFactor;
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var generator = getGenerator();
        generator.getTerminal()
                .setP(-targetP * PerUnit.SB)
                .setQ(Double.isNaN(calculatedQ) ? -getTargetQ() * PerUnit.SB : -calculatedQ * PerUnit.SB);
        if (parameters.isWriteReferenceTerminals() && isReference()) {
            ReferenceTerminals.addTerminal(generator.getTerminal());
        }
    }

    @Override
    protected boolean checkIfGeneratorStartedForVoltageControl(LfNetworkLoadingReport report) {
        return voltageControlAlways ?
                true :
                super.checkIfGeneratorStartedForVoltageControl(report);
    }

    @Override
    protected boolean checkIfGeneratorIsInsideActivePowerLimitsForVoltageControl(LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        return voltageControlAlways ?
                true :
                super.checkIfGeneratorIsInsideActivePowerLimitsForVoltageControl(parameters, report);
    }
}
