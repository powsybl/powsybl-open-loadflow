/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.extensions.AbcPhaseType;
import com.powsybl.openloadflow.network.extensions.AsymBusLoadType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LfAsymLoad {

    private final LfBus bus;

    private final LegConnectionType loadConnectionType; // how loads are connected between each other
    private final AsymBusLoadType loadType;
    private Complex totalDeltaSa;
    private Complex totalDeltaSb;
    private Complex totalDeltaSc;

    public LfAsymLoad(LfBus bus, AsymBusLoadType loadType, LegConnectionType loadConnectionType, Complex totalDeltaSa, Complex totalDeltaSb, Complex totalDeltaSc) {
        this.bus = bus;
        this.loadType = loadType;
        this.loadConnectionType = loadConnectionType;
        this.totalDeltaSa = totalDeltaSa;
        this.totalDeltaSb = totalDeltaSb;
        this.totalDeltaSc = totalDeltaSc;
    }

    public Complex getS(AbcPhaseType abcPhaseType) {
        if (abcPhaseType == AbcPhaseType.A) {
            return totalDeltaSa;
        } else if (abcPhaseType == AbcPhaseType.B) {
            return totalDeltaSb;
        } else if (abcPhaseType == AbcPhaseType.C) {
            return totalDeltaSc;
        } else {
            throw new IllegalStateException("Unknown Abc Phase Type ");
        }
    }

    public Complex getTotalDeltaSa() {
        return totalDeltaSa;
    }

    public Complex getTotalDeltaSb() {
        return totalDeltaSb;
    }

    public Complex getTotalDeltaSc() {
        return totalDeltaSc;
    }

    public AsymBusLoadType getLoadType() {
        return loadType;
    }

    public LegConnectionType getLoadConnectionType() {
        return loadConnectionType;
    }

    public LfBus getBus() {
        return bus;
    }

    public void addSa(Complex sa) {
        totalDeltaSa = totalDeltaSa.add(sa);
    }

    public void addSb(Complex sb) {
        totalDeltaSb = totalDeltaSb.add(sb);
    }

    public void addSc(Complex sc) {
        totalDeltaSc = totalDeltaSc.add(sc);
    }

    public void addSabc(Complex sa, Complex sb, Complex sc) {
        addSa(sa);
        addSb(sb);
        addSc(sc);
    }
}
