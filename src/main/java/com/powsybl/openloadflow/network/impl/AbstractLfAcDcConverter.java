/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

/**
 * Base class of the AC/DC converters of a {@link LfNetwork}.
 * <p>Usage rules for subclasses, which IIDM does not enforce:
 * <ul>
 *     <li>a converter in {@code DC_DROOP} control mode must have a droop curve and both {@code targetP} and
 *     {@code targetVdc} defined, otherwise loading the network fails with a {@code PowsyblException};</li>
 *     <li>all DC buses of a DC component share the same nominal voltage, which is the DC voltage base
 *     (see {@link #getDcVoltageBase()}).</li>
 * </ul>
 *
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public abstract class AbstractLfAcDcConverter extends AbstractElement implements LfAcDcConverter {

    protected Evaluable calculatedPac; // in pu

    protected Evaluable calculatedQac; // in pu

    protected Evaluable calculatedIconv1; // in pu

    protected Evaluable calculatedIconv2; // in pu

    protected double targetP; // in pu

    protected final double minP; // in pu

    protected final double maxP; // in pu

    protected double pAc; // in MW

    protected double qAc; // in MVAr

    protected final LossFactors lossFactors; // in MW, MW/A and Ohm

    protected double targetVdc; // in pu

    private final AcDcConverter.ControlMode initialControlMode;

    protected AcDcConverter.ControlMode controlMode;

    protected final LfDcBus dcBus1;

    protected final LfDcBus dcBus2;

    protected final LfBus bus1;

    private final int numDCC;

    protected AbstractLfAcDcConverter(AcDcConverter<?> converter, LfNetwork network, int numDCC, LfDcBus dcBus1, LfDcBus dcBus2, LfBus bus1, boolean checkActiveLimits) {
        super(network);
        if (checkActiveLimits && converter.getControlMode() == AcDcConverter.ControlMode.P_PCC) {
            validateActivePowerLimits(converter);
        }

        this.numDCC = numDCC;
        this.dcBus1 = dcBus1;
        this.dcBus2 = dcBus2;
        this.bus1 = bus1;
        this.lossFactors = new LossFactors(converter.getIdleLoss(), converter.getSwitchingLoss(), converter.getResistiveLoss());
        this.initialControlMode = converter.getControlMode();
        this.controlMode = converter.getControlMode();
        this.targetP = converter.getTargetP() / PerUnit.SB;
        this.minP = converter.getMinP() / PerUnit.SB;
        this.maxP = converter.getMaxP() / PerUnit.SB;
        this.targetVdc = converter.getTargetVdc() / getDcVoltageBase();
        this.pAc = converter.getTerminal1().getP();
        this.qAc = converter.getTerminal1().getQ();
    }

    protected AbstractLfAcDcConverter(AbstractLfAcDcConverter other, LfNetwork network, LfDcBus dcBus1, LfDcBus dcBus2, LfBus bus1) {
        super(network);
        this.numDCC = other.numDCC;
        this.dcBus1 = dcBus1;
        this.dcBus2 = dcBus2;
        this.bus1 = bus1;
        this.lossFactors = other.lossFactors;
        this.initialControlMode = other.initialControlMode;
        this.controlMode = other.controlMode;
        this.targetP = other.targetP;
        this.minP = other.minP;
        this.maxP = other.maxP;
        this.targetVdc = other.targetVdc;
        this.pAc = other.pAc;
        this.qAc = other.qAc;
        this.disabled = other.disabled;
    }

    /**
     * Check that the target AC power of a converter is within the allowed bounds.
     *
     * @param converter The converter being checked
     * @throws PowsyblException if the converter's target AC power is not within its power bounds.
     */
    private static void validateActivePowerLimits(AcDcConverter<?> converter) {
        if (converter.getTargetP() < converter.getMinP()) {
            throw new PowsyblException(String.format("Converter %s targetP (%f) is below its minP (%f)", converter.getId(), converter.getTargetP(), converter.getMinP()));
        }
        if (converter.getTargetP() > converter.getMaxP()) {
            throw new PowsyblException(String.format("Converter %s targetP (%f) is above its maxP (%f)", converter.getId(), converter.getTargetP(), converter.getMaxP()));
        }
    }

    @Override
    public int getNumDCC() {
        return numDCC;
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
    public void setTargetP(double newTargetP) {
        if (newTargetP != targetP) {
            targetP = newTargetP;
            for (LfNetworkListener listener : network.getListeners()) {
                listener.onAcDcConverterTargetPChange(this, newTargetP);
            }
        }
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
    public AcDcConverter.ControlMode getInitialControlMode() {
        return initialControlMode;
    }

    @Override
    public AcDcConverter.ControlMode getControlMode() {
        return controlMode;
    }

    @Override
    public void setControlMode(AcDcConverter.ControlMode newControlMode) {
        if (newControlMode != controlMode) {
            controlMode = newControlMode;
            for (LfNetworkListener listener : network.getListeners()) {
                listener.onAcDcConverterControlModeChange(this, newControlMode);
            }
        }
    }

    @Override
    public double getTargetVdc() {
        return targetVdc;
    }

    @Override
    public void setTargetVdc(double newTargetVdc) {
        if (newTargetVdc != targetVdc) {
            targetVdc = newTargetVdc;
            // This is only called when initialising the LfNetwork. At this stage, there is no LfNetworkListeners.
            // This will need to be updated if DC network validation is used outside the LfNetwork initialisation
            // (e.g. in security analysis)
        }
    }

    @Override
    public double getDcVoltageBase() {
        // Hypothesis: all buses in the DC voltage have the same nominal DC voltage.
        return dcBus1.getNominalV();
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
