package com.powsybl.openloadflow.network.Extensions;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Load;
import com.powsybl.openloadflow.network.Extensions.iidm.LoadUnbalanced;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.impl.LfBusImpl;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.EvaluableConstants;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymBus {

    public static final String PROPERTY_ASYMMETRICAL = "Asymmetrical";

    public static final double SB = 100.;

    public AsymBus(LfBus lfBus, double vHomopolar, double angleHompolar, double vInverse, double angleInverse) {
        this.vZero = vHomopolar;
        this.angleZero = angleHompolar;
        this.vNegative = vInverse;
        this.angleNegative = angleInverse;
        this.lfBus = lfBus;

        LfBusImpl lfBusImpl = null;
        if (lfBus instanceof LfBusImpl) {
            lfBusImpl = (LfBusImpl) lfBus;
        }
        Bus bus = null;
        if (lfBusImpl != null) {
            bus = lfBusImpl.getBus();
        }

        if (bus != null) {
            for (Load load : bus.getLoads()) {
                var extension = load.getExtension(LoadUnbalanced.class);
                if (extension != null) {
                    double deltaPa = extension.getDeltaPa();
                    double deltaQa = extension.getDeltaQa();
                    double deltaPb = extension.getDeltaPb();
                    double deltaQb = extension.getDeltaQb();
                    double deltaPc = extension.getDeltaPc();
                    double deltaQc = extension.getDeltaQc();
                    totalDeltaPa = totalDeltaPa + deltaPa / SB;
                    totalDeltaQa = totalDeltaQa + deltaQa / SB;
                    totalDeltaPb = totalDeltaPb + deltaPb / SB;
                    totalDeltaQb = totalDeltaQb + deltaQb / SB;
                    totalDeltaPc = totalDeltaPc + deltaPc / SB;
                    totalDeltaQc = totalDeltaQc + deltaQc / SB;
                }
            }
        }
    }

    public AsymBus(LfBus bus) {
        this(bus, 0., 0., 0., 0.);
    }

    private final LfBus lfBus;

    private double vZero;
    private double angleZero;

    private double vNegative;
    private double angleNegative;

    private double totalDeltaPa = 0.;
    private double totalDeltaQa = 0.;
    private double totalDeltaPb = 0.;
    private double totalDeltaQb = 0.;
    private double totalDeltaPc = 0.;
    private double totalDeltaQc = 0.;

    private double bZeroEquivalent = 0.; // equivalent shunt in zero and negative sequences induced by all equipment connected to the bus (generating units, loads modelled as shunts etc.)
    private double gZeroEquivalent = 0.;
    private double bNegativeEquivalent = 0.;
    private double gNegativeEquivalent = 0.;

    private Evaluable ixZero = EvaluableConstants.NAN;
    private Evaluable iyZero = EvaluableConstants.NAN;

    private Evaluable ixNegative = EvaluableConstants.NAN;
    private Evaluable iyNegative = EvaluableConstants.NAN;

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
        return lfBus.getLoadTargetP() + totalDeltaPa;
    }

    public double getPb() {
        return lfBus.getLoadTargetP() + totalDeltaPb;
    }

    public double getPc() {
        return lfBus.getLoadTargetP() + totalDeltaPc;
    }

    public double getQa() {
        return lfBus.getLoadTargetQ() + totalDeltaQa;
    }

    public double getQb() {
        return lfBus.getLoadTargetQ() + totalDeltaQb;
    }

    public double getQc() {
        return lfBus.getLoadTargetQ() + totalDeltaQc;
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
