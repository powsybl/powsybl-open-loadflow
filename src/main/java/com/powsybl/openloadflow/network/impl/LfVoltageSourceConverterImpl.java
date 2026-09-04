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
import java.util.function.ToDoubleFunction;

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
     * coefficient {@code k} that is the slope of {@code U_dc} versus {@code P}
     * (see {@link com.powsybl.openloadflow.ac.equations.dcnetwork.ConverterDroopEquationTerm}).
     * The piecewise-linear curve is anchored at {@code (targetVdc, targetP)} and {@code refP} is propagated from
     * band to band so the curve stays continuous at each band boundary. For each band we store {@code refP}, the
     * anchored power at the band's lower voltage bound {@code refVdc = minV}.
     */
    private static List<DroopBand> buildDroopBands(VoltageSourceConverter converter, double vBase) {
        DroopCurve curve = converter.getDroopCurve();
        List<DroopCurve.Segment> segments = new ArrayList<>(curve.getSegments());
        if (segments.isEmpty()) {
            throw new PowsyblException("AC/DC converter '" + converter.getId()
                    + "' in P_PCC_DROOP control mode must have a droop curve");
        }
        // All coefficients must share the same strict sign: a k=0 band leaves its reference power undefined
        // (division by zero below), and a sign change would break the band lookup, since two different P values
        // could then land on the same U_dc.
        boolean allPositive = segments.stream().allMatch(segment -> segment.getK() > 0);
        boolean allNegative = segments.stream().allMatch(segment -> segment.getK() < 0);
        if (!allPositive && !allNegative) {
            throw new PowsyblException("AC/DC converter '" + converter.getId()
                    + "' in P_PCC_DROOP control mode must have a droop curve with all coefficients of the same strict sign (all positive or all negative)");
        }
        double targetVdc = converter.getTargetVdc();
        double targetP = converter.getTargetP();
        if (Double.isNaN(targetVdc) || Double.isNaN(targetP)) {
            throw new PowsyblException("AC/DC converter '" + converter.getId()
                    + "' in P_PCC_DROOP control mode must have targetP and targetVdc defined");
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
        int anchor = clampedBandIndex(segments, targetVdc, DroopCurve.Segment::getMinV, DroopCurve.Segment::getMaxV);
        refPpu[anchor] = targetPpu - (targetVdcPu - minVpu[anchor]) / kPu[anchor];
        for (int i = anchor + 1; i < n; i++) {
            refPpu[i] = refPpu[i - 1] + (maxVpu[i - 1] - minVpu[i - 1]) / kPu[i - 1];
        }
        for (int i = anchor - 1; i >= 0; i--) {
            refPpu[i] = refPpu[i + 1] - (maxVpu[i] - minVpu[i]) / kPu[i];
        }

        List<DroopBand> bands = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            bands.add(new DroopBand(kPu[i], minVpu[i], maxVpu[i], refPpu[i]));
        }
        return bands;
    }

    /**
     * Index of the band containing {@code v} in an ordered list of {@code [minV, maxV)} ranges, clamped to the nearest
     * band when {@code v} falls below the first or on/above the last (mirrors {@code DroopCurveImpl#getK}). Used both at
     * build time over {@link DroopCurve.Segment} (kV) and at solve time over {@link DroopBand} (per unit), hence the
     * min/max accessors rather than a fixed element type.
     */
    private static <T> int clampedBandIndex(List<T> bands, double v, ToDoubleFunction<T> minV, ToDoubleFunction<T> maxV) {
        if (v <= minV.applyAsDouble(bands.getFirst())) {
            return 0;
        }
        for (int i = 0; i < bands.size(); i++) {
            T band = bands.get(i);
            if (v >= minV.applyAsDouble(band) && v < maxV.applyAsDouble(band)) {
                return i;
            }
        }
        return bands.size() - 1;
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
    public DroopReference getDroopReference(double uDc) {
        if (droopBands.isEmpty()) {
            throw new PowsyblException("getDroopReference called on AC/DC converter '" + getId()
                    + "' which is not in P_PCC_DROOP control mode");
        }
        // Band containing the solved DC voltage uDc (per unit), clamped to the nearest band outside the curve range.
        DroopBand band = droopBands.get(clampedBandIndex(droopBands, uDc, DroopBand::minVpu, DroopBand::maxVpu));
        return new DroopReference(band.kPu(), band.minVpu(), band.refPpu());
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
