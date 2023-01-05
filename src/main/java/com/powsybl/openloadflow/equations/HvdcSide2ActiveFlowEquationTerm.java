/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.LfHvdc;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class HvdcSide2ActiveFlowEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractHvdcFlowEquationTerm<V, E> {

    public HvdcSide2ActiveFlowEquationTerm(LfHvdc hvdc) {
        super(hvdc);
    }

    private double p2(double power, double lossFactor1, double lossFactor2) {
        if (getHvdc().isControllerSide1()) {
            return -getNonControllerTargetP(power, lossFactor1, lossFactor2);
        } else {
            return power;
        }
    }

    @Override
    public double eval() {
        return p2(getHvdc().getPower(), getLossFactor1(), getLossFactor2());
    }

    @Override
    public double der(Variable<V> variable) {
        return 0.0;
    }

    @Override
    protected String getName() {
        return "hvdc_p_2";
    }
}
