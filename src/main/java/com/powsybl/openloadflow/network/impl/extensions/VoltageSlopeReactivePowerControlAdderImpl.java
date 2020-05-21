/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl.extensions;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.StaticVarCompensator;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VoltageSlopeReactivePowerControlAdderImpl
        extends AbstractExtensionAdder<StaticVarCompensator, VoltageSlopeReactivePowerControl>
        implements VoltageSlopeReactivePowerControlAdder {

    private double slope;

    protected VoltageSlopeReactivePowerControlAdderImpl(StaticVarCompensator svc) {
        super(svc);
    }

    @Override
    protected VoltageSlopeReactivePowerControlImpl createExtension(StaticVarCompensator svc) {
        return new VoltageSlopeReactivePowerControlImpl(svc, slope);
    }

    @Override
    public VoltageSlopeReactivePowerControlAdder withSlope(double slope) {
        this.slope = slope;
        return this;
    }
}
