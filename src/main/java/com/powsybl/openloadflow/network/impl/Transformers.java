/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.RatioTapChanger;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoWindingsTransformer;

/**
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
public final class Transformers {

    private Transformers() {
    }

    /**
     * Get ratio on side 1 of a two windings transformer.
     */
    public static double getRatio(TwoWindingsTransformer twt) {
        double rho = twt.getRatedU2() / twt.getRatedU1();
        if (twt.getRatioTapChanger() != null) {
            rho *= twt.getRatioTapChanger().getCurrentStep().getRho();
        }
        if (twt.getPhaseTapChanger() != null) {
            rho *= twt.getPhaseTapChanger().getCurrentStep().getRho();
        }
        return rho;
    }

    /**
     * Get ratio on network side of a three windings transformer leg.
     */
    public static double getRatioLeg(ThreeWindingsTransformer twt, ThreeWindingsTransformer.Leg leg) {
        double rho = twt.getRatedU0() / leg.getRatedU();
        if (leg.getRatioTapChanger() != null) {
            rho *= leg.getRatioTapChanger().getCurrentStep().getRho();
        }
        if (leg.getPhaseTapChanger() != null) {
            rho *= leg.getPhaseTapChanger().getCurrentStep().getRho();
        }
        return rho;
    }

    /**
     * Get shift angle on side 1 of a two windings transformer.
     */
    public static double getAngle(TwoWindingsTransformer twt) {
        return twt.getPhaseTapChanger() != null ? Math.toRadians(twt.getPhaseTapChanger().getCurrentStep().getAlpha()) : 0f;
    }

    /**
     * Get shift angle on network side of a three windings transformer leg.
     */
    public static double getAngleLeg(ThreeWindingsTransformer.Leg leg) {
        return leg.getPhaseTapChanger() != null ? Math.toRadians(leg.getPhaseTapChanger().getCurrentStep().getAlpha()) : 0f;
    }

    private static double getValue(double initialValue, double rtcStepValue, double ptcStepValue) {
        return initialValue * (1 + rtcStepValue / 100) * (1 + ptcStepValue / 100);
    }

    /**
     * Get the nominal series resistance of a two windings transformer, located on side 2.
     */
    public static double getR(TwoWindingsTransformer twt) {
        return getX(twt.getR(), twt.getRatioTapChanger(), twt.getPhaseTapChanger());
    }

    /**
     * Get the nominal series resistance of a three windings transformer leg, located on bus star side.
     */
    public static double getR(ThreeWindingsTransformer.Leg leg) {
        return getR(leg.getR(), leg.getRatioTapChanger(), leg.getPhaseTapChanger());
    }

    private static double getR(double r, RatioTapChanger rtc, PhaseTapChanger ptc) {
        return getValue(r,
                rtc != null ? rtc.getCurrentStep().getR() : 0,
                ptc != null ? ptc.getCurrentStep().getR() : 0);
    }

    /**
     * Get the nominal series reactance of a two windings transformer, located on side 2.
     */
    public static double getX(TwoWindingsTransformer twt) {
        return getX(twt.getX(), twt.getRatioTapChanger(), twt.getPhaseTapChanger());
    }

    /**
     * Get the nominal series reactance of a three windings transformer leg, located on bus star side.
     */
    public static double getX(ThreeWindingsTransformer.Leg leg) {
        return getX(leg.getX(), leg.getRatioTapChanger(), leg.getPhaseTapChanger());
    }

    private static double getX(double x, RatioTapChanger rtc, PhaseTapChanger ptc) {
        return getValue(x,
                rtc != null ? rtc.getCurrentStep().getX() : 0,
                ptc != null ? ptc.getCurrentStep().getX() : 0);
    }

    /**
     * Get the nominal magnetizing conductance of a two windings transformer, located on side 2.
     */
    public static double getG1(TwoWindingsTransformer twt, boolean specificCompatibility) {
        return getG1(specificCompatibility ? twt.getG() / 2 : twt.getG(), twt.getRatioTapChanger(), twt.getPhaseTapChanger());
    }

    /**
     * Get the nominal magnetizing conductance of a three windings transformer, located on bus star side.
     */
    public static double getG1(ThreeWindingsTransformer.Leg leg, boolean specificCompatibility) {
        return getG1(specificCompatibility ? leg.getG() / 2 : leg.getG(), leg.getRatioTapChanger(), leg.getPhaseTapChanger());
    }

    public static double getG1(TwoWindingsTransformer twt) {
        return getG1(twt, false);
    }

    public static double getG1(ThreeWindingsTransformer.Leg leg) {
        return getG1(leg, false);
    }

    private static double getG1(double g1, RatioTapChanger rtc, PhaseTapChanger ptc) {
        return getValue(g1,
                rtc != null ? rtc.getCurrentStep().getG() : 0,
                ptc != null ? ptc.getCurrentStep().getG() : 0);
    }

    /**
     * Get the nominal magnetizing susceptance of a two windings transformer, located on side 2.
     */
    public static double getB1(TwoWindingsTransformer twt, boolean specificCompatibility) {
        return getB1(specificCompatibility ? twt.getB() / 2 : twt.getB(), twt.getRatioTapChanger(), twt.getPhaseTapChanger());
    }

    /**
     * Get the nominal magnetizing susceptance of a three windings transformer, located on bus star side.
     */
    public static double getB1(ThreeWindingsTransformer.Leg leg, boolean specificCompatibility) {
        return getB1(specificCompatibility ? leg.getB() / 2 : leg.getB(), leg.getRatioTapChanger(), leg.getPhaseTapChanger());
    }

    public static double getB1(TwoWindingsTransformer twt) {
        return getB1(twt, false);
    }

    public static double getB1(ThreeWindingsTransformer.Leg leg) {
        return getB1(leg, false);
    }

    private static double getB1(double b1, RatioTapChanger rtc, PhaseTapChanger ptc) {
        return getValue(b1,
                rtc != null ? rtc.getCurrentStep().getB() : 0,
                ptc != null ? ptc.getCurrentStep().getB() : 0);
    }
}
