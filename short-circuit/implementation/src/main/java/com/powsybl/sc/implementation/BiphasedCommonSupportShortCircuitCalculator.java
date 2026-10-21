/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class BiphasedCommonSupportShortCircuitCalculator extends AbstractShortCircuitCalculator {

    protected Complex zo12;
    protected Complex zo22;
    protected Complex zo21;

    protected Complex zd12;
    protected Complex zd22;
    protected Complex zd21;

    protected Complex zif;
    protected Complex zi12;
    protected Complex zi22;
    protected Complex zi21;

    protected Complex v2dInit;

    protected Complex i2o; // current from bus 2
    protected Complex i2d;
    protected Complex i2i;

    protected Complex vo;
    protected Complex vd;
    protected Complex vi;

    protected Complex v2o;
    protected Complex v2d;
    protected Complex v2i;

    protected Complex zt; // values of total impedance used to get Ic

    protected Complex ic; // short circuit phase C1 current circulating from common support 1 to 2

    public BiphasedCommonSupportShortCircuitCalculator(Complex zdf, Complex zof, ShortCircuitFaultImpedance zFault,
                                                       Complex initV, Complex v2dInit,
                                                       Complex zo12, Complex zo22, Complex zo21,
                                                       Complex zd12, Complex zd22, Complex zd21) {
        super(zdf, zof, zFault, initV);
        this.zo12 = zo12;
        this.zo22 = zo22;
        this.zo21 = zo21;

        this.zd12 = zd12;
        this.zd22 = zd22;
        this.zd21 = zd21;

        // By default, zif = zdf
        this.zif = zdf;
        this.zi12 = zd12;
        this.zi22 = zd22;
        this.zi21 = zd21;

        this.v2dInit = v2dInit;
    }

    public void computeZt() { }

    public void computeIc() { }

    public void computeCurrents() { }

    public void computeVoltages() {
        // Function using no input args
        // [ Vof ] = -tM * inv(Yo) * M * [ Iof ]
        // [ Vdf ] = -tM * inv(Yd) * M * [ Idf ] + tM * [ V(init) ]
        // [ Vif ] = -tM * inv(Yd) * M * [ Iif ]

        //get the voltage vectors
        // Vo :
        // [v1o]          [ zof_11    zof_12 ]   [ i1o ]
        // [v2o] = -1  *  [ zof_21    zof_22 ] * [ i2o ]

        vo = zof.multiply(io).add(zo12.multiply(i2o)).multiply(-1.);
        v2o = zo21.multiply(io).add(zo22.multiply(i2o)).multiply(-1.);

        // Vd :
        // [v1d]          [ zdf_11    zdf_12 ]   [ i1d ]    [v1d_init]
        // [v2d] = -1  *  [ zdf_21    zdf_22 ] * [ i2d ] +  [v2d_init]
        vd = initV.subtract(zdf.multiply(id).add(zd12.multiply(i2d)));
        v2d = v2dInit.subtract(zd21.multiply(id).add(zd22.multiply(i2d)));

        // Vi :
        vi = zif.multiply(ii).add(zi12.multiply(i2i)).multiply(-1.);
        v2i = zi21.multiply(ii).add(zi22.multiply(i2i)).multiply(-1.);
    }

    public Complex getI2d() {
        return i2d;
    }

    public Complex getI2i() {
        return i2i;
    }

    public Complex getI2o() {
        return i2o;
    }

    public Complex getV2d() {
        return v2d;
    }

    public Complex getV2o() {
        return v2o;
    }

    public Complex getV2i() {
        return v2i;
    }

    public Complex getVd() {
        return vd;
    }

    public Complex getVi() {
        return vi;
    }

    public Complex getVo() {
        return vo;
    }
}
