/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
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
public class ClosedBranchTfoZeroIflowEquationTerm extends AbstractAsymmetricalClosedBranchFlowEquationTerm {

    private final FlowType flowType;
    private final Complex z0T1;
    private final Complex z0T2;
    private final Complex y0m;
    private final Complex zG1;
    private final Complex zG2;
    private final LegConnectionType leg1ConnectionType;
    private final LegConnectionType leg2ConnectionType;
    private boolean freeFluxes;

    public ClosedBranchTfoZeroIflowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                    boolean deriveA1, boolean deriveR1, FlowType flowType) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.ZERO);

        this.flowType = Objects.requireNonNull(flowType);

        AsymTransfo2W asymTransfo2W = (AsymTransfo2W) branch.getProperty(AsymTransfo2W.PROPERTY_ASYMMETRICAL);
        if (asymTransfo2W == null) {
            throw new IllegalStateException("Branch : " + branch.getId() + " has no asymmetric extension but is required here ");
        }

        leg1ConnectionType = asymTransfo2W.getLeg1ConnectionType();
        leg2ConnectionType = asymTransfo2W.getLeg2ConnectionType();
        freeFluxes = asymTransfo2W.isFreeFluxes();

        zG1 = asymTransfo2W.getZ1Ground();
        zG2 = asymTransfo2W.getZ2Ground();
        double epsilon = 0.00000001;
        Complex y1 = new Complex(g1, b1);
        Complex y2 = new Complex(g2, b2);
        if (freeFluxes || y1.abs() < epsilon && y2.abs() < epsilon) {
            // magnetizing circuit is open or Y1 or Y2 are zero, leading Ym to zero
            this.z0T1 = Complex.ZERO;
            this.z0T2 = asymTransfo2W.getZo();
            this.y0m = Complex.ZERO;
            freeFluxes = true;
        } else {
            Complex z12 = asymTransfo2W.getZo();

            if (y2.abs() > epsilon) {
                throw new IllegalArgumentException("Transfomer " + branch.getId() + " has homopolar input y2 not equal to zero and is not supported in current version of the asymmetric load flow");
            }

            this.z0T1 = Complex.ZERO;
            this.y0m = y1;
            this.z0T2 = z12;
        }
    }

    public double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public static DenseMatrix createYgYgForcedFluxesAdmittanceMatrix(Complex z0T1, Complex z0T2, Complex y0m, Complex zG1, Complex zG2, double r1) {
        Complex z11 = (zG1.multiply(3.)).add((z0T1.add(y0m.reciprocal())).multiply(1 / (r1 * r1)));
        Complex z12 = (y0m.reciprocal()).multiply(1 / r1);
        Complex z22 = (zG2.multiply(3.)).add(z0T2.add(y0m.reciprocal()));

        DenseMatrix mZ = createMatrixFromBloc44(z11, z22, z12);
        DenseMatrix b44 = ComplexMatrix.createIdentity(2).toRealCartesianMatrix();
        try (LUDecomposition lu = mZ.decomposeLU()) {
            lu.solve(b44);
        }
        return b44;

    }

    public static DenseMatrix createYgYgFreeFluxesImpedanceMatrix(Complex z0T1, Complex z0T2, Complex zG1, Complex zG2, double r1) {
        // F(x) = 1 / (A + B.x²) = yeq
        Complex yeq = ((zG1.multiply(r1 * r1).add(zG2)).multiply(3)).add(z0T1.add(z0T2)).reciprocal();
        Complex y11 = yeq.multiply(r1 * r1);
        Complex y12 = yeq.multiply(-r1);
        Complex y22 = yeq;

        return createMatrixFromBloc44(y11, y22, y12); // [Y]
    }

    public static DenseMatrix createAdmittanceMatrix(Complex z0T1, Complex z0T2, Complex y0m, Complex zG1, Complex zG2,
                                                     double r1, LegConnectionType leg1Type, LegConnectionType leg2Type, boolean freeFluxes) {
        if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.Y_GROUNDED) {
            if (freeFluxes) {
                return createYgYgFreeFluxesImpedanceMatrix(z0T1, z0T2, zG1, zG2, r1);
            } else {
                return createYgYgForcedFluxesAdmittanceMatrix(z0T1, z0T2, y0m, zG1, zG2, r1);
            }
        } else {
            Complex y11 = Complex.ZERO;
            Complex y22 = Complex.ZERO;
            Complex y12 = y22;
            if (leg1Type == LegConnectionType.DELTA && leg2Type == LegConnectionType.Y_GROUNDED) {
                Complex tmp1 = z0T2.add(y0m.add(z0T1.reciprocal()).reciprocal());
                y22 = (zG2.multiply(3).add(tmp1)).reciprocal();
            } else if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.DELTA) {
                Complex tmp2 = z0T1.add(y0m.add(z0T2.reciprocal()).reciprocal()).multiply(1 / (r1 * r1));
                y11 = (zG1.multiply(3).add(tmp2)).reciprocal();
            } else if (leg1Type == LegConnectionType.Y && leg2Type == LegConnectionType.Y_GROUNDED && !freeFluxes) {
                Complex tmp3 = z0T2.add(y0m.reciprocal());
                y22 = (zG2.multiply(3).add(tmp3)).reciprocal();
            } else if (leg1Type == LegConnectionType.Y_GROUNDED && leg2Type == LegConnectionType.Y && !freeFluxes) {
                Complex tmp4 = z0T1.add(y0m.reciprocal()).multiply(1 / (r1 * r1));
                y11 = (zG1.multiply(3).add(tmp4)).reciprocal();
            } else {
                throw new IllegalArgumentException("Transfomer configuration not supported");
            }
            // if windings are in another configuration we consider it is a zero admittance matrix
            return createMatrixFromBloc44(y11, y22, y12);
        }
    }

    public static DenseMatrix createIvector(DenseMatrix mV, double r1,
                                            Complex z0T1, Complex z0T2, Complex y0m, Complex zG1, Complex zG2,
                                            LegConnectionType leg1Type, LegConnectionType leg2Type, boolean isFreeFluxes) {

        return createAdmittanceMatrix(z0T1, z0T2, y0m, zG1, zG2, r1, leg1Type, leg2Type, isFreeFluxes).times(mV); // get admittance matrix in static times voltage to get the current
    }

    @Override
    public double eval() {
        return createIvector(getCartesianVoltageVector(v1(), ph1(), v2(), ph2()), r1(),
                z0T1, z0T2, y0m, zG1, zG2, leg1ConnectionType, leg2ConnectionType, freeFluxes).get(getIndexline(flowType), 0);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);

        if (variable.equals(r1Var)) {
            throw new PowsyblException("State variable rho1 not yet handled in transformers for asymmetrical load flow, keep in fixed for now ");
        } else {
            DenseMatrix mdV = getdVdx(variable);
            return createAdmittanceMatrix(z0T1, z0T2, y0m, zG1, zG2, r1(), leg1ConnectionType, leg2ConnectionType, freeFluxes).times(mdV).get(getIndexline(flowType), 0);
        }
    }

    @Override
    public String getName() {
        return "ac_i_tfo_negative_closed";
    }

    public static DenseMatrix createMatrixFromBloc44(Complex bloc11, Complex bloc22, Complex bloc12) {
        ComplexMatrix complexMatrix = new ComplexMatrix(2, 2);
        complexMatrix.set(1, 1, bloc11);
        complexMatrix.set(1, 2, bloc12);
        complexMatrix.set(2, 1, bloc12);
        complexMatrix.set(2, 2, bloc22);
        return complexMatrix.toRealCartesianMatrix();
    }
}
