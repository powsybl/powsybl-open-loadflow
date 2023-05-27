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
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.Side;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue.SequenceType;
import net.jafama.FastMath;

import java.util.Objects;

/**
 * We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_i-a_j)) * y*_ij_gh * V_gi * V*_hj
 *     where i,j are line's ends i,j included in {1,2}
 *     where g,h are fortescue sequences g,h included in {z,p,n} = {0,1,2}
 *
 *  Expanded formula :
 *  T(i,j,g,h) =     rho_i * rho_j * V_gi * V_hj * yx_ij_gh * cos(a_i - a_j + th_gi - th_hj)
 *                 - rho_i * rho_j * V_gi * V_hj * yy_ij_gh * sin(a_i - a_j + th_gi - th_hj)
 *              -j(  rho_i * rho_j * V_gi * V_hj * yx_ij_gh * sin(a_i - a_j + th_gi - th_hj)
 *                 + rho_i * rho_j * V_gi * V_hj * yy_ij_gh * cos(a_i - a_j + th_gi - th_hj) )
 *
 *  By construction we have :
 *           [ y_11_zz y_11_zp y_11_zn y_12_zz y_12_zp y_12_zn ]
 *           [ y_11_pz y_11_pp y_11_pn y_12_pz y_12_pp y_12_pn ]
 *  [Yzpn] = [ y_11_nz y_11_np y_11_nn y_12_nz y_12_np y_12_nn ]
 *           [ y_21_zz y_21_zp y_21_zn y_22_zz y_22_zp y_22_zn ]
 *           [ y_21_pz y_21_pp y_21_pn y_22_pz y_22_pp y_22_pn ]
 *           [ y_21_nz y_21_np y_21_nn y_22_nz y_22_np y_22_nn ]
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymmetricalClosedBranchCoupledPowerEquationTerm extends AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm {

    public AsymmetricalClosedBranchCoupledPowerEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                            ComplexPart complexPart, Side side, SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, complexPart, side, sequenceType);
    }

    public double dpdv(Side i, Side j, SequenceType g, SequenceType h,
                       Side derivationSide, SequenceType derivationSequence) {
        double tmpVal = y.getX(i, j, g, h) * FastMath.cos(a(i) - a(j) + ph(g, i) - ph(h, j)) + y.getY(i, j, g, h) * FastMath.sin(a(i) - a(j) + ph(g, i) - ph(h, j));
        if (i == derivationSide && g == derivationSequence && j == derivationSide && h == derivationSequence) {
            return 2 * r(i) * r(j) * v(g, i) * tmpVal;
        } else if (i == derivationSide && g == derivationSequence) {
            return r(i) * r(j) * v(h, j) * tmpVal;
        } else if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * v(g, i) * tmpVal;
        }
        return 0;
    }

    public double dpdph(Side i, Side j, SequenceType g, SequenceType h,
                        Side derivationSide, SequenceType derivationSequence) {
        if (i == derivationSide && g == derivationSequence && j == derivationSide && h == derivationSequence) {
            return 0;
        } else if (i == derivationSide && g == derivationSequence) {
            return r(i) * r(j) * v(g, i) * v(h, j) * (y.getX(i, j, g, h) * -FastMath.sin(a(i) - a(j) + ph(g, i) - ph(h, j)) + y.getY(i, j, g, h) * FastMath.cos(a(i) - a(j) + ph(g, i) - ph(h, j)));
        } else if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * v(g, i) * v(h, j) * (y.getX(i, j, g, h) * -FastMath.sin(a(i) - a(j) + ph(g, i) - ph(h, j)) - y.getY(i, j, g, h) * FastMath.cos(a(i) - a(j) + ph(g, i) - ph(h, j)));
        }
        return 0;
    }

    public double dqdv(Side i, Side j, SequenceType g, SequenceType h,
                       Side derivationSide, SequenceType derivationSequence) {
        double tmpVal = y.getX(i, j, g, h) * FastMath.sin(a(i) - a(j) + ph(g, i) - ph(h, j)) - y.getY(i, j, g, h) * FastMath.cos(a(i) - a(j) + ph(g, i) - ph(h, j));
        if (i == derivationSide && g == derivationSequence && j == derivationSide && h == derivationSequence) {
            return 2 * r(i) * r(j) * v(g, i) * tmpVal;
        } else if (i == derivationSide && g == derivationSequence) {
            return r(i) * r(j) * v(h, j) * tmpVal;
        } else if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * v(g, i) * tmpVal;
        }
        return 0;
    }

    public double dqdph(Side i, Side j, SequenceType g, SequenceType h,
                        Side derivationSide, SequenceType derivationSequence) {
        if (i == derivationSide && g == derivationSequence && j == derivationSide && h == derivationSequence) {
            return 0;
        } else if (i == derivationSide && g == derivationSequence) {
            return r(i) * r(j) * v(g, i) * v(h, j) * (y.getX(i, j, g, h) * FastMath.cos(a(i) - a(j) + ph(g, i) - ph(h, j)) + y.getY(i, j, g, h) * FastMath.sin(a(i) - a(j) + ph(g, i) - ph(h, j)));
        } else if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * v(g, i) * v(h, j) * (y.getX(i, j, g, h) * -FastMath.cos(a(i) - a(j) + ph(g, i) - ph(h, j)) + y.getY(i, j, g, h) * FastMath.sin(a(i) - a(j) + ph(g, i) - ph(h, j)));
        }
        return 0;
    }

    public double p(Side i, Side j, SequenceType g, SequenceType h) {
        return r(i) * r(j) * v(g, i) * v(h, j) * (y.getX(i, j, g, h) * FastMath.cos(a(i) - a(j) + ph(g, i) - ph(h, j))
                + y.getY(i, j, g, h) * FastMath.sin(a(i) - a(j) + ph(g, i) - ph(h, j)));
    }

    public double q(Side i, Side j, SequenceType g, SequenceType h) {
        return r(i) * r(j) * v(g, i) * v(h, j) * (y.getX(i, j, g, h) * FastMath.sin(a(i) - a(j) + ph(g, i) - ph(h, j))
                - y.getY(i, j, g, h) * FastMath.cos(a(i) - a(j) + ph(g, i) - ph(h, j)));
    }

    public double dp(Side i, Side j, SequenceType g, SequenceType h,
                     Side derivationSide, SequenceType derivationSequence, boolean phase) {
        if (phase) {
            return dpdph(i, j, g, h, derivationSide, derivationSequence);
        } else {
            return dpdv(i, j, g, h, derivationSide, derivationSequence);
        }
    }

    public double dq(Side i, Side j, SequenceType g, SequenceType h,
                     Side derivationSide, SequenceType derivationSequence, boolean phase) {
        if (phase) {
            return dqdph(i, j, g, h, derivationSide, derivationSequence);
        } else {
            return dqdv(i, j, g, h, derivationSide, derivationSequence);
        }
    }

    public double s() {
        Side i;
        Side j;
        if (side == Side.ONE) {
            i = Side.ONE;
            j = Side.TWO;
        } else {
            i = Side.TWO;
            j = Side.ONE;
        }

        if (complexPart == ComplexPart.REAL) { // P
            return p(i, i, sequenceType, SequenceType.ZERO)
                    + p(i, i, sequenceType, SequenceType.POSITIVE)
                    + p(i, i, sequenceType, SequenceType.NEGATIVE)
                    + p(i, j, sequenceType, SequenceType.ZERO)
                    + p(i, j, sequenceType, SequenceType.POSITIVE)
                    + p(i, j, sequenceType, SequenceType.NEGATIVE);
        } else { // Q
            return q(i, i, sequenceType, SequenceType.ZERO)
                    + q(i, i, sequenceType, SequenceType.POSITIVE)
                    + q(i, i, sequenceType, SequenceType.NEGATIVE)
                    + q(i, j, sequenceType, SequenceType.ZERO)
                    + q(i, j, sequenceType, SequenceType.POSITIVE)
                    + q(i, j, sequenceType, SequenceType.NEGATIVE);
        }
    }

    public double ds(Variable<AcVariableType> variable) {
        Side i;
        Side j;
        if (side == Side.ONE) {
            i = Side.ONE;
            j = Side.TWO;
        } else {
            i = Side.TWO;
            j = Side.ONE;
        }
        Side derivationSide = getSide(variable);
        SequenceType derivationSequence = getSequenceType(variable);
        boolean phase = isPhase(variable);

        // iDerivative is the side of "variable" that is used for derivation
        if (complexPart == ComplexPart.REAL) {
            // dP
            return dp(i, i, sequenceType, SequenceType.ZERO, derivationSide, derivationSequence, phase)
                    + dp(i, i, sequenceType, SequenceType.POSITIVE, derivationSide, derivationSequence, phase)
                    + dp(i, i, sequenceType, SequenceType.NEGATIVE, derivationSide, derivationSequence, phase)
                    + dp(i, j, sequenceType, SequenceType.ZERO, derivationSide, derivationSequence, phase)
                    + dp(i, j, sequenceType, SequenceType.POSITIVE, derivationSide, derivationSequence, phase)
                    + dp(i, j, sequenceType, SequenceType.NEGATIVE, derivationSide, derivationSequence, phase);
        } else {
            // dQ
            return dq(i, i, sequenceType, SequenceType.ZERO, derivationSide, derivationSequence, phase)
                    + dq(i, i, sequenceType, SequenceType.POSITIVE, derivationSide, derivationSequence, phase)
                    + dq(i, i, sequenceType, SequenceType.NEGATIVE, derivationSide, derivationSequence, phase)
                    + dq(i, j, sequenceType, SequenceType.ZERO, derivationSide, derivationSequence, phase)
                    + dq(i, j, sequenceType, SequenceType.POSITIVE, derivationSide, derivationSequence, phase)
                    + dq(i, j, sequenceType, SequenceType.NEGATIVE, derivationSide, derivationSequence, phase);
        }
    }

    @Override
    public double eval() {
        return s();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        return ds(variable);
    }

    @Override
    public String getName() {
        return "ac_pq_coupled_closed";
    }
}
