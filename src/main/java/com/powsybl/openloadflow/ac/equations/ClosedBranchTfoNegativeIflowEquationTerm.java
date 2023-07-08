package com.powsybl.openloadflow.ac.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.Fortescue;
import net.jafama.FastMath;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;

import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.R2;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ClosedBranchTfoNegativeIflowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchTfoNegativeIflowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                           boolean deriveA1, boolean deriveR1, FlowType flowType) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.NEGATIVE);

        this.flowType = flowType;
    }

    FlowType flowType;

    public double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        return 0;
    }

    public static DenseMatrix getIvector(double g1, double b1, double g2, double b2,
                                   double v1, double ph1,
                                   double v2, double ph2,
                                   double g12, double b12,
                                   double r1, double a1, double r2) {

        // Supposing A2 = 0 and R2 is constant We have:
        // [I1x]   [ R1.cos(A1)  R1.sin(A1)     0       0    ] [ g1+g12  -b1-b12   -g12     b12   ] [ R1.cos(A1) -R1.sin(A1)     0       0    ]  [V1x]
        // [I1y]   [-R1.sin(A1)  R1.cos(A1)     0       0    ] [ b1+b12   g1+g12   -b12    -g12   ] [ R1.sin(A1)  R1.cos(A1)     0       0    ]  [V1y]
        // [I2x] = [    0           0           R2      0    ] [  -g21     b21    g2+g21  -b2-b21 ] [    0           0           R2      0    ]* [V2x]
        // [I2y]   [    0           0           0       R2   ] [  -b21    -g21    b2+b21   g2+g21 ] [    0           0           0       R2   ]  [V2y]

        DenseMatrix mRho = getRhoMatrix(r1, a1, r2);
        DenseMatrix mRhoConjugate = getRhoMatrix(r1, -a1, r2);
        DenseMatrix mY = getFixedYmatrix(g1, b1, g2, b2, g12, b12); // TODO : inlcude g2 and b2

        DenseMatrix mV = getCartesianVoltageVector(v1, ph1, v2, ph2);

        DenseMatrix mTmp1 = mRho.times(mV);
        DenseMatrix mTmp2 = mY.times(mTmp1);
        DenseMatrix mI = mRhoConjugate.times(mTmp2);

        return mI;
    }

    public static DenseMatrix getdIda1dr1(double g1, double b1, double g2, double b2,
                                   double v1, double ph1,
                                   double v2, double ph2,
                                   double g12, double b12,
                                   double r1, double a1, double r2, boolean isDeriveR1) {

        // Supposing A2 = 0 and R2 is constant We have:
        // [dI1x]   [ R1.cos(A1+Pi/2)  R1.sin(A1+Pi/2)     0       0    ] [ g1+g12  -b1-b12   -g12     b12   ] [ R1.cos(A1) -R1.sin(A1)     0       0    ]  [V1x]
        // [dI1y]   [-R1.sin(A1+Pi/2)  R1.cos(A1+Pi/2)     0       0    ] [ b1+b12   g1+g12   -b12    -g12   ] [ R1.sin(A1)  R1.cos(A1)     0       0    ]  [V1y]
        // [dI2x] = [        0                0            R2      0    ] [  -g21     b21    g2+g21  -b2-b21 ] [    0           0           R2      0    ]* [V2x]
        // [dI2y]   [        0                0            0       R2   ] [  -b21    -g21    b2+b21   g2+g21 ] [    0           0           0       R2   ]  [V2y]

        //         [ R1.cos(A1)  R1.sin(A1)  0       0    ] [ g1+g12  -b1-b12   -g12     b12   ] [ R1.cos(A1+Pi/2) -R1.sin(A1+Pi/2)     0       0    ]  [V1x]
        //         [-R1.sin(A1)  R1.cos(A1)  0       0    ] [ b1+b12   g1+g12   -b12    -g12   ] [ R1.sin(A1+Pi/2)  R1.cos(A1+Pi/2)     0       0    ]  [V1y]
        //       + [      0         0        R2      0    ] [  -g21     b21    g2+g21  -b2-b21 ] [        0               0             R2      0    ]* [V2x]
        //         [      0         0        0       R2   ] [  -b21    -g21    b2+b21   g2+g21 ] [        0               0             0       R2   ]  [V2y]

        DenseMatrix mRho = getRhoMatrix(r1, a1, r2);
        DenseMatrix mRhoConjugate = getRhoMatrix(r1, -a1, r2);
        double dr1 = r1;
        double da1 = a1 + FastMath.PI / 2;
        if (isDeriveR1) {
            dr1 = 1;
            da1 = a1;
        }
        DenseMatrix mdRho = getRhoMatrix(dr1, da1, r2);
        DenseMatrix mdRhoConjugate = getRhoMatrix(dr1, -da1, r2);
        DenseMatrix mY = getFixedYmatrix(g1, b1, g2, b2, g12, b12);

        DenseMatrix mV = getCartesianVoltageVector(v1, ph1, v2, ph2);

        DenseMatrix mTmp1 = mRho.times(mV);
        DenseMatrix mTmp2 = mY.times(mTmp1);
        DenseMatrix mTmp3 = mdRhoConjugate.times(mTmp2);

        DenseMatrix mTmp4 = mdRho.times(mV);
        DenseMatrix mTmp5 = mY.times(mTmp4);
        DenseMatrix mTmp6 = mRhoConjugate.times(mTmp5);

        DenseMatrix mdI = new DenseMatrix(4, 0);
        mdI.add(0, 0, mTmp3.get(0, 0) + mTmp6.get(0, 0));
        mdI.add(1, 0, mTmp3.get(1, 0) + mTmp6.get(1, 0));
        mdI.add(2, 0, mTmp3.get(2, 0) + mTmp6.get(2, 0));
        mdI.add(3, 0, mTmp3.get(3, 0) + mTmp6.get(3, 0));

        return mdI;
    }

    public static DenseMatrix getRhoMatrix(double r1, double a1, double r2) {

        Complex rho1 = ComplexUtils.polar2Complex(r1, a1);
        Complex rho2 = ComplexUtils.polar2Complex(r2, 0.);
        ComplexMatrix mRho = new ComplexMatrix(2, 2);
        mRho.set(1, 1, rho1);
        mRho.set(2, 2, rho2);

        return mRho.getRealCartesianMatrix();
    }

    public static DenseMatrix getFixedYmatrix(double g1, double b1, double g2, double b2, double g12, double b12) {

        double g21 = g12;
        double b21 = b12;

        ComplexMatrix mY = new ComplexMatrix(2, 2);
        mY.set(1, 1, new Complex(g1 + g12, b1 + b12));
        mY.set(1, 2, new Complex(-g12, -b12));
        mY.set(2, 1, new Complex(-g21, -b21));
        mY.set(2, 2, new Complex(g2 + g21, b2 + b21));

        return mY.getRealCartesianMatrix();
    }

    public static DenseMatrix getdIdv(double g1, double b1, double g2, double b2,
                                   DenseMatrix mdV,
                                   double g12, double b12,
                                   double r1, double a1, double r2) {

        // Supposing A2 = 0 and R2 is constant We have:
        // [dI1x]   [ R1.cos(A1)  R1.sin(A1)     0       0    ] [ g1+g12  -b1-b12   -g12     b12   ] [ R1.cos(A1) -R1.sin(A1)     0       0    ]  [dV1x]
        // [dI1y]   [-R1.sin(A1)  R1.cos(A1)     0       0    ] [ b1+b12   g1+g12   -b12    -g12   ] [ R1.sin(A1)  R1.cos(A1)     0       0    ]  [dV1y]
        // [dI2x] = [    0           0           R2      0    ] [  -g21     b21    g2+g21  -b2-b21 ] [    0           0           R2      0    ]* [dV2x]
        // [dI2y]   [    0           0           0       R2   ] [  -b21    -g21    b2+b21   g2+g21 ] [    0           0           0       R2   ]  [dV2y]

        DenseMatrix mRho = getRhoMatrix(r1, a1, r2);
        DenseMatrix mRhoConjugate = getRhoMatrix(r1, -a1, r2);
        DenseMatrix mY = getFixedYmatrix(g1, b1, g2, b2, g12, b12);

        DenseMatrix mTmp1 = mRho.times(mdV);
        DenseMatrix mTmp2 = mY.times(mTmp1);
        DenseMatrix mI = mRhoConjugate.times(mTmp2);

        return mI;
    }

    @Override
    public double eval() {
        //System.out.println("Inegative = " + getIvector(g1, b1, g2, b2, v1(), ph1(), v2(), ph2(), g12, b12, r1(), a1(), R2).get(getIndexline(flowType), 0));
        return getIvector(g1, b1, g2, b2, v1(), ph1(), v2(), ph2(), g12, b12, r1(), a1(), R2).get(getIndexline(flowType), 0);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(r1Var) || variable.equals(a1Var)) {
            boolean isDerivR1 = false;
            if (variable.equals(r1Var)) {
                isDerivR1 = true;
            }
            return getdIda1dr1(g1, b1, g2, b2, v1(), ph1(), v2(), ph2(), g12, b12, r1(), a1(), R2, isDerivR1).get(getIndexline(flowType), 0);
        } else {
            DenseMatrix mdV = getdVdx(variable);
            return getdIdv(g1, b1, g2, b2, mdV, g12, b12, r1(), a1(), R2).get(getIndexline(flowType), 0);
        }
    }

    @Override
    public String getName() {
        return "ac_i_tfo_negative_closed";
    }

}
