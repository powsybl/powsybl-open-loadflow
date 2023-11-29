/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfAsymBus;
import com.powsybl.openloadflow.network.LfAsymLoad;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadAbcImpedantEquationTerm extends AsymmetricalLoadTerm {

    private static final String PHASE_CONFIG = "Phase config not handled at bus : ";

    public LoadAbcImpedantEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, ComplexPart complexPart, Fortescue.SequenceType sequenceType, LegConnectionType loadConnectionType) {
        super(bus, variableSet, complexPart, sequenceType, loadConnectionType);
        Objects.requireNonNull(variableSet);

        Complex s0 = Complex.ZERO;
        Complex sa = s0;
        Complex sb = s0;
        Complex sc = s0;

        LfAsymLoad asymLoad;
        if (loadConnectionType == LegConnectionType.DELTA) {
            asymLoad = asymBus.getLoadDelta2();
        } else {
            asymLoad = asymBus.getLoadWye2();
        }

        this.sabc = getSabc(sa, sb, sc, asymLoad);

    }

    public static double pq(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType,
                            ComplexMatrix v0V1V2, ComplexMatrix sabc, LegConnectionType loadConnectionType) {

        // We suppose that input for impedant load is S0 at nominal voltage
        // For each phase we have :
        // S = S0 * (V/V0)²
        // S is also S = I* * V  which gives I* = S0 * V / V0²
        //
        // Case of a Wye load connected to a Wye-variables bus :
        //
        // [Ia*]    [Sa0/Va0² * Va]
        // [Ib*] =  [Sb0/Vb0² * Vb]
        // [Ic*]    [Sc0/Vc0² * Vc]
        //
        // Case of a Delta load connected to a Delta Bus
        //
        // Not yet handled
        //
        // Case of a Delta load connected to a Wye-variables Bus
        // We suppose that provided input data is Sab, Sbc, Sca and carried respectively through attributes (Pa,Qa), (Pb,Qb), (Pc,Qc)
        //
        // [Ia*]    [Sab0/Vab0² * Vab - Sca0/Vca0² * Vca]
        // [Ib*] =  [Sbc0/Vbc0² * Vbc - Sab0/Vab0² * Vab]
        // [Ic*]    [Sca0/Vca0² * Vca - Sbc0/Vbc0² * Vbc]
        //

        // For the derivation formula:
        // We derivate the PQ formula with complex matrices:

        // Case of a Wye load connected to a Wye-variables bus :
        //
        // [dIa*]    [Sa0/Va0² * dVa]
        // [dIb*] =  [Sb0/Vb0² * dVb]
        // [dIc*]    [Sc0/Vc0² * dVc]
        //
        // Case of a Delta load connected to a Delta Bus
        //
        // Not yet handled
        //
        // Case of a Delta load connected to a Wye-variables Bus
        // We suppose that provided input data is Sab, Sbc, Sca and carried respectively through attributes (Pa,Qa), (Pb,Qb), (Pc,Qc)
        //
        // [dIa*]    [Sab0/Vab0² * dVab - Sca0/Vca0² * dVca]
        // [dIb*] =  [Sbc0/Vbc0² * dVbc - Sab0/Vab0² * dVab]
        // [dIc*]    [Sca0/Vca0² * dVca - Sbc0/Vbc0² * dVbc]
        //

        LfAsymBus asymBus = bus.getAsym();
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + bus.getId());
        }

        AsymBusVariableType busVariableType = asymBus.getAsymBusVariableType();
        if (busVariableType != AsymBusVariableType.WYE) {
            throw new IllegalStateException("ABC load with delta variables  not yet handled at bus " + bus.getId());
        }

        Complex iZero = Complex.ZERO;
        Complex iPosi;
        Complex iNega = Complex.ZERO;

        Complex vPositiveComplex = v0V1V2.getTerm(2, 1);
        Complex vNegativeComplex = v0V1V2.getTerm(3, 1);
        Complex vZeroComplex = v0V1V2.getTerm(1, 1);

        Complex vA0 = LfAsymBus.getVa0();
        Complex vB0 = LfAsymBus.getVb0();
        Complex vC0 = LfAsymBus.getVc0();

        boolean hasPhaseA = asymBus.isHasPhaseA();
        boolean hasPhaseB = asymBus.isHasPhaseB();
        boolean hasPhaseC = asymBus.isHasPhaseC();

        if (loadConnectionType == LegConnectionType.Y || loadConnectionType == LegConnectionType.Y_GROUNDED) {
            Complex sA = sabc.getTerm(1, 1);
            Complex sB = sabc.getTerm(2, 1);
            Complex sC = sabc.getTerm(3, 1);

            Complex saByVa0Sq = sA.multiply(vA0.reciprocal()).multiply(vA0.reciprocal());
            Complex sbByVb0Sq = sB.multiply(vB0.reciprocal()).multiply(vB0.reciprocal());
            Complex scByVc0Sq = sC.multiply(vC0.reciprocal()).multiply(vC0.reciprocal());

            if (hasPhaseA && hasPhaseB && hasPhaseC) {
                iZero = (vZeroComplex.multiply(saByVa0Sq)).conjugate();
                iPosi = (vPositiveComplex.multiply(sbByVb0Sq)).conjugate();
                iNega = (vNegativeComplex.multiply(scByVc0Sq)).conjugate();
            } else if (hasPhaseB && hasPhaseC) {
                iZero = (vZeroComplex.multiply(sbByVb0Sq)).conjugate();
                iPosi = (vPositiveComplex.multiply(scByVc0Sq)).conjugate();
            } else if (hasPhaseA && hasPhaseC) {
                iZero = (vZeroComplex.multiply(saByVa0Sq)).conjugate();
                iPosi = (vPositiveComplex.multiply(scByVc0Sq)).conjugate();
            } else if (hasPhaseA && hasPhaseB) {
                iZero = (vZeroComplex.multiply(saByVa0Sq)).conjugate();
                iPosi = (vPositiveComplex.multiply(sbByVb0Sq)).conjugate();
            } else if (hasPhaseA) {
                iPosi = (vPositiveComplex.multiply(saByVa0Sq)).conjugate();
            } else if (hasPhaseB) {
                iPosi = (vPositiveComplex.multiply(sbByVb0Sq)).conjugate();
            } else if (hasPhaseC) {
                iPosi = (vPositiveComplex.multiply(scByVc0Sq)).conjugate();
            } else {
                throw new IllegalStateException(PHASE_CONFIG + bus.getId());
            }
        } else if (loadConnectionType == LegConnectionType.DELTA) {

            Complex sAb = sabc.getTerm(1, 1);
            Complex sBc = sabc.getTerm(2, 1);
            Complex sCa = sabc.getTerm(3, 1);

            Complex vAb02 = vA0.add(vB0.multiply(-1.)).multiply(vA0.add(vB0.multiply(-1.)));
            Complex vBc02 = vB0.add(vC0.multiply(-1.)).multiply(vB0.add(vC0.multiply(-1.)));
            Complex vCa02 = vC0.add(vA0.multiply(-1.)).multiply(vC0.add(vA0.multiply(-1.)));

            Complex sabByVab0Sq = sAb.multiply(vAb02.reciprocal());
            Complex sbcByVbc0Sq = sBc.multiply(vBc02.reciprocal());
            Complex scaByVca0Sq = sCa.multiply(vCa02.reciprocal());

            if (hasPhaseA && hasPhaseB && hasPhaseC) {
                Complex vab = vZeroComplex.add(vPositiveComplex.multiply(-1.));
                Complex vbc = vPositiveComplex.add(vNegativeComplex.multiply(-1.));
                Complex vca = vNegativeComplex.add(vZeroComplex.multiply(-1.));
                iZero = (vab.multiply(sabByVab0Sq).add(vca.multiply(scaByVca0Sq).multiply(-1.))).conjugate();
                iPosi = (vbc.multiply(sbcByVbc0Sq).add(vab.multiply(sabByVab0Sq).multiply(-1.))).conjugate();
                iNega = (vca.multiply(scaByVca0Sq).add(vbc.multiply(sbcByVbc0Sq).multiply(-1.))).conjugate();
            } else if (hasPhaseB && hasPhaseC) {
                Complex vbc = vZeroComplex.add(vPositiveComplex.multiply(-1.));
                iZero = (vbc.multiply(sbcByVbc0Sq)).conjugate();
                iPosi = iZero.multiply(-1.);
            } else if (hasPhaseA && hasPhaseC) {
                Complex vca = vPositiveComplex.add(vZeroComplex.multiply(-1.));
                iZero = (vca.multiply(scaByVca0Sq)).conjugate();
                iPosi = iZero.multiply(-1.);
            } else if (hasPhaseA && hasPhaseB) {
                Complex vab = vZeroComplex.add(vPositiveComplex.multiply(-1.));
                iZero = (vab.multiply(sabByVab0Sq)).conjugate();
                iPosi = iZero.multiply(-1.);
            } else {
                throw new IllegalStateException(PHASE_CONFIG + bus.getId());
            }

        } else {
            throw new IllegalStateException("Load connection type not handled at bus : " + bus.getId());
        }

        return getIvalue(bus, complexPart, sequenceType, iZero, iPosi, iNega);
    }

    @Override
    public double eval() {
        return pq(element, complexPart, sequenceType, getVfortescue(), sabc, loadConnectionType);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return pq(element, complexPart, sequenceType, getdVfortescue(variable), sabc, loadConnectionType);
    }

    @Override
    public String getName() {
        return "ac_pq_fortescue_load";
    }

}
