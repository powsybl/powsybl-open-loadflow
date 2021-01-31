/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.SimplePiModel;

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
        double angle = 0;
        double r = twt.getR();
        double x = twt.getX();
        double g = twt.getG();
        double b = twt.getB();

        RatioTapChanger rtc = twt.getRatioTapChanger();
        if (rtc != null) {
            Objects.requireNonNull(rtcPosition);
            RatioTapChangerStep step = rtc.getStep(rtcPosition);
            r *= 1 + step.getR() / 100;
            x *= 1 + step.getX() / 100;
            g *= 1 + step.getG() / 100;
            b *= 1 + step.getB() / 100;
            ratio *= step.getRho();
        }

        PhaseTapChanger ptc = twt.getPhaseTapChanger();
        if (ptc != null) {
            Objects.requireNonNull(ptcPosition);
            PhaseTapChangerStep step = ptc.getStep(ptcPosition);
            r *= 1 + step.getR() / 100;
            x *= 1 + step.getX() / 100;
            g *= 1 + step.getG() / 100;
            b *= 1 + step.getB() / 100;
            ratio *= step.getRho();
            angle += Math.toRadians(step.getAlpha());
        }

        return new TapCharacteristics(r, x, g, b, ratio, angle);
    }

    public static Integer getCurrentPosition(RatioTapChanger rtc) {
        return rtc != null ? rtc.getTapPosition() : null;
    }

    public static Integer getCurrentPosition(PhaseTapChanger rtc) {
        return rtc != null ? rtc.getTapPosition() : 0;
    }

    public static TapCharacteristics getTapCharacteristics(TwoWindingsTransformer twt) {
        return getTapCharacteristics(twt, getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()));
    }

    public static TapCharacteristics getTapCharacteristics(ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg, Integer rtcPosition, Integer ptcPosition) {
        double ratio = twt.getRatedU0() / leg.getRatedU();
        double angle = 0;
        double r = leg.getR();
        double x = leg.getX();
        double g = leg.getG();
        double b = leg.getB();

        RatioTapChanger rtc = leg.getRatioTapChanger();
        if (rtc != null) {
            Objects.requireNonNull(rtcPosition);
            RatioTapChangerStep step = rtc.getStep(rtcPosition);
            r *= 1 + step.getR() / 100;
            x *= 1 + step.getX() / 100;
            g *= 1 + step.getG() / 100;
            b *= 1 + step.getB() / 100;
            ratio *= step.getRho();
        }

        PhaseTapChanger ptc = leg.getPhaseTapChanger();
        if (ptc != null) {
            Objects.requireNonNull(ptcPosition);
            PhaseTapChangerStep step = ptc.getStep(ptcPosition);
            r *= 1 + step.getR() / 100;
            x *= 1 + step.getX() / 100;
            g *= 1 + step.getG() / 100;
            b *= 1 + step.getB() / 100;
            ratio *= step.getRho();
            angle += Math.toRadians(step.getAlpha());
        }

        return new TapCharacteristics(r, x, g, b, ratio, angle);
    }

    public static TapCharacteristics getTapCharacteristics(ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg) {
        return getTapCharacteristics(twt, leg, getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()));
    }

    public static SimplePiModel createPiModel(Transformers.TapCharacteristics tapCharacteristics, double zb,
                                              double baseRatio, boolean twtSplitShuntAdmittance) {
        double r = tapCharacteristics.getR() / zb;
        double x = tapCharacteristics.getX() / zb;
        double g1 = (twtSplitShuntAdmittance ? tapCharacteristics.getG() / 2 : tapCharacteristics.getG()) * zb;
        double g2 = twtSplitShuntAdmittance ? g1 : 0;
        double b1 = (twtSplitShuntAdmittance ? tapCharacteristics.getB() / 2 : tapCharacteristics.getB()) * zb;
        double b2 = twtSplitShuntAdmittance ? b1 : 0;
        double r1 = tapCharacteristics.getRatio() / baseRatio;
        double a1 = tapCharacteristics.getAngle();
        return new SimplePiModel()
                .setR(r)
                .setX(x)
                .setG1(g1)
                .setG2(g2)
                .setB1(b1)
                .setB2(b2)
                .setR1(r1)
                .setA1(a1);
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
