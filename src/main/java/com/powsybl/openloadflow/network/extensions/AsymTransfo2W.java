package com.powsybl.openloadflow.network.extensions;

import org.apache.commons.math3.complex.Complex;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymTransfo2W {

    public static final String PROPERTY_ASYMMETRICAL = "AsymmetricalT2w";

    private final LegConnectionType leg1ConnectionType;
    private final LegConnectionType leg2ConnectionType;

    private final Complex zo; // Zo : value of the homopolar admittance (in pu) expressed at the leg2 side

    private final boolean freeFluxes;

    private final Complex z1Ground;
    private final Complex z2Ground;

    public AsymTransfo2W(LegConnectionType leg1ConnectionType, LegConnectionType leg2ConnectionType, Complex zo, boolean freeFluxes,
                Complex z1Ground, Complex z2Ground) {
        this.leg1ConnectionType = Objects.requireNonNull(leg1ConnectionType);
        this.leg2ConnectionType = Objects.requireNonNull(leg2ConnectionType);
        this.zo = zo;
        this.freeFluxes = freeFluxes;
        this.z1Ground = z1Ground;
        this.z2Ground = z2Ground;
    }

    public LegConnectionType getLeg1ConnectionType() {
        return leg1ConnectionType;
    }

    public LegConnectionType getLeg2ConnectionType() {
        return leg2ConnectionType;
    }

    public Complex getZ1Ground() {
        return z1Ground;
    }

    public Complex getZ2Ground() {
        return z2Ground;
    }

    public Complex getZo() {
        return zo;
    }

    public boolean isFreeFluxes() {
        return freeFluxes;
    }

}
