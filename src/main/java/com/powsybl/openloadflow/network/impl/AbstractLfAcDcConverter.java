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

    protected final LfDcNode dcNode1;

    protected final LfDcNode dcNode2;

    protected final LfBus bus1;

    public AbstractLfAcDcConverter(AcDcConverter<?> converter, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfBus bus1) {
        super(network);
        this.dcNode1 = dcNode1;
        this.dcNode2 = dcNode2;
        //By convention, the dcNode2 is supposed to be the neutral layer, it is just needed for voltage initialization
        dcNode2.setNeutralPole(true);
        this.bus1 = bus1;
        this.lossFactors = List.of(converter.getIdleLoss(), converter.getSwitchingLoss(), converter.getResistiveLoss());
        this.controlMode = converter.getControlMode();
        this.targetP = converter.getTargetP() / PerUnit.SB;
        targetVdc = dcNode1.isGrounded() ? converter.getTargetVdc() / dcNode2.getNominalV() : converter.getTargetVdc() / dcNode1.getNominalV();
        this.pAc = converter.getTerminal1().getP();
        this.qAc = converter.getTerminal1().getQ();
    }

    @Override
    public LfBus getBus1() {
        return bus1;
    }

    @Override
    public LfDcNode getDcNode1() {
        return dcNode1;
    }

    @Override
    public LfDcNode getDcNode2() {
        return dcNode2;
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
