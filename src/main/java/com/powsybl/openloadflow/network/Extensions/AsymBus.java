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
        this.vHomopolar = vHomopolar;
        this.angleHompolar = angleHompolar;
        this.vInverse = vInverse;
        this.angleInverse = angleInverse;
        this.lfBus = lfBus;

        // TODO : add here info about unbalanced lfLoad
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
                    System.out.println("********************* >>>>>>>> completion of unbalanced load of bus = " + bus.getId() + "*************************************");
                    //System.out.println(">>>>>>>> total Pa = " + totalDeltaPa);
                    //System.out.println(">>>>>>>> total Qa = " + totalDeltaQa);
                }
            }
        }
    }

    public AsymBus(LfBus bus) {
        this(bus, 0., 0., 0., 0.);
    }

    private final LfBus lfBus;

    private double vHomopolar;
    private double angleHompolar;

    private double vInverse;
    private double angleInverse;

    private double totalDeltaPa = 0.;
    private double totalDeltaQa = 0.;
    private double totalDeltaPb = 0.;
    private double totalDeltaQb = 0.;
    private double totalDeltaPc = 0.;
    private double totalDeltaQc = 0.;

    private Evaluable pHomopolar = EvaluableConstants.NAN;
    private Evaluable qHomopolar = EvaluableConstants.NAN;

    private Evaluable pInverse = EvaluableConstants.NAN;
    private Evaluable qInverse = EvaluableConstants.NAN;

    private Evaluable ixHomopolar = EvaluableConstants.NAN;
    private Evaluable iyHomopolar = EvaluableConstants.NAN;

    private Evaluable ixInverse = EvaluableConstants.NAN;
    private Evaluable iyInverse = EvaluableConstants.NAN;

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

    public void setIxHomopolar(Evaluable ixHomopolar) {
        this.ixHomopolar = ixHomopolar;
    }

    public void setIxInverse(Evaluable ixInverse) {
        this.ixInverse = ixInverse;
    }

    public void setIyHomopolar(Evaluable iyHomopolar) {
        this.iyHomopolar = iyHomopolar;
    }

    public void setIyInverse(Evaluable iyInverse) {
        this.iyInverse = iyInverse;
    }

    // TODO : check if there is a x3 coefficient somewhere
    public double getPa() {
        return lfBus.getLoadTargetP() + totalDeltaPa;
    }

    // TODO : check if there is a x3 coefficient somewhere
    public double getPb() {
        return lfBus.getLoadTargetP() + totalDeltaPb;
    }

    // TODO : check if there is a x3 coefficient somewhere
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

    public boolean isBalancedLoad() {
        if (Math.abs(totalDeltaPa) + Math.abs(totalDeltaQa) + Math.abs(totalDeltaPb) + Math.abs(totalDeltaQb) + Math.abs(totalDeltaPc) + Math.abs(totalDeltaQc) > 0.000001) {
            return false;
        }
        return true;
    }
}
