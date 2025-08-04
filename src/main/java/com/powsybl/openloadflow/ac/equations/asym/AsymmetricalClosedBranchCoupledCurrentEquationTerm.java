/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue.SequenceType;
import net.jafama.FastMath;

import java.util.Objects;

/**
 *  We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_j-a_i)) * y*_ij_gh * V_hj
 *     where i,j are line's ends i,j included in {1,2}
 *     where g,h are fortescue sequences g,h included in {z, p, n} = {0,1,2} (z = zero = 0, p = positive = 1, n = negative = 2)
 *
 *  Expanded formula :
 *  T(i,j,g,h) =     rho_i * rho_j * V_hj * yx_ij_gh * cos(a_j - a_i + th_hj)
 *                 - rho_i * rho_j * V_hj * yy_ij_gh * sin(a_j - a_i + th_hj)
 *              +j(  rho_i * rho_j * V_hj * yx_ij_gh * sin(a_j - a_i + th_hj)
 *                 + rho_i * rho_j * V_hj * yy_ij_gh * cos(a_j - a_i + th_hj) )
 *
 *  By construction we have :
 *           [ y_11_zz y_11_zp y_11_zn y_12_zz y_12_zp y_12_zn ]
 *           [ y_11_pz y_11_pp y_11_pn y_12_pz y_12_pp y_12_pn ]
 *  [Yzpn] = [ y_11_nz y_11_np y_11_nn y_12_nz y_12_np y_12_nn ]
 *           [ y_21_zz y_21_zp y_21_zn y_22_zz y_22_zp y_22_zn ]
 *           [ y_21_pz y_21_pp y_21_pn y_22_pz y_22_pp y_22_pn ]
 *           [ y_21_nz y_21_np y_21_nn y_22_nz y_22_np y_22_nn ]
 *
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 */
public class AsymmetricalClosedBranchCoupledCurrentEquationTerm extends AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm {

