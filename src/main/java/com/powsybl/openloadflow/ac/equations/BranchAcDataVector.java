/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac.equations;
import com.powsybl.openloadflow.equations.*;
import net.jafama.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.DoubleSupplier;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 *
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
    private final ArrayList<AbstractClosedBranchAcFlowEquationTerm> supplyingTerms = new ArrayList<>();
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
    public final boolean[] dp2dv1Valid;
    public final double[] dp2dv1;
    public final VecToVal[] vecToDP2dv1;
    public final boolean[] dp2dv2Valid;
    public final double[] dp2dv2;
    public final VecToVal[] vecToDP2dv2;
    public final boolean[] dp2dph1Valid;
    public final double[] dp2dph1;
    public final VecToVal[] vecToDP2dph1;

    public interface VecToVal {
        double value(double v1, double v2, double sinKsi, double sinTheta2, double cosTheta2,
                     double b1, double b2, double g1, double g2, double y,
                     double g12, double b12, double a1, double r1);
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
        dp2dv1Valid = new boolean[branchCount];
        dp2dv1 = new double[branchCount];
        vecToDP2dv1 = new VecToVal[branchCount];
        dp2dv2Valid = new boolean[branchCount];
        dp2dv2 = new double[branchCount];
        vecToDP2dv2 = new VecToVal[branchCount];
        dp2dph1Valid = new boolean[branchCount];
        dp2dph1 = new double[branchCount];
        vecToDP2dph1 = new VecToVal[branchCount];

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
        Arrays.fill(dp2dv1Valid, false);
        Arrays.fill(dp2dv2Valid, false);
        Arrays.fill(dp2dph1Valid, false);
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

    public void addSupplyingTerm(AbstractClosedBranchAcFlowEquationTerm t) {
        supplyingTerms.add(t);
    }

    private void updateSuppliers() {
        Arrays.fill(r1, Double.NaN);
        Arrays.fill(r1Supplier, null);
        Arrays.fill(a1, Double.NaN);
        Arrays.fill(a1Supplier, null);
        Arrays.fill(vecToP2, null);
        supplyingTerms.stream()
                .filter(AbstractEquationTerm::isActive)
                .filter(t -> t.getEquation().isActive())
                .forEach(AbstractClosedBranchAcFlowEquationTerm::updateVectorSuppliers);
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
            double a1Evaluated = a1Supplier[i] == null ? a1[i] : a1Supplier[i].getAsDouble();
            double r1Evaluated = r1Supplier[i] == null ? r1[i] : r1Supplier[i].getAsDouble();
            double sinKsi = FastMath.sin(ksi[i]);
            double theta2 = AbstractClosedBranchAcFlowEquationTerm.theta2(ksi[i], ph1[i], a1Evaluated, ph2[i]);
            double sinTheta2 = FastMath.sin(theta2);
            double cosTheta2 = FastMath.cos(theta2);
            if (vecToP2[i] != null) {
                // All dp2 functions should be available then
                p2[i] = vecToP2[i].value(v1[i], v2[i], sinKsi, sinTheta2, cosTheta2,
                        b1[i], b2[i], g1[i], g2[i], y[i], g12[i], b12[i],
                        a1Evaluated, r1Evaluated);
                p2Valid[i] = true;
                dp2dv1[i] = vecToDP2dv1[i].value(v1[i], v2[i], sinKsi, sinTheta2, cosTheta2,
                        b1[i], b2[i], g1[i], g2[i], y[i], g12[i], b12[i],
                        a1Evaluated, r1Evaluated);
                dp2dv1Valid[i] = true;
                dp2dv2[i] = vecToDP2dv2[i].value(v1[i], v2[i], sinKsi, sinTheta2, cosTheta2,
                        b1[i], b2[i], g1[i], g2[i], y[i], g12[i], b12[i],
                        a1Evaluated, r1Evaluated);
                dp2dv2Valid[i] = true;
                dp2dph1[i] = vecToDP2dph1[i].value(v1[i], v2[i], sinKsi, sinTheta2, cosTheta2,
                        b1[i], b2[i], g1[i], g2[i], y[i], g12[i], b12[i],
                        a1Evaluated, r1Evaluated);
                dp2dph1Valid[i] = true;
            }
        }
    }
}
