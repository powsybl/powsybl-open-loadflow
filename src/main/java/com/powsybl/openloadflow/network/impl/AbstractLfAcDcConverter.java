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

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public abstract class AbstractLfAcDcConverter extends AbstractElement implements LfAcDcConverter {

    protected Evaluable calculatedPac; // in pu

    protected Evaluable calculatedQac; // in pu

    protected Evaluable calculatedIconv1; // in pu

    protected Evaluable calculatedIconv2; // in pu

    protected final double targetP; // in pu

    protected double pAc; // in MW

    protected double qAc; // in MVAr

    protected final LossFactors lossFactors; // in MW, MW/A and Ohm

    protected double targetVdc; // in pu

    protected final double vBase; // nominal voltage of the non-grounded DC bus, in kV; per-unit base of the DC voltage

    protected final AcDcConverter.ControlMode controlMode;

    protected final LfDcBus dcBus1;

    protected final LfDcBus dcBus2;

    protected final LfBus bus1;

    protected AbstractLfAcDcConverter(AcDcConverter<?> converter, LfNetwork network, LfDcBus dcBus1, LfDcBus dcBus2, LfBus bus1) {
        super(network);

        this.dcBus1 = dcBus1;
        this.dcBus2 = dcBus2;
        this.bus1 = bus1;
        this.lossFactors = new LossFactors(converter.getIdleLoss(), converter.getSwitchingLoss(), converter.getResistiveLoss());
        this.controlMode = converter.getControlMode();
        this.targetP = converter.getTargetP() / PerUnit.SB;
        this.vBase = dcBus1.isGrounded() ? dcBus2.getNominalV() : dcBus1.getNominalV();
        targetVdc = converter.getTargetVdc() / vBase;
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
    public LossFactors getLossFactors() {
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

    public double getDcVoltageBase() {
        return vBase;
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
