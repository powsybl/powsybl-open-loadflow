/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Line;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.extensions.AsymThreePhaseTransfo;
import com.powsybl.openloadflow.util.ComplexMatrix;
import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LineAsymmetrical extends AbstractExtension<Line> {
    //
    // We suppose that an asymmetrical line is modelled by:
    // - its A,B,C connection status (phase connected / disconnected)
    // - the values of its physical attributes Rz,Xz,Rn,Xn (Rp and Xp are the values from the balanced Pi-model)
    //
    // From those values we define the Fortescue admittance matrix that will be used in the load-flow equations

    public static final String NAME = "lineAsymmetrical";

    private ComplexMatrix yabc; // a three phase admittance matrix can be provided in input and will be used as first option if not null

    private AsymmetricalBranchConnector asymConnectorBus1; // Quad ends will carry extra info related to asymmetrical connection
    private AsymmetricalBranchConnector asymConnectorBus2;

    @Override
    public String getName() {
        return NAME;
    }

    public LineAsymmetrical(Line line, AsymmetricalBranchConnector c1, AsymmetricalBranchConnector c2) {
        super(line);
        this.asymConnectorBus1 = c1;
        this.asymConnectorBus2 = c2;
        this.yabc = null;

    }

    public LineAsymmetrical(Line line, AsymmetricalBranchConnector c1, AsymmetricalBranchConnector c2,
                            ComplexMatrix yabc) {
        super(line);
        this.asymConnectorBus1 = c1;
        this.asymConnectorBus2 = c2;
        this.yabc = yabc;

    }

    public void setYabc(ComplexMatrix yabc) {
        this.yabc = yabc;
    }

    public ComplexMatrix getYabc() {
        return yabc;
    }

    public AsymmetricalBranchConnector getAsymConnectorBus1() {
        return asymConnectorBus1;
    }

    public AsymmetricalBranchConnector getAsymConnectorBus2() {
        return asymConnectorBus2;
    }

    public void setAsymConnectorBus2(AsymmetricalBranchConnector asymConnectorBus2) {
        this.asymConnectorBus2 = asymConnectorBus2;
    }

    public void setAsymConnectorBus1(AsymmetricalBranchConnector asymConnectorBus1) {
        this.asymConnectorBus1 = asymConnectorBus1;
    }

    public static ComplexMatrix getAdmittanceMatrixFromImpedanceAndBmatrix(ComplexMatrix zabc, ComplexMatrix babc, boolean hasPhaseA, boolean hasPhaseB, boolean hasPhaseC, double length) {

        // The lines are sometimes specified as impedance and susceptance matrices.
        // This function helps to build the Yabc from those in input of the constructor

        // second member [b] used as [b] = inv([z]) * [Id]
        DenseMatrix b3 = ComplexMatrix.complexMatrixIdentity(3).getRealCartesianMatrix();
        DenseMatrix minusId3 = ComplexMatrix.getMatrixScaled(ComplexMatrix.complexMatrixIdentity(3), -1.).getRealCartesianMatrix();

        // At this stage, zabc is not necessarily invertible since phases might be missing and then equivalent to zero blocs
        Complex one = new Complex(1., 0.);
        Complex zero = new Complex(0., 0.);
        if (!hasPhaseA) {
            // cancel all lines and columns of phase A and put 1 in the diagonal bloc for invertibility
            zabc.set(1, 1, one);
            zabc.set(1, 2, zero);
            zabc.set(1, 3, zero);
            zabc.set(2, 1, zero);
            zabc.set(3, 1, zero);
        }
        if (!hasPhaseB) {
            zabc.set(2, 2, one);
            zabc.set(1, 2, zero);
            zabc.set(3, 2, zero);
            zabc.set(2, 3, zero);
            zabc.set(2, 1, zero);
        }
        if (!hasPhaseC) {
            zabc.set(3, 3, one);
            zabc.set(1, 3, zero);
            zabc.set(2, 3, zero);
            zabc.set(3, 2, zero);
            zabc.set(3, 1, zero);
        }

        DenseMatrix zReal = zabc.getRealCartesianMatrix();
        zReal.decomposeLU().solve(b3);

        // Then we set to zero blocs with no phase
        ComplexMatrix invZabc = ComplexMatrix.getComplexMatrixFromRealCartesian(b3);
        if (!hasPhaseA) {
            invZabc.set(1, 1, zero);
        }
        if (!hasPhaseB) {
            invZabc.set(2, 2, zero);
        }
        if (!hasPhaseC) {
            invZabc.set(3, 3, zero);
        }

        b3 = invZabc.getRealCartesianMatrix();

        DenseMatrix minusB3 = b3.times(minusId3);
        DenseMatrix realYabc = AsymThreePhaseTransfo.buildFromBlocs(b3, minusB3, minusB3, b3);
        ComplexMatrix yabc = ComplexMatrix.getComplexMatrixFromRealCartesian(realYabc);
        yabc = ComplexMatrix.getMatrixScaled(yabc, 1. / length);

        // taking into account susceptance matrix babc
        yabc.set(4, 4, yabc.getTerm(4, 4).add(babc.getTerm(1, 1).multiply(length)));
        yabc.set(4, 5, yabc.getTerm(4, 5).add(babc.getTerm(1, 2).multiply(length)));
        yabc.set(4, 6, yabc.getTerm(4, 6).add(babc.getTerm(1, 3).multiply(length)));

        yabc.set(5, 4, yabc.getTerm(5, 4).add(babc.getTerm(2, 1).multiply(length)));
        yabc.set(5, 5, yabc.getTerm(5, 5).add(babc.getTerm(2, 2).multiply(length)));
        yabc.set(5, 6, yabc.getTerm(5, 6).add(babc.getTerm(2, 3).multiply(length)));

        yabc.set(6, 4, yabc.getTerm(6, 4).add(babc.getTerm(3, 1).multiply(length)));
        yabc.set(6, 5, yabc.getTerm(6, 5).add(babc.getTerm(3, 2).multiply(length)));
        yabc.set(6, 6, yabc.getTerm(6, 6).add(babc.getTerm(3, 3).multiply(length)));

        return yabc;
    }

    public static ComplexMatrix getAdmittanceMatrixFromImpedanceAndBmatrix(ComplexMatrix zabc, ComplexMatrix babc, boolean hasPhaseA, boolean hasPhaseB, boolean hasPhaseC) {
        return getAdmittanceMatrixFromImpedanceAndBmatrix(zabc, babc, hasPhaseA, hasPhaseB, hasPhaseC, 1.);
    }
}
