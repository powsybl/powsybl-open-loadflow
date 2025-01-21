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

/**
 * A data container that contains primitive type arrays that can be iterrated
 * efficiently to avoid memory cache misses
 */
public class BranchAcDataVector {
    public final double[] b1;
    public final double[] b2;
    public final double[] g1;
    public final double[] g2;
    public final double[] y;
    public final double[] ksi;
    public final double[] g12;
    public final double[] b12;

    public BranchAcDataVector(int branchCount) {
        b1 = new double[branchCount];
        b2 = new double[branchCount];
        g1 = new double[branchCount];
        g2 = new double[branchCount];
        y = new double[branchCount];
        ksi = new double[branchCount];
        g12 = new double[branchCount];
        b12 = new double[branchCount];
    }

}
