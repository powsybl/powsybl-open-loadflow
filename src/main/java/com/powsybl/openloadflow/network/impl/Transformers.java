/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;

import java.util.Objects;

/**
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public final class Transformers {

    private static final double EPS_ALPHA = Math.pow(10, -8);

    private Transformers() {
    }

    static class TapCharacteristics {

        private final double r;

        private final double x;

        private final double g;

        private final double b;

        private final double ratio;

        private final double angle;

        public TapCharacteristics(double r, double x, double g, double b, double ratio, double angle) {
            this.r = r;
            this.x = x;
            this.g = g;
            this.b = b;
            this.ratio = ratio;
            this.angle = angle;
        }

        public double getR() {
            return r;
        }

        public double getX() {
            return x;
        }

        public double getG() {
            return g;
        }

        public double getB() {
            return b;
        }

        public double getRatio() {
            return ratio;
        }

        public double getAngle() {
            return angle;
        }
    }

    public static TapCharacteristics getTapCharacteristics(TwoWindingsTransformer twt, Integer rtcPosition, Integer ptcPosition) {
        double ratio = twt.getRatedU2() / twt.getRatedU1();
        double phase = 0;
        double r = twt.getR();
        double x = twt.getX();
        double g = twt.getG();
        double b = twt.getB();

        RatioTapChanger rtc = twt.getRatioTapChanger();
        if (rtc != null) {
            Objects.requireNonNull(rtcPosition);
            RatioTapChangerStep step = twt.getRatioTapChanger().getStep(rtcPosition);
            r *= 1 + step.getR() / 100;
            x *= 1 + step.getX() / 100;
            g *= 1 + step.getG() / 100;
            b *= 1 + step.getB() / 100;
            ratio *= step.getRho();
        }

        PhaseTapChanger ptc = twt.getPhaseTapChanger();
        if (ptc != null) {
            Objects.requireNonNull(ptcPosition);
            PhaseTapChangerStep step = twt.getPhaseTapChanger().getStep(ptcPosition);
            r *= 1 + step.getR() / 100;
            x *= 1 + step.getX() / 100;
            g *= 1 + step.getG() / 100;
            b *= 1 + step.getB() / 100;
            ratio *= step.getRho();
            phase += Math.toRadians(step.getAlpha());
        }

        return new TapCharacteristics(r, x, g, b, ratio, phase);
    }

    public static TapCharacteristics getTapCharacteristics(TwoWindingsTransformer twt) {
        return getTapCharacteristics(twt, getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()));
    }

    public static Integer getCurrentPosition(RatioTapChanger rtc) {
        return rtc != null ? rtc.getTapPosition() : null;
    }

    /**
     * Get ratio on network side of a three windings transformer leg.
     */
    public static double getRatioLeg(ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg) {
        return getRatioLeg(twt, leg, getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()));
    }

    public static double getRatioLeg(ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg, Integer rtcPosition, Integer ptcPosition) {
        double rho = twt.getRatedU0() / leg.getRatedU();
        if (leg.getRatioTapChanger() != null) {
            Objects.requireNonNull(rtcPosition);
            rho *= leg.getRatioTapChanger().getStep(rtcPosition).getRho();
        }
        if (leg.getPhaseTapChanger() != null) {
            Objects.requireNonNull(ptcPosition);
            rho *= leg.getPhaseTapChanger().getStep(ptcPosition).getRho();
        }
        return rho;
    }

    public static Integer getCurrentPosition(PhaseTapChanger rtc) {
        return rtc != null ? rtc.getTapPosition() : 0;
    }

    /**
     * Get shift angle on network side of a three windings transformer leg.
     */
    public static double getAngleLeg(ThreeWindingsTransformer.Leg leg) {
        return getAngleLeg(leg, getCurrentPosition(leg.getPhaseTapChanger()));
    }

    public static double getAngleLeg(ThreeWindingsTransformer.Leg leg, Integer position) {
        if (leg.getPhaseTapChanger() != null) {
            Objects.requireNonNull(position);
            return Math.toRadians(leg.getPhaseTapChanger().getStep(position).getAlpha());
        }
        return 0f;
    }

    /**
     * Get the nominal series resistance of a three windings transformer leg, located on bus star side.
     */
    public static double getR(ThreeWindingsTransformer.Leg leg) {
        return getR(leg.getR(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()));
    }

    public static double getR(ThreeWindingsTransformer.Leg leg, Integer rtcPosition, Integer ptcPosition) {
        return getR(leg.getR(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    private static double getR(double r, RatioTapChanger rtc, PhaseTapChanger ptc, Integer rtcPosition, Integer ptcPosition) {
        double rtcStepValue = 0;
        if (rtc != null) {
            Objects.requireNonNull(rtcPosition);
            rtcStepValue = rtc.getStep(rtcPosition).getR();
        }

        double ptcStepValue = 0;
        if (ptc != null) {
            Objects.requireNonNull(ptcPosition);
            ptcStepValue = ptc.getStep(ptcPosition).getR();
        }

        return getValue(r, rtcStepValue, ptcStepValue);
    }

    private static double getValue(double initialValue, double rtcStepValue, double ptcStepValue) {
        return initialValue * (1 + rtcStepValue / 100) * (1 + ptcStepValue / 100);
    }

    /**
     * Get the nominal series reactance of a three windings transformer leg, located on bus star side.
     */
    public static double getX(ThreeWindingsTransformer.Leg leg) {
        return getX(leg.getX(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()));
    }

    public static double getX(ThreeWindingsTransformer.Leg leg, Integer rtcPosition, Integer ptcPosition) {
        return getX(leg.getX(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    private static double getX(double x, RatioTapChanger rtc, PhaseTapChanger ptc, Integer rtcPosition, Integer ptcPosition) {
        double rtcStepValue = 0;
        if (rtc != null) {
            Objects.requireNonNull(rtcPosition);
            rtcStepValue = rtc.getStep(rtcPosition).getX();
        }

        double ptcStepValue = 0;
        if (ptc != null) {
            Objects.requireNonNull(ptcPosition);
            ptcStepValue = ptc.getStep(ptcPosition).getX();
        }

        return getValue(x, rtcStepValue, ptcStepValue);
    }

    /**
     * Get the nominal magnetizing conductance of a three windings transformer, located on bus star side.
     */
    public static double getG1(ThreeWindingsTransformer.Leg leg, boolean twtSplitShuntAdmittance) {
        return getG1(leg, getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()), twtSplitShuntAdmittance);
    }

    public static double getG1(ThreeWindingsTransformer.Leg leg, Integer rtcPosition, Integer ptcPosition, boolean twtSplitShuntAdmittance) {
        return getG1(twtSplitShuntAdmittance ? leg.getG() / 2 : leg.getG(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    private static double getG1(double g1, RatioTapChanger rtc, PhaseTapChanger ptc, Integer rtcPosition, Integer ptcPosition) {
        double rtcStepValue = 0;
        if (rtc != null) {
            Objects.requireNonNull(rtcPosition);
            rtcStepValue = rtc.getStep(rtcPosition).getG();
        }

        double ptcStepValue = 0;
        if (ptc != null) {
            Objects.requireNonNull(ptcPosition);
            ptcStepValue = ptc.getStep(ptcPosition).getG();
        }

        return getValue(g1, rtcStepValue, ptcStepValue);
    }

    /**
     * Get the nominal magnetizing susceptance of a three windings transformer, located on bus star side.
     */
    public static double getB1(ThreeWindingsTransformer.Leg leg, boolean twtSplitShuntAdmittance) {
        return getB1(leg, getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()), twtSplitShuntAdmittance);
    }

    public static double getB1(ThreeWindingsTransformer.Leg leg, Integer rtcPosition, Integer ptcPosition, boolean twtSplitShuntAdmittance) {
        return getB1(twtSplitShuntAdmittance ? leg.getB() / 2 : leg.getB(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    private static double getB1(double b1, RatioTapChanger rtc, PhaseTapChanger ptc, Integer rtcPosition, Integer ptcPosition) {
        double rtcStepValue = 0;
        if (rtc != null) {
            Objects.requireNonNull(rtcPosition);
            rtcStepValue = rtc.getStep(rtcPosition).getB();
        }

        double ptcStepValue = 0;
        if (ptc != null) {
            Objects.requireNonNull(ptcPosition);
            ptcStepValue = ptc.getStep(ptcPosition).getB();
        }

        return getValue(b1, rtcStepValue, ptcStepValue);
    }

    /**
     * Find the tap position of a phase tap changer corresponding to a given phase shift.
     */
    public static int findTapPosition(PhaseTapChanger ptc, double angle) {
        for (int position = ptc.getLowTapPosition(); position <= ptc.getHighTapPosition(); position++) {
            if (Math.abs(angle - ptc.getStep(position).getAlpha()) < EPS_ALPHA) {
                return position;
            }
        }
        throw new PowsyblException("No tap position found (should never happen)");
    }

    /**
     * Find the tap position of a ratio tap changer corresponding to a given rho shift.
     */
    public static int findTapPosition(RatioTapChanger rtc, double ptcRho, double rho) {
        for (int position = rtc.getLowTapPosition(); position <= rtc.getHighTapPosition(); position++) {
            if (Math.abs(rho - ptcRho * rtc.getStep(position).getRho()) < EPS_ALPHA) {
                return position;
            }
        }
        throw new PowsyblException("No tap position found (should never happen)");
    }

    public static double getRatioPerUnitBase(ThreeWindingsTransformer.Leg leg, ThreeWindingsTransformer twt) {
        double nominalV1 = leg.getTerminal().getVoltageLevel().getNominalV();
        double nominalV2 = twt.getRatedU0();
        return nominalV2 / nominalV1;
    }

    public static double getRatioPerUnitBase(TwoWindingsTransformer twt) {
        double nominalV1 = twt.getTerminal1().getVoltageLevel().getNominalV();
        double nominalV2 = twt.getTerminal2().getVoltageLevel().getNominalV();
        return nominalV2 / nominalV1;
    }
}
