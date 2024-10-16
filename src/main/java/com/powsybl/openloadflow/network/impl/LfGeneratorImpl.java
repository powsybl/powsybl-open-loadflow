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

    private final boolean initialParticipating;

    private boolean participating;

    private final double droop;

    private final double participationFactor;

    private Double qPercent;

    private final boolean forceVoltageControl;

    private final double maxTargetP;

    private final double minTargetP;

    private LfGeneratorImpl(Generator generator, LfNetwork network, LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        super(network, generator.getTargetP() / PerUnit.SB);
        this.generatorRef = Ref.create(generator, parameters.isCacheEnabled());
        // we force voltage control of generators tagged as condensers or tagged as fictitious if the dedicated mode is activated.
        forceVoltageControl = generator.isCondenser() || generator.isFictitious() && parameters.getFictitiousGeneratorVoltageControlCheckMode() == OpenLoadFlowParameters.FictitiousGeneratorVoltageControlCheckMode.FORCED;
        var apcHelper = ActivePowerControlHelper.create(generator, generator.getMinP(), generator.getMaxP());
        initialParticipating = apcHelper.participating();
        participating = initialParticipating;
        participationFactor = apcHelper.participationFactor();
        droop = apcHelper.droop();
        minTargetP = apcHelper.minTargetP();
        maxTargetP = apcHelper.maxTargetP();

        if (generator.getId().equals("PENLY7PENLYT1")) {
            System.out.println("dddd");
        }
        setReferencePriority(ReferencePriority.get(generator));

        if (!checkActivePowerControl(generator.getId(), generator.getTargetP(), generator.getMaxP(), minTargetP, maxTargetP,
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

    @Override
    public void reApplyActivePowerControlChecks(LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        participating = initialParticipating;
        var generator = getGenerator();
        if (!checkActivePowerControl(generator.getId(), targetP * PerUnit.SB, generator.getMaxP(), minTargetP, maxTargetP,
                parameters.getPlausibleActivePowerLimit(), parameters.isUseActiveLimits(), report)) {
            participating = false;
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
    public double getMinTargetP() {
        return minTargetP / PerUnit.SB;
    }

    @Override
    public double getMaxTargetP() {
        return maxTargetP / PerUnit.SB;
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
        return forceVoltageControl || super.checkIfGeneratorStartedForVoltageControl(report);
    }

    @Override
    protected boolean checkIfGeneratorIsInsideActivePowerLimitsForVoltageControl(LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        return forceVoltageControl || super.checkIfGeneratorIsInsideActivePowerLimitsForVoltageControl(parameters, report);
    }
}
