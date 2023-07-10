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
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import java.util.Objects;

public class LoadAbcImpedantEquationTerm extends AbstractAsymmetricalLoadTerm {

    public LoadAbcImpedantEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, ComplexPart complexPart, Fortescue.SequenceType sequenceType, LegConnectionType loadConnectionType) {
        super(bus, variableSet, complexPart, sequenceType, loadConnectionType);
        Objects.requireNonNull(variableSet);

        Complex s0 = new Complex(0., 0.);
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
                            double vZero, double phZero, double vPositive, double phPositive, double vNegative, double phNegative, ComplexMatrix sabc, LegConnectionType loadConnectionType) {

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

        LfAsymBus asymBus = bus.getAsym();
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + bus.getId());
        }

        AsymBusVariableType busVariableType = asymBus.getAsymBusVariableType();
        if (busVariableType != AsymBusVariableType.WYE) {
            throw new IllegalStateException("ABC load with delta variables  not yet handled at bus " + bus.getId());
        }

        Complex iZero = new Complex(0., 0.);
        Complex iPosi;
        Complex iNega = new Complex(0., 0.);

        Vector2D positiveSequence = Fortescue.getCartesianFromPolar(vPositive, phPositive);
        Vector2D zeroSequence = Fortescue.getCartesianFromPolar(vZero, phZero);
        Vector2D negativeSequence = Fortescue.getCartesianFromPolar(vNegative, phNegative);

        Complex vPositiveComplex = new Complex(positiveSequence.getX(), positiveSequence.getY());
        Complex vNegativeComplex = new Complex(negativeSequence.getX(), negativeSequence.getY());
        Complex vZeroComplex = new Complex(zeroSequence.getX(), zeroSequence.getY());

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
            } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
                iZero = (vZeroComplex.multiply(sbByVb0Sq)).conjugate();
                iPosi = (vPositiveComplex.multiply(scByVc0Sq)).conjugate();
            } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
                iZero = (vZeroComplex.multiply(saByVa0Sq)).conjugate();
                iPosi = (vPositiveComplex.multiply(scByVc0Sq)).conjugate();
            } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
                iZero = (vZeroComplex.multiply(saByVa0Sq)).conjugate();
                iPosi = (vPositiveComplex.multiply(sbByVb0Sq)).conjugate();
            } else if (hasPhaseA && !hasPhaseB && !hasPhaseC) {
                iPosi = (vPositiveComplex.multiply(saByVa0Sq)).conjugate();
            } else if (!hasPhaseA && hasPhaseB && !hasPhaseC) {
                iPosi = (vPositiveComplex.multiply(sbByVb0Sq)).conjugate();
            } else if (!hasPhaseA && !hasPhaseB && hasPhaseC) {
                iPosi = (vPositiveComplex.multiply(scByVc0Sq)).conjugate();
            } else {
                throw new IllegalStateException("Phase config not handled at bus : " + bus.getId());
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
            } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
                Complex vbc = vZeroComplex.add(vPositiveComplex.multiply(-1.));
                iZero = (vbc.multiply(sbcByVbc0Sq)).conjugate();
                iPosi = iZero.multiply(-1.);
            } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
                Complex vca = vPositiveComplex.add(vZeroComplex.multiply(-1.));
                iZero = (vca.multiply(scaByVca0Sq)).conjugate();
                iPosi = iZero.multiply(-1.);
            } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
                Complex vab = vZeroComplex.add(vPositiveComplex.multiply(-1.));
                iZero = (vab.multiply(sabByVab0Sq)).conjugate();
                iPosi = iZero.multiply(-1.);
            } else {
                throw new IllegalStateException("Phase config not handled at bus : " + bus.getId());
            }

        } else {
            throw new IllegalStateException("Load connection type not handled at bus : " + bus.getId());
        }

        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? iZero.getReal() : iZero.getImaginary(); // IxZero or IyZero

            case POSITIVE:
                // check if positive sequence is modelled as P,Q or Ix,Iy
                if (asymBus.isPositiveSequenceAsCurrent()) {
                    return complexPart == ComplexPart.REAL ? iPosi.getReal() : iPosi.getImaginary(); // IxZero or IyZero
                } else {
                    // todo : handle delta impedant case
                    throw new IllegalStateException("positive sequence as Power not yet implemented in ABC load : " + bus.getId());
                }

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? iNega.getReal() : iNega.getImaginary(); // IxNegative or IyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    public static double dpq(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType, Variable<AcVariableType> derVariable, double vo, double pho, double vd, double phd, double vi, double phi, ComplexMatrix sabc, LegConnectionType loadConnectionType) {
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

        ComplexMatrix v0V1V2 = AbstractAsymmetricalLoadTerm.getdVvector(bus, busVariableType, derVariable, vo, pho, vd, phd, vi, phi);
        // computation of dV0/dx , dV1/dx, dV2/dx
        Complex dV0 = v0V1V2.getTerm(1, 1);
        Complex dV1 = v0V1V2.getTerm(2, 1);
        Complex dV2 = v0V1V2.getTerm(3, 1);

        if (busVariableType != AsymBusVariableType.WYE) {
            throw new IllegalStateException("ABC load with delta variables  not yet handled at bus " + bus.getId());
        }

        Complex sA = sabc.getTerm(1, 1);
        Complex sB = sabc.getTerm(2, 1);
        Complex sC = sabc.getTerm(3, 1);

        Complex vA0 = LfAsymBus.getVa0();
        Complex vB0 = LfAsymBus.getVb0();
        Complex vC0 = LfAsymBus.getVc0();

        boolean hasPhaseA = asymBus.isHasPhaseA();
        boolean hasPhaseB = asymBus.isHasPhaseB();
        boolean hasPhaseC = asymBus.isHasPhaseC();

        Complex diZero = new Complex(0., 0.);
        Complex diPosi;
        Complex diNega = new Complex(0., 0.);

        if (loadConnectionType == LegConnectionType.Y || loadConnectionType == LegConnectionType.Y_GROUNDED) {
            Complex saByVa0Sq = sA.multiply(vA0.reciprocal()).multiply(vA0.reciprocal());
            Complex sbByVb0Sq = sB.multiply(vB0.reciprocal()).multiply(vB0.reciprocal());
            Complex scByVc0Sq = sC.multiply(vC0.reciprocal()).multiply(vC0.reciprocal());

            if (hasPhaseA && hasPhaseB && hasPhaseC) {
                diZero = (dV0.multiply(saByVa0Sq)).conjugate();
                diPosi = (dV1.multiply(sbByVb0Sq)).conjugate();
                diNega = (dV2.multiply(scByVc0Sq)).conjugate();
            } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
                diZero = (dV0.multiply(sbByVb0Sq)).conjugate();
                diPosi = (dV1.multiply(scByVc0Sq)).conjugate();
            } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
                diZero = (dV0.multiply(saByVa0Sq)).conjugate();
                diPosi = (dV1.multiply(scByVc0Sq)).conjugate();
            } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
                diZero = (dV0.multiply(saByVa0Sq)).conjugate();
                diPosi = (dV1.multiply(sbByVb0Sq)).conjugate();
            } else if (hasPhaseA && !hasPhaseB && !hasPhaseC) {
                diPosi = (dV1.multiply(saByVa0Sq)).conjugate();
            } else if (!hasPhaseA && hasPhaseB && !hasPhaseC) {
                diPosi = (dV1.multiply(sbByVb0Sq)).conjugate();
            } else if (!hasPhaseA && !hasPhaseB && hasPhaseC) {
                diPosi = (dV1.multiply(scByVc0Sq)).conjugate();
            } else {
                throw new IllegalStateException("Phase config not handled at bus : " + bus.getId());
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
                Complex dvab = dV0.add(dV1.multiply(-1.));
                Complex dvbc = dV1.add(dV2.multiply(-1.));
                Complex dvca = dV2.add(dV0.multiply(-1.));
                diZero = (dvab.multiply(sabByVab0Sq).add(dvca.multiply(scaByVca0Sq).multiply(-1.))).conjugate();
                diPosi = (dvbc.multiply(sbcByVbc0Sq).add(dvab.multiply(sabByVab0Sq).multiply(-1.))).conjugate();
                diNega = (dvca.multiply(scaByVca0Sq).add(dvbc.multiply(sbcByVbc0Sq).multiply(-1.))).conjugate();
            } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
                Complex dvbc = dV0.add(dV1.multiply(-1.));
                diZero = (dvbc.multiply(sbcByVbc0Sq)).conjugate();
                diPosi = diZero.multiply(-1.);
            } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
                Complex dvca = dV1.add(dV0.multiply(-1.));
                diZero = (dvca.multiply(scaByVca0Sq)).conjugate();
                diPosi = diZero.multiply(-1.);
            } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
                Complex dvab = dV0.add(dV1.multiply(-1.));
                diZero = (dvab.multiply(sabByVab0Sq)).conjugate();
                diPosi = diZero.multiply(-1.);
            } else {
                throw new IllegalStateException("Phase config not handled at bus : " + bus.getId());
            }
        } else {
            throw new IllegalStateException("Load connection type not handled at bus : " + bus.getId());
        }

        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? diZero.getReal() : diZero.getImaginary(); // IxZero or IyZero

            case POSITIVE:
                // check if positive sequence is modelled as P,Q or Ix,Iy
                if (asymBus.isPositiveSequenceAsCurrent()) {
                    return complexPart == ComplexPart.REAL ? diPosi.getReal() : diPosi.getImaginary(); // IxPositive or IyPositive
                } else {
                    // return complexPart == ComplexPart.REAL ? diPosi.conjugate().multiply(vPositiveComplex).getReal() : diPosi.conjugate().multiply(vPositiveComplex).getImaginary(); // Ppositive or Qpositive
                    throw new IllegalStateException("positive sequence as Power not yet implemented in ABC load : " + bus.getId());
                }

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? diNega.getReal() : diNega.getImaginary(); // IxNegative or IyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    @Override
    public double eval() {
        return pq(element, complexPart, sequenceType,
                v(Fortescue.SequenceType.ZERO), ph(Fortescue.SequenceType.ZERO),
                v(Fortescue.SequenceType.POSITIVE), ph(Fortescue.SequenceType.POSITIVE),
                v(Fortescue.SequenceType.NEGATIVE), ph(Fortescue.SequenceType.NEGATIVE), sabc, loadConnectionType);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return dpq(element, complexPart, sequenceType, variable,
                v(Fortescue.SequenceType.ZERO), ph(Fortescue.SequenceType.ZERO),
                v(Fortescue.SequenceType.POSITIVE), ph(Fortescue.SequenceType.POSITIVE),
                v(Fortescue.SequenceType.NEGATIVE), ph(Fortescue.SequenceType.NEGATIVE), sabc, loadConnectionType);
    }

    @Override
    public String getName() {
        return "ac_pq_fortescue_load";
    }

}
