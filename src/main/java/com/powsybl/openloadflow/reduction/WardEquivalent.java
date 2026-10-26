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
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>The Ward equivalent computation.</p>
 *
 * <p>This consists in calculating the admittance matrix of a network
 * and decomposing it into submatrices for internal, boundary and external parts.
 * The external part is then removed by Gaussian elimination
 * and its influence is reported onto the boundary one.</p>
 *
 * <p>This reduction results in equivalent branches, shunts and injections.</p>
 *
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class WardEquivalent {

    private final Matrix yMatrix;
    private final AdmittanceEquationSystem yEquationSystem;
    private final MatrixFactory matrixFactory;
    private final List<LfBus> internalBuses;
    private final List<LfBus> boundaryBuses;
    private final List<LfBus> externalBuses;
    private final List<EquivalentBranch> equivalentBranches = new ArrayList<>();
    private final List<EquivalentShunt> equivalentShunts = new ArrayList<>();
    private final List<EquivalentInjection> equivalentInjections = new ArrayList<>();

    /**
     * Create a new WardEquivalent instance for the given network, zone decomposition, equivalent type and matrix factory.
     *
     * @param lfNetwork The {@link LfNetwork} for which the admittance matrix is to be created.
     * @param zoneDecomposition The {@link ZoneDecomposition} according to which the admittance matrix is to be partitioned.
     * @param wardEquivalentType The {@link WardEquivalentType} specifying the equivalent to be calculated.
     * @param matrixFactory The {@link MatrixFactory} for creating matrices used in the computations.
     */
    public WardEquivalent(LfNetwork lfNetwork, ZoneDecomposition zoneDecomposition, WardEquivalentType wardEquivalentType, MatrixFactory matrixFactory) {
        this.yEquationSystem = createEquationSystem(lfNetwork, wardEquivalentType);
        try (AdmittanceMatrix y = AdmittanceMatrix.create(yEquationSystem, matrixFactory)) {
            yMatrix = y.getMatrix().transpose();
        }
        this.matrixFactory = matrixFactory;
        this.internalBuses = zoneDecomposition.getInternalBuses();
        this.boundaryBuses = zoneDecomposition.getBoundaryBuses();
        this.externalBuses = zoneDecomposition.getExternalBuses();

        calculateEquivalent();
    }

    private AdmittanceEquationSystem createEquationSystem(LfNetwork lfNetwork, WardEquivalentType wardEquivalentType) {
        // Create the equation system based on the Ward equivalent type
        if (wardEquivalentType == WardEquivalentType.STANDARD) {
            return AdmittanceEquationSystem.create(lfNetwork, new VariableSet<>());
        } else {
            throw new IllegalStateException("Unknown Ward equivalent type: " + wardEquivalentType);
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
        DenseMatrix ieq = ib.add(ybe.times(yeeInv).times(ie, -1.0)).toDense();
        DenseMatrix yeq = getYbbWithoutInternal(ybb).add(ybe.times(yeeInv).times(yeb, -1.0)).toDense();

        // Calculate equivalents line, shunts, injections from the equivalent admittance and current injection matrices
        calculateEquivalentShunts(yeq);
        calculateEquivalentBranches(yeq);
        calculateEquivalentInjections(ieq);
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

    private void calculateEquivalentBranches(DenseMatrix yeq) {
        // Retrieve the equivalent branches between boundary buses from the mutual admittances of equivalent Y matrix
        double zb;
        for (int i = 0; i < boundaryBuses.size(); i++) {
            for (int j = i + 1; j < boundaryBuses.size(); j++) {
                LfBus bus1 = boundaryBuses.get(i);
                LfBus bus2 = boundaryBuses.get(j);
                // Calculate the series admittance in engineering units
                double v1 = bus1.getNominalV();
                double v2 = bus2.getNominalV();
                zb = v1 * v2 / PerUnit.SB;
                double g = -yeq.get(2 * i, 2 * j) / zb;
                double b = yeq.get(2 * i, 2 * j + 1) / zb;
                equivalentBranches.add(new EquivalentBranch(bus1, bus2, g, b));

                // Compute line shunt admittances as separate shunt objects.
                // This is a way to preserve distinct values for both buses when exporting to external formats
                // (such as CGMES) where line shunt admittances are aggregated into a single g and b.
                if (Math.abs(v1 - v2) > 1e-3) {
                    double g1 = (v2 - v1) / v1 * g;
                    double b1 = (v2 - v1) / v1 * b;
                    equivalentShunts.add(new EquivalentShunt(bus1, g1, b1));

                    double g2 = (v1 - v2) / v2 * g;
                    double b2 = (v1 - v2) / v2 * b;
                    equivalentShunts.add(new EquivalentShunt(bus2, g2, b2));
                }
            }
        }
    }

    private void calculateEquivalentShunts(DenseMatrix yeq) {
        // Retrieve the equivalent shunts at boundary buses from the self and mutual admittances of equivalent Y matrix
        double zb;
        for (int j = 0; j < boundaryBuses.size(); j++) {
            double g = 0.0;
            double b = 0.0;
            for (int i = 0; i < boundaryBuses.size(); i++) {
                g += yeq.get(2 * i, 2 * j);
                b += yeq.get(2 * i + 1, 2 * j);
            }
            LfBus bus = boundaryBuses.get(j);
            // Calculate the shunt admittance in engineering units
            zb = PerUnit.zb(bus.getNominalV());
            g = g / zb;
            b = b / zb;
            equivalentShunts.add(new EquivalentShunt(bus, g, b));
        }
    }

    private void calculateEquivalentInjections(DenseMatrix ieq) {
        // Retrieve the equivalent power injections at boundary buses from the equivalent I matrix
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
            // Calculate the power injection in engineering units
            double p = (ux * ix + uy * iy) * PerUnit.SB;
            double q = (-ux * iy + uy * ix) * PerUnit.SB;
            equivalentInjections.add(new Reducer.EquivalentInjection(bus, p, q));
        }
    }

    /**
     * Get the list of equivalent branches between boundary buses stemming from the reduction process.
     *
     * @return a list of {@link EquivalentBranch} between boundary buses.
     */
    public List<EquivalentBranch> getEquivalentBranches() {
        return equivalentBranches;
    }

    /**
     * Get the list of equivalent shunts at boundary buses stemming from the reduction process.
     *
     * @return a list of {@link EquivalentShunt} at boundary buses.
     */
    public List<EquivalentShunt> getEquivalentShunts() {
        // Merge equivalent shunts on the same bus
        return equivalentShunts.stream()
            .collect(Collectors.toMap(
                EquivalentShunt::bus,
                Function.identity(),
                (shunt1, shunt2) -> new EquivalentShunt(
                    shunt1.bus(), shunt1.g() + shunt2.g(), shunt1.b() + shunt2.b())))
            .values().stream()
            .sorted(Comparator.comparing(e -> e.bus().getId()))
            .toList();
    }

    /**
     * Get the list of equivalent injections at boundary buses stemming from the reduction process.
     *
     * @return a list of {@link EquivalentInjection} at boundary buses.
     */
    public List<EquivalentInjection> getEquivalentInjections() {
        return equivalentInjections;
    }

}
