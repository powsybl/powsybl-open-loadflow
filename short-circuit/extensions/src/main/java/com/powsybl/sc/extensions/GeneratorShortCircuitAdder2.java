/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Generator;

import static com.powsybl.sc.extensions.ShortCircuitConstants.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public class GeneratorShortCircuitAdder2 extends AbstractExtensionAdder<Generator, GeneratorShortCircuit2> {

    private double transRd = DEFAULT_TRANS_RD;
    private double subTransRd = DEFAULT_SUB_TRANS_RD;
    private double cosPhi = DEFAULT_COS_PHI;
    private double ratedU = DEFAULT_RATED_U;
    private GeneratorShortCircuit2.RotorType rotorType = GeneratorShortCircuit2.RotorType.UNDEFINED;
    private double cq = DEFAULT_CQ;
    private double ikQmax = DEFAULT_IKQ;
    private double maxR1ToX1Ratio = DEFAULT_R1_X1_RATIO;
    private double voltageRegulationRange = 0.;
    private double kG = 1.;

    public GeneratorShortCircuitAdder2(Generator generator) {
        super(generator);
    }

    @Override
    public Class<? super GeneratorShortCircuit2> getExtensionClass() {
        return GeneratorShortCircuit2.class;
    }

    @Override
    protected GeneratorShortCircuit2 createExtension(Generator generator) {
        return new GeneratorShortCircuit2(generator, transRd, subTransRd, rotorType, ratedU, cosPhi, cq, ikQmax, maxR1ToX1Ratio, voltageRegulationRange);
    }

    public GeneratorShortCircuitAdder2 withTransRd(double transRd) {
        this.transRd = transRd;
        return this;
    }

    public GeneratorShortCircuitAdder2 withSubTransRd(double subTransRd) {
        this.subTransRd = subTransRd;
        return this;
    }

    public GeneratorShortCircuitAdder2 withRotorType(GeneratorShortCircuit2.RotorType rotorType) {
        this.rotorType = rotorType;
        return this;
    }

    public GeneratorShortCircuitAdder2 withCosPhi(double cosPhi) {
        this.cosPhi = cosPhi;
        return this;
    }

    public GeneratorShortCircuitAdder2 withRatedU(double ratedU) {
        this.ratedU = ratedU;
        return this;
    }

    public GeneratorShortCircuitAdder2 withIkQmax(double ikQ) {
        this.ikQmax = ikQ;
        return this;
    }

    public GeneratorShortCircuitAdder2 withCq(double cq) {
        this.cq = cq;
        return this;
    }

    public GeneratorShortCircuitAdder2 withMaxR1ToX1Ratio(double maxR1ToX1Ratio) {
        this.maxR1ToX1Ratio = maxR1ToX1Ratio;
        return this;
    }

    public GeneratorShortCircuitAdder2 withVoltageRegulationRange(double voltageRegulationRange) {
        this.voltageRegulationRange = voltageRegulationRange;
        return this;
    }

}
