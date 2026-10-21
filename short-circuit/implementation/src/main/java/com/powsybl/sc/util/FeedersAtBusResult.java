/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class FeedersAtBusResult {

    private static final double EPSILON = 0.000001;

    private Complex iFeedersSum; //sum of currents coming from branches, which is also the sum of currents from injector feeders at the same LfBus

    private List<FeederResult> busFeedersResult; // output data of feeders at bus

    private FeedersAtBus feedersAtBus;

    public FeedersAtBusResult(FeedersAtBus feedersAtBus) {
        this.iFeedersSum = new Complex(0.);
        this.busFeedersResult = new ArrayList<>();
        this.feedersAtBus = feedersAtBus;

        // init on feeder results based on equation system feeders
        for (Feeder feeder : feedersAtBus.getFeeders()) {
            FeederResult feederResult = new FeederResult(feeder, new Complex(0.));
            busFeedersResult.add(feederResult);
        }
    }

    public void addItofeedersSum(Complex i) {
        this.iFeedersSum = this.iFeedersSum.add(i);
    }

    public void updateContributions() {
        Complex zSum = new Complex(0.);

        for (FeederResult feederResult : busFeedersResult) {
            zSum = zSum.add(feederResult.getFeeder().getZ());
        }

        if (zSum.abs() > EPSILON) {
            for (FeederResult feederResult : busFeedersResult) {
                Complex zk = feederResult.getFeeder().getZ();
                // ik = zk / zsum * iFeederSum
                Complex ik = zk.multiply(iFeedersSum).divide(zSum);
                feederResult.updateIcontribution(ik);
                feederResult.printContributions(feedersAtBus.getFeedersBus());
            }
        }
    }

    public List<FeederResult> getBusFeedersResult() {
        return busFeedersResult;
    }
}
