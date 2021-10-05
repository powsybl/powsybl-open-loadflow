/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.*;
import net.jafama.FastMath;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class BranchVector {

    private final LfNetwork network;

    public double[] b1;
    public double[] b2;
    public double[] g1;
    public double[] g2;
    public double[] y;
    public double[] ksi;
    public double[] sinKsi;
    public double[] cosKsi;

    public BranchVector(LfNetwork network) {
        this.network = Objects.requireNonNull(network);
        update();
    }

    private void update() {
        List<LfBranch> branches = network.getBranches();
        int branchCount = branches.size();
        b1 = new double[branchCount];
        b2 = new double[branchCount];
        g1 = new double[branchCount];
        g2 = new double[branchCount];
        y = new double[branchCount];
        ksi = new double[branchCount];
        sinKsi = new double[branchCount];
        cosKsi = new double[branchCount];
        for (int i = 0; i < branchCount; i++) {
            LfBranch branch = branches.get(i);
            PiModel piModel = branch.getPiModel();
//            if (piModel.getR() == 0 && piModel.getX() == 0) {
//                throw new IllegalArgumentException("Non impedant branch not supported: " + branch.getId());
//            }
            b1[i] = piModel.getB1();
            b2[i] = piModel.getB2();
            g1[i] = piModel.getG1();
            g2[i] = piModel.getG2();
            y[i] = 1 / piModel.getZ();
            ksi[i] = piModel.getKsi();
            cosKsi[i] = FastMath.cos(ksi[i]);
            sinKsi[i] = FastMath.sin(ksi[i]);
        }
    }
}
