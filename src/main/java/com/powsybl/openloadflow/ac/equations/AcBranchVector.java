/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.BranchVector;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcBranchVector extends BranchVector<AcVariableType, AcEquationType> {

    public double[] v1Row;
    public double[] v2Row;
    public double[] ph1Row;
    public double[] ph2Row;
    public double[] a1Row;
    public double[] r1Row;

    public AcBranchVector(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem, VariableSet<AcVariableType> variableSet) {
        super(network, equationSystem, variableSet);
    }

    private void init() {
        List<LfBranch> branches = network.getBranches();
        int branchCount = branches.size();
        v1Row = new double[branchCount];
        v2Row = new double[branchCount];
        ph1Row = new double[branchCount];
        ph2Row = new double[branchCount];
        a1Row = new double[branchCount];
        r1Row = new double[branchCount];
        for (int i = 0; i < branchCount; i++) {
            LfBranch branch = branches.get(i);
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
//            v1Var = variableSet.create(bus1.getNum(), AcVariableType.BUS_V);
//            v2Var = variableSet.create(bus2.getNum(), AcVariableType.BUS_V);
//            ph1Var = variableSet.create(bus1.getNum(), AcVariableType.BUS_PHI);
//            ph2Var = variableSet.create(bus2.getNum(), AcVariableType.BUS_PHI);

        }
    }
}
