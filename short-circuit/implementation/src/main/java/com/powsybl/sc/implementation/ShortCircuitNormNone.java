/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.*;
import com.powsybl.sc.extensions.GeneratorFortescueType;
import com.powsybl.sc.util.extensions.GeneratorNorm;
import com.powsybl.sc.util.extensions.ShortCircuitNormExtensions;
import com.powsybl.sc.util.extensions.ThreeWindingsTransformerNorm;
import com.powsybl.sc.util.extensions.TwoWindingsTransformerNorm;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitNormNone implements ShortCircuitNorm {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitNormNone.class);

    public static final double EPSILON = 0.000001;

    public Network network;

    private ShortCircuitNormExtensions normExtensions;

    public ShortCircuitNormNone() {
        this.normExtensions = new ShortCircuitNormExtensions();
    }

    @Override
    public ShortCircuitNormExtensions getNormExtensions() {
        return normExtensions;
    }

    @Override
    public double getCmaxVoltageFactor(double nominalVoltage) {
        return 1.0;
    }

    @Override
    public double getCminVoltageFactor(double nominalVoltage) {
        return 1.0;
    }

    @Override
    public double getKtT2W(TwoWindingsTransformer t2w) {
        return 1.0;
    }

    @Override
    public String getNormType() {
        return "NONE";
    }

    @Override
    public double getKg(Generator gen) {
        return 1.;
    }

    @Override
    public void setKg(Generator gen, double kg) {

        GeneratorNorm genNormExtension = normExtensions.getNormExtension(gen);
        if (genNormExtension != null) {
            genNormExtension.setkG(kg);
        } else {
            genNormExtension = new GeneratorNorm(kg);
            normExtensions.setNormExtension(gen, genNormExtension);
        }

    }

    @Override
    public void setKtT3Wi(ThreeWindingsTransformer t3w) {
        T3wCoefs t3wCoefs = getKtT3Wi(t3w);

        ThreeWindingsTransformerNorm t3wExtensionNorm = normExtensions.getNormExtension(t3w);
        if (t3wExtensionNorm == null) {
            t3wExtensionNorm = new ThreeWindingsTransformerNorm();
        }

        t3wExtensionNorm.getLeg1().setKtR(t3wCoefs.ktr1);
        t3wExtensionNorm.getLeg1().setKtX(t3wCoefs.ktx1);
        t3wExtensionNorm.getLeg2().setKtR(t3wCoefs.ktr2);
        t3wExtensionNorm.getLeg2().setKtX(t3wCoefs.ktx2);
        t3wExtensionNorm.getLeg3().setKtR(t3wCoefs.ktr3);
        t3wExtensionNorm.getLeg3().setKtX(t3wCoefs.ktx3);

        t3wExtensionNorm.getLeg1().setKtRo(t3wCoefs.ktro1);
        t3wExtensionNorm.getLeg1().setKtXo(t3wCoefs.ktxo1);
        t3wExtensionNorm.getLeg2().setKtRo(t3wCoefs.ktro2);
        t3wExtensionNorm.getLeg2().setKtXo(t3wCoefs.ktxo2);
        t3wExtensionNorm.getLeg3().setKtRo(t3wCoefs.ktro3);
        t3wExtensionNorm.getLeg3().setKtXo(t3wCoefs.ktxo3);

        if (t3wCoefs.updateCoefo) {
            t3wExtensionNorm.setOverloadHomopolarCoefs(true);
            t3wExtensionNorm.getLeg1().setLegCoeffRoOverload(t3wCoefs.coefro1);
            t3wExtensionNorm.getLeg1().setLegCoeffXoOverload(t3wCoefs.coefxo1);
            t3wExtensionNorm.getLeg2().setLegCoeffRoOverload(t3wCoefs.coefro2);
            t3wExtensionNorm.getLeg2().setLegCoeffXoOverload(t3wCoefs.coefxo2);
            t3wExtensionNorm.getLeg3().setLegCoeffRoOverload(t3wCoefs.coefro3);
            t3wExtensionNorm.getLeg3().setLegCoeffXoOverload(t3wCoefs.coefxo3);
        }
        normExtensions.setNormExtension(t3w, t3wExtensionNorm);

    }

    @Override
    public T3wCoefs getKtT3Wi(ThreeWindingsTransformer t3w) {
        return new T3wCoefs(1., 1., 1., 1., 1., 1., 1., 1., 1., 1., 1., 1.);
    }

    public Pair<Double, Double> getAdjustedLoadfromInfo(Load load, double defaultRd, double defaultXd) {

        return new Pair<>(defaultRd, defaultXd);
    }

    public void applyNormToT2W(Network network) {
        // Work on two windings transformers
        for (TwoWindingsTransformer t2w : network.getTwoWindingsTransformers()) {
            double kNorm = getKtT2W(t2w);

            TwoWindingsTransformerNorm t2wNormExtension = normExtensions.getNormExtension(t2w);
            if (t2wNormExtension != null) {
                t2wNormExtension.setkNorm(kNorm);
            } else {
                t2wNormExtension = new TwoWindingsTransformerNorm(kNorm);
                normExtensions.setNormExtension(t2w, t2wNormExtension);
            }
        }
    }

    public void applyNormToGenerators(Network network) {
        // Work on generators
        for (Generator gen : network.getGenerators()) {
            GeneratorFortescueType extensionGenType = gen.getExtension(GeneratorFortescueType.class);
            if (extensionGenType != null) {
                GeneratorFortescueType.GeneratorType genType = extensionGenType.getGeneratorType();
                if (genType == GeneratorFortescueType.GeneratorType.FEEDER) {
                    //adjustGenValuesWithFeederInputs(gen);
                } else {
                    // this includes standard rotating machines
                    double kg = getKg(gen);
                    setKg(gen, kg);
                }
            }
        }
    }

    public void applyNormToT3w(Network network) {
        // Work on three Windings transformers
        for (ThreeWindingsTransformer t3w : network.getThreeWindingsTransformers()) {
            setKtT3Wi(t3w); // adjust coeffs to respect norm
        }
    }

    @Override
    public void applyNormToNetwork(Network network) {

        this.network = network;
        this.normExtensions = new ShortCircuitNormExtensions();
        applyNormToT2W(network); // the application of the norm to t2w includes generators with t2w associated to them
        applyNormToGenerators(network);
        applyNormToT3w(network);
    }

    public double getCheckedCoef(String id, double ztk, double zt) {

        if (zt == 0.) {
            if (ztk != 0.) {
                LOGGER.warn("Transformer {} has r or x equal to zero and computed rk or xk is non null, short circuit calculation might be wrong", id);
            }
            return 1.;
        } else {
            return ztk / zt;
        }
    }
}
