package com.powsybl.openloadflow.ac.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.extensions.AsymTransfo2W;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;

import java.util.Objects;

public class ClosedBranchTfoZeroIflowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchTfoZeroIflowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                    boolean deriveA1, boolean deriveR1, ClosedBranchTfoNegativeIflowEquationTerm.FlowType flowType) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.NEGATIVE);

        this.flowType = flowType;

        AsymTransfo2W asymTransfo2W = (AsymTransfo2W) branch.getProperty(AsymTransfo2W.PROPERTY_ASYMMETRICAL);
        if (asymTransfo2W == null) {
            throw new IllegalStateException("Branch : " + branch.getId() + " has no asymmetric extension but is required here ");
        }

        this.leg1ConnectionType = asymTransfo2W.getLeg1ConnectionType();
        this.leg2ConnectionType = asymTransfo2W.getLeg2ConnectionType();
        this.isFreeFluxes = asymTransfo2W.isFreeFluxes();

        this.zG1 = new Complex(asymTransfo2W.getR1Ground(), asymTransfo2W.getX1Ground());
        this.zG2 = new Complex(asymTransfo2W.getR2Ground(), asymTransfo2W.getX2Ground());
        double epsilon = 0.00000001;
        if (isFreeFluxes || ((Math.abs(g1) < epsilon && Math.abs(b1) < epsilon) || (Math.abs(b2) < epsilon && Math.abs(g2) < epsilon))) {
            // magnetizing circuit is open or Y1 or Y2 are zero, leading Ym to zero
            this.z0T1 = new Complex(0, 0);
            this.z0T2 = new Complex(asymTransfo2W.getRo(), asymTransfo2W.getXo());
            this.y0m = new Complex(g1 + g2, b1 + b2);
            if (y0m.abs() < epsilon) {
                // if y0m is very small, we consifer we are in free flux config
                isFreeFluxes = true;
            }
        } else {
            // triangle to star Kennelly transformation to have a T model for the transformer
            Complex z1 = (new Complex(g1, b1)).reciprocal();
            Complex z2 = (new Complex(g2, b2)).reciprocal();
            Complex z12 = new Complex(asymTransfo2W.getRo(), asymTransfo2W.getXo());

            Complex sumZ = z1.add(z2.add(z12));
            if (sumZ.abs() < epsilon) {
                throw new IllegalArgumentException("Branch " + branch.getId() + " has homopolar input data that will bring singularity in the Newton Raphson resolution");
            }

            this.z0T1 = (z1.multiply(z12)).divide(sumZ);
            this.y0m = ((z1.multiply(z2)).divide(sumZ)).reciprocal();
            this.z0T2 = (z2.multiply(z12)).divide(sumZ);
        }
    }

    private ClosedBranchTfoNegativeIflowEquationTerm.FlowType flowType;
    private Complex z0T1;
    private Complex z0T2;
    private Complex y0m;
    private Complex zG1;
    private Complex zG2;
    private LegConnectionType leg1ConnectionType;
    private LegConnectionType leg2ConnectionType;
    private boolean isFreeFluxes;

    public double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        return 0;
    }

    public static DenseMatrix getYgYgForcedFluxesImpedanceMatrix(Complex z0T1, Complex z0T2, Complex y0m, Complex zG1, Complex zG2, double r1, boolean getInverse) {

        Complex z11 = (zG1.multiply(3.)).add((z0T1.add(y0m.reciprocal())).multiply(1 / (r1 * r1)));
        Complex z12 = (y0m.reciprocal()).multiply(1 / r1);
        Complex z22 = (zG2.multiply(3.)).add(z0T2.add(y0m.reciprocal()));

        DenseMatrix mZ = getMatrixFromBloc44(z11, z22, z12);

        if (getInverse) {
            DenseMatrix b44 = getId44();
            mZ.decomposeLU().solve(b44);
            return b44;
        }
        return mZ;
    }

    public static DenseMatrix getYgYgForcedFluxesDeriveAdmittanceMatrixdr1(Complex z0T1, Complex z0T2, Complex y0m, Complex zG1, Complex zG2, double r1) {
        // using the matrix formula:
        // d(Inv(A(x)))                d(A(x))
        // ------------ = - Inv(A(x)).---------.Inv(A(x))
        //     dx                        dx
        Complex minusdz11 = (z0T1.add(y0m.reciprocal())).multiply(2 / (r1 * r1 * r1));
        Complex minusdz12 = (y0m.reciprocal()).multiply(1 / (r1 * r1));
        Complex minusdz22 = new Complex(0, 0);

        DenseMatrix mMinusdZdr1 = getMatrixFromBloc44(minusdz11, minusdz22, minusdz12);
        DenseMatrix mY = getYgYgForcedFluxesImpedanceMatrix(z0T1, z0T2, y0m, zG1, zG2, r1, true);

        return mY.times(mMinusdZdr1.times(mY)); // [dY]

    }

    public static DenseMatrix getYgYgFreeFluxesImpedanceMatrix(Complex z0T1, Complex z0T2, Complex zG1, Complex zG2, double r1) {
        // F(x) = 1 / (A + B.x²) = yeq
        Complex yeq = ((zG1.multiply(r1 * r1).add(zG2)).multiply(3)).add(z0T1.add(z0T2)).reciprocal();
        Complex y11 = yeq.multiply(r1 * r1);
        Complex y12 = yeq.multiply(-r1);
        Complex y22 = yeq;

        return getMatrixFromBloc44(y11, y22, y12); // [Y]
    }

    public static DenseMatrix getYgYgFreeFluxesDeriveAdmittanceMatrixdr1(Complex z0T1, Complex z0T2, Complex zG1, Complex zG2, double r1) {
        // F(x) = 1 / (a + b.x²) = yeq
        // F'(x) = -2.b.x.(a+b.x²)^-2 = dYeq
        Complex a = zG2.multiply(3).add(z0T1.add(z0T2));
        Complex b = zG1.multiply(3);
        Complex yeq = (b.multiply(r1 * r1).add(a)).reciprocal();

        Complex dyTerm1 = b.multiply(-2 * r1);
        Complex dyTerm2 = a.add(b.multiply(r1 * r1)).reciprocal();
        Complex dYeq = dyTerm1.multiply(dyTerm2.multiply(dyTerm2));

        Complex dy11 = (yeq.multiply(2 * r1)).add(dYeq.multiply(r1 * r1));
        Complex dy12 = (yeq.multiply(-1)).add(dYeq.multiply(-r1));
        Complex dy22 = dYeq;

        return getMatrixFromBloc44(dy11, dy22, dy12); // [dY]
    }

    public static DenseMatrix getAdmittanceMatrix(Complex z0T1, Complex z0T2, Complex y0m, Complex zG1, Complex zG2,
                                           double r1, LegConnectionType leg1Type, LegConnectionType leg2Type, boolean isFreeFluxes) {
        if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.Y_GROUNDED) {

            if (isFreeFluxes) {
                return getYgYgFreeFluxesImpedanceMatrix(z0T1, z0T2, zG1, zG2, r1);
            } else {
                return getYgYgForcedFluxesImpedanceMatrix(z0T1, z0T2, y0m, zG1, zG2, r1, true);
            }
        } else {
            double epsilon = 0.00000001;
            Complex y11 = new Complex(epsilon, epsilon);
            Complex y22 = new Complex(epsilon, epsilon);
            Complex y12 = new Complex(epsilon, epsilon);
            if (leg1Type == LegConnectionType.DELTA && leg2Type == LegConnectionType.Y_GROUNDED) {
                Complex tmp1 = z0T2.add(y0m.add(z0T1.reciprocal()).reciprocal());
                y22 = (zG2.multiply(3).add(tmp1)).reciprocal();
            } else if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.DELTA) {
                Complex tmp2 = z0T1.add(y0m.add(z0T2.reciprocal()).reciprocal()).multiply(1 / (r1 * r1));
                y11 = (zG1.multiply(3).add(tmp2)).reciprocal();
            } else if (leg1Type == LegConnectionType.Y && leg2Type == LegConnectionType.Y_GROUNDED && !isFreeFluxes) {
                Complex tmp3 = z0T2.add(y0m.reciprocal());
                y22 = (zG2.multiply(3).add(tmp3)).reciprocal();
            } else if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.Y && !isFreeFluxes) {
                Complex tmp4 = z0T1.add(y0m.reciprocal()).multiply(1 / (r1 * r1));
                y11 = (zG1.multiply(3).add(tmp4)).reciprocal();
            }
            // if windings are in another configuration we consider it is a zero admittance matrix
            return getMatrixFromBloc44(y11, y22, y12);
        }
    }

    public static DenseMatrix getDeriveAdmittanceMatrixdr1(Complex z0T1, Complex z0T2, Complex y0m, Complex zG1, Complex zG2,
                                                           double r1, LegConnectionType leg1Type, LegConnectionType leg2Type, boolean isFreeFluxes) {

        if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.Y_GROUNDED) {
            if (isFreeFluxes) {
                return getYgYgFreeFluxesDeriveAdmittanceMatrixdr1(z0T1, z0T2, zG1, zG2, r1);
            } else {
                return getYgYgForcedFluxesDeriveAdmittanceMatrixdr1(z0T1, z0T2, y0m, zG1, zG2, r1);
            }
        } else {
            Complex dy11;
            double epsilon = 0.000001;
            Complex zeroComplex = new Complex(epsilon, 0);
            // F(x) = 1 / (a + b/x²) = y11
            // F'(x) = 2.b.x^-3.(a+b/x²)^-2 = dY11
            Complex b;
            Complex a = zG1.multiply(3);
            if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.DELTA) {
                b = z0T1.add((y0m.add(z0T2.reciprocal())).reciprocal());
            } else if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.Y && !isFreeFluxes) {
                b = z0T1.add(y0m.reciprocal());
            } else {
                // if windings are in another configuration we consider it is a zero admittance matrix
                return getMatrixFromBloc44(zeroComplex, zeroComplex, zeroComplex);
            }

            Complex dyTerm1 = b.multiply(2 / (r1 * r1 * r1));
            Complex dyTerm2 = ((b.multiply(1 / (r1 * r1))).add(a)).reciprocal();
            dy11 = dyTerm1.multiply(dyTerm2.multiply(dyTerm2));

            return getMatrixFromBloc44(dy11, zeroComplex, zeroComplex);
        }
    }

    public static DenseMatrix getIvector(DenseMatrix mV, double r1,
                                         Complex z0T1, Complex z0T2, Complex y0m, Complex zG1, Complex zG2,
                                         LegConnectionType leg1Type, LegConnectionType leg2Type, boolean isFreeFluxes) {

        return getAdmittanceMatrix(z0T1, z0T2, y0m, zG1, zG2, r1, leg1Type, leg2Type, isFreeFluxes).times(mV); // get admittance matrix in static times voltage to get the current
    }

    @Override
    public double eval() {
        return getIvector(getCartesianVoltageVector(v1(), ph1(), v2(), ph2()), r1(),
                z0T1, z0T2, y0m, zG1, zG2, leg1ConnectionType, leg2ConnectionType, isFreeFluxes).get(getIndexline(flowType), 0);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);

        if (variable.equals(r1Var)) {
            DenseMatrix mV = getCartesianVoltageVector(v1(), ph1(), v2(), ph2());
            return getDeriveAdmittanceMatrixdr1(z0T1, z0T2, y0m, zG1, zG2,
                    r1(), leg1ConnectionType, leg2ConnectionType, isFreeFluxes).times(mV).get(getIndexline(flowType), 0);
        } else {
            DenseMatrix mdV = getdVdx(variable);
            return getAdmittanceMatrix(z0T1, z0T2, y0m, zG1, zG2, r1(), leg1ConnectionType, leg2ConnectionType, isFreeFluxes).times(mdV).get(getIndexline(flowType), 0);
        }
    }

    @Override
    public String getName() {
        return "ac_i_tfo_negative_closed";
    }

    public static DenseMatrix getId44() {

        ComplexMatrix complexMatrix = new ComplexMatrix(2, 2);
        Complex one = new Complex(1., 0.);
        complexMatrix.set(1, 1, one);
        complexMatrix.set(2, 2, one);

        return complexMatrix.getRealCartesianMatrix();

    }

    public static DenseMatrix getMatrixFromBloc44(Complex bloc11, Complex bloc22, Complex bloc12) {

        ComplexMatrix complexMatrix = new ComplexMatrix(2, 2);
        complexMatrix.set(1, 1, bloc11);
        complexMatrix.set(1, 2, bloc12);
        complexMatrix.set(2, 1, bloc12);
        complexMatrix.set(2, 2, bloc22);

        return complexMatrix.getRealCartesianMatrix();

    }

}
