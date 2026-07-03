/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * What equation ACTIVATION decides, in the stable row indexing — rebuilt only when an
 * activation event fired (PV→PQ switching, slack change), never per solve:
 * <ul>
 *   <li>{@code rowMode[r]}: 1 = the row's power equation is active, 0 = the row is a
 *       slack/PV target identity (applied as a device-side mask);</li>
 *   <li>{@code targetMap[r]}: the OLF equation column whose target lands in stable row
 *       {@code r} (-1 = none) — applying it to the target vector is the per-solve work.</li>
 * </ul>
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public record GpuAcActivation(int[] rowMode, int[] targetMap) {

    public double[] applyTargets(double[] olfTargets) {
        double[] t = new double[targetMap.length];
        for (int r = 0; r < targetMap.length; r++) {
            t[r] = targetMap[r] >= 0 ? olfTargets[targetMap[r]] : 0;
        }
        return t;
    }

    public static GpuAcActivation fromEquationSystem(LfNetwork network,
                                                     EquationSystem<AcVariableType, AcEquationType> es) {
        int n = es.getIndex().getSortedVariablesToFind().size();
        return GpuAcDataExtractor.extractActivation(network, es, n);
    }
}
