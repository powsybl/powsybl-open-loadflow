package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.Side;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue.SequenceType;

import java.util.Objects;

/**
 *  We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_j-a_i)) * y*_ij_gh * V_hj
 *     where i,j are line's ends i,j included in {1,2}
 *     where g,h are fortescue sequences g,h included in {o,d,i} (o = zero = homopolar = 0, d = direct = positive = 1, i = inverse = negative = 2)
 *
 *  Expanded formula :
 *  T(i,j,g,h) =     rho_i * rho_j * V_hj * yx_ij_gh * cos(a_j - a_i + th_hj)
 *                 - rho_i * rho_j * V_hj * yy_ij_gh * sin(a_j - a_i + th_hj)
 *              +j(  rho_i * rho_j * V_hj * yx_ij_gh * sin(a_j - a_i + th_hj)
 *                 + rho_i * rho_j * V_hj * yy_ij_gh * cos(a_j - a_i + th_hj) )
 *
 *  By construction we have :
 *           [ y_11_oo y_11_od y_11_oi y_12_oo y_12_od y_12_oi ]
 *           [ y_11_do y_11_dd y_11_di y_12_do y_12_dd y_12_di ]
 *  [Y012] = [ y_11_io y_11_id y_11_ii y_12_io y_12_id y_12_ii ]
 *           [ y_21_oo y_21_od y_21_oi y_22_oo y_22_od y_22_oi ]
 *           [ y_21_do y_21_dd y_21_di y_22_do y_22_dd y_22_di ]
 *           [ y_21_io y_21_id y_21_ii y_22_io y_22_id y_22_ii ]
 *
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymmetricalClosedBranchCoupledCurrentEquationTerm extends AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm {

    public AsymmetricalClosedBranchCoupledCurrentEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                              ComplexPart complexPart, Side side, SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, complexPart, side, sequenceType);
    }

    public double ix(Side i, Side j, SequenceType g, SequenceType h) {
        return r(i) * r(j) * v(h, j) * (y.getX(i, j, g, h) * Math.cos(a(j) - a(i) + ph(h, j))
                - y.getY(i, j, g, h) * Math.sin(a(j) - a(i) + ph(h, j)));
    }

    public double iy(Side i, Side j, SequenceType g, SequenceType h) {
        return r(i) * r(j) * v(h, j) * (y.getX(i, j, g, h) * Math.sin(a(j) - a(i) + ph(h, j))
                + y.getY(i, j, g, h) * Math.cos(a(j) - a(i) + ph(h, j)));
    }

    public double dixdv(Side i, Side j, SequenceType g, SequenceType h,
                        Side derivationSide, SequenceType derivationSequence) {
        if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * (y.getX(i, j, g, h) * Math.cos(a(j) - a(i) + ph(h, j)) - y.getY(i, j, g, h) * Math.sin(a(j) - a(i) + ph(h, j)));
        }
        return 0;
    }

    public double dixdph(Side i, Side j, SequenceType g, SequenceType h,
                         Side derivationSide, SequenceType derivationSequence) {
        if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * v(h, j) * (-y.getX(i, j, g, h) * Math.sin(a(j) - a(i) + ph(h, j)) - y.getY(i, j, g, h) * Math.cos(a(j) - a(i) + ph(h, j)));
        }
        return 0;
    }

    public double dix(Side i, Side j, SequenceType g, SequenceType h,
                      Side derivationSide, SequenceType derivationSequence, boolean phase) {
        if (phase) {
            return dixdph(i, j, g, h, derivationSide, derivationSequence);
        } else {
            return dixdv(i, j, g, h, derivationSide, derivationSequence);
        }
    }

    public double diydv(Side i, Side j, SequenceType g, SequenceType h,
                        Side derivationSide, SequenceType derivationSequence) {
        if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * (y.getX(i, j, g, h) * Math.sin(a(j) - a(i) + ph(h, j)) + y.getY(i, j, g, h) * Math.cos(a(j) - a(i) + ph(h, j)));
        }
        return 0;
    }

    public double diydph(Side i, Side j, SequenceType g, SequenceType h,
                         Side derivationSide, SequenceType derivationSequence) {
        if (j == derivationSide && h == derivationSequence) {
            return r(i) * r(j) * v(h, j) * (y.getX(i, j, g, h) * Math.cos(a(j) - a(i) + ph(h, j)) - y.getY(i, j, g, h) * Math.sin(a(j) - a(i) + ph(h, j)));
        }
        return 0;
    }

    public double diy(Side i, Side j, SequenceType g, SequenceType h, Side derivationSide, SequenceType derivationSequence, boolean phase) {
        if (phase) {
            return diydph(i, j, g, h, derivationSide, derivationSequence);
        } else {
            return diydv(i, j, g, h, derivationSide, derivationSequence);
        }
    }

    public double di(Variable<AcVariableType> variable) {
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
        Side i;
        Side j;
        if (side == Side.ONE) {
            i = Side.ONE;
            j = Side.TWO;
        } else {
            i = Side.TWO;
            j = Side.ONE;
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
