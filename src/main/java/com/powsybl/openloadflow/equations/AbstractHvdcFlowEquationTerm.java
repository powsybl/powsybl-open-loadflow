/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.LfHvdc;

import java.util.Collections;
import java.util.List;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public abstract class AbstractHvdcFlowEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractElementEquationTerm<LfHvdc, V, E> {

    protected final LfHvdc hvdc;

    protected final double lossFactor1;

    protected final double lossFactor2;

    protected AbstractHvdcFlowEquationTerm(LfHvdc hvdc) {
        super(hvdc);
        this.hvdc = hvdc;
        lossFactor1 = hvdc.getConverterStation1().getLossFactor() / 100;
        lossFactor2 = hvdc.getConverterStation2().getLossFactor() / 100;
    }

    protected LfHvdc getHvdc() {
        return hvdc;
    }

    protected double getLossFactor1() {
        return lossFactor1;
    }

    protected double getLossFactor2() {
        return lossFactor2;
    }

    protected static double getLossMultiplier(double lossFactor) {
        return 1 - lossFactor;
    }

    protected double getNonControllerTargetP(double power, double controllerLossFactor, double nonControllerLossFactor) {
        double controllerPDc = power * getLossMultiplier(controllerLossFactor);
        return (controllerPDc - hvdc.getLosses(controllerPDc)) * getLossMultiplier(nonControllerLossFactor);
    }

    @Override
    public List<Variable<V>> getVariables() {
        return Collections.emptyList();
    }

    @Override
    public boolean hasRhs() {
        return false;
    }
}
