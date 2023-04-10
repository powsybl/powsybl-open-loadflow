package com.powsybl.openloadflow.network.extensions;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.EvaluableConstants;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymBus {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    private final LfBus bus;

    private final double totalDeltaPa;
    private final double totalDeltaQa;
    private final double totalDeltaPb;
    private final double totalDeltaQb;
    private final double totalDeltaPc;
    private final double totalDeltaQc;

    private double vZero = 0;
    private double angleZero = 0;

    private double vNegative = 0;
    private double angleNegative = 0;

    private double bZeroEquivalent = 0.; // equivalent shunt in zero and negative sequences induced by all equipment connected to the bus (generating units, loads modelled as shunts etc.)
    private double gZeroEquivalent = 0.;
    private double bNegativeEquivalent = 0.;
    private double gNegativeEquivalent = 0.;

    private Evaluable ixZero = EvaluableConstants.NAN;
    private Evaluable iyZero = EvaluableConstants.NAN;

    private Evaluable ixNegative = EvaluableConstants.NAN;
    private Evaluable iyNegative = EvaluableConstants.NAN;

    public AsymBus(LfBus bus, double totalDeltaPa, double totalDeltaQa, double totalDeltaPb, double totalDeltaQb, double totalDeltaPc, double totalDeltaQc) {
        this.bus = Objects.requireNonNull(bus);
        this.totalDeltaPa = totalDeltaPa;
        this.totalDeltaQa = totalDeltaQa;
        this.totalDeltaPb = totalDeltaPb;
        this.totalDeltaQb = totalDeltaQb;
        this.totalDeltaPc = totalDeltaPc;
        this.totalDeltaQc = totalDeltaQc;
    }

    public void setAngleZero(double angleZero) {
        this.angleZero = angleZero;
    }

    public void setAngleNegative(double angleNegative) {
        this.angleNegative = angleNegative;
    }

    public void setvZero(double vZero) {
        this.vZero = vZero;
    }

    public void setvNegative(double vNegative) {
        this.vNegative = vNegative;
    }

    public void setIxZero(Evaluable ixZero) {
        this.ixZero = ixZero;
    }

    public void setIxNegative(Evaluable ixNegative) {
        this.ixNegative = ixNegative;
    }

    public void setIyZero(Evaluable iyZero) {
        this.iyZero = iyZero;
    }

    public void setIyNegative(Evaluable iyNegative) {
        this.iyNegative = iyNegative;
    }

    public double getPa() {
        return bus.getLoadTargetP() + totalDeltaPa;
    }

    public double getPb() {
        return bus.getLoadTargetP() + totalDeltaPb;
    }

    public double getPc() {
        return bus.getLoadTargetP() + totalDeltaPc;
    }

    public double getQa() {
        return bus.getLoadTargetQ() + totalDeltaQa;
    }

    public double getQb() {
        return bus.getLoadTargetQ() + totalDeltaQb;
    }

    public double getQc() {
        return bus.getLoadTargetQ() + totalDeltaQc;
    }

    public double getbNegativeEquivalent() {
        return bNegativeEquivalent;
    }

    public double getbZeroEquivalent() {
        return bZeroEquivalent;
    }

    public double getgZeroEquivalent() {
        return gZeroEquivalent;
    }

    public double getgNegativeEquivalent() {
        return gNegativeEquivalent;
    }

    public void setbNegativeEquivalent(double bNegativeEquivalent) {
        this.bNegativeEquivalent = bNegativeEquivalent;
    }

    public void setbZeroEquivalent(double bZeroEquivalent) {
        this.bZeroEquivalent = bZeroEquivalent;
    }

    public void setgNegativeEquivalent(double gNegativeEquivalent) {
        this.gNegativeEquivalent = gNegativeEquivalent;
    }

    public void setgZeroEquivalent(double gZeroEquivalent) {
        this.gZeroEquivalent = gZeroEquivalent;
    }
}
