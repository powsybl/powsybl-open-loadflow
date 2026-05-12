/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.List;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public abstract class AbstractLfAcDcConverter extends AbstractElement implements LfAcDcConverter {

    protected Evaluable calculatedPac;

    protected Evaluable calculatedQac;

    protected Evaluable calculatedIconv1;

    protected Evaluable calculatedIconv2;

    protected final double targetP;

    protected double pAc;

    protected double qAc;

    protected double targetVac;

    protected final List<Double> lossFactors;

    protected double targetVdc;

    protected final AcDcConverter.ControlMode controlMode;

    protected final LfDcBus dcBus1;

    protected final LfDcBus dcBus2;

    protected final LfBus bus1;

    protected AbstractLfAcDcConverter(AcDcConverter<?> converter, LfNetwork network, LfDcBus dcBus1, LfDcBus dcBus2, LfBus bus1) {
        super(network);

        // We need to ensure that two LfDcBuses connected to the same converter are initialized with different voltage
        // values. There is no such constraints for other LfDcBuses.
        if (!dcBus1.isInitialVoltageSet() && !dcBus2.isInitialVoltageSet()) {
            dcBus1.setInitialVoltage(1);
            dcBus2.setInitialVoltage(0);
        } else if (dcBus1.isInitialVoltageSet() && !dcBus2.isInitialVoltageSet()) {
            // Complement ensures the two DC buses get distinct values
            dcBus2.setInitialVoltage(1 - dcBus1.getInitialVoltage());
        } else if (!dcBus1.isInitialVoltageSet()) {
            // Complement ensures the two DC buses get distinct values
            dcBus1.setInitialVoltage(1 - dcBus2.getInitialVoltage());
        }
        // else: both already set by a previous converter, nothing to do

        this.dcBus1 = dcBus1;
        this.dcBus2 = dcBus2;
        this.bus1 = bus1;
        this.lossFactors = List.of(converter.getIdleLoss(), converter.getSwitchingLoss(), converter.getResistiveLoss());
        this.controlMode = converter.getControlMode();
        this.targetP = converter.getTargetP() / PerUnit.SB;
        targetVdc = dcBus1.isGrounded() ? converter.getTargetVdc() / dcBus2.getNominalV() : converter.getTargetVdc() / dcBus1.getNominalV();
        this.pAc = converter.getTerminal1().getP();
        this.qAc = converter.getTerminal1().getQ();
    }

    @Override
    public LfBus getBus1() {
        return bus1;
    }

    @Override
    public LfDcBus getDcBus1() {
        return dcBus1;
    }

    @Override
    public LfDcBus getDcBus2() {
        return dcBus2;
    }

    @Override
    public double getTargetP() {
        return targetP;
    }

    @Override
    public double getTargetVac() {
        return targetVac;
    }

    @Override
    public List<Double> getLossFactors() {
        return lossFactors;
    }

    @Override
    public ElementType getType() {
        return ElementType.CONVERTER;
    }

    @Override
    public double getPac() {
        return pAc / PerUnit.SB;
    }

    @Override
    public void setPac(double pac) {
        this.pAc = pac * PerUnit.SB;
    }

    @Override
    public AcDcConverter.ControlMode getControlMode() {
        return controlMode;
    }

    @Override
    public double getTargetVdc() {
        return targetVdc;
    }

    @Override
    public double getQac() {
        return qAc / PerUnit.SB;
    }

    @Override
    public void setQac(double qac) {
        this.qAc = qac * PerUnit.SB;
    }

    @Override
    public void setCalculatedIconv1(Evaluable iconv) {
        calculatedIconv1 = iconv;
    }

    @Override
    public void setCalculatedIconv2(Evaluable iconv) {
        calculatedIconv2 = iconv;
    }

    @Override
    public void setCalculatedPac(Evaluable p) {
        calculatedPac = p;
    }

    @Override
    public void setCalculatedQac(Evaluable q) {
        calculatedQac = q;
    }
}
