/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.adm.AdmittanceEquationSystem;
import com.powsybl.openloadflow.adm.AdmittanceMatrix;
import com.powsybl.openloadflow.equations.SingleEquation;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.List;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class WardAdmittance {

    private final Matrix yMatrix;
    private final AdmittanceEquationSystem yEquationSystem;
    private final MatrixFactory matrixFactory;
    private final List<LfBus> internalBuses;
    private final List<LfBus> boundaryBuses;
    private final List<LfBus> externalBuses;
    private Matrix ieq;
    private Matrix yeq;

    public WardAdmittance(LfNetwork lfNetwork, ZoneDecomposition zoneDecomposition, WardReductionParameters.ReductionType reductionType, MatrixFactory matrixFactory) {
        this.yEquationSystem = createEquationSystem(lfNetwork, reductionType);
        try (AdmittanceMatrix y = AdmittanceMatrix.create(yEquationSystem, matrixFactory)) {
            yMatrix = y.getMatrix().transpose();
        }
        this.matrixFactory = matrixFactory;
        this.internalBuses = zoneDecomposition.getInternalBuses();
        this.boundaryBuses = zoneDecomposition.getBoundaryBuses();
        this.externalBuses = zoneDecomposition.getExternalBuses();

        reduce();
    }

    private AdmittanceEquationSystem createEquationSystem(LfNetwork lfNetwork, WardReductionParameters.ReductionType reductionType) {
        // Create the equation system differently depending on the reduction type
        if (reductionType == WardReductionParameters.ReductionType.STANDARD_WARD) {
            return AdmittanceEquationSystem.create(lfNetwork, new VariableSet<>());
        } else {
            throw new IllegalStateException("Unknown Reduction type: " + reductionType);
        }
    }

    private void reduce() {
        // Partition the network in 3 subsets: internal, boundary and external
        // ┌    ┐     ┌                   ┐   ┌    ┐
        // │ Ii │     │ Yii    Yib     0  │   │ Vi │
        // │ Ib │  =  │ Ybi    Ybb    Ybe │ * │ Vb │
        // │ Ie │     │  0     Yeb    Yee │   │ Ve │
        // └    ┘     └                   ┘   └    ┘
        // note:
        //   Ybb = Ybb(i) + Ybb(b+e)
        //   Ybb(i) contains the tie-line admittances between boundary buses and internal buses (tie lines)
        //   Ybb(b+e) contains the branch admittances between non-internal buses
        // the distinction is important since the former will be kept while the later will be reduced

        // Calculate sub admittance matrices using selectors
        Matrix boundaryRowsSelector = getRowSelectorMatrix(boundaryBuses);
        Matrix externalRowsSelector = getRowSelectorMatrix(externalBuses);
        Matrix internalColsSelector = getColSelectorMatrix(internalBuses);
        Matrix boundaryColsSelector = getColSelectorMatrix(boundaryBuses);
        Matrix externalColsSelector = getColSelectorMatrix(externalBuses);

        Matrix ybi = boundaryRowsSelector.times(yMatrix).times(internalColsSelector);
        Matrix ybb = boundaryRowsSelector.times(yMatrix).times(boundaryColsSelector);
        Matrix ybe = boundaryRowsSelector.times(yMatrix).times(externalColsSelector);
        Matrix yeb = externalRowsSelector.times(yMatrix).times(boundaryColsSelector);
        Matrix yee = externalRowsSelector.times(yMatrix).times(externalColsSelector);

        // Get bus voltage vectors Vb and Ve
        Matrix vi = getVoltageVector(internalBuses);
        Matrix vb = getVoltageVector(boundaryBuses);
        Matrix ve = getVoltageVector(externalBuses);

        // Retrieve current nodal injection vectors
        //   Ib = Ybi * Vi + Ybb * Vb + Ybe * Ve
        //   Ie = Yeb * Vb + Yee * Ve
        Matrix ib = ybi.times(vi).add(ybb.times(vb)).add(ybe.times(ve));
        Matrix ie = yeb.times(vb).add(yee.times(ve));

        // Reduce the system to:
        // ┌     ┐     ┌                     ┐   ┌    ┐
        // │ Ii  │     │ Yii        Yib      │   │ Vi │
        // │ Ieq │  =  │ Ybi    Ybb(i) + Yeq │ * │ Vb │
        // └     ┘     └                     ┘   └    ┘
        // where:
        //   Ieq = Ib - Ybe * Yee⁻1 * Ie
        //   Yeq = Ybb(be) - Ybe * Yee^-1 * Yeb
        DenseMatrix yeeInv = getIdentityMatrix(2 * externalBuses.size()).toDense();
        try (LUDecomposition lu = yee.decomposeLU()) {
            lu.solve(yeeInv);
        }
        ieq = ib.add(ybe.times(yeeInv).times(ie, -1.0));
        yeq = getYbbWithoutInternal(ybi, ybb).add(ybe.times(yeeInv).times(yeb, -1.0));
    }

    private Matrix getRowSelectorMatrix(List<LfBus> buses) {
        List<Integer> busNums = buses.stream().map(LfBus::getNum).toList();
        Matrix selector = matrixFactory.create(2 * busNums.size(), yMatrix.getColumnCount(), 2 * busNums.size());
        List<Integer> eqColNums = getEqColNums(busNums);
        for (int i = 0; i < eqColNums.size(); i++) {
            selector.set(i, eqColNums.get(i), 1.0);
        }
        return selector;
    }

    private Matrix getColSelectorMatrix(List<LfBus> buses) {
        List<Integer> busNums = buses.stream().map(LfBus::getNum).toList();
        Matrix selector = matrixFactory.create(yMatrix.getColumnCount(), 2 * busNums.size(), 2 * busNums.size());
        List<Integer> eqColNums = getEqColNums(busNums);
        for (int j = 0; j < eqColNums.size(); j++) {
            selector.set(eqColNums.get(j), j, 1.0);
        }
        return selector;
    }

    //TODO is it necessary? It looks like the equation numbers are 2 * busNum and 2 * busNum + 1
    private List<Integer> getEqColNums(List<Integer> busNums) {
        return yEquationSystem.getEquationSystem().getEquations().stream()
            .filter(eq -> busNums.contains(eq.getElementNum()))
            .map(SingleEquation::getColumn)
            .sorted()
            .toList();
    }

    private Matrix getVoltageVector(List<LfBus> buses) {
        Matrix voltageVector = matrixFactory.create(2 * buses.size(), 1, 2 * buses.size());
        for (int i = 0; i < buses.size(); i++) {
            voltageVector.set(2 * i, 0, buses.get(i).getV() * Math.cos(buses.get(i).getAngle()));
            voltageVector.set(2 * i + 1, 0, buses.get(i).getV() * Math.sin(buses.get(i).getAngle()));
        }
        return voltageVector;
    }

    private Matrix getIdentityMatrix(int size) {
        Matrix identity = matrixFactory.create(size, size, size);
        for (int i = 0; i < size; i++) {
            identity.set(i, i, 1.0);
        }
        return identity;
    }

    private Matrix getYbbWithoutInternal(Matrix ybi, Matrix ybb) {
        // Calculate Ybb(b+e) where Ybb = Ybb(i) + Ybb(b+e)
        Matrix ybbWithoutInternal = ybb.copy(matrixFactory).toDense();
        for (int b = 0; b < boundaryBuses.size(); b++) {
            double sumGbi = 0.0;
            double sumBbi = 0.0;
            for (int i = 0; i < internalBuses.size(); i++) {
                sumGbi += ybi.toDense().get(2 * b, 2 * i);
                sumBbi += ybi.toDense().get(2 * b, 2 * i + 1);
            }
            double bbGWithoutInternal = ybbWithoutInternal.toDense().get(2 * b, 2 * b) + sumGbi;
            double bbBWithoutInternal = ybbWithoutInternal.toDense().get(2 * b, 2 * b + 1) + sumBbi;
            ybbWithoutInternal.set(2 * b, 2 * b, bbGWithoutInternal);
            ybbWithoutInternal.set(2 * b, 2 * b + 1, bbBWithoutInternal);
            ybbWithoutInternal.set(2 * b + 1, 2 * b, -bbBWithoutInternal);
            ybbWithoutInternal.set(2 * b + 1, 2 * b + 1, bbGWithoutInternal);
        }

        return ybbWithoutInternal;
    }

    public Matrix getIeq() {
        return ieq;
    }

    public Matrix getYeq() {
        return yeq;
    }

}
