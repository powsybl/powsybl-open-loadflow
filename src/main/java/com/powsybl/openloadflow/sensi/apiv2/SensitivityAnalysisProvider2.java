/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface SensitivityAnalysisProvider2 {

    CompletableFuture<SensitivityAnalysisResult2> run(Network network,
                                                      String workingStateId,
                                                      List<SensitivityFactorConfiguration> factorConfigurations,
                                                      List<Contingency> contingencies,
                                                      SensitivityAnalysisParameters parameters,
                                                      ComputationManager computationManager);
}
