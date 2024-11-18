/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.SimplePiModel;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * @author Sylvain Leclerc {@literal <sylvain.leclerc at rte-france.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
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

    private static TapCharacteristics getTapCharacteristics(RatioTapChanger rtc, PhaseTapChanger ptc, Integer rtcPosition, Integer ptcPosition,
                                                            double ratio0, double angle0, double r0, double x0, double g0, double b0) {
        double ratio = ratio0;
        double angle = angle0;
        double r = r0;
        double x = x0;
        double g = g0;
        double b = b0;

        if (rtc != null && ptc == null) {
            Objects.requireNonNull(rtcPosition);
            RatioTapChangerStep step = rtc.getStep(rtcPosition);
            r *= 1 + step.getR() / 100;
            x *= 1 + step.getX() / 100;
            g *= 1 + step.getG() / 100;
            b *= 1 + step.getB() / 100;
            ratio *= step.getRho();
        } else if (ptc != null && rtc == null) {
            Objects.requireNonNull(ptcPosition);
            PhaseTapChangerStep step = ptc.getStep(ptcPosition);
            r *= 1 + step.getR() / 100;
            x *= 1 + step.getX() / 100;
            g *= 1 + step.getG() / 100;
            b *= 1 + step.getB() / 100;
            ratio *= step.getRho();
            angle += Math.toRadians(step.getAlpha());
        } else if (ptc != null && rtc != null) {
            Objects.requireNonNull(rtcPosition);
            Objects.requireNonNull(ptcPosition);
            RatioTapChangerStep rtcStep = rtc.getStep(rtcPosition);
            PhaseTapChangerStep ptcStep = ptc.getStep(ptcPosition);
            r *= (1 + rtcStep.getR() / 100) * (1 + ptcStep.getR() / 100);
            x *= (1 + rtcStep.getX() / 100) * (1 + ptcStep.getX() / 100);
            g *= (1 + rtcStep.getG() / 100) * (1 + ptcStep.getG() / 100);
            b *= (1 + rtcStep.getB() / 100) * (1 + ptcStep.getB() / 100);
            ratio *= rtcStep.getRho() * ptcStep.getRho();
            angle += Math.toRadians(ptcStep.getAlpha());
        }

        return new TapCharacteristics(r, x, g, b, ratio, angle);
    }

    public static TapCharacteristics getTapCharacteristics(TwoWindingsTransformer twt, Integer rtcPosition, Integer ptcPosition) {
        double ratio0 = twt.getRatedU2() / twt.getRatedU1();
        double angle0 = 0;
        double r0 = twt.getR();
        double x0 = twt.getX();
        double g0 = twt.getG();
        double b0 = twt.getB();
        return getTapCharacteristics(twt.getRatioTapChanger(), twt.getPhaseTapChanger(), rtcPosition, ptcPosition, ratio0,
                angle0, r0, x0, g0, b0);
    }

    public static Integer getCurrentPosition(RatioTapChanger rtc) {
        return rtc != null ? rtc.getTapPosition() : null;
    }

    public static Integer getCurrentPosition(PhaseTapChanger ptc) {
        return ptc != null ? ptc.getTapPosition() : null;
    }

    public static TapCharacteristics getTapCharacteristics(TwoWindingsTransformer twt) {
        return getTapCharacteristics(twt, getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()));
    }

    public static TapCharacteristics getTapCharacteristics(ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg, Integer rtcPosition, Integer ptcPosition) {
        double ratio0 = twt.getRatedU0() / leg.getRatedU();
        double angle0 = 0;
        double r0 = leg.getR();
        double x0 = leg.getX();
        double g0 = leg.getG();
        double b0 = leg.getB();
        return getTapCharacteristics(leg.getRatioTapChanger(), leg.getPhaseTapChanger(), rtcPosition, ptcPosition,
                ratio0, angle0, r0, x0, g0, b0);
    }

    public static TapCharacteristics getTapCharacteristics(ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg) {
        return getTapCharacteristics(twt, leg, getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()));
    }

    public static SimplePiModel createPiModel(Transformers.TapCharacteristics tapCharacteristics, double zb,
                                              double baseRatio, boolean twtSplitShuntAdmittance, double ratedRatio) {
        // If twtSplitShuntAdmittance is used we use the ratedRatio, not the tapCharacteristics's ratio. With this choice, if the tap changers step
        // do not change g or b, then the splitted g1/g2 b1/b2 are not changed.
        double r = tapCharacteristics.getR() / zb;
        double x = tapCharacteristics.getX() / zb;
        double g1 = (twtSplitShuntAdmittance ? tapCharacteristics.getG() * ratedRatio / (ratedRatio + 1) : tapCharacteristics.getG()) * zb;
        double g2 = twtSplitShuntAdmittance ? g1 : 0;
        double b1 = (twtSplitShuntAdmittance ? tapCharacteristics.getB() * ratedRatio / (ratedRatio + 1) : tapCharacteristics.getB()) * zb;
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
    public static OptionalInt findTapPosition(RatioTapChanger rtc, double ptcRho, double rho) {
        for (int position = rtc.getLowTapPosition(); position <= rtc.getHighTapPosition(); position++) {
            if (Math.abs(rho - ptcRho * rtc.getStep(position).getRho()) < EPS_ALPHA) {
                return OptionalInt.of(position);
            }
        }
        return OptionalInt.empty();
    }

    public static double getRatioPerUnitBase(ThreeWindingsTransformer.Leg leg, ThreeWindingsTransformer twt) {
        double nominalV1 = leg.getTerminal().getVoltageLevel().getNominalV();
        double nominalV2 = twt.getRatedU0();
        return nominalV2 / nominalV1;
    }

    public static double getRatioPerUnitBase(Branch<?> branch) {
        double nominalV1 = branch.getTerminal1().getVoltageLevel().getNominalV();
        double nominalV2 = branch.getTerminal2().getVoltageLevel().getNominalV();
        return nominalV2 / nominalV1;
    }

    public static double getRatioPerUnitBase(TieLine line) {
        double nominalV1 = line.getDanglingLine1().getTerminal().getVoltageLevel().getNominalV();
        double nominalV2 = line.getDanglingLine2().getTerminal().getVoltageLevel().getNominalV();
        return nominalV2 / nominalV1;
    }
}
