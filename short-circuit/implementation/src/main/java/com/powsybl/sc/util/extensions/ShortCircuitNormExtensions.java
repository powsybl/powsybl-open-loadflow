/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoWindingsTransformer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitNormExtensions {

    private final Map<ThreeWindingsTransformer, ThreeWindingsTransformerNorm> t3wNormExtensions = new HashMap<>();
    private final Map<TwoWindingsTransformer, TwoWindingsTransformerNorm> t2wNormExtensions = new HashMap<>();
    private final Map<Generator, GeneratorNorm> genNormExtensions = new HashMap<>();

    public ThreeWindingsTransformerNorm getNormExtension(ThreeWindingsTransformer t3w) {
        return t3wNormExtensions.get(t3w);
    }

    public TwoWindingsTransformerNorm getNormExtension(TwoWindingsTransformer t2w) {
        return t2wNormExtensions.get(t2w);
    }

    public GeneratorNorm getNormExtension(Generator gen) {
        return genNormExtensions.get(gen);
    }

    public void setNormExtension(ThreeWindingsTransformer t3w, ThreeWindingsTransformerNorm t3wNorm) {
        t3wNormExtensions.put(t3w, t3wNorm);
    }

    public void setNormExtension(TwoWindingsTransformer t2w, TwoWindingsTransformerNorm t2wNorm) {
        t2wNormExtensions.put(t2w, t2wNorm);
    }

    public void setNormExtension(Generator gen, GeneratorNorm genNorm) {
        genNormExtensions.put(gen, genNorm);
    }
}
