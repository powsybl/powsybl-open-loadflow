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
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.NaiveGraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.security.LimitViolationDetector;
import com.powsybl.security.LimitViolationFilter;
import com.powsybl.security.SecurityAnalysisFactory;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;

import javax.inject.Provider;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSecurityAnalysisFactory implements SecurityAnalysisFactory {

    private final MatrixFactory matrixFactory;
    private final Provider<GraphDecrementalConnectivity<LfBus>> connectivityProvider;

    public OpenSecurityAnalysisFactory() {
        this(new SparseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
    }

    public OpenSecurityAnalysisFactory(MatrixFactory matrixFactory, Provider<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityProvider = Objects.requireNonNull(connectivityProvider);
    }

    @Override
    public OpenSecurityAnalysis create(Network network, ComputationManager computationManager, int priority) {
        return new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(), new LimitViolationFilter(), matrixFactory, connectivityProvider);
    }

    @Override
    public OpenSecurityAnalysis create(Network network, LimitViolationFilter filter, ComputationManager computationManager, int priority) {
        return new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(), filter, matrixFactory, connectivityProvider);
    }

    @Override
    public OpenSecurityAnalysis create(Network network, LimitViolationDetector detector, LimitViolationFilter filter, ComputationManager computationManager, int priority) {
        return new OpenSecurityAnalysis(network, detector, filter, matrixFactory, connectivityProvider);
    }
}
