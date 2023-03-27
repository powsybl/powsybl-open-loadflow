package com.powsybl.openloadflow.network.Extensions;

import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.util.Pair;

public class AsymLineAdmittanceTerms {

    public AsymLineAdmittanceTerms(double y11x, double y11y, double y12x, double y12y, double y13x, double y13y,
                                   double y21x, double y21y, double y22x, double y22y, double y23x, double y23y,
                                   double y31x, double y31y, double y32x, double y32y, double y33x, double y33y,
                                   Fortescue.ComponentType componentType) {
        this.componentType = componentType;
        this.y11 = new Pair<>(y11x, y11y);
        this.y12 = new Pair<>(y12x, y12y);
        this.y13 = new Pair<>(y13x, y13y);

        this.y21 = new Pair<>(y21x, y21y);
        this.y22 = new Pair<>(y22x, y22y);
        this.y23 = new Pair<>(y23x, y23y);

        this.y31 = new Pair<>(y31x, y31y);
        this.y32 = new Pair<>(y32x, y32y);
        this.y33 = new Pair<>(y33x, y33y);
    }

    Fortescue.ComponentType componentType;
    private Pair<Double, Double> y11;
    private Pair<Double, Double> y12;
    private Pair<Double, Double> y13;

    private Pair<Double, Double> y21;
    private Pair<Double, Double> y22;
    private Pair<Double, Double> y23;

    private Pair<Double, Double> y31;
    private Pair<Double, Double> y32;
    private Pair<Double, Double> y33;

    public Pair<Double, Double> getY11() {
        return y11;
    }

    public Pair<Double, Double> getY12() {
        return y12;
    }

    public Pair<Double, Double> getY13() {
        return y13;
    }

    public Pair<Double, Double> getY21() {
        return y21;
    }

    public Pair<Double, Double> getY22() {
        return y22;
    }

    public Pair<Double, Double> getY23() {
        return y23;
    }

    public Pair<Double, Double> getY31() {
        return y31;
    }

    public Pair<Double, Double> getY32() {
        return y32;
    }

    public Pair<Double, Double> getY33() {
        return y33;
    }
}
