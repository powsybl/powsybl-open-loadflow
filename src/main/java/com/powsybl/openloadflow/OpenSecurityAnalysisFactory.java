/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.security.LimitViolationDetector;
import com.powsybl.security.LimitViolationFilter;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisFactory;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSecurityAnalysisFactory implements SecurityAnalysisFactory {

    private final MatrixFactory matrixFactory;

    public OpenSecurityAnalysisFactory() {
        this(new SparseMatrixFactory());
    }

    public OpenSecurityAnalysisFactory(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    @Override
    public SecurityAnalysis create(Network network, ComputationManager computationManager, int priority) {
        return new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(), new LimitViolationFilter(), matrixFactory);
    }

    @Override
    public SecurityAnalysis create(Network network, LimitViolationFilter filter, ComputationManager computationManager, int priority) {
        return new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(), filter, matrixFactory);
    }

    @Override
    public SecurityAnalysis create(Network network, LimitViolationDetector detector, LimitViolationFilter filter, ComputationManager computationManager, int priority) {
        return new OpenSecurityAnalysis(network, detector, filter, matrixFactory);
    }
}
