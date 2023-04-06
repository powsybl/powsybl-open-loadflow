package com.powsybl.openloadflow.network.extensions;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymGenerator {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    private final double b0;
    private final double g0;
    private final double g2;
    private final double b2;

    public AsymGenerator(double g0, double b0, double g2, double b2) {
        this.g0 = g0;
        this.b0 = b0;
        this.g2 = g2;
        this.b2 = b2;
    }

    public double getg0() {
        return g0;
    }

    public double getg2() {
        return g2;
    }

    public double getb0() {
        return b0;
    }

    public double getb2() {
        return b2;
    }
}
