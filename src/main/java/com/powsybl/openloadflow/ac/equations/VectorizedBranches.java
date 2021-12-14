/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VectorizedBranches {

    private final List<LfBranch> branches;
    private final double[] b1;
    private final double[] b2;
    private final double[] g1;
    private final double[] g2;
    private final double[] y;
    private final double[] ksi;
    private final double[] a1;
    private final double[] r1;

    public VectorizedBranches(List<LfBranch> branches) {
        this.branches = Objects.requireNonNull(branches);
        b1 = new double[branches.size()];
        b2 = new double[branches.size()];
        g1 = new double[branches.size()];
        g2 = new double[branches.size()];
        y = new double[branches.size()];
        ksi = new double[branches.size()];
        a1 = new double[branches.size()];
        r1 = new double[branches.size()];
        for (LfBranch branch : branches) {
            PiModel piModel = branch.getPiModel();
            b1[branch.getNum()] = piModel.getB1();
            b2[branch.getNum()] = piModel.getB2();
            g1[branch.getNum()] = piModel.getG1();
            g2[branch.getNum()] = piModel.getG2();
            y[branch.getNum()] = 1 / piModel.getZ();
            ksi[branch.getNum()] = piModel.getKsi();
            a1[branch.getNum()] = piModel.getA1();
            r1[branch.getNum()] = piModel.getR1();
        }
    }

    public LfBranch get(int num) {
        return branches.get(num);
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
}
