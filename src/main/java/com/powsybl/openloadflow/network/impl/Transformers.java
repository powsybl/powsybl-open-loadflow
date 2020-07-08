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

import java.util.Objects;

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

    public static double getRatio(TwoWindingsTransformer twt, Integer rtcPosition, Integer ptcPosition) {
        double rho = twt.getRatedU2() / twt.getRatedU1();
        if (twt.getRatioTapChanger() != null) {
            Objects.requireNonNull(rtcPosition);
            rho *= twt.getRatioTapChanger().getStep(rtcPosition).getRho();
        }
        if (twt.getPhaseTapChanger() != null) {
            Objects.requireNonNull(ptcPosition);
            rho *= twt.getPhaseTapChanger().getStep(ptcPosition).getRho();
        }
        return rho;
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

    /**
     * Get shift angle on side 1 of a two windings transformer.
     */
    public static double getAngle(TwoWindingsTransformer twt) {
        return getAngle(twt, getCurrentPosition(twt.getPhaseTapChanger()));
    }

    public static double getAngle(TwoWindingsTransformer twt, Integer position) {
        if (twt.getPhaseTapChanger() != null) {
            Objects.requireNonNull(position);
            return Math.toRadians(twt.getPhaseTapChanger().getStep(position).getAlpha());
        }
        return 0f;
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
     * Get the nominal series resistance of a two windings transformer, located on side 2.
     */
    public static double getR(TwoWindingsTransformer twt) {
        return getR(twt.getR(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()));
    }

    public static double getR(TwoWindingsTransformer twt, Integer rtcPosition, Integer ptcPosition) {
        return getR(twt.getR(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), rtcPosition, ptcPosition);
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
     * Get the nominal series reactance of a two windings transformer, located on side 2.
     */
    public static double getX(TwoWindingsTransformer twt) {
        return getX(twt.getX(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()));
    }

    public static double getX(TwoWindingsTransformer twt, Integer rtcPosition, Integer ptcPosition) {
        return getX(twt.getX(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), rtcPosition, ptcPosition);
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
     * Get the nominal magnetizing conductance of a two windings transformer, located on side 2.
     */
    public static double getG1(TwoWindingsTransformer twt, boolean twtSplitShuntAdmittance) {
        return getG1(twt, getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()), twtSplitShuntAdmittance);
    }

    public static double getG1(TwoWindingsTransformer twt, Integer rtcPosition, Integer ptcPosition, boolean twtSplitShuntAdmittance) {
        return getG1(twtSplitShuntAdmittance ? twt.getG() / 2 : twt.getG(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), rtcPosition, ptcPosition);
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
     * Get the nominal magnetizing susceptance of a two windings transformer, located on side 2.
     */
    public static double getB1(TwoWindingsTransformer twt, boolean twtSplitShuntAdmittance) {
        return getB1(twt, getCurrentPosition(twt.getRatioTapChanger()), getCurrentPosition(twt.getPhaseTapChanger()), twtSplitShuntAdmittance);
    }

    public static double getB1(TwoWindingsTransformer twt, Integer rtcPosition, Integer ptcPosition, boolean twtSplitShuntAdmittance) {
        return getB1(twtSplitShuntAdmittance ? twt.getB() / 2 : twt.getB(), twt.getRatioTapChanger(), twt.getPhaseTapChanger(), rtcPosition, ptcPosition);
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
    public static int findTapPosition(RatioTapChanger rtc, double rho) {
        for (int position = rtc.getLowTapPosition(); position <= rtc.getHighTapPosition(); position++) {
            if (Math.abs(rho - rtc.getStep(position).getRho()) < EPS_ALPHA) {
                return position;
            }
        }
        throw new PowsyblException("No tap position found (should never happen)");
    }

}
