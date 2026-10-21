/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Generator;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public class GeneratorShortCircuit2 extends AbstractExtension<Generator> {

    public static final String NAME = "generatorShortCircuit2";

    public enum RotorType {
        UNDEFINED,
        TURBOSERIES_1
    }

    public enum GeneratorType {
        UNKNOWN,
        ROTATING_MACHINE,
        FEEDER
    }

    private double transRd; // transient resistance // TODO : will be final when kG of the norm will be handled
    private double subTransRd; // sub-transient resistance // TODO : will be final when kG of the norm will be handled
    private final RotorType rotorType;
    private final double ratedU;
    private final double cosPhi;
    private final double cq;
    private final double maxR1ToX1Ratio;
    private final double ikQmax;
    private final double voltageRegulationRange;

    @Override
    public String getName() {
        return NAME;
    }

    public GeneratorShortCircuit2(Generator generator, double transRd, double subTransRd, RotorType rotorType, double ratedU, double cosPhi,
                                  double cq, double ikQmax, double maxR1ToX1Ratio, double voltageRegulationRange) {
        super(generator);
        this.transRd = transRd;
        this.subTransRd = subTransRd;
        this.rotorType = rotorType;
        this.ratedU = ratedU;
        this.cosPhi = cosPhi;
        this.cq = cq;
        this.ikQmax = ikQmax;
        this.maxR1ToX1Ratio = maxR1ToX1Ratio;
        this.voltageRegulationRange = voltageRegulationRange;
    }

    public double getTransRd() {
        return transRd;
    }

    public double getSubTransRd() {
        return subTransRd;
    }

    public RotorType getRotorType() {
        return rotorType;
    }

    public double getCosPhi() {
        return cosPhi;
    }

    public double getRatedU() {
        return ratedU;
    }

    public void setSubTransRd(double subTransRd) {
        this.subTransRd = subTransRd;
    }

    public void setTransRd(double transRd) {
        this.transRd = transRd;
    }

    public double getCq() {
        return cq;
    }

    public double getIkQmax() {
        return ikQmax;
    }

    public double getMaxR1ToX1Ratio() {
        return maxR1ToX1Ratio;
    }

    public double getVoltageRegulationRange() {
        return voltageRegulationRange;
    }

}
