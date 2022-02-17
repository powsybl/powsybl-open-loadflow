/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class BranchVector {

    private final LfNetwork network;

    private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

    private final double[] b1;
    private final double[] b2;
    private final double[] g1;
    private final double[] g2;
    private final double[] y;
    private final double[] ksi;
    private final double[] a1;
    private final double[] r1;

    private final int[] v1Row;
    private final int[] v2Row;
    private final int[] ph1Row;
    private final int[] ph2Row;
    private final int[] a1Row;
    private final int[] r1Row;

    private final LfNetworkListener networkListener = new AbstractLfNetworkListener() {
        @Override
        public void onDiscretePhaseControlTapPositionChange(PiModel piModel, int oldPosition, int newPosition) {
         //   a1[branch.getNum()] = branch.getPiModel().getA1();
        }

        @Override
        public void onTransformerVoltageControlChange(LfBranch controllerBranch, boolean newVoltageControllerEnabled) {
            for (LfBranch branch : controllerBranch.getVoltageControl().orElseThrow().getControllers()) {
                r1[branch.getNum()] = branch.getPiModel().getR1();
            }
        }
    };

    private final EquationSystemListener<AcVariableType, AcEquationType> equationSystemListener = new AbstractEquationSystemListener<AcVariableType, AcEquationType>() {

        @Override
        public void onIndexUpdate() {
            updateVarRow();
        }
    };

    public BranchVector(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        this.network = Objects.requireNonNull(network);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        List<LfBranch> branches = network.getBranches();
        int branchCount = branches.size();
        b1 = new double[branchCount];
        b2 = new double[branchCount];
        g1 = new double[branchCount];
        g2 = new double[branchCount];
        y = new double[branchCount];
        ksi = new double[branchCount];
        a1 = new double[branchCount];
        r1 = new double[branchCount];
        v1Row = new int[branchCount];
        v2Row = new int[branchCount];
        ph1Row = new int[branchCount];
        ph2Row = new int[branchCount];
        a1Row = new int[branchCount];
        r1Row = new int[branchCount];
        for (LfBranch branch : branches) {
            int num = branch.getNum();
            PiModel piModel = branch.getPiModel();
            b1[num] = piModel.getB1();
            b2[num] = piModel.getB2();
            g1[num] = piModel.getG1();
            g2[num] = piModel.getG2();
            y[num] = 1 / piModel.getZ();
            ksi[num] = piModel.getKsi();
            a1[num] = piModel.getA1();
            r1[num] = piModel.getR1();
        }
        updateVarRow();
        network.addListener(networkListener);
        equationSystem.addListener(equationSystemListener);
    }

    private static void updateBusVarRow(VariableSet<AcVariableType> variableSet, int num, LfBus bus,
                                        int[] vRow, int[] phRow) {
        if (bus !=  null) {
            vRow[num] = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V).getRow();
            phRow[num] = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI).getRow();
        } else {
            vRow[num] = -1;
            phRow[num] = -1;
        }
    }

    private void updateVarRow() {
        VariableSet<AcVariableType> variableSet = equationSystem.getVariableSet();
        for (LfBranch branch : network.getBranches()) {
            int num = branch.getNum();
            updateBusVarRow(variableSet, num, branch.getBus1(), v1Row, ph1Row);
            updateBusVarRow(variableSet, num, branch.getBus2(), v2Row, ph2Row);
            Variable<AcVariableType> a1Var = variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_ALPHA1);
            a1Row[num] = a1Var != null ? a1Var.getRow() : -1;
            Variable<AcVariableType> r1Var = variableSet.getVariable(branch.getNum(), AcVariableType.BRANCH_RHO1);
            r1Row[num] = r1Var != null ? r1Var.getRow() : -1;
        }
    }

    public LfBranch get(int num) {
        return network.getBranches().get(num);
    }

    public double b1(int num) {
        return b1[num];
    }

    public double b2(int num) {
        return b2[num];
    }

    public double g1(int num) {
        return g1[num];
    }

    public double g2(int num) {
        return g2[num];
    }

    public double y(int num) {
        return y[num];
    }

    public double ksi(int num) {
        return ksi[num];
    }

    public double a1(int num) {
        return a1[num];
    }

    public double r1(int num) {
        return r1[num];
    }

    public int v1Row(int num) {
        return v1Row[num];
    }

    public int v2Row(int num) {
        return v2Row[num];
    }

    public int ph1Row(int num) {
        return ph1Row[num];
    }

    public int ph2Row(int num) {
        return ph2Row[num];
    }

    public int a1Row(int num) {
        return a1Row[num];
    }

    public int r1Row(int num) {
        return r1Row[num];
    }
}
