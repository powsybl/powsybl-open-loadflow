/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;


import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.adm.AdmittanceEquationSystem;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.powsybl.openloadflow.reduction.ReductionUtil.create4BusNetwork;
import static com.powsybl.openloadflow.reduction.ReductionUtil.getIEEE118InternalVlIds;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
class WardAdmittanceTest {

    @Test
    void wardAdmittanceIEEE118Test() {
        Network network = IeeeCdfNetworkFactory.create118();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters());
        LfNetwork lfNetwork = lfNetworks.getFirst();
        Set<String> internalVlIds = getIEEE118InternalVlIds();

        ZoneDecomposition zones = new ZoneDecomposition(lfNetwork, internalVlIds);

        AdmittanceEquationSystem yEquationSystem = AdmittanceEquationSystem.create(lfNetwork, new VariableSet<>());
        WardAdmittance wardAdmittance = new WardAdmittance(yEquationSystem, zones, new SparseMatrixFactory());

        Matrix ieq = wardAdmittance.getIeq();
        Matrix yeq = wardAdmittance.getYeq();

        double[] ieqValues = {0.005166133823640062, 2.0646030696262185, 0.292214846302657, 4.094441362677833,
            -0.030821820854532785, 0.19488697513300748, 0.2260440777818795, -0.19051673069166608};
        Matrix ieqExpected = new DenseMatrix(8, 1, ieqValues);
        assertMatrixEquals(ieqExpected, ieq);

        double[] yeqValues = {
            0.9859769945166228, -4.225305285510922, -0.8302640984657124, 5.206373305025931,
            -0.016125201985069997, 0.05815617352377401, -0.14515262108035273, 1.029615959211429,
            4.22530528551091, 0.9859769945166059, -5.20637330502595, -0.8302640984658046,
            -0.05815617352377414, -0.016125201985070885, -1.029615959211432, -0.14515262108037286,
            -0.8302640984657137, 5.206373305025937, 2.7643306328763835, -13.304555321529762,
            -1.3008670350618456, 5.31054473617419, -0.40319060385949823, 7.168674354154866,
            -5.206373305025946, -0.8302640984658127, 13.304555321529733, 2.7643306328761064,
            -5.310544736174191, -1.3008670350618463, -7.168674354154865, -0.4031906038595792,
            -0.016125201985070097, 0.05815617352377394, -1.3008670350618454, 5.31054473617419,
            1.3192919117853046, -5.322518150853223, -0.0033120136576258365, 0.019003166163478673,
            -0.058156173523774196, -0.016125201985070885, -5.310544736174191, -1.3008670350618465,
            5.322518150853223, 1.3192919117853048, -0.019003166163478778, -0.0033120136576263066,
            -0.14515262108035254, 1.0296159592114296, -0.4031906038594974, 7.168674354154861,
            -0.0033120136576258044, 0.01900316616347872, 0.6254568965541755, -7.2910973475734195,
            -1.029615959211431, -0.14515262108037566, -7.1686743541548665, -0.4031906038595804,
            -0.019003166163478687, -0.0033120136576263504, 7.2910973475734195, 0.6254568965541536
        };
        Matrix yeqExpected = new DenseMatrix(8, 8, yeqValues);
        assertMatrixEquals(yeqExpected, yeq);



    }

    @Test
    void wardAdmittance4BusNetworkTest() {
        Network network = create4BusNetwork();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new SparseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        loadFlowRunner.run(network, parameters);
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters());
        LfNetwork lfNetwork = lfNetworks.getFirst();
        Set<String> internalVlIds = Set.of("VL1");

        ZoneDecomposition zones = new ZoneDecomposition(lfNetwork, internalVlIds);

        AdmittanceEquationSystem yEquationSystem = AdmittanceEquationSystem.create(lfNetwork, new VariableSet<>());
        WardAdmittance wardAdmittance = new WardAdmittance(yEquationSystem, zones, new SparseMatrixFactory());

        Matrix ieq = wardAdmittance.getIeq();
        Matrix yeq = wardAdmittance.getYeq();

        double[] ieqValues = {-0.002463737074419189, 0.014868835287145723, -0.003460921994714815, 0.020587668985362816};
        Matrix ieqExpected = new DenseMatrix(4, 1, ieqValues);
        assertMatrixEquals(ieqExpected, ieq);

        double[] yeqValues = {
            0.4186729259132811, -4.186729259132809, -0.36929668828951856, 3.6929668828951856,
            4.186729259132809, 0.41867292591328037, -3.6929668828951856, -0.36929668828951956,
            -0.36929668828951856, 3.692966882895185, 0.408963024923182, -4.089630249231819,
            -3.692966882895185, -0.36929668828951956, 4.089630249231819, 0.4089630249231806
        };
        Matrix yeqExpected = new DenseMatrix(4, 4, yeqValues);
        assertMatrixEquals(yeqExpected, yeq);
    }

    private void assertMatrixEquals(Matrix expected, Matrix actual) {
        DenseMatrix denseExpected = expected.toDense();
        DenseMatrix denseActual = actual.toDense();
        assertEquals(denseExpected.getRowCount(), denseActual.getRowCount());
        assertEquals(denseExpected.getColumnCount(), denseActual.getColumnCount());
        for (int i = 0; i < expected.getRowCount(); i++) {
            for (int j = 0; j < expected.getColumnCount(); j++) {
                assertEquals(denseExpected.get(i, j), denseActual.get(i, j), 1e-10);
            }
        }
    }

}
