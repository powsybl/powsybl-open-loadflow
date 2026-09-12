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
import com.powsybl.iidm.network.DroopCurve;
import com.powsybl.iidm.network.VoltageSourceConverter;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class LfVoltageSourceConverterImpl extends AbstractLfAcDcConverter implements LfVoltageSourceConverter {

    private final Ref<VoltageSourceConverter> converterRef;

    protected final boolean isVoltageRegulatorOn;

    protected double targetQ; // In pu

    protected double targetVac; // In pu

    // Droop curve bands (only populated in DC_DROOP control mode), sorted by DC voltage, all values in per unit.
    private final List<LfDroopReference> droopBands;

    public LfVoltageSourceConverterImpl(VoltageSourceConverter converter, LfNetwork network, LfDcBus dcBus1, LfDcBus dcBus2, LfBus bus1,
                                        LfNetworkParameters parameters) {
        super(converter, network, dcBus1, dcBus2, bus1);
        bus1.addConverter(this);
        this.converterRef = Ref.create(converter, parameters.isCacheEnabled());
        this.isVoltageRegulatorOn = converter.isVoltageRegulatorOn();
        if (isVoltageRegulatorOn) {
            this.targetVac = converter.getVoltageSetpoint() / bus1.getNominalV();
        } else {
            this.targetQ = converter.getReactivePowerSetpoint() / PerUnit.SB;
        }
        this.droopBands = getControlMode() == AcDcConverter.ControlMode.DC_DROOP
                ? buildDroopBands(converter, getDcVoltageBase())
                : List.of();
    }

    /**
     * Precompute the per-band droop reference points in per unit. The droop curve gives, per DC-voltage band, a
     * coefficient {@code k} that is the slope of {@code U_dc} versus {@code P}
     * (see {@link com.powsybl.openloadflow.ac.equations.dcnetwork.ConverterDroopEquationTerm}).
     * The piecewise-linear curve is anchored at {@code (targetVdc, targetP)} and {@code refP} is propagated from
     * band to band so the curve stays continuous at each band boundary. For each band we store {@code refP}, the
     * anchored power at the band's lower voltage bound {@code refVdc = minV}.
     */
    private static List<LfDroopReference> buildDroopBands(VoltageSourceConverter converter, double vBase) {
        DroopCurve curve = converter.getDroopCurve();
        List<DroopCurve.Segment> segments = new ArrayList<>(curve.getSegments());
        if (segments.isEmpty()) {
            throw new PowsyblException("AC/DC converter '" + converter.getId()
                    + "' in DC_DROOP control mode must have a droop curve");
        }
        // All coefficients must share the same strict sign, but this is already checked in IIDM.

        double targetVdc = converter.getTargetVdc();
        double targetP = converter.getTargetP();
        if (Double.isNaN(targetVdc) || Double.isNaN(targetP)) {
            throw new PowsyblException("AC/DC converter '" + converter.getId()
                    + "' in DC_DROOP control mode must have targetP and targetVdc defined");
        }
        segments.sort(Comparator.comparingDouble(DroopCurve.Segment::getMinV));
        int n = segments.size();

        // Convert everything to per unit first, then anchor and integrate refP in that same per-unit system:
        // the equation actually solved (see ConverterDroopEquationTerm) is
        // U_dc_pu = refVdc_pu + k_pu * (P_pu - refP_pu). k is in kV/MW (the slope of U_dc versus P), so
        // k_pu = dU_dc_pu/dP_pu = (dU_dc/vBase) / (dP/SB) = k * SB / vBase.
        double[] kPu = new double[n];
        double[] minVpu = new double[n];
        double[] maxVpu = new double[n];
        for (int i = 0; i < n; i++) {
            DroopCurve.Segment seg = segments.get(i);
            kPu[i] = seg.getK() * PerUnit.SB / vBase;
            minVpu[i] = seg.getMinV() / vBase;
            maxVpu[i] = seg.getMaxV() / vBase;
        }
        double targetVdcPu = targetVdc / vBase;
        double targetPpu = targetP / PerUnit.SB;

        // Anchored active power (per unit) at each band's lower voltage bound.
        double[] refPpu = new double[n];
        int anchor = clampedBandIndex(segments, targetVdc);
        refPpu[anchor] = targetPpu - (targetVdcPu - minVpu[anchor]) / kPu[anchor];
        for (int i = anchor + 1; i < n; i++) {
            refPpu[i] = refPpu[i - 1] + (maxVpu[i - 1] - minVpu[i - 1]) / kPu[i - 1];
        }
        for (int i = anchor - 1; i >= 0; i--) {
            refPpu[i] = refPpu[i + 1] - (maxVpu[i] - minVpu[i]) / kPu[i];
        }

        List<LfDroopReference> bands = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            bands.add(new LfDroopReference(kPu[i], minVpu[i], refPpu[i]));
        }
        return bands;
    }

    /**
     * Index of the band containing {@code v} in the ordered list of {@code [minV, maxV)} segments, clamped to the
     * nearest band when {@code v} falls below the first or on/above the last (mirrors {@code DroopCurveImpl#getK}).
     */
    private static int clampedBandIndex(List<DroopCurve.Segment> segments, double v) {
        if (v <= segments.getFirst().getMinV()) {
            return 0;
        }
        for (int i = 0; i < segments.size(); i++) {
            DroopCurve.Segment segment = segments.get(i);
            if (v >= segment.getMinV() && v < segment.getMaxV()) {
                return i;
            }
        }
        return segments.size() - 1;
    }

    public static LfVoltageSourceConverterImpl create(VoltageSourceConverter acDcConverter, LfNetwork network, LfDcBus dcBus1, LfDcBus dcBus2, LfBus bus1, LfNetworkParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(acDcConverter);
        Objects.requireNonNull(dcBus1);
        Objects.requireNonNull(dcBus2);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(parameters);
        return new LfVoltageSourceConverterImpl(acDcConverter, network, dcBus1, dcBus2, bus1, parameters);

    }

    VoltageSourceConverter getConverter() {
        return converterRef.get();
    }

    @Override
    public boolean isVoltageRegulatorOn() {
        return isVoltageRegulatorOn;
    }

    @Override
    public double getTargetQ() {
        return targetQ;
    }

    @Override
    public double getTargetVac() {
        return targetVac;
    }

    @Override
    public String getId() {
        return getConverter().getId();
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport) {
        if (isDisabled()) {
            updateFlows(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        } else {
            updateFlows(calculatedIconv1.eval(), calculatedIconv2.eval(), calculatedPac.eval(), calculatedQac.eval());
        }
    }

    @Override
    public void updateFlows(double iConv1, double iConv2, double pAc, double qAc) {
        var converter = getConverter();
        double v1 = converter.getDcTerminal1().getDcBus().getV() / dcBus1.getNominalV();
        double v2 = converter.getDcTerminal2().getDcBus().getV() / dcBus2.getNominalV();
        // iConv1 is the current going from dcBus1 to dcBus2
        converter.getDcTerminal1().setI(iConv1 * PerUnit.ibDc(dcBus1.getNominalV()));
        converter.getDcTerminal2().setI(iConv2 * PerUnit.ibDc(dcBus2.getNominalV()));
        // Active power injected by the DC network in the converter
        converter.getDcTerminal1().setP(iConv1 * v1 * PerUnit.SB);
        converter.getDcTerminal2().setP(iConv2 * v2 * PerUnit.SB);
        // Active and reactive power injected by the AC network in the converter
        converter.getTerminal1().setP(pAc * PerUnit.SB);
        converter.getTerminal1().setQ(qAc * PerUnit.SB);
    }

    @Override
    public List<LfDroopReference> getDroopCurve() {
        return droopBands;
    }
}
