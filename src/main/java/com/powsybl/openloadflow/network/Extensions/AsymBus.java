package com.powsybl.openloadflow.network.Extensions;

import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.EvaluableConstants;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymBus {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    public AsymBus(double vHomopolar, double angleHompolar, double vInverse, double angleInverse) {
        this.vHomopolar = vHomopolar;
        this.angleHompolar = angleHompolar;
        this.vInverse = vInverse;
        this.angleInverse = angleInverse;
    }

    public AsymBus() {
        this(0., 0., 0., 0.);
    }

    private double vHomopolar;
    private double angleHompolar;

    private double vInverse;
    private double angleInverse;

    private Evaluable pHomopolar = EvaluableConstants.NAN;
    private Evaluable qHomopolar = EvaluableConstants.NAN;

    private Evaluable pInverse = EvaluableConstants.NAN;
    private Evaluable qInverse = EvaluableConstants.NAN;

    public double getAngleHompolar() {
        return angleHompolar;
    }

    public double getAngleInverse() {
        return angleInverse;
    }

    public double getvHomopolar() {
        return vHomopolar;
    }

    public double getvInverse() {
        return vInverse;
    }

    public void setAngleHompolar(double angleHompolar) {
        this.angleHompolar = angleHompolar;
    }

    public void setAngleInverse(double angleInverse) {
        this.angleInverse = angleInverse;
    }

    public void setvHomopolar(double vHomopolar) {
        this.vHomopolar = vHomopolar;
    }

    public void setvInverse(double vInverse) {
        this.vInverse = vInverse;
    }

    public Evaluable getPHomopolar() {
        return pHomopolar;
    }

    public Evaluable getPInverse() {
        return pInverse;
    }

    public Evaluable getQHomopolar() {
        return qHomopolar;
    }

    public Evaluable getQInverse() {
        return qInverse;
    }

    public void setPHomopolar(Evaluable pHomopolar) {
        this.pHomopolar = pHomopolar;
    }

    public void setPInverse(Evaluable pInverse) {
        this.pInverse = pInverse;
    }

    public void setQHomopolar(Evaluable qHomopolar) {
        this.qHomopolar = qHomopolar;
    }

    public void setQInverse(Evaluable qInverse) {
        this.qInverse = qInverse;
    }
}
