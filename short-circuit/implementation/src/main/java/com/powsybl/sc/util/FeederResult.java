/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class FeederResult {

    private Feeder feeder;

    private Complex iContribution;

    public FeederResult(Feeder feeder, Complex i) {
        this.feeder = feeder;
        this.iContribution = i;
    }

    public void updateIcontribution(Complex i) {
        iContribution = iContribution.add(i);
    }

    public void printContributions(LfBus bus) {
        System.out.println(" ix(" + feeder.getId() + ", " + feeder.getFeederType() + ") = " + iContribution.getReal() + " + j(" + iContribution.getImaginary() + ")  Module I = "
                + 1000. * 100. / bus.getNominalV() * Math.sqrt(3.) * iContribution.abs()); //TODO : issue with a 3x factor
    }

    public Complex getIContribution() {
        return iContribution;
    }

    public Feeder getFeeder() {
        return feeder;
    }
}
