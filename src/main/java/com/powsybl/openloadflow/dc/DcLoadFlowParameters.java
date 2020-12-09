/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.network.SlackBusSelector;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowParameters {

    private final SlackBusSelector slackBusSelector;

    private final MatrixFactory matrixFactory;

    private final boolean updateFlows;

    private final boolean twtSplitShuntAdmittance;

    boolean useTransformerRatio;

    public DcLoadFlowParameters(SlackBusSelector slackBusSelector, MatrixFactory matrixFactory, boolean updateFlows,
                                boolean twtSplitShuntAdmittance, boolean useTransformerRatio) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.updateFlows = updateFlows;
        this.twtSplitShuntAdmittance = twtSplitShuntAdmittance;
        this.useTransformerRatio = useTransformerRatio;
    }

    public SlackBusSelector getSlackBusSelector() {
        return slackBusSelector;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public boolean isUpdateFlows() {
        return updateFlows;
    }

    public boolean isTwtSplitShuntAdmittance() {
        return twtSplitShuntAdmittance;
    }

    public boolean isUseTransformerRatio() {
        return useTransformerRatio;
    }
}
