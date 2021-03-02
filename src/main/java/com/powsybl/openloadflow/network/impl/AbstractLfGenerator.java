/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.MinMaxReactiveLimits;
import com.powsybl.iidm.network.ReactiveCapabilityCurve;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfGenerator implements LfGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLfGenerator.class);

    protected double targetP;

    protected LfBus bus;

    protected double calculatedQ = Double.NaN;

    private double targetV = Double.NaN;

    protected AbstractLfGenerator(double targetP) {
        this.targetP = targetP;
    }

    public LfBus getBus() {
        return bus;
    }

    public void setBus(LfBus bus) {
        this.bus = bus;
    }

    @Override
    public double getTargetP() {
        return targetP / PerUnit.SB;
    }

    @Override
    public void setTargetP(double targetP) {
        this.targetP = targetP * PerUnit.SB;
    }

    @Override
    public double getTargetV() {
        return targetV;
    }

    protected void setTargetV(double targetV) {
        double newTargetV = targetV;
        // check that targetV has a plausible value (wrong nominal voltage issue)
        if (targetV < PlausibleValues.MIN_TARGET_VOLTAGE_PU || targetV > PlausibleValues.MAX_TARGET_VOLTAGE_PU) {
            LOGGER.warn("Generator " + getId() + "' has an inconsistent target voltage: " + targetV + " pu");
            newTargetV = (targetV > PlausibleValues.MAX_TARGET_VOLTAGE_PU) ? PlausibleValues.MAX_TARGET_VOLTAGE_PU : PlausibleValues.MAX_TARGET_VOLTAGE_PU;
        }
        this.targetV = newTargetV;
    }

    @Override
    public OptionalDouble getRemoteControlReactiveKey() {
        return OptionalDouble.empty();
    }

    protected abstract Optional<ReactiveLimits> getReactiveLimits();

    @Override
    public double getMinQ() {
        return getReactiveLimits()
                .map(limits -> limits.getMinQ(targetP) / PerUnit.SB)
                .orElse(-Double.MAX_VALUE);
    }

    @Override
    public double getMaxQ() {
        return getReactiveLimits()
                .map(limits -> limits.getMaxQ(targetP) / PerUnit.SB)
                .orElse(Double.MAX_VALUE);
    }

    @Override
    public double getMaxRangeQ() {
        double maxRangeQ = Double.NaN;
        ReactiveLimits reactiveLimits = getReactiveLimits().orElse(null);
        if (reactiveLimits != null) {
            switch (reactiveLimits.getKind()) {
                case CURVE:
                    ReactiveCapabilityCurve reactiveCapabilityCurve = (ReactiveCapabilityCurve) reactiveLimits;
                    for (ReactiveCapabilityCurve.Point point : reactiveCapabilityCurve.getPoints()) {
                        if (Double.isNaN(maxRangeQ)) {
                            maxRangeQ = point.getMaxQ() - point.getMinQ();
                        } else {
                            maxRangeQ = Math.max(maxRangeQ, point.getMaxQ() - point.getMinQ());
                        }
                    }
                    break;

                case MIN_MAX:
                    MinMaxReactiveLimits minMaxReactiveLimits = (MinMaxReactiveLimits) reactiveLimits;
                    maxRangeQ = minMaxReactiveLimits.getMaxQ() - minMaxReactiveLimits.getMinQ();
                    break;

                default:
                    throw new IllegalStateException("Unknown reactive limits kind: " + reactiveLimits.getKind());
            }
            return maxRangeQ / PerUnit.SB;
        } else {
            return Double.MAX_VALUE;
        }
    }

    @Override
    public double getCalculatedQ() {
        return calculatedQ / PerUnit.SB;
    }

    @Override
    public void setCalculatedQ(double calculatedQ) {
        this.calculatedQ = calculatedQ * PerUnit.SB;
    }

}
