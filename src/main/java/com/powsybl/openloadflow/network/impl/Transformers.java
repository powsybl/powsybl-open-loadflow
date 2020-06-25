/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.RatioTapChanger;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoWindingsTransformer;

/**
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public final class Transformers {

    private static final double EPS_ALPHA = Math.pow(10, -8);

    private Transformers() {
    }

    /**
     * Get ratio on side 1 of a two windings transformer.
     */
    public static double getRatio(TwoWindingsTransformer twt) {
        return getRatio(twt, getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()));
    }

    public static double getRatio(TwoWindingsTransformer twt, int rtcPosition, int ptcPosition) {
        double rho = twt.getRatedU2() / twt.getRatedU1();
        if (twt.getRatioTapChanger() != null) {
            rho *= twt.getRatioTapChanger().getStep(rtcPosition).getRho();
        }
        if (twt.getPhaseTapChanger() != null) {
            rho *= twt.getPhaseTapChanger().getStep(ptcPosition).getRho();
        }
        return rho;
    }

    private static int getCurrentPosition(RatioTapChanger rtc) {
        return rtc != null ? rtc.getTapPosition() : -1;
    }

    /**
     * Get ratio on network side of a three windings transformer leg.
     */
    public static double getRatioLeg(ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg) {
        return getRatioLeg(twt, leg, getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()));
    }

    public static double getRatioLeg(ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg, int rtcPosition, int ptcPosition) {
        double rho = twt.getRatedU0() / leg.getRatedU();
        if (leg.getRatioTapChanger() != null) {
            rho *= leg.getRatioTapChanger().getStep(rtcPosition).getRho();
        }
        if (leg.getPhaseTapChanger() != null) {
            rho *= leg.getPhaseTapChanger().getStep(ptcPosition).getRho();
        }
        return rho;
    }

    /**
     * Get shift angle on side 1 of a two windings transformer.
     */
    public static double getAngle(TwoWindingsTransformer twt) {
        return getAngle(twt, getCurrentPosition(twt.getPhaseTapChanger()));
    }

    public static double getAngle(TwoWindingsTransformer twt, int position) {
        return twt.getPhaseTapChanger() != null ? Math.toRadians(twt.getPhaseTapChanger().getStep(position).getAlpha()) : 0f;
    }

    private static int getCurrentPosition(PhaseTapChanger rtc) {
        return rtc != null ? rtc.getTapPosition() : -1;
    }

    /**
     * Get shift angle on network side of a three windings transformer leg.
     */
    public static double getAngleLeg(ThreeWindingsTransformer.Leg leg) {
        return getAngleLeg(leg, getCurrentPosition(leg.getPhaseTapChanger()));
    }

    public static double getAngleLeg(ThreeWindingsTransformer.Leg leg, int position) {
        return leg.getPhaseTapChanger() != null ? Math.toRadians(leg.getPhaseTapChanger().getStep(position).getAlpha()) : 0f;
    }

    /**
     * Get the nominal series resistance of a two windings transformer, located on side 2.
     */
    public static double getR(TwoWindingsTransformer twt) {
        return getR(twt.getR(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()));
    }

    public static double getR(TwoWindingsTransformer twt, int rtcPosition, int ptcPosition) {
        return getR(twt.getR(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    /**
     * Get the nominal series resistance of a three windings transformer leg, located on bus star side.
     */
    public static double getR(ThreeWindingsTransformer.Leg leg) {
        return getR(leg.getR(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()));
    }

    public static double getR(ThreeWindingsTransformer.Leg leg, int rtcPosition, int ptcPosition) {
        return getR(leg.getR(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    private static double getR(double r, RatioTapChanger rtc, PhaseTapChanger ptc, int rtcPosition, int ptcPosition) {
        return getValue(r,
                rtc != null ? rtc.getStep(rtcPosition).getR() : 0,
                ptc != null ? ptc.getStep(ptcPosition).getR() : 0);
    }

    private static double getValue(double initialValue, double rtcStepValue, double ptcStepValue) {
        return initialValue * (1 + rtcStepValue / 100) * (1 + ptcStepValue / 100);
    }

    /**
     * Get the nominal series reactance of a two windings transformer, located on side 2.
     */
    public static double getX(TwoWindingsTransformer twt) {
        return getX(twt.getX(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()));
    }

    public static double getX(TwoWindingsTransformer twt, int rtcPosition, int ptcPosition) {
        return getX(twt.getX(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    /**
     * Get the nominal series reactance of a three windings transformer leg, located on bus star side.
     */
    public static double getX(ThreeWindingsTransformer.Leg leg) {
        return getX(leg.getX(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()));
    }

    public static double getX(ThreeWindingsTransformer.Leg leg, int rtcPosition, int ptcPosition) {
        return getX(leg.getX(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    private static double getX(double x, RatioTapChanger rtc, PhaseTapChanger ptc, int rtcPosition, int ptcPosition) {
        return getValue(x,
                rtc != null ? rtc.getStep(rtcPosition).getX() : 0,
                ptc != null ? ptc.getStep(ptcPosition).getX() : 0);
    }

    /**
     * Get the nominal magnetizing conductance of a two windings transformer, located on side 2.
     */
    public static double getG1(TwoWindingsTransformer twt, boolean twtSplitShuntAdmittance) {
        return getG1(twt, getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()), twtSplitShuntAdmittance);
    }

    public static double getG1(TwoWindingsTransformer twt, int rtcPosition, int ptcPosition, boolean twtSplitShuntAdmittance) {
        return getG1(twtSplitShuntAdmittance ? twt.getG() / 2 : twt.getG(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    /**
     * Get the nominal magnetizing conductance of a three windings transformer, located on bus star side.
     */
    public static double getG1(ThreeWindingsTransformer.Leg leg, boolean twtSplitShuntAdmittance) {
        return getG1(leg, getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()), twtSplitShuntAdmittance);
    }

    public static double getG1(ThreeWindingsTransformer.Leg leg, int rtcPosition, int ptcPosition, boolean twtSplitShuntAdmittance) {
        return getG1(twtSplitShuntAdmittance ? leg.getG() / 2 : leg.getG(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    public static double getG1(TwoWindingsTransformer twt) {
        return getG1(twt, false);
    }

    public static double getG1(ThreeWindingsTransformer.Leg leg) {
        return getG1(leg, false);
    }

    private static double getG1(double g1, RatioTapChanger rtc, PhaseTapChanger ptc, int rtcPosition, int ptcPosition) {
        return getValue(g1,
                rtc != null ? rtc.getStep(rtcPosition).getG() : 0,
                ptc != null ? ptc.getStep(ptcPosition).getG() : 0);
    }

    /**
     * Get the nominal magnetizing susceptance of a two windings transformer, located on side 2.
     */
    public static double getB1(TwoWindingsTransformer twt, boolean twtSplitShuntAdmittance) {
        return getB1(twt, getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()), twtSplitShuntAdmittance);
    }

    public static double getB1(TwoWindingsTransformer twt, int rtcPosition, int ptcPosition, boolean twtSplitShuntAdmittance) {
        return getB1(twtSplitShuntAdmittance ? twt.getB() / 2 : twt.getB(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    /**
     * Get the nominal magnetizing susceptance of a three windings transformer, located on bus star side.
     */
    public static double getB1(ThreeWindingsTransformer.Leg leg, boolean twtSplitShuntAdmittance) {
        return getB1(leg, getCurrentPosition(leg.getRatioTapChanger()), getCurrentPosition(leg.getPhaseTapChanger()), twtSplitShuntAdmittance);
    }

    public static double getB1(ThreeWindingsTransformer.Leg leg, int rtcPosition, int ptcPosition, boolean twtSplitShuntAdmittance) {
        return getB1(twtSplitShuntAdmittance ? leg.getB() / 2 : leg.getB(), leg.getRatioTapChanger(), leg.getPhaseTapChanger(), rtcPosition, ptcPosition);
    }

    public static double getB1(TwoWindingsTransformer twt) {
        return getB1(twt, false);
    }

    public static double getB1(ThreeWindingsTransformer.Leg leg) {
        return getB1(leg, false);
    }

    private static double getB1(double b1, RatioTapChanger rtc, PhaseTapChanger ptc) {
        return getB1(b1, rtc, ptc, rtc.getTapPosition(), ptc.getTapPosition());
    }

    private static double getB1(double b1, RatioTapChanger rtc, PhaseTapChanger ptc, int rtcPosition, int ptcPosition) {
        return getValue(b1,
                rtc != null ? rtc.getStep(rtcPosition).getB() : 0,
                ptc != null ? ptc.getStep(ptcPosition).getB() : 0);
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

}
