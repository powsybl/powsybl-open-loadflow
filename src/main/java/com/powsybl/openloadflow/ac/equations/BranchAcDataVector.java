/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac.equations;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */

import com.powsybl.openloadflow.equations.*;

import java.util.Arrays;
import java.util.function.DoubleSupplier;

/**
 * A data container that contains primitive type arrays that can be iterrated
 * efficiently to avoid memory cache misses
 */
public class BranchAcDataVector implements StateVectorListener, EquationSystemListener {

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    public final boolean[] networkDataInitialized;
    public final double[] b1;
    public final double[] b2;
    public final double[] g1;
    public final double[] g2;
    public final double[] y;
    public final double[] ksi;
    public final double[] g12;
    public final double[] b12;

    // possibly computed input values
    private boolean suppliersValid = false;
    public final double[] a1;
    public final DoubleSupplier[] a1Supplier;
    public final double[] r1;
    public final DoubleSupplier[] r1Supplier;

    // variables
    public final Variable<AcVariableType>[] v1Var;
    private final double[] v1;
    public final Variable<AcVariableType>[] v2Var;
    private final double[] v2;
    public final Variable<AcVariableType>[] ph1Var;
    private final double[] ph1;
    public final Variable<AcVariableType>[] ph2Var;
    private final double[] ph2;

    // eval values
    public final boolean[] p2Valid;
    public final double[] p2;
    public final VecToVal[] vecToP2;

    public interface VecToVal {
        double value(double v1, double v2, double ph1, double ph2, double b1, double b2, double g1, double g2, double y,
                            double ksi, double g12, double b12, double a1, double r1);
    }

    public BranchAcDataVector(int branchCount, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        this.equationSystem = equationSystem;

        b1 = new double[branchCount];
        b2 = new double[branchCount];
        g1 = new double[branchCount];
        g2 = new double[branchCount];
        y = new double[branchCount];
        ksi = new double[branchCount];
        g12 = new double[branchCount];
        b12 = new double[branchCount];
        networkDataInitialized = new boolean[branchCount];

        a1 = new double[branchCount];
        a1Supplier = new DoubleSupplier[branchCount];

        r1 = new double[branchCount];
        r1Supplier = new DoubleSupplier[branchCount];

        v1Var = new Variable[branchCount];
        v1 = new double[branchCount];
        v2Var = new Variable[branchCount];
        v2 = new double[branchCount];
        ph1Var = new Variable[branchCount];
        ph1 = new double[branchCount];
        ph2Var = new Variable[branchCount];
        ph2 = new double[branchCount];

        p2Valid = new boolean[branchCount];
        p2 = new double[branchCount];
        vecToP2 = new VecToVal[branchCount];

        if (equationSystem != null) {
            equationSystem.getStateVector().addListener(this);
            equationSystem.addListener(this);
        }
    }

    @Override
    public void onStateUpdate() {
        if (!suppliersValid) {
            updateSuppliers();
        }
        Arrays.fill(p2Valid, false);
        updateVariables();
        vecToP2();
    }

    @Override
    public void onEquationChange(Equation equation, EquationEventType eventType) {
        suppliersValid = false;
    }

    @Override
    public void onEquationTermChange(EquationTerm term, EquationTermEventType eventType) {
        suppliersValid = false;
    }

    private void updateSuppliers() {
        Arrays.fill(r1, Double.NaN);
        Arrays.fill(r1Supplier, null);
        Arrays.fill(a1, Double.NaN);
        Arrays.fill(a1Supplier, null);
        Arrays.fill(vecToP2, null);
        equationSystem.getEquations().stream()
                .filter(Equation::isActive)
                .flatMap(e -> e.getTerms().stream())
                .filter(EquationTerm::isActive)
                // Just filter the implemented classes for now
               .filter(t -> t instanceof AbstractBranchAcFlowEquationTerm)
                .map(t -> (AbstractBranchAcFlowEquationTerm) t)
                .forEach(t -> t.updateVectorSuppliers());
        suppliersValid = true;
    }

    private void updateVariables() {
        StateVector stateVector = equationSystem.getStateVector();
        for (int i = 0; i < v1Var.length; i++) {
            v1[i] = v1Var[i] != null && v1Var[i].getRow() >= 0 ? stateVector.get(v1Var[i].getRow()) : Double.NaN;
            v2[i] = v2Var[i] != null && v2Var[i].getRow() >= 0 ? stateVector.get(v2Var[i].getRow()) : Double.NaN;
            ph1[i] = ph1Var[i] != null && ph1Var[i].getRow() >= 0 ? stateVector.get(ph1Var[i].getRow()) : Double.NaN;
            ph2[i] = ph2Var[i] != null && ph2Var[i].getRow() >= 0 ? stateVector.get(ph2Var[i].getRow()) : Double.NaN;
        }
    }

    private void vecToP2() {
        for (int i = 0; i < vecToP2.length; i++) {
            if (vecToP2[i] != null) {
                p2[i] = vecToP2[i].value(v1[i], v2[i], ph1[i], ph2[i],
                        b1[i], b2[i], g1[i], g2[i], y[i], ksi[i], g12[i], b12[i],
                        a1Supplier[i] == null ? a1[i] : a1Supplier[i].getAsDouble(),
                        r1Supplier[i] == null ? r1[i] : r1Supplier[i].getAsDouble());
                p2Valid[i] = true;
            }
        }
    }
}
