package com.powsybl.openloadflow.network.extensions;

import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.SimplePiModel;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymLinePiValues {

    public AsymLinePiValues(double r1, double x1, double gi1, double bi1, double gj1, double bj1,
                            double r2, double x2, double gi2, double bi2, double gj2, double bj2,
                            double r3, double x3, double gi3, double bi3, double gj3, double bj3) {

        this(r1, x1, gi1, bi1, gj1, bj1, r3, x3, gi3, bi3, gj3, bj3);

        this.piComponent2 = new SimplePiModel()
                .setR1(1.)
                .setR(r2)
                .setX(x2)
                .setG1(gi2)
                .setG2(gj2)
                .setB1(bi2)
                .setB2(bj2);
    }

    public AsymLinePiValues(double r1, double x1, double gi1, double bi1, double gj1, double bj1,
                            double r3, double x3, double gi3, double bi3, double gj3, double bj3) {

        this.piComponent1 = new SimplePiModel()
                .setR1(1.)
                .setR(r1)
                .setX(x1)
                .setG1(gi1)
                .setG2(gj1)
                .setB1(bi1)
                .setB2(bj1);

        this.piComponent2 = null;

        this.piComponent3 = new SimplePiModel()
                .setR1(1.)
                .setR(r3)
                .setX(x3)
                .setG1(gi3)
                .setG2(gj3)
                .setB1(bi3)
                .setB2(bj3);

    }

    PiModel piComponent1;
    PiModel piComponent2;
    PiModel piComponent3;
}
