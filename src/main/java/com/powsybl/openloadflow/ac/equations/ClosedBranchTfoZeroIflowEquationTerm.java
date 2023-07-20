/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
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

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
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

        this.zG1 = asymTransfo2W.getZ1Ground();
        this.zG2 = asymTransfo2W.getZ2Ground();
        double epsilon = 0.00000001;
        Complex y1 = new Complex(g1, b1);
        Complex y2 = new Complex(g2, b2);
        if (isFreeFluxes || (y1.abs() < epsilon && y2.abs() < epsilon)) {
            // magnetizing circuit is open or Y1 or Y2 are zero, leading Ym to zero
            this.z0T1 = new Complex(0, 0);
            this.z0T2 = asymTransfo2W.getZo();
            this.y0m = new Complex(0, 0);
            isFreeFluxes = true;
        } else {
            Complex z12 = asymTransfo2W.getZo();

            if (y2.abs() > epsilon) {
                throw new IllegalArgumentException("Transfomer " + branch.getId() + " has homopolar input y2 not equal to zero and is not supported in current version of the asymmetric load flow");
            }

            this.z0T1 = new Complex(0, 0);
            this.y0m = y1;
            this.z0T2 = z12;
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

    public static DenseMatrix getYgYgForcedFluxesAdmittanceMatrix(Complex z0T1, Complex z0T2, Complex y0m, Complex zG1, Complex zG2, double r1) {

        Complex z11 = (zG1.multiply(3.)).add((z0T1.add(y0m.reciprocal())).multiply(1 / (r1 * r1)));
        Complex z12 = (y0m.reciprocal()).multiply(1 / r1);
        Complex z22 = (zG2.multiply(3.)).add(z0T2.add(y0m.reciprocal()));

        DenseMatrix mZ = getMatrixFromBloc44(z11, z22, z12);
        DenseMatrix b44 = getId44();
        mZ.decomposeLU().solve(b44);
        return b44;

    }

    public static DenseMatrix getYgYgFreeFluxesImpedanceMatrix(Complex z0T1, Complex z0T2, Complex zG1, Complex zG2, double r1) {
        // F(x) = 1 / (A + B.x²) = yeq
        Complex yeq = ((zG1.multiply(r1 * r1).add(zG2)).multiply(3)).add(z0T1.add(z0T2)).reciprocal();
        Complex y11 = yeq.multiply(r1 * r1);
        Complex y12 = yeq.multiply(-r1);
        Complex y22 = yeq;

        return getMatrixFromBloc44(y11, y22, y12); // [Y]
    }

    public static DenseMatrix getAdmittanceMatrix(Complex z0T1, Complex z0T2, Complex y0m, Complex zG1, Complex zG2,
                                           double r1, LegConnectionType leg1Type, LegConnectionType leg2Type, boolean isFreeFluxes) {
        if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.Y_GROUNDED) {

            if (isFreeFluxes) {
                return getYgYgFreeFluxesImpedanceMatrix(z0T1, z0T2, zG1, zG2, r1);
            } else {
                return getYgYgForcedFluxesAdmittanceMatrix(z0T1, z0T2, y0m, zG1, zG2, r1);
            }
        } else {
            Complex y11;
            Complex y22 = new Complex(0., 0.);
            Complex y12 = y22;
            if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.DELTA) {
                Complex tmp2 = z0T1.add(y0m.add(z0T2.reciprocal()).reciprocal()).multiply(1 / (r1 * r1));
                y11 = (zG1.multiply(3).add(tmp2)).reciprocal();
            } else {
                throw new IllegalArgumentException("Transfomer with winding config Yg or Y or DELTA not supported in current version of the asymmetric load flow");
            }
            // if windings are in another configuration we consider it is a zero admittance matrix
            return getMatrixFromBloc44(y11, y22, y12);
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
            throw new PowsyblException("State variable rho1 not yet handled in transformers for asymmetrical load flow, keep in fixed for now ");
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
