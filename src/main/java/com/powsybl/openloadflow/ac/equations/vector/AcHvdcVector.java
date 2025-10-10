/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcHvdcVector {

    final int[] bus1Num;
    final int[] bus2Num;

    final boolean[] disabled;

    public final int[] ph1Row;
    public final int[] ph2Row;

    final double[] k;

    final double[] p0;

    final double[] r;

    final double[] lossFactor1;

    final double[] lossFactor2;

    final double[] pMaxFromCS1toCS2;

    final double[] pMaxFromCS2toCS1;

    final boolean[] acEmulationFrozen;

    final double[] angleDifferenceToFreeze;

    final double[] p1;
    final double[] p2;

    final double[] dp1dph1;
    final double[] dp1dph2;

    final double[] dp2dph1;
    final double[] dp2dph2;

    public AcHvdcVector(List<LfHvdc> hvdcs) {
        int size = hvdcs.size();
        bus1Num = new int[size];
        bus2Num = new int[size];
        disabled = new boolean[size];

        ph1Row = new int[size];
        ph2Row = new int[size];

        p1 = new double[size];
        p2 = new double[size];

        p0 = new double[size];
        k = new double[size];
        r = new double[size];
        lossFactor1 = new double[size];
        lossFactor2 = new double[size];
        pMaxFromCS1toCS2 = new double[size];
        pMaxFromCS2toCS1 = new double[size];
        acEmulationFrozen = new boolean[size];
        angleDifferenceToFreeze = new double[size];

        dp1dph1 = new double[size];
        dp1dph2 = new double[size];
        dp2dph1 = new double[size];
        dp2dph2 = new double[size];

        for (int i = 0; i < size; i++) {
            LfHvdc hvdc = hvdcs.get(i);
            LfBus bus1 = hvdc.getBus1();
            LfBus bus2 = hvdc.getBus2();
            bus1Num[i] = bus1 != null ? bus1.getNum() : -1;
            bus2Num[i] = bus2 != null ? bus2.getNum() : -1;
            disabled[i] = hvdc.isDisabled();

            p0[i] = hvdc.getP0();
            r[i] = hvdc.getR();
            k[i] = hvdc.getDroop() * 180 / Math.PI;
            lossFactor1[i] = hvdc.getConverterStation1().getLossFactor() / 100;
            lossFactor2[i] = hvdc.getConverterStation2().getLossFactor() / 100;
            pMaxFromCS1toCS2[i] = hvdc.getPMaxFromCS1toCS2();
            pMaxFromCS2toCS1[i] = hvdc.getPMaxFromCS2toCS1();

            acEmulationFrozen[i] = hvdc.isAcEmulationFrozen();
            angleDifferenceToFreeze[i] = hvdc.getAngleDifferenceToFreeze();
        }
    }

    public int getSize() {
        return bus1Num.length;
    }
}
