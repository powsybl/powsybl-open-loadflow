/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.NetworkBuffer;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcNetworkBuffer extends NetworkBuffer<AcVariableType, AcEquationType> {

    private int[] v1Row;
    private int[] v2Row;
    private int[] ph1Row;
    private int[] ph2Row;
    private int[] a1Row;
    private int[] r1Row;

    public AcNetworkBuffer(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem, VariableSet<AcVariableType> variableSet) {
        super(network, equationSystem, variableSet);
        init();
    }

    private void init() {
        System.out.println("BUFFER INIT");
        List<LfBranch> branches = network.getBranches();
        int branchCount = branches.size();
        v1Row = new int[branchCount];
        v2Row = new int[branchCount];
        ph1Row = new int[branchCount];
        ph2Row = new int[branchCount];
        a1Row = new int[branchCount];
        r1Row = new int[branchCount];
        for (int i = 0; i < branchCount; i++) {
            LfBranch branch = branches.get(i);
            LfBus bus1 = branch.getBus1();
            if (bus1 !=  null) {
                v1Row[i] = variableSet.get(bus1.getNum(), AcVariableType.BUS_V).getRow();
                ph1Row[i] = variableSet.get(bus1.getNum(), AcVariableType.BUS_PHI).getRow();
            } else {
                v1Row[i] = -1;
                ph1Row[i] = -1;
            }
            LfBus bus2 = branch.getBus2();
            if (bus2 != null) {
                v2Row[i] = variableSet.get(bus2.getNum(), AcVariableType.BUS_V).getRow();
                ph2Row[i] = variableSet.get(bus2.getNum(), AcVariableType.BUS_PHI).getRow();
            } else {
                v2Row[i] = -1;
                ph2Row[i] = -1;
            }
            Variable<AcVariableType> a1Var = variableSet.get(branch.getNum(), AcVariableType.BRANCH_ALPHA1);
            a1Row[i] = a1Var != null ? a1Var.getRow() : -1;
            Variable<AcVariableType> r1Var = variableSet.get(branch.getNum(), AcVariableType.BRANCH_RHO1);
            r1Row[i] = r1Var != null ? r1Var.getRow() : -1;
        }
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

    @Override
    public void onIndexUpdate() {
        init();
    }
}
