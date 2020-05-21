/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.StaticVarCompensator;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VoltageSlopeReactivePowerControlImpl extends AbstractExtension<StaticVarCompensator> implements VoltageSlopeReactivePowerControl {

    private double slope;

    public VoltageSlopeReactivePowerControlImpl(StaticVarCompensator svc, double slope) {
        super(svc);
        this.slope = slope;
    }

    @Override
    public double getSlope() {
        return slope;
    }

    public VoltageSlopeReactivePowerControl setSlope(double slope) {
        this.slope = slope;
        return this;
    }
}
