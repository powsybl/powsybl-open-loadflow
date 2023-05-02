/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.network.LfBus;

import java.util.List;

/**
 * Vectorized view of the buses. Only variables related the buses at the moment.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcBusVector {

    public final int[] vRow;
    public final int[] phRow;

    public final double[] p;
    public final double[] q;

    public final double[] dpdv1;
    public final double[] dpdv2;
    public final double[] dpdph1;
    public final double[] dpdph2;
    public final double[] dpda1;
    public final double[] dpdr1;

    public final double[] dqdv1;
    public final double[] dqdv2;
    public final double[] dqdph1;
    public final double[] dqdph2;
    public final double[] dqda1;
    public final double[] dqdr1;

    public AcBusVector(List<LfBus> buses) {
        int size = buses.size();
        vRow = new int[size];
        phRow = new int[size];
        p = new double[size];
        q = new double[size];
        dpdv1 = new double[size];
        dpdv2 = new double[size];
        dpdph1 = new double[size];
        dpdph2 = new double[size];
        dpda1 = new double[size];
        dpdr1 = new double[size];
        dqdv1 = new double[size];
        dqdv2 = new double[size];
        dqdph1 = new double[size];
        dqdph2 = new double[size];
        dqda1 = new double[size];
        dqdr1 = new double[size];
    }
}
