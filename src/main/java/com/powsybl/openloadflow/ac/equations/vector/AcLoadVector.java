/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.network.LfLoad;
import com.powsybl.openloadflow.network.LfLoadModel;

import java.util.List;

/**
 * Vectorized view of the loads.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadVector {
    final int[] busNum;

    public final List<LfLoadModel.ExpTerm>[] expTermsP;
    public final List<LfLoadModel.ExpTerm>[] expTermsQ;
    public final double[] targetP;
    public final double[] targetQ;

    public final double[] pLoadModel;
    public final double[] qLoadModel;
    public final double[] dpdvLoadModel;
    public final double[] dqdvLoadModel;

    public AcLoadVector(List<LfLoad> loads) {
        busNum = new int[loads.size()];
        expTermsP = new List[loads.size()];
        expTermsQ = new List[loads.size()];
        targetP = new double[loads.size()];
        targetQ = new double[loads.size()];

        pLoadModel = new double[loads.size()];
        qLoadModel = new double[loads.size()];
        dpdvLoadModel = new double[loads.size()];
        dqdvLoadModel = new double[loads.size()];

        for (int loadNum = 0; loadNum < loads.size(); loadNum++) {
            LfLoad load = loads.get(loadNum);
            busNum[loadNum] = load.getBus().getNum();

            final int finalLoadNum = loadNum;
            load.getLoadModel().ifPresent(model -> {
                expTermsP[finalLoadNum] = model.getExpTermsP();
                expTermsQ[finalLoadNum] = model.getExpTermsQ();
                targetP[finalLoadNum] = load.getTargetP();
                targetQ[finalLoadNum] = load.getTargetQ();
            });
        }
    }
}
