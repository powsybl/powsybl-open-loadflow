/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.math.matrix.ComplexMatrix;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.sc.util.AdmittanceEquationSystem;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;

import java.util.Objects;

/**
 * zero sequence additional attributes
 *
 * Proposed Transformer model :
 *      Ia       Yg    A'  rho                 B'     Yg        Ib        Zga : grounding impedance on A side (in ohms expressed on A side)
 *   A-->--3*Zga--+    +--(())--+--Zoa--+--Zob--+     +--3*ZGb--<--B      Zoa : leakage impedance of A-winding (in ohms expressed on B side)
 *                Y +                   |           + Y                   Zob : leakage impdedance of B-winding (in ohms expressed on B side)
 *                    + D              Zom        + D                     Zom : magnetizing impedance of the two windings (in ohms expressed on B side)
 *                    |                 |         |                       Zgb : grounding impedance on B side (in ohms expressed on B side)
 *                    |                 |         |                       rho might be a complex value
 *                    |    free fluxes \          |
 *                    |                 |         |
 *                  /////             /////     /////                     A' and B' are connected to Yg, Y or D depending on the winding connection type (Y to ground, Y or Delta)
 *
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public class HomopolarModel {

    private static final double EPSILON = 0.000001;

    private final LfBranch branch;

    // values here are expressed in pu (Vnom_B, Sbase = 100.)
    private Complex zo = new Complex(0.); // zo = zoa + zob
    private Complex yom = new Complex(0.); // Zom = 1 / Yom with Yom = gom + j*bom
    private Complex zga = new Complex(0.);
    private Complex zgb = new Complex(0.);

    // if the branch is not a transfo, then it is the correct default behaviour
    private WindingConnectionType leg1ConnectionType = WindingConnectionType.Y_GROUNDED;
    private WindingConnectionType leg2ConnectionType = WindingConnectionType.Y_GROUNDED;

    private boolean freeFluxes = false;

    private ComplexMatrix homopolarAdmittanceMatrix;

    protected HomopolarModel(LfBranch branch) {
        this.branch = Objects.requireNonNull(branch);
    }

    public Complex getYom() {
        return yom;
    }

    public Complex getZo() {
        return zo;
    }

    public ComplexMatrix getHomopolarAdmittanceMatrix() {
        return homopolarAdmittanceMatrix;
    }

    public double getZoInvSquare() {
        return zo.abs() != 0 ? 1 / (zo.abs() * zo.abs()) : 0;
    }

    public static HomopolarModel build(LfBranch branch, AdmittanceEquationSystem.FrequencyType frequencyType) {
        Objects.requireNonNull(branch);

        double freqCoef = 1.0;
        if (frequencyType == AdmittanceEquationSystem.FrequencyType.FREQ_20_HZ) {
            freqCoef = 20. / 50.;
        }

        var piModel = branch.getPiModel();
        Complex z = new Complex(piModel.getR(), piModel.getX());
        Complex yPi1 = new Complex(piModel.getG1(), piModel.getB1());

        var homopolarExtension = new HomopolarModel(branch);

        // default initialization if no homopolar values available
        homopolarExtension.zo = z.divide(AdmittanceConstants.COEF_XO_XD);
        homopolarExtension.yom = yPi1.multiply(AdmittanceConstants.COEF_XO_XD); //TODO : adapt

        if (branch.getBranchType() == LfBranch.BranchType.LINE) {
            // branch is a line and homopolar data available
            ScLine scLine = (ScLine) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            if (scLine != null) {
                Complex zo = scLine.getZo();

                // We propose to scale b and g accordingly with x / x0 and r / r0
                // this assumption could be changed if not relevant
                double gCoeff = 1.;
                double bCoeff = 1.;
                if (Math.abs(zo.getImaginary()) > EPSILON) {
                    bCoeff = z.getImaginary() / zo.getImaginary();
                }
                if (Math.abs(zo.getReal()) > EPSILON) {
                    gCoeff = z.getReal() / zo.getReal();
                }

                homopolarExtension.zo = zo;
                homopolarExtension.yom = new Complex(yPi1.getReal() * gCoeff, yPi1.getImaginary() * bCoeff);
            }
        } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_2) {
            // branch is a 2 windings transformer and homopolar data available
            ScTransfo2W scTransfo = (ScTransfo2W) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            if (scTransfo != null) {

                double kT = 1.0;
                if (branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM) != null) {
                    kT = (Double) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
                }

                Complex zo = scTransfo.getZo();
                Complex zok = zo.multiply(kT);

                // We propose to scale b and g accordingly with x / x0 and r / r0
                // this assumption could be changed if not relevant
                double gCoeff = 1.;
                double bCoeff = 1.;
                if (Math.abs(zo.getImaginary()) > EPSILON) {
                    bCoeff = z.getImaginary() / zo.getImaginary();
                }
                if (Math.abs(zo.getReal()) > EPSILON) {
                    gCoeff = z.getReal() / zo.getReal();
                }

                homopolarExtension.zo = zok.add(scTransfo.getZ1Ground().add(scTransfo.getZ2Ground()));
                homopolarExtension.yom = new Complex(yPi1.getReal() * gCoeff / kT, yPi1.getImaginary() * bCoeff / kT);

                homopolarExtension.leg1ConnectionType = scTransfo.getLeg1ConnectionType();
                homopolarExtension.leg2ConnectionType = scTransfo.getLeg2ConnectionType();
            }
        } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_3) {
            // branch is leg1 of a 3 windings transformer and homopolar data available
            ScTransfo3W scTransfo = (ScTransfo3W) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            ScTransfo3wKt scTransfoKt = (ScTransfo3wKt) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
            if (scTransfoKt != null && scTransfo != null) {
                Complex zo;
                double kTro;
                double kTxo;
                if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1) {
                    zo = scTransfo.getLeg1().getZo();
                    kTro = scTransfoKt.getLeg1().getkTro();
                    kTxo = scTransfoKt.getLeg1().getkTxo();
                    homopolarExtension.leg1ConnectionType = scTransfo.getLeg1().getLegConnectionType();
                    homopolarExtension.freeFluxes = scTransfo.getLeg1().isFreeFluxes();
                } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2) {
                    zo = scTransfo.getLeg2().getZo();
                    kTro = scTransfoKt.getLeg2().getkTro();
                    kTxo = scTransfoKt.getLeg2().getkTxo();
                    homopolarExtension.leg1ConnectionType = scTransfo.getLeg2().getLegConnectionType();
                    homopolarExtension.freeFluxes = scTransfo.getLeg2().isFreeFluxes();
                } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_3) {
                    zo = scTransfo.getLeg3().getZo();
                    kTro = scTransfoKt.getLeg3().getkTro();
                    kTxo = scTransfoKt.getLeg3().getkTxo();
                    homopolarExtension.leg1ConnectionType = scTransfo.getLeg3().getLegConnectionType();
                    homopolarExtension.freeFluxes = scTransfo.getLeg3().isFreeFluxes();
                } else {
                    throw new IllegalArgumentException("Branch " + branch.getId() + " has unknown 3-winding leg number");
                }

                // We propose to scale b and g accordingly with x / x0 and r / r0
                // this assumption could be changed if not relevant
                double gCoeff = 1.;
                double bCoeff = 1.;
                if (Math.abs(zo.getImaginary()) > EPSILON) {
                    bCoeff = z.getImaginary() / zo.getImaginary();
                }
                if (Math.abs(zo.getReal()) > EPSILON) {
                    gCoeff = z.getReal() / zo.getReal();
                }

                homopolarExtension.zo = new Complex(zo.getReal() * kTro, zo.getImaginary() * kTxo);
                homopolarExtension.yom = new Complex(yPi1.getReal() * gCoeff, yPi1.getImaginary() * bCoeff);
            }
        } else {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has a not yet supported type");
        }

        // update zo and yom if frequency is not 50Hz
        homopolarExtension.zo = new Complex(homopolarExtension.zo.getReal(), homopolarExtension.zo.getImaginary() * freqCoef);
        Complex zom = homopolarExtension.yom.reciprocal();
        homopolarExtension.yom = new Complex(zom.getReal(), zom.getImaginary() * freqCoef).reciprocal();

        homopolarExtension.computeHomopolarAdmittanceMatrix();

        return homopolarExtension;
    }

    public void computeHomopolarAdmittanceMatrix() {
        double infiniteImpedanceAdmittance = AdmittanceConstants.INFINITE_IMPEDANCE_ADMITTANCE_VALUE;
        Complex yInfinite = new Complex(infiniteImpedanceAdmittance);
        ComplexMatrix mY = new ComplexMatrix(2, 2);
        mY.set(0, 0, yInfinite); // Default matrix is zero
        mY.set(1, 1, yInfinite);

        var piModel = branch.getPiModel();
        Complex rhoA = ComplexUtils.polar2Complex(piModel.getR1(), Math.toRadians(piModel.getA1()));

        // if the free fluxes option is false, we suppose that if Yom given in input is zero, then Zom = is zero  : TODO : see if there is a more robust way to handle this
        // if the free fluxes option is true, Zom is infinite and Yom is then considered as zero
        Complex zm = new Complex(0.);
        if (yom.abs() != 0.) {
            zm = yom.reciprocal();
        }

        // we suppose that zob = zoa = Zo / 2  : this approximation could be questioned if necessary
        Complex zoa = zo.divide(2.);
        Complex zob = zo.divide(2.);

        // we suppose that all impedance and admittance terms of the homopolar extension are per-unitized on Sbase = 100 MVA and Vnom = Vnom on B side
        if (leg1ConnectionType == WindingConnectionType.Y && leg2ConnectionType == WindingConnectionType.Y
                || leg1ConnectionType == WindingConnectionType.Y && leg2ConnectionType == WindingConnectionType.DELTA
                || leg1ConnectionType == WindingConnectionType.DELTA && leg2ConnectionType == WindingConnectionType.Y
                || leg1ConnectionType == WindingConnectionType.DELTA && leg2ConnectionType == WindingConnectionType.DELTA
                || leg1ConnectionType == WindingConnectionType.Y_GROUNDED && leg2ConnectionType == WindingConnectionType.Y && freeFluxes
                || leg1ConnectionType == WindingConnectionType.Y && leg2ConnectionType == WindingConnectionType.Y_GROUNDED && freeFluxes) {
            // homopolar admittance matrix is zero-Matrix

        } else if (leg1ConnectionType == WindingConnectionType.Y_GROUNDED && leg2ConnectionType == WindingConnectionType.Y) {
            // we suppose that Zoa = Zo given in input for the transformer
            // we suppose that if Yom given in input is zero, then Zom = is zero : if we want to model an open circuit, then set free fluxes to true

            // we have yo11 = 1 / ( 3Zga(pu) + (Zoa(pu)+ Zom(pu))/(rho*e(jAlpha))² )
            // and yo12 = yo22 = yo21 = 0.
            Complex yo11 = zga.multiply(3.).add(zo.add(zm).divide(rhoA.multiply(rhoA))).reciprocal();
            mY.set(0, 0, yo11);

        } else if (leg1ConnectionType == WindingConnectionType.Y && leg2ConnectionType == WindingConnectionType.Y_GROUNDED) {
            // we suppose that zob = Zo given in input for the transformer
            // we suppose that if Yom given in input is zero, then Zom = is zero : if we want to model an open circuit, then set free fluxes to true

            // we have yo22 = 1 / ( 3Zga(pu) + Zob(pu) + Zom(pu) )
            // and yo12 = yo11 = yo21 = 0.
            Complex yo22 = zgb.multiply(3.).add(zo.add(zm)).reciprocal();
            mY.set(1, 1, yo22);
        } else if (leg1ConnectionType == WindingConnectionType.Y_GROUNDED && leg2ConnectionType == WindingConnectionType.DELTA) {

            // we suppose that if Yom given in input is zero, then Zom = is zero : if we want to model an open circuit, then set free fluxes to true

            // we have yo11 = 1 / ( 3Zga(pu) + (Zoa(pu) + 1 / (1/Zom + 1/Zob))/(rho*e(jAlpha))² )
            // and yo12 = yo22 = yo21 = 0.
            // using Ztmp = Zoa(pu) + 1 / (Yom + 1/Zob)
            Complex ztmp = yom.add(zob.reciprocal()).reciprocal().add(zoa);

            if (freeFluxes) {
                // we have Zm = infinity : yo11 = 1 / ( 3Zga(pu) + (Zoa(pu) + Zob(pu))/(rho*e(jAlpha))² )
                ztmp = zoa.add(zob);
            }

            Complex yo11 = ztmp.divide(rhoA.multiply(rhoA)).add(zga.multiply(3.)).reciprocal();
            mY.set(0, 0, yo11);

        } else if (leg1ConnectionType == WindingConnectionType.DELTA && leg2ConnectionType == WindingConnectionType.Y_GROUNDED) {

            // we have yo22 = 1 / ( 3Zga(pu) + Zob(pu) + 1/(1/Zom(pu)+1/Zoa(pu)) )
            // and yo12 = yo11 = yo21 = 0.
            Complex yo22 = zgb.multiply(3.).add(zob.add(zoa.reciprocal().add(yom).reciprocal())).reciprocal();

            if (freeFluxes) {
                // we have Zm = infinity : yo22 = 1 / ( 3Zga(pu) + Zob(pu) + Zoa(pu) )
                // and yo12 = yo11 = yo21 = 0.
                yo22 = zgb.multiply(3.).add(zob).add(zoa).reciprocal();
            }
            mY.set(1, 1, yo22);

        } else if (leg1ConnectionType == WindingConnectionType.Y_GROUNDED && leg2ConnectionType == WindingConnectionType.Y_GROUNDED) {

            Complex yo11;
            Complex yo12;
            Complex yo22;

            if (!freeFluxes) {
                // Case where fluxes are forced, meaning that Zm is not ignored (and could be zero with a direct connection to ground)
                //
                //  k = rho*e(jAlpha))
                //
                // [Ia]                   1                             [ Zom+Zob+3Zgs    -Zom/k        ] [Va]
                // [  ] = ------------------------------------------  * [                               ] [  ]
                // [Ib]   (Zom/k²+3Zga+Zoa/k²)(Zom+Zob+3Zgb)-(Zom/k)²   [   -Zom/k   Zom/k²+3Zga+Zoa/k² ] [Vb]
                // [  ]                                                 [                               ] [  ]
                //
                // Zc = Zom/k²+3Zga+Zoa/k²
                // Zd = Zom+Zob+3Zgb
                // Ze = Zom/k
                //
                // we suppose that if Yom given in input is zero, then Zom = is zero : if we want to model an open circuit, then set free fluxes to true

                Complex zc = zga.multiply(3.).add(zm.add(zoa).divide(rhoA.multiply(rhoA)));
                Complex zd = zm.add(zob).add(zgb.multiply(3.));
                Complex ze = zm.divide(rhoA);

                // this gives :
                // [Ia]         1         [ Zd -Ze ] [Va]
                // [  ] = ------------- * [        ] [  ]
                // [Ib]   Zc * Zd - Ze²   [-Ze  Zc ] [Vb]
                // [  ]                   [        ] [  ]
                //
                // We set ycde = 1 / Zc * Zd - Ze²
                Complex ycde = zc.multiply(zd).add(ze.multiply(ze)).reciprocal();
                yo11 = ycde.multiply(zd);
                yo12 = ycde.multiply(-1.).multiply(ze);
                yo22 = ycde.multiply(zc);

            } else {
                //
                // Zm = infinity
                //  k = rho*e(jAlpha))
                //
                // [Ia]                   1               [    1    -1/k  ] [Va]
                // [  ] = ---------------------------   * [               ] [  ]
                // [Ib]   3Zga+Zoa/k²+Zob/k²+3Zgb/k²)     [   -1/k   1/k² ] [Vb]
                // [  ]                                   [               ] [  ]
                //
                // Zc = 3Zga+Zoa/k²+Zob/k²+3Zgb/k²)

                // Yc = 1 / Zc
                Complex yc = zga.multiply(3.).add(zoa.add(zob).add(zgb.multiply(3.)).divide(rhoA.multiply(rhoA))).reciprocal();

                // this gives :
                // [Ia]                   [ 1   -1/k ] [Va]
                // [  ] =       Yc      * [          ] [  ]
                // [Ib]                   [-1/k  1/k²] [Vb]
                // [  ]                   [          ] [  ]

                yo11 = yc;
                yo12 = yc.multiply(-1.).divide(rhoA);
                yo22 = yc.divide(rhoA.multiply(rhoA));
            }

            mY.set(0, 0, yo11);
            mY.set(1, 1, yo22);
            mY.set(0, 1, yo12);
            mY.set(1, 0, yo12);

        } else {
            throw new IllegalArgumentException("Branch " + branch.getId() + " configuration is not supported yet : " + leg1ConnectionType + " --- " + leg2ConnectionType);
        }

        homopolarAdmittanceMatrix = mY;
    }
}
