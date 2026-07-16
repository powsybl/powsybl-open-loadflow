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

    // Droop curve bands (only populated in P_PCC_DROOP control mode), sorted by DC voltage, all values in per unit.
    private final List<DroopBand> droopBands;

    private record DroopBand(double kPu, double minVpu, double maxVpu, double refPpu) {
    }

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
        this.droopBands = getControlMode() == AcDcConverter.ControlMode.P_PCC_DROOP
                ? buildDroopBands(converter, getDcVoltageBase())
                : List.of();
    }

    /**
     * Precompute the per-band droop reference points in per unit. The droop curve gives, per DC-voltage band, a
     * coefficient {@code k}; the piecewise-linear power {@code P(U_dc)} is anchored at {@code (targetVdc, targetP)}
     * and made continuous by integrating {@code k} across bands. For each band we store {@code refP},
     * the anchored power at the band's lower voltage bound {@code refVdc = minV}.
     */
    private static List<DroopBand> buildDroopBands(VoltageSourceConverter converter, double vBase) {
        DroopCurve curve = converter.getDroopCurve();
        List<DroopCurve.Segment> segments = new ArrayList<>(curve.getSegments());
        if (segments.isEmpty()) {
            throw new PowsyblException("AC/DC converter '" + converter.getId()
                    + "' in P_PCC_DROOP control mode must have a droop curve");
        }
        double targetVdc = converter.getTargetVdc();
        double targetP = converter.getTargetP();
        if (Double.isNaN(targetVdc) || Double.isNaN(targetP)) {
            throw new PowsyblException("AC/DC converter '" + converter.getId()
                    + "' in P_PCC_DROOP control mode must have targetP and targetVdc defined");
        }
        segments.sort(Comparator.comparingDouble(DroopCurve.Segment::getMinV));
        int n = segments.size();

        // Anchored active power (MW) at each band's lower voltage bound.
        double[] refP = new double[n];
        int anchor = bandIndex(segments, targetVdc);
        DroopCurve.Segment anchorSeg = segments.get(anchor);
        refP[anchor] = targetP - anchorSeg.getK() * (targetVdc - anchorSeg.getMinV());
        for (int i = anchor + 1; i < n; i++) {
            DroopCurve.Segment prev = segments.get(i - 1);
            refP[i] = refP[i - 1] + prev.getK() * (prev.getMaxV() - prev.getMinV());
        }
        for (int i = anchor - 1; i >= 0; i--) {
            DroopCurve.Segment seg = segments.get(i);
            refP[i] = refP[i + 1] - seg.getK() * (seg.getMaxV() - seg.getMinV());
        }

        List<DroopBand> bands = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            DroopCurve.Segment seg = segments.get(i);
            // k is in MW/kV: k_pu = k * vBase / SB so that P_pu = k_pu * (U_dc_pu - refVdc_pu).
            bands.add(new DroopBand(seg.getK() * vBase / PerUnit.SB,
                    seg.getMinV() / vBase,
                    seg.getMaxV() / vBase,
                    refP[i] / PerUnit.SB));
        }
        return bands;
    }

    // Index of the band containing v, clamped to the nearest band outside the curve range (mirrors DroopCurveImpl#getK).
    private static int bandIndex(List<DroopCurve.Segment> segments, double v) {
        if (v <= segments.getFirst().getMinV()) {
            return 0;
        }
        for (int i = 0; i < segments.size(); i++) {
            DroopCurve.Segment seg = segments.get(i);
            if (v >= seg.getMinV() && v < seg.getMaxV()) {
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
    public double getDcVoltageBase() {
        return vBase;
    }

    @Override
    public DroopReference getDroopReference(double uDc) {
        if (droopBands.isEmpty()) {
            throw new PowsyblException("getDroopReference called on AC/DC converter '" + getId()
                    + "' which is not in P_PCC_DROOP control mode");
        }
        DroopBand band = selectBand(uDc);
        return new DroopReference(band.kPu(), band.minVpu(), band.refPpu());
    }

    // Band containing the solved DC voltage uDc (per unit), clamped to the nearest band outside the curve range.
    private DroopBand selectBand(double uDc) {
        DroopBand first = droopBands.getFirst();
        if (uDc <= first.minVpu()) {
            return first;
        }
        for (DroopBand band : droopBands) {
            if (uDc >= band.minVpu() && uDc < band.maxVpu()) {
                return band;
            }
        }
        return droopBands.getLast();
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
}
