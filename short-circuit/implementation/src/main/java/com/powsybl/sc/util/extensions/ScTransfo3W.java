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
public class ScTransfo3W {

    public static class Leg {

        private WindingConnectionType legConnectionType;

        private final Complex zo; // only used for now for 3 windings transformers

        private final boolean freeFluxes; // only used for now for 3 windings transformers

        private final Complex zGround = new Complex(0.);

        /*public Leg(LegConnectionType legConnectionType) {
            this(legConnectionType, 0, 0, 1., 1., 1., 1., false);
        }*/

        public Leg(WindingConnectionType legConnectionType, Complex zo, boolean freeFluxes) {
            this.legConnectionType = legConnectionType;
            this.zo = zo;
            this.freeFluxes = freeFluxes;
        }

        public WindingConnectionType getLegConnectionType() {
            return legConnectionType;
        }

        public void setLegConnectionType(WindingConnectionType legConnectionType) {
            this.legConnectionType = Objects.requireNonNull(legConnectionType);
        }

        public Complex getZo() {
            return zo;
        }

        public boolean isFreeFluxes() {
            return freeFluxes;
        }

        public Complex getzGround() {
            return zGround;
        }
    }

    private final Leg leg1;
    private final Leg leg2;
    private final Leg leg3;

    ScTransfo3W(Leg leg1, Leg leg2, Leg leg3) {
        this.leg1 = Objects.requireNonNull(leg1);
        this.leg2 = Objects.requireNonNull(leg2);
        this.leg3 = Objects.requireNonNull(leg3);
    }

    public Leg getLeg1() {
        return leg1;
    }

    public Leg getLeg2() {
        return leg2;
    }

    public Leg getLeg3() {
        return leg3;
    }
}
