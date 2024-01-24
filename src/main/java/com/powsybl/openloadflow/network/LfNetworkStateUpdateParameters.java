/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfNetworkStateUpdateParameters {

    private final boolean reactiveLimits;

    private final boolean writeSlackBus;

    private final boolean phaseShifterRegulationOn;

    private final boolean transformerVoltageControlOn;

    private final boolean transformerReactivePowerControlOn;

    private final boolean loadPowerFactorConstant;

    private final boolean dc;

    private final boolean breakers;

    private final ReactivePowerDispatchMode reactivePowerDispatchMode;

    private final boolean writeReferenceTerminals;

    public LfNetworkStateUpdateParameters(boolean reactiveLimits, boolean writeSlackBus, boolean phaseShifterRegulationOn,
                                          boolean transformerVoltageControlOn, boolean transformerReactivePowerControlOn, boolean loadPowerFactorConstant, boolean dc,
                                          boolean breakers, ReactivePowerDispatchMode reactivePowerDispatchMode,
                                          boolean writeReferenceTerminals) {
        this.reactiveLimits = reactiveLimits;
        this.writeSlackBus = writeSlackBus;
        this.phaseShifterRegulationOn = phaseShifterRegulationOn;
        this.transformerVoltageControlOn = transformerVoltageControlOn;
        this.transformerReactivePowerControlOn = transformerReactivePowerControlOn;
        this.loadPowerFactorConstant = loadPowerFactorConstant;
        this.dc = dc;
        this.breakers = breakers;
        this.reactivePowerDispatchMode = Objects.requireNonNull(reactivePowerDispatchMode);
        this.writeReferenceTerminals = writeReferenceTerminals;
    }

    public boolean isReactiveLimits() {
        return reactiveLimits;
    }

    public boolean isWriteSlackBus() {
        return writeSlackBus;
    }

    public boolean isPhaseShifterRegulationOn() {
        return phaseShifterRegulationOn;
    }

    public boolean isTransformerVoltageControlOn() {
        return transformerVoltageControlOn;
    }

    public boolean isTransformerReactivePowerControlOn() {
        return transformerReactivePowerControlOn;
    }

    public boolean isLoadPowerFactorConstant() {
        return loadPowerFactorConstant;
    }

    public boolean isDc() {
        return dc;
    }

    public boolean isBreakers() {
        return breakers;
    }

    public boolean isWriteReferenceTerminals() {
        return writeReferenceTerminals;
    }

    public ReactivePowerDispatchMode getReactivePowerDispatchMode() {
        return reactivePowerDispatchMode;
    }
}
