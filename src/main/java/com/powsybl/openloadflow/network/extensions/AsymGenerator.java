package com.powsybl.openloadflow.network.extensions;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymGenerator {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    private final double bz;
    private final double gz;
    private final double gn;
    private final double bn;

    public AsymGenerator(double gz, double bz, double gn, double bn) {
        this.gz = gz;
        this.bz = bz;
        this.gn = gn;
        this.bn = bn;
    }

    public double getGz() {
        return gz;
    }

    public double getGn() {
        return gn;
    }

    public double getBz() {
        return bz;
    }

    public double getBn() {
        return bn;
    }
}
