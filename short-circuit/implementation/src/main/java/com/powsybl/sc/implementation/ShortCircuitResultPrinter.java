/**
 * Copyright (c) 2026, Jean-Baptiste Heyberger
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.sc.util.FeederResult;
import com.powsybl.sc.util.FeedersAtBusResult;
import com.powsybl.shortcircuit.FortescueValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static com.powsybl.sc.extensions.LoadShortCircuit.EPSILON;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitResultPrinter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitResultPrinter.class);

    private final ShortCircuitResult shortCircuitResult;

    ShortCircuitResultPrinter(ShortCircuitResult shortCircuitResult) {
        this.shortCircuitResult = Objects.requireNonNull(shortCircuitResult);
    }

    public void printEquivalentDirectImpedancePu() {
        LOGGER.info("Equivalent Direct Thevenin Impedance (Pu) : Zd = {}", shortCircuitResult.getZd());
    }

    public void printEquivalentDirectImpedance20hzPu() {
        LOGGER.info("Equivalent Direct Thevenin Impedance at 20 Hz (Pu) : Zd = {}", shortCircuitResult.getZd20hz());
    }

    public void printTheveninVoltagePu() {
        LOGGER.info("Thevenin Voltage : Eth  (Pu) = {}", shortCircuitResult.getEth());
    }

    public void printEquivalentHomopolarImpedancePu() {
        LOGGER.info("Equivalent Homopolar Thevenin Impedance (Pu) : Zo = {}", shortCircuitResult.getZh());
    }

    public void printEquivalentHomopolarImpedance20hzPu() {
        LOGGER.info("Equivalent Homopolar Thevenin Impedance at 20 Hz (Pu) : Zo = {}", shortCircuitResult.getZh20hz());
    }

    public void printIk() {
        LOGGER.info("Ik (kA) = {}   => |Ik| (kA) = {}", shortCircuitResult.getIk(), shortCircuitResult.getIk().abs());
    }

    public void printSk() {
        LOGGER.info("Sk (MVA) = {}   => |Sk| (MVA) = {}", shortCircuitResult.getSk(), shortCircuitResult.getSk().abs());
    }

    public void printIpeakc() {
        LOGGER.info("Ipeak(c) (kA) = {}", shortCircuitResult.getIpeakc());
    }

    public void printIfortescuePu() {
        LOGGER.info("Short Circuit Current at Bus (Pu) = {} : I = {}", shortCircuitResult.getLfBus().getId(), getStringFortescueValue(shortCircuitResult.getiFortescue()));
    }

    public void printVfortescuePu() {
        LOGGER.info("Short Circuit Voltage at Bus (Pu) = {} : V = {}", shortCircuitResult.getLfBus().getId(), getStringFortescueValue(shortCircuitResult.getvFortescue()));
    }

    public void printDvAtBussesPu() {
        LOGGER.info("---Bus voltage deltas :  ");
        for (LfBus bus : shortCircuitResult.getLfNetwork().getBuses()) {
            int busNum = bus.getNum();
            LOGGER.info("  -> dV({}) = {} (Pu) ", bus.getId(), getStringFortescueValue(shortCircuitResult.getBusNum2Dv().get(busNum)));
        }
    }

    public void printDIAtBranchPu() {
        LOGGER.info("---Branch currents :  ");
        for (LfBranch branch : shortCircuitResult.getLfNetwork().getBranches()) {
            LOGGER.info("  -> dI1({}) = {} (Pu) ", branch.getId(), getStringFortescueValue(shortCircuitResult.getBranchDi1().get(branch)));
            LOGGER.info("  -> dI2({}) = {} (Pu) ", branch.getId(), getStringFortescueValue(shortCircuitResult.getBranchDi2().get(branch)));
        }
    }

    public void printFeedersPu() {
        LOGGER.info("---Feeders : ");
        for (LfBus bus : shortCircuitResult.getLfNetwork().getBuses()) {
            FeedersAtBusResult feedBus = shortCircuitResult.getFeedersResultDirect().get(bus);
            for (FeederResult fr : feedBus.getBusFeedersResult()) {
                if (fr.getIContribution().abs() > EPSILON) {
                    LOGGER.info("  -> Direct Feeder {} : {} has I (Pu) contribution  =  {}", fr.getFeeder().getFeederType(), fr.getFeeder().getId(), fr.getIContribution());
                }
            }

            if (shortCircuitResult.getShortCircuitFault().getType() == ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND) {
                continue;
            }

            feedBus = shortCircuitResult.getFeedersResultsHomopolar().get(bus);
            for (FeederResult fr : feedBus.getBusFeedersResult()) {
                if (fr.getIContribution().abs() > EPSILON) {
                    LOGGER.info("  -> Homopolar Feeder {} : {} has I (Pu) contribution  =  {}", fr.getFeeder().getFeederType(), fr.getFeeder().getId(), fr.getIContribution());
                }
            }

            feedBus = shortCircuitResult.getFeedersResultsInverse().get(bus);
            for (FeederResult fr : feedBus.getBusFeedersResult()) {
                if (fr.getIContribution().abs() > EPSILON) {
                    LOGGER.info("  -> Inverse Feeder {} : {} has I (Pu) contribution  =  {}", fr.getFeeder().getFeederType(), fr.getFeeder().getId(), fr.getIContribution());
                }
            }
            LOGGER.info("  ---- ");
        }
    }

    public void printShortCircuitResult() {
        LOGGER.info("--------------- {} --------------- ", shortCircuitResult.getShortCircuitFault().getFaultId());
        LOGGER.info("Short Circuit at Bus = {}", shortCircuitResult.getLfBus().getId());
        printEquivalentDirectImpedancePu();
        printEquivalentHomopolarImpedancePu();
        printEquivalentDirectImpedance20hzPu();
        printEquivalentHomopolarImpedance20hzPu();
        printTheveninVoltagePu();
        printIfortescuePu();
        printVfortescuePu();
        printIk();
        printSk();
        printIpeakc();
        if (shortCircuitResult.isVoltageProfileUpdated()) {
            printDvAtBussesPu();
            printDIAtBranchPu();
            printFeedersPu();
        }
    }

    public String getStringFortescueValue(FortescueValue fv) {
        String s = "[ " + fv.getZeroMagnitude() + " < " + fv.getZeroAngle() + " ; "
                        + fv.getPositiveMagnitude() + " < " + fv.getPositiveAngle() + " ; "
                        + fv.getNegativeMagnitude() + " < " + fv.getNegativeAngle() + " ] ";
        return s;
    }
}
