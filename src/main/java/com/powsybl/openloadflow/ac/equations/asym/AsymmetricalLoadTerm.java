/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfAsymBus;
import com.powsybl.openloadflow.network.LfAsymLoad;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.extensions.AbcPhaseType;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
class AsymmetricalLoadTerm extends AbstractElementEquationTerm<LfBus, AcVariableType, AcEquationType> {

    // positive
    protected final Variable<AcVariableType> vVar;

    protected final Variable<AcVariableType> phVar;

    // negative
    protected final Variable<AcVariableType> vVarNegative;

    protected final Variable<AcVariableType> phVarNegative;

    // zero
    protected final Variable<AcVariableType> vVarZero;

    protected final Variable<AcVariableType> phVarZero;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected final ComplexPart complexPart;

    protected final Fortescue.SequenceType sequenceType;

    protected final WindingConnectionType loadConnectionType; // how 3 phase loads are connected between each other

    private final AsymBusVariableType busVariableType; // variables available at bus (Wye variables, Va, Vb, Vc or Delta variables : Vab, Vbc and Vca = -Vab - Vbc)

    protected final LfAsymBus asymBus;

    protected ComplexMatrix sabc;

    protected AsymmetricalLoadTerm(LfBus bus, VariableSet<AcVariableType> variableSet, ComplexPart complexPart, Fortescue.SequenceType sequenceType, WindingConnectionType loadConnectionType) {
        super(bus);
        Objects.requireNonNull(variableSet);
        this.complexPart = Objects.requireNonNull(complexPart);
        this.sequenceType = Objects.requireNonNull(sequenceType);
        this.asymBus = bus.getAsym();

        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + bus.getId());
        }

        this.loadConnectionType = Objects.requireNonNull(loadConnectionType);
        this.busVariableType = Objects.requireNonNull(asymBus.getAsymBusVariableType());

        int nbPhases = 3 - asymBus.getNbMissingPhases();

        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        phVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI);

        variables.add(vVar);
        variables.add(phVar);

        if (busVariableType == AsymBusVariableType.WYE) {
            if (nbPhases == 3) {
                vVarNegative = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_NEGATIVE);
                phVarNegative = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_NEGATIVE);
                variables.add(vVarNegative);
                variables.add(phVarNegative);
            } else {
                vVarNegative = null;
                phVarNegative = null;
            }

            if (nbPhases > 1) {
                vVarZero = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_ZERO);
                phVarZero = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_ZERO);
                variables.add(vVarZero);
                variables.add(phVarZero);
            } else {
                vVarZero = null;
                phVarZero = null;
            }
        } else {
            // delta config
            vVarZero = null;
            phVarZero = null;

            vVarNegative = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_NEGATIVE);
            phVarNegative = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_NEGATIVE);
        }
    }

    public void setSabc() {
        Complex s0 = new Complex(element.getLoadTargetP(), element.getLoadTargetQ());
        Complex sa = s0;
        Complex sb = s0;
        Complex sc = s0;

        LfAsymLoad asymLoad;
        if (loadConnectionType == WindingConnectionType.DELTA) {
            asymLoad = asymBus.getLoadDelta0();
        } else {
            asymLoad = asymBus.getLoadWye0();
        }

        this.sabc = getSabc(sa, sb, sc, asymLoad);
    }

    public double ph(Fortescue.SequenceType g) {
        switch (g) {
            case ZERO:
                if (phVarZero == null) {
                    return 0.;
                }
                return sv.get(phVarZero.getRow());

            case POSITIVE:
                return sv.get(phVar.getRow());

            case NEGATIVE:
                if (phVarNegative == null) {
                    return 0.;
                }
                return sv.get(phVarNegative.getRow());

            default:
                throw new IllegalStateException("Unknown Phi variable at bus: " + element.getId());
        }
    }

    public double v(Fortescue.SequenceType g) {
        switch (g) {
            case ZERO:
                if (vVarZero == null) {
                    return 0.;
                }
                return sv.get(vVarZero.getRow());

            case POSITIVE:
                return sv.get(vVar.getRow());

            case NEGATIVE:
                if (vVarNegative == null) {
                    return 0.;
                }
                return sv.get(vVarNegative.getRow());

            default:
                throw new IllegalStateException("Unknown V variable at bus: " + element.getId());
        }
    }

    protected static ComplexMatrix getdVvector(LfBus bus, AsymBusVariableType busVariableType, Variable<AcVariableType> derVariable, double vo, double pho, double vd, double phd, double vi, double phi) {

        // computation of dV0/dx , dV1/dx, dV2/dx
        Complex dV0 = Complex.ZERO;
        Complex dV1 = Complex.ZERO;
        Complex dV2 = Complex.ZERO;

        if (derVariable.getType() == AcVariableType.BUS_V) {
            dV1 = new Complex(Math.cos(phd), Math.sin(phd));
        } else if (derVariable.getType() == AcVariableType.BUS_V_ZERO) {
            dV0 = new Complex(Math.cos(pho), Math.sin(pho));
            if (busVariableType == AsymBusVariableType.DELTA) {
                dV0 = Complex.ZERO;
            }
        } else if (derVariable.getType() == AcVariableType.BUS_V_NEGATIVE) {
            dV2 = new Complex(Math.cos(phi), Math.sin(phi));
        } else if (derVariable.getType() == AcVariableType.BUS_PHI) {
            dV1 = new Complex(vd * -Math.sin(phd), vd * Math.cos(phd));
        } else if (derVariable.getType() == AcVariableType.BUS_PHI_ZERO) {
            dV0 = new Complex(vo * -Math.sin(pho), vo * Math.cos(pho));
            if (busVariableType == AsymBusVariableType.DELTA) {
                dV0 = Complex.ZERO;
            }
        } else if (derVariable.getType() == AcVariableType.BUS_PHI_NEGATIVE) {
            dV2 = new Complex(vi * -Math.sin(phi), vi * Math.cos(phi));
        } else {
            throw new IllegalStateException("Unknown derivation variable: " + derVariable + " at bus : " + bus.getId());
        }

        ComplexMatrix dv0V1V2 = new ComplexMatrix(3, 1);
        dv0V1V2.set(1, 1, dV0);
        dv0V1V2.set(2, 1, dV1);
        dv0V1V2.set(3, 1, dV2);

        return dv0V1V2;
    }

    public static ComplexMatrix vFortescue(double vZero, double phZero, double vPositive, double phPositive, double vNegative, double phNegative) {

        Vector2D positiveSequence = Fortescue.getCartesianFromPolar(vPositive, phPositive);
        Vector2D zeroSequence = Fortescue.getCartesianFromPolar(vZero, phZero);
        Vector2D negativeSequence = Fortescue.getCartesianFromPolar(vNegative, phNegative);

        Complex vPositiveComplex = new Complex(positiveSequence.getX(), positiveSequence.getY());
        Complex vNegativeComplex = new Complex(negativeSequence.getX(), negativeSequence.getY());
        Complex vZeroComplex = new Complex(zeroSequence.getX(), zeroSequence.getY());

        ComplexMatrix v0V1V2 = new ComplexMatrix(3, 1);
        v0V1V2.set(1, 1, vZeroComplex);
        v0V1V2.set(2, 1, vPositiveComplex);
        v0V1V2.set(3, 1, vNegativeComplex);

        return v0V1V2;

    }

    public ComplexMatrix getVfortescue() {
        return vFortescue(v(Fortescue.SequenceType.ZERO), ph(Fortescue.SequenceType.ZERO),
                v(Fortescue.SequenceType.POSITIVE), ph(Fortescue.SequenceType.POSITIVE),
                v(Fortescue.SequenceType.NEGATIVE), ph(Fortescue.SequenceType.NEGATIVE));
    }

    public ComplexMatrix getdVfortescue(Variable<AcVariableType> variable) {
        return getdVvector(element, busVariableType, variable,
                v(Fortescue.SequenceType.ZERO), ph(Fortescue.SequenceType.ZERO),
                v(Fortescue.SequenceType.POSITIVE), ph(Fortescue.SequenceType.POSITIVE),
                v(Fortescue.SequenceType.NEGATIVE), ph(Fortescue.SequenceType.NEGATIVE));
    }

    public static ComplexMatrix getSabc(Complex sa, Complex sb, Complex sc, LfAsymLoad asymLoad) {
        ComplexMatrix sabc = new ComplexMatrix(3, 1);
        sabc.set(1, 1, getAddedS(sa, asymLoad, AbcPhaseType.A));
        sabc.set(2, 1, getAddedS(sb, asymLoad, AbcPhaseType.B));
        sabc.set(3, 1, getAddedS(sc, asymLoad, AbcPhaseType.C));
        return sabc;
    }

    public static Complex getAddedS(Complex s, LfAsymLoad asymLoad, AbcPhaseType phaseType) {
        if (asymLoad != null) {
            if (phaseType == AbcPhaseType.A) {
                return s.add(asymLoad.getTotalDeltaSa());
            }
            if (phaseType == AbcPhaseType.B) {
                return s.add(asymLoad.getTotalDeltaSb());
            }
            if (phaseType == AbcPhaseType.C) {
                return s.add(asymLoad.getTotalDeltaSc());
            }
        }
        return s;
    }

    public static double getIvalue(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType,
                                   Complex iZero, Complex iPosi, Complex iNega) {
        LfAsymBus asymBus = bus.getAsym();
        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? iZero.getReal() : iZero.getImaginary(); // IxZero or IyZero

            case POSITIVE:
                // check if positive sequence is modelled as P,Q or Ix,Iy
                if (asymBus.isPositiveSequenceAsCurrent()) {
                    return complexPart == ComplexPart.REAL ? iPosi.getReal() : iPosi.getImaginary(); // IxZero or IyZero
                } else {
                    throw new IllegalStateException("positive sequence as Power not yet implemented in ABC load : " + bus.getId());
                }

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? iNega.getReal() : iNega.getImaginary(); // IxNegative or IyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    @Override
    public double eval() {
        return 0.;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return 0.;
    }

    @Override
    public String getName() {
        return "ac_pq_fortescue_load";
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
