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
import com.powsybl.openloadflow.adm.*;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.SingleEquation;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.reduction.Reducer.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.ArrayList;
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
    private DenseMatrix ieq;
    private DenseMatrix yeq;

    public WardAdmittance(LfNetwork lfNetwork, ZoneDecomposition zoneDecomposition, WardReductionParameters.ReductionType reductionType, MatrixFactory matrixFactory) {
        this.yEquationSystem = createEquationSystem(lfNetwork, reductionType);
        try (AdmittanceMatrix y = AdmittanceMatrix.create(yEquationSystem, matrixFactory)) {
            yMatrix = y.getMatrix().transpose();
        }
        this.matrixFactory = matrixFactory;
        this.internalBuses = zoneDecomposition.getInternalBuses();
        this.boundaryBuses = zoneDecomposition.getBoundaryBuses();
        this.externalBuses = zoneDecomposition.getExternalBuses();

        calculateEquivalent();
    }

    private AdmittanceEquationSystem createEquationSystem(LfNetwork lfNetwork, WardReductionParameters.ReductionType reductionType) {
        // Create the equation system based on the reduction type
        if (reductionType == WardReductionParameters.ReductionType.STANDARD_WARD) {
            return AdmittanceEquationSystem.create(lfNetwork, new VariableSet<>());
        } else {
            throw new IllegalStateException("Unknown Reduction type: " + reductionType);
        }
    }

    private void calculateEquivalent() {
        // Partition the network in 3 subsets: internal, boundary and external
        // ┌    ┐     ┌                   ┐   ┌    ┐
        // │ Ii │     │ Yii    Yib     0  │   │ Vi │
        // │ Ib │  =  │ Ybi    Ybb    Ybe │ * │ Vb │
        // │ Ie │     │  0     Yeb    Yee │   │ Ve │
        // └    ┘     └                   ┘   └    ┘

        // Get sub admittance matrices using selectors
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

        // Get bus voltage vectors
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
        //   Yeq = Ybb(b+e) - Ybe * Yee^-1 * Yeb
        // note:
        //   Ybb = Ybb(i) + Ybb(b+e)
        //   Ybb(i) contains the branch admittances between boundary buses and internal buses (tie lines)
        //   Ybb(b+e) contains the branch admittances between boundary buses and other boundary or external buses
        // the distinction is important since the former will be kept while the later will be reduced
        DenseMatrix yeeInv = getIdentityMatrix(2 * externalBuses.size()).toDense();
        try (LUDecomposition lu = yee.decomposeLU()) {
            lu.solve(yeeInv);
        }
        ieq = ib.add(ybe.times(yeeInv).times(ie, -1.0)).toDense();
        yeq = getYbbWithoutInternal(ybb).add(ybe.times(yeeInv).times(yeb, -1.0)).toDense();
    }

    private Matrix getRowSelectorMatrix(List<LfBus> buses) {
        // Return a row selector matrix for the given buses
        List<Integer> busNums = buses.stream().map(LfBus::getNum).toList();
        Matrix selector = matrixFactory.create(2 * busNums.size(), yMatrix.getColumnCount(), 2 * busNums.size());
        List<Integer> eqColNums = getEqColNums(busNums);
        for (int i = 0; i < eqColNums.size(); i++) {
            selector.set(i, eqColNums.get(i), 1.0);
        }
        return selector;
    }

    private Matrix getColSelectorMatrix(List<LfBus> buses) {
        // Return a column selector matrix for the given buses
        List<Integer> busNums = buses.stream().map(LfBus::getNum).toList();
        Matrix selector = matrixFactory.create(yMatrix.getColumnCount(), 2 * busNums.size(), 2 * busNums.size());
        List<Integer> eqColNums = getEqColNums(busNums);
        for (int j = 0; j < eqColNums.size(); j++) {
            selector.set(eqColNums.get(j), j, 1.0);
        }
        return selector;
    }

    private List<Integer> getEqColNums(List<Integer> busNums) {
        // Return the column numbers of equations associated with the given bus numbers
        return yEquationSystem.getEquationSystem().getEquations().stream()
            .filter(eq -> busNums.contains(eq.getElementNum()))
            .map(SingleEquation::getColumn)
            .sorted()
            .toList();
    }

    private Matrix getVoltageVector(List<LfBus> buses) {
        // Return a vector containing bus voltages real part and imaginary part
        Matrix voltageVector = matrixFactory.create(2 * buses.size(), 1, 2 * buses.size());
        for (int i = 0; i < buses.size(); i++) {
            voltageVector.set(2 * i, 0, buses.get(i).getV() * Math.cos(buses.get(i).getAngle()));
            voltageVector.set(2 * i + 1, 0, buses.get(i).getV() * Math.sin(buses.get(i).getAngle()));
        }
        return voltageVector;
    }

    private Matrix getIdentityMatrix(int size) {
        // Return an identity matrix of given size
        Matrix identity = matrixFactory.create(size, size, size);
        for (int i = 0; i < size; i++) {
            identity.set(i, i, 1.0);
        }
        return identity;
    }

    private Matrix getYbbWithoutInternal(Matrix ybb) {
        // Calculate Ybb(b+e) where Ybb = Ybb(i) + Ybb(b+e)
        // that is remove the influence of tie-lines onto the Ybb sub admittance matrix
        Matrix ybbWithoutInternal = ybb.copy(matrixFactory).toDense();
        List<Integer> internalBusNums = internalBuses.stream()
            .map(LfBus::getNum)
            .toList();
        for (int i = 0; i < boundaryBuses.size(); i++) {
            // Total G and B including the influence of tie-lines
            double gi = ybb.toDense().get(2 * i, 2 * i);
            double bi = ybb.toDense().get(2 * i + 1, 2 * i);

            // Retrieve the boundary bus equation
            LfBus boundaryBus = boundaryBuses.get(i);
            Equation<AdmittanceVariableType, AdmittanceEquationType> equation = yEquationSystem
                .getEquationSystem()
                .getEquation(boundaryBus.getNum(), AdmittanceEquationType.BUS_ADM_IX)
                .orElseThrow(() -> new IllegalStateException("Equation not found"));

            // Select the terms related to a tie-line
            for (EquationTerm<AdmittanceVariableType, AdmittanceEquationType> term : equation.getTerms()) {
                List<Variable<AdmittanceVariableType>> variables = term.getVariables();
                if (variables.stream().anyMatch(v -> internalBusNums.contains(v.getElementNum()))) {
                    // Subtract the series and shunt admittance of the boundary side
                    double gSum = term.der(variables.stream()
                        .filter(v -> v.getElementNum() == boundaryBus.getNum()
                            && v.getType() == AdmittanceVariableType.BUS_ADM_VX)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Variable not found")));
                    double bSum = -term.der(variables.stream()
                        .filter(v -> v.getElementNum() == boundaryBus.getNum()
                            && v.getType() == AdmittanceVariableType.BUS_ADM_VY)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Variable not found")));

                    gi -= gSum;
                    bi -= bSum;
                }
            }

            ybbWithoutInternal.set(2 * i, 2 * i, gi);
            ybbWithoutInternal.set(2 * i, 2 * i + 1, -bi);
            ybbWithoutInternal.set(2 * i + 1, 2 * i, bi);
            ybbWithoutInternal.set(2 * i + 1, 2 * i + 1, gi);
        }

        return ybbWithoutInternal;
    }

    public List<EquivalentBranch> getEquivalentBranches() {
        // Retrieve the equivalent branches between boundary buses from the mutual admittances of equivalent Y matrix
        List<Reducer.EquivalentBranch> equivalentBranches = new ArrayList<>();
        double zb;
        for (int i = 0; i < boundaryBuses.size(); i++) {
            for (int j = i + 1; j < boundaryBuses.size(); j++) {
                LfBus bus1 = boundaryBuses.get(i);
                LfBus bus2 = boundaryBuses.get(j);
                // Get the admittance values in engineering units
                double v1 = bus1.getNominalV();
                double v2 = bus2.getNominalV();
                zb = v1 * v2 / PerUnit.SB;
                double g = -yeq.get(2 * i, 2 * j) / zb;
                double b = yeq.get(2 * i, 2 * j + 1) / zb;
                double g1 = (v2 - v1) / v1 * g;
                double b1 = (v2 - v1) / v1 * b;
                double g2 = (v1 - v2) / v2 * g;
                double b2 = (v1 - v2) / v2 * b;
                equivalentBranches.add(new Reducer.EquivalentBranch(bus1, bus2, g, b, g1, b1, g2, b2));
            }
        }
        return equivalentBranches;
    }

    public List<EquivalentShunt> getEquivalentShunts() {
        // Retrieve the equivalent shunts at boundary buses from the self and mutual admittances of equivalent Y matrix
        List<Reducer.EquivalentShunt> equivalentShunts = new ArrayList<>();
        double zb;
        for (int j = 0; j < boundaryBuses.size(); j++) {
            double g = 0.0;
            double b = 0.0;
            for (int i = 0; i < boundaryBuses.size(); i++) {
                g += yeq.get(2 * i, 2 * j);
                b += yeq.get(2 * i + 1, 2 * j);
            }
            LfBus bus = boundaryBuses.get(j);
            // Get the admittance values in engineering units
            zb = PerUnit.zb(bus.getNominalV());
            g = g / zb;
            b = b / zb;
            equivalentShunts.add(new Reducer.EquivalentShunt(boundaryBuses.get(j), g, b));
        }
        return equivalentShunts;
    }

    public List<EquivalentInjection> getEquivalentInjections() {
        // Retrieve the equivalent power injections at boundary buses from the equivalent I matrix
        List<Reducer.EquivalentInjection> equivalentInjections = new ArrayList<>();
        for (int i = 0; i < boundaryBuses.size(); i++) {
            // S = P + j Q
            //   = U I*
            //   = (Ux + j Uy) (Ix - j Iy)
            //   = (Ux Ix + Uy Iy) + j (-Ux Iy + Uy Ix)
            LfBus bus = boundaryBuses.get(i);
            double ux = bus.getV() * Math.cos(bus.getAngle());
            double uy = bus.getV() * Math.sin(bus.getAngle());
            double ix = ieq.get(2 * i, 0);
            double iy = ieq.get(2 * i + 1, 0);
            // Get the power values in engineering units
            double p = (ux * ix + uy * iy) * PerUnit.SB;
            double q = (-ux * iy + uy * ix) * PerUnit.SB;
            equivalentInjections.add(new Reducer.EquivalentInjection(bus, p, q));
        }
        return equivalentInjections;
    }

}
