package com.powsybl.openloadflow.network.extensions;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymTransfo2W {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    private final LegConnectionType leg1ConnectionType;
    private final LegConnectionType leg2ConnectionType;

    private final double xo; // Xo : value of the homopolar admittance (in pu) expressed at the leg2 side
    private final double ro; // Ro : value of the homopolar resistance (in pu) expressed at the leg2 side

    private final boolean freeFluxes;

    private final double r1Ground;
    private final double x1Ground;
    private final double r2Ground;
    private final double x2Ground;

    public AsymTransfo2W(LegConnectionType leg1ConnectionType, LegConnectionType leg2ConnectionType, double ro, double xo, boolean freeFluxes,
                double r1Ground, double x1Ground, double r2Ground, double x2Ground) {
        this.leg1ConnectionType = Objects.requireNonNull(leg1ConnectionType);
        this.leg2ConnectionType = Objects.requireNonNull(leg2ConnectionType);
        this.ro = ro;
        this.xo = xo;
        this.freeFluxes = freeFluxes;
        this.r1Ground = r1Ground;
        this.r2Ground = r2Ground;
        this.x1Ground = x1Ground;
        this.x2Ground = x2Ground;
    }

    public LegConnectionType getLeg1ConnectionType() {
        return leg1ConnectionType;
    }

    public LegConnectionType getLeg2ConnectionType() {
        return leg2ConnectionType;
    }

    public double getXo() {
        return xo;
    }

    public double getRo() {
        return ro;
    }

    public boolean isFreeFluxes() {
        return freeFluxes;
    }

    public double getR1Ground() {
        return r1Ground;
    }

    public double getX1Ground() {
        return x1Ground;
    }

    public double getX2Ground() {
        return x2Ground;
    }

    public double getR2Ground() {
        return r2Ground;
    }
}
