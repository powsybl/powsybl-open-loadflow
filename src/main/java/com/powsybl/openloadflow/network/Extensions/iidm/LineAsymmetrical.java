package com.powsybl.openloadflow.network.Extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Line;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LineAsymmetrical extends AbstractExtension<Line> {

    public class LinePhase {
        private final double rPhase;
        private final double xPhase;
        private boolean isPhaseOpen;

        LinePhase(double rPhase, double xPhase, boolean isPhaseOpen) {
            this.rPhase = rPhase;
            this.xPhase = xPhase;
            this.isPhaseOpen = isPhaseOpen;
        }

        public double getrPhase() {
            return rPhase;
        }

        public boolean isPhaseOpen() {
            return isPhaseOpen;
        }

        public double getxPhase() {
            return xPhase;
        }

        public void setOpen(boolean isOpen) {
            isPhaseOpen = isOpen;
        }
    }

    public static final String NAME = "lineAsymmetrical";

    private LinePhase phaseA;
    private final LinePhase phaseB;
    private final LinePhase phaseC;

    @Override
    public String getName() {
        return NAME;
    }

    public LineAsymmetrical(Line line,
                            double rPhaseA, double xPhaseA, boolean isPhaseOpenA,
                            double rPhaseB, double xPhaseB, boolean isPhaseOpenB,
                            double rPhaseC, double xPhaseC, boolean isPhaseOpenC) {
        super(line);
        this.phaseA = new LinePhase(rPhaseA, xPhaseA, isPhaseOpenA);
        this.phaseB = new LinePhase(rPhaseB, xPhaseB, isPhaseOpenB);
        this.phaseC = new LinePhase(rPhaseC, xPhaseC, isPhaseOpenC);
    }

    public LinePhase getPhaseA() {
        return phaseA;
    }

    public LinePhase getPhaseB() {
        return phaseB;
    }

    public LinePhase getPhaseC() {
        return phaseC;
    }

    public void setPhaseA(boolean isOpen) {
        phaseA.setOpen(isOpen);
    }
}
