/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

import com.powsybl.iidm.network.extensions.WindingConnectionType;
import org.apache.commons.math3.complex.Complex;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ScTransfo2W {

    private final WindingConnectionType leg1ConnectionType;
    private final WindingConnectionType leg2ConnectionType;

    private final Complex zo; // zo : value of the homopolar impedance (in pu) expressed at the leg2 side

    private final boolean freeFluxes;

    private final Complex z1Ground;
    private final Complex z2Ground;

    /*ScTransfo2W(LegConnectionType leg1ConnectionType, LegConnectionType leg2ConnectionType, double coeffRo, double coeffXo, boolean freeFluxes) {
        this(leg1ConnectionType, leg2ConnectionType, coeffRo, coeffXo, freeFluxes, 1d, 0., 0., 0., 0.);
    }*/

    ScTransfo2W(WindingConnectionType leg1ConnectionType, WindingConnectionType leg2ConnectionType, Complex zo, boolean freeFluxes,
                Complex z1Ground, Complex z2Ground) {
        this.leg1ConnectionType = Objects.requireNonNull(leg1ConnectionType);
        this.leg2ConnectionType = Objects.requireNonNull(leg2ConnectionType);
        this.zo = zo;
        this.freeFluxes = freeFluxes;
        this.z1Ground = z1Ground;
        this.z2Ground = z2Ground;
    }

    public WindingConnectionType getLeg1ConnectionType() {
        return leg1ConnectionType;
    }

    public WindingConnectionType getLeg2ConnectionType() {
        return leg2ConnectionType;
    }

    public Complex getZo() {
        return zo;
    }

    public boolean isFreeFluxes() {
        return freeFluxes;
    }

    public Complex getZ1Ground() {
        return z1Ground;
    }

    public Complex getZ2Ground() {
        return z2Ground;
    }
}
