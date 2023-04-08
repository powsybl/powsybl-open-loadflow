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

        piComponent1 = new SimplePiModel()
                .setR1(1.)
                .setR(r1)
                .setX(x1)
                .setG1(gi1)
                .setG2(gj1)
                .setB1(bi1)
                .setB2(bj1);

        piComponent2 = new SimplePiModel()
                .setR1(1.)
                .setR(r2)
                .setX(x2)
                .setG1(gi2)
                .setG2(gj2)
                .setB1(bi2)
                .setB2(bj2);

        piComponent3 = new SimplePiModel()
                .setR1(1.)
                .setR(r3)
                .setX(x3)
                .setG1(gi3)
                .setG2(gj3)
                .setB1(bi3)
                .setB2(bj3);
    }

    private final PiModel piComponent1;
    private final PiModel piComponent2;
    private final PiModel piComponent3;

    public PiModel getPiComponent1() {
        return piComponent1;
    }

    public PiModel getPiComponent2() {
        return piComponent2;
    }

    public PiModel getPiComponent3() {
        return piComponent3;
    }
}
