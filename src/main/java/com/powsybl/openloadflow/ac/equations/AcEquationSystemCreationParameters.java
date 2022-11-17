/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.network.LfNetworkParameters;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcEquationSystemCreationParameters {

    private final boolean forceA1Var;

    private double lowImpedanceThreshold;

    public AcEquationSystemCreationParameters() {
        this(false, LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE);
    }

    public AcEquationSystemCreationParameters(boolean forceA1Var, double lowImpedanceThreshold) {
        this.forceA1Var = forceA1Var;
        this.lowImpedanceThreshold = lowImpedanceThreshold;
    }

    public boolean isForceA1Var() {
        return forceA1Var;
    }

    public double getLowImpedanceThreshold() {
        return lowImpedanceThreshold;
    }

    public AcEquationSystemCreationParameters setLowImpedanceThreshold(double lowImpedanceThreshold) {
        this.lowImpedanceThreshold = lowImpedanceThreshold;
        return this;
    }

    @Override
    public String toString() {
        return "AcEquationSystemCreationParameters(" +
                "forceA1Var=" + forceA1Var +
                ", lowImpedanceThreshold=" + lowImpedanceThreshold +
                ')';
    }
}
