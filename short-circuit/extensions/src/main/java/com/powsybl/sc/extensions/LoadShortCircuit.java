/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Load;
import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadShortCircuit extends AbstractExtension<Load> {

    public static final double EPSILON = 0.000001;

    public static class AsynchronousMachineLoadData {

        private final double ratedMechanicalP;
        private final double ratedPowerFactor; // cosPhi
        private final double ratedS;
        private final double ratedU;
        private final double efficiency;
        private final double iaIrRatio;
        private final int polePairNumber;
        private final double rxLockedRotorRatio;

        public AsynchronousMachineLoadData(double ratedMechanicalP, double ratedPowerFactor, double ratedS, double ratedU,
                                           double efficiency, double iaIrRatio, int polePairNumber, double rxLockedRotorRatio) {
            this.ratedMechanicalP = ratedMechanicalP;
            this.ratedPowerFactor = ratedPowerFactor;
            this.ratedS = ratedS;
            this.ratedU = ratedU;
            this.efficiency = efficiency;
            this.iaIrRatio = iaIrRatio;
            this.polePairNumber = polePairNumber;
            this.rxLockedRotorRatio = rxLockedRotorRatio;
        }

        public double getEfficiency() {
            return efficiency;
        }

        public double getRatedU() {
            return ratedU;
        }

        public double getIaIrRatio() {
            return iaIrRatio;
        }

        public double getRatedMechanicalP() {
            return ratedMechanicalP;
        }

        public double getRatedPowerFactor() {
            return ratedPowerFactor;
        }

        public double getRatedS() {
            return ratedS;
        }

        public double getRxLockedRotorRatio() {
            return rxLockedRotorRatio;
        }

        public int getPolePairNumber() {
            return polePairNumber;
        }

    }

    public static final String NAME = "loadShortCircuit";

    public enum LoadShortCircuitType {
        UNKNOWN,
        CONSTANT_LOAD,
        ASYNCHRONOUS_MACHINE
    }

    private final LoadShortCircuitType loadShortCircuitType;
    private final AsynchronousMachineLoadData asynchronousMachineLoadData;

    @Override
    public String getName() {
        return NAME;
    }

    public LoadShortCircuit(Load load, LoadShortCircuitType loadShortCircuitType, AsynchronousMachineLoadData asynchronousMachineLoadData) {
        super(load);
        this.loadShortCircuitType = loadShortCircuitType;
        this.asynchronousMachineLoadData = asynchronousMachineLoadData;
    }

    public Complex getZeqLoad() {

        double xn = 0.;
        double rn = 0.;

        // Default case
        var load = getExtendable();
        double pLoad = load.getP0();
        double qLoad = load.getQ0();
        double uNom = load.getTerminal().getVoltageLevel().getNominalV();
        double s2 = pLoad * pLoad + qLoad * qLoad;
        // using formula P(MW) = Re(Z) * |V|² / |Z|² and Q(MVAR) = Im(Z) * |V|² / |Z|²  or  Z = |V|² / (P-jQ)
        // We compute the equivalent impedance at Unom

        if (s2 > EPSILON) {
            xn = qLoad * uNom * uNom / s2;
            rn = pLoad * uNom * uNom / s2;
        }

        // Case where the load is an asynchronous machine defined by the extension attributes
        if (loadShortCircuitType == LoadShortCircuit.LoadShortCircuitType.ASYNCHRONOUS_MACHINE) {

            if (asynchronousMachineLoadData == null) {
                throw new PowsyblException("Load '" + load.getId() + "' is an asynchronous machine without associated data, therefore equivalent admittance could not be generated ");
            }

            double ratedMechanicalPower = asynchronousMachineLoadData.getRatedMechanicalP();
            double ratedPowerFactor = asynchronousMachineLoadData.getRatedPowerFactor(); // cosPhi
            double ratedS = asynchronousMachineLoadData.getRatedS();
            double ratedU = asynchronousMachineLoadData.getRatedU();
            double efficiency = asynchronousMachineLoadData.getEfficiency() / 100.; // conversion from percentages
            double iaIrRatio = asynchronousMachineLoadData.getIaIrRatio();
            double rxLockedRotorRatio = asynchronousMachineLoadData.getRxLockedRotorRatio();
            int polePairNumber = asynchronousMachineLoadData.getPolePairNumber();

            // Zn = 1/(Ilr/Irm) * Urm / (sqrt3 * Irm) = 1/(Ilr/Irm) * Urm² / (Prm / (efficiency * cosPhi))
            // Xn = Zn / sqrt(1+ (Rm/Xm)²)
            double zn = 1. / iaIrRatio * ratedU * ratedU / (ratedMechanicalPower / (efficiency * ratedPowerFactor));
            xn = zn / Math.sqrt(rxLockedRotorRatio * rxLockedRotorRatio + 1.);
            rn = xn * rxLockedRotorRatio;

        }

        return new Complex(rn, xn);
    }
}