    public AsymmetricalClosedBranchCoupledCurrentEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                              ComplexPart complexPart, TwoSides side, SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, complexPart, side, sequenceType);
    }

    public double ix(TwoSides i, TwoSides j, SequenceType g, SequenceType h) {
        return r(i) * r(j) * v(h, j) * (y.getX(i, j, g, h) * FastMath.cos(a(j) - a(i) + ph(h, j))
                - y.getY(i, j, g, h) * FastMath.sin(a(j) - a(i) + ph(h, j)));
    }

    public double iy(TwoSides i, TwoSides j, SequenceType g, SequenceType h) {
        return r(i) * r(j) * v(h, j) * (y.getX(i, j, g, h) * FastMath.sin(a(j) - a(i) + ph(h, j))
                + y.getY(i, j, g, h) * FastMath.cos(a(j) - a(i) + ph(h, j)));
    }

    public double dixdv(TwoSides i, TwoSides j, SequenceType g, SequenceType h,
                        TwoSides derivationSide, SequenceType derivationSequence) {
        if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * (y.getX(i, j, g, h) * FastMath.cos(a(j) - a(i) + ph(h, j)) - y.getY(i, j, g, h) * FastMath.sin(a(j) - a(i) + ph(h, j)));
        }
        return 0;
    }

    public double dixdph(TwoSides i, TwoSides j, SequenceType g, SequenceType h,
                         TwoSides derivationSide, SequenceType derivationSequence) {
        if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * v(h, j) * (-y.getX(i, j, g, h) * FastMath.sin(a(j) - a(i) + ph(h, j)) - y.getY(i, j, g, h) * FastMath.cos(a(j) - a(i) + ph(h, j)));
        }
        return 0;
    }

    public double dix(TwoSides i, TwoSides j, SequenceType g, SequenceType h,
                      TwoSides derivationSide, SequenceType derivationSequence, boolean phase) {
        if (phase) {
            return dixdph(i, j, g, h, derivationSide, derivationSequence);
        } else {
            return dixdv(i, j, g, h, derivationSide, derivationSequence);
        }
    }

    public double diydv(TwoSides i, TwoSides j, SequenceType g, SequenceType h,
                        TwoSides derivationSide, SequenceType derivationSequence) {
        if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * (y.getX(i, j, g, h) * FastMath.sin(a(j) - a(i) + ph(h, j)) + y.getY(i, j, g, h) * FastMath.cos(a(j) - a(i) + ph(h, j)));
        }
        return 0;
    }

    public double diydph(TwoSides i, TwoSides j, SequenceType g, SequenceType h,
                         TwoSides derivationSide, SequenceType derivationSequence) {
        if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * v(h, j) * (y.getX(i, j, g, h) * FastMath.cos(a(j) - a(i) + ph(h, j)) - y.getY(i, j, g, h) * FastMath.sin(a(j) - a(i) + ph(h, j)));
        }
        return 0;
    }

    public double diy(TwoSides i, TwoSides j, SequenceType g, SequenceType h, TwoSides derivationSide, SequenceType derivationSequence, boolean phase) {
        if (phase) {
            return diydph(i, j, g, h, derivationSide, derivationSequence);
        } else {
            return diydv(i, j, g, h, derivationSide, derivationSequence);
        }
    }

    public double di(Variable<AcVariableType> variable) {
        TwoSides i;
        TwoSides j;
        if (side == TwoSides.ONE) {
            i = TwoSides.ONE;
            j = TwoSides.TWO;
        } else {
            i = TwoSides.TWO;
            j = TwoSides.ONE;
        }
        TwoSides derivationSide = getSide(variable);
        SequenceType derivationSequence = getSequenceType(variable);
        boolean phase = isPhase(variable);

        // iDerivative is the side of "variable" that is used for derivation
        if (complexPart == ComplexPart.REAL) {
            // dIx
            return dix(i, i, sequenceType, SequenceType.ZERO, derivationSide, derivationSequence, phase)
                    + dix(i, i, sequenceType, SequenceType.POSITIVE, derivationSide, derivationSequence, phase)
                    + dix(i, i, sequenceType, SequenceType.NEGATIVE, derivationSide, derivationSequence, phase)
                    + dix(i, j, sequenceType, SequenceType.ZERO, derivationSide, derivationSequence, phase)
                    + dix(i, j, sequenceType, SequenceType.POSITIVE, derivationSide, derivationSequence, phase)
                    + dix(i, j, sequenceType, SequenceType.NEGATIVE, derivationSide, derivationSequence, phase);
        } else {
            // dIy
            return diy(i, i, sequenceType, SequenceType.ZERO, derivationSide, derivationSequence, phase)
                    + diy(i, i, sequenceType, SequenceType.POSITIVE, derivationSide, derivationSequence, phase)
                    + diy(i, i, sequenceType, SequenceType.NEGATIVE, derivationSide, derivationSequence, phase)
                    + diy(i, j, sequenceType, SequenceType.ZERO, derivationSide, derivationSequence, phase)
                    + diy(i, j, sequenceType, SequenceType.POSITIVE, derivationSide, derivationSequence, phase)
                    + diy(i, j, sequenceType, SequenceType.NEGATIVE, derivationSide, derivationSequence, phase);
        }
    }

    public double i() {
        TwoSides i;
        TwoSides j;
        if (side == TwoSides.ONE) {
            i = TwoSides.ONE;
            j = TwoSides.TWO;
        } else {
            i = TwoSides.TWO;
            j = TwoSides.ONE;
        }

        if (complexPart == ComplexPart.REAL) { // Ix
            return ix(i, i, sequenceType, SequenceType.ZERO)
                    + ix(i, i, sequenceType, SequenceType.POSITIVE)
                    + ix(i, i, sequenceType, SequenceType.NEGATIVE)
                    + ix(i, j, sequenceType, SequenceType.ZERO)
                    + ix(i, j, sequenceType, SequenceType.POSITIVE)
                    + ix(i, j, sequenceType, SequenceType.NEGATIVE);
        } else { // Iy
            return iy(i, i, sequenceType, SequenceType.ZERO)
                    + iy(i, i, sequenceType, SequenceType.POSITIVE)
                    + iy(i, i, sequenceType, SequenceType.NEGATIVE)
                    + iy(i, j, sequenceType, SequenceType.ZERO)
                    + iy(i, j, sequenceType, SequenceType.POSITIVE)
                    + iy(i, j, sequenceType, SequenceType.NEGATIVE);
        }
    }

    @Override
    public double eval() {
        return i();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        return di(variable);
    }

    @Override
    public String getName() {
        return "ac_ixiy_coupled_closed";
    }

}
