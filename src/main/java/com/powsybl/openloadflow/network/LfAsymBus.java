/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.EvaluableConstants;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LfAsymBus {

    private LfBus bus;

    private final double totalDeltaPa;
    private final double totalDeltaQa;
    private final double totalDeltaPb;
    private final double totalDeltaQb;
    private final double totalDeltaPc;
    private final double totalDeltaQc;

    private double vz = 0;
    private double angleZ = 0;

    private double vn = 0;
    private double angleN = 0;

    private double bzEquiv = 0.; // equivalent shunt in zero and negative sequences induced by all equipment connected to the bus (generating units, loads modelled as shunts etc.)
    private double gzEquiv = 0.;
    private double bnEquiv = 0.;
    private double gnEquiv = 0.;

    private Evaluable ixZ = EvaluableConstants.NAN;
    private Evaluable iyZ = EvaluableConstants.NAN;

    private Evaluable ixN = EvaluableConstants.NAN;
    private Evaluable iyN = EvaluableConstants.NAN;

    public LfAsymBus(double totalDeltaPa, double totalDeltaQa, double totalDeltaPb, double totalDeltaQb, double totalDeltaPc, double totalDeltaQc) {
        this.totalDeltaPa = totalDeltaPa;
        this.totalDeltaQa = totalDeltaQa;
        this.totalDeltaPb = totalDeltaPb;
        this.totalDeltaQb = totalDeltaQb;
        this.totalDeltaPc = totalDeltaPc;
        this.totalDeltaQc = totalDeltaQc;
    }

    public void setBus(LfBus bus) {
        this.bus = Objects.requireNonNull(bus);
    }

    public void setAngleZ(double angleZ) {
        this.angleZ = angleZ;
    }

    public void setAngleN(double angleN) {
        this.angleN = angleN;
    }

    public void setVz(double vz) {
        this.vz = vz;
    }

    public void setVn(double vn) {
        this.vn = vn;
    }

    public void setIxZ(Evaluable ixZ) {
        this.ixZ = ixZ;
    }

    public void setIxN(Evaluable ixN) {
        this.ixN = ixN;
    }

    public void setIyZ(Evaluable iyZ) {
        this.iyZ = iyZ;
    }

    public void setIyN(Evaluable iyN) {
        this.iyN = iyN;
    }

    public double getPa() {
        return bus.getLoadTargetP() + totalDeltaPa;
    }

    public double getPb() {
        return bus.getLoadTargetP() + totalDeltaPb;
    }

    public double getPc() {
        return bus.getLoadTargetP() + totalDeltaPc;
    }

    public double getQa() {
        return bus.getLoadTargetQ() + totalDeltaQa;
    }

    public double getQb() {
        return bus.getLoadTargetQ() + totalDeltaQb;
    }

    public double getQc() {
        return bus.getLoadTargetQ() + totalDeltaQc;
    }

    public double getBnEquiv() {
        return bnEquiv;
    }

    public double getBzEquiv() {
        return bzEquiv;
    }

    public double getGzEquiv() {
        return gzEquiv;
    }

    public double getGnEquiv() {
        return gnEquiv;
    }

    public void setBnEquiv(double bnEquiv) {
        this.bnEquiv = bnEquiv;
    }

    public void setBzEquiv(double bzEquiv) {
        this.bzEquiv = bzEquiv;
    }

    public void setGnEquiv(double gnEquiv) {
        this.gnEquiv = gnEquiv;
    }

    public void setGzEquiv(double gzEquiv) {
        this.gzEquiv = gzEquiv;
    }
}
