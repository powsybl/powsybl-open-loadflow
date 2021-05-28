/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.security.results.ThreeWindingsTransformerResult;

import java.util.Objects;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
public class LfThreeWindingsTransformer {

    private final String lfThreeWindingsTransformerId;
    private final LfLegBranch branch1;
    private final LfLegBranch branch2;
    private final LfLegBranch branch3;

    public LfThreeWindingsTransformer(String lfThreeWindingsTransformerId, LfLegBranch branch1, LfLegBranch branch2, LfLegBranch branch3) {
        this.lfThreeWindingsTransformerId = Objects.requireNonNull(lfThreeWindingsTransformerId);
        this.branch1 = Objects.requireNonNull(branch1);
        this.branch2 = Objects.requireNonNull(branch2);
        this.branch3 = Objects.requireNonNull(branch3);
    }

    public ThreeWindingsTransformerResult createThreeWindingsTransformerResult() {
        return new ThreeWindingsTransformerResult(lfThreeWindingsTransformerId, branch1.getP1().eval(), branch1.getQ1().eval(),
            branch1.getI1().eval(), branch2.getP1().eval(), branch2.getQ1().eval(), branch2.getI1().eval(), branch3.getP1().eval(),
            branch3.getQ1().eval(), branch3.getI1().eval());
    }

    public String getLfThreeWindingsTransformerId() {
        return lfThreeWindingsTransformerId;
    }

    public String getBranchId1() {
        return branch1.getId();
    }

    public String getBranchId2() {
        return branch2.getId();
    }

    public String getBranchId3() {
        return branch3.getId();
    }
}
