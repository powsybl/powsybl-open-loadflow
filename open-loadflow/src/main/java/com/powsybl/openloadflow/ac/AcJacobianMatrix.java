/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.na.equations.EquationSystem;
import com.powsybl.na.equations.JacobianMatrix;
import com.powsybl.na.equations.ac.AcEquationType;
import com.powsybl.na.equations.ac.AcVariableType;
import com.powsybl.na.network.AbstractLfNetworkListener;
import com.powsybl.na.network.LfLoad;
import com.powsybl.na.network.LfNetwork;
import com.powsybl.na.network.LfNetworkListener;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcJacobianMatrix extends JacobianMatrix<AcVariableType, AcEquationType> {

    private final LfNetwork network;

    private final LfNetworkListener networkListener = new AbstractLfNetworkListener() {

        private void onLoadTargetChange(LfLoad load) {
            load.getLoadModel().ifPresent(ignored -> updateStatus(Status.VALUES_INVALID));
        }

        @Override
        public void onLoadActivePowerTargetChange(LfLoad load, double oldTargetP, double newTargetP) {
            onLoadTargetChange(load);
        }

        @Override
        public void onLoadReactivePowerTargetChange(LfLoad load, double oldTargetQ, double newTargetQ) {
            onLoadTargetChange(load);
        }
    };

    public AcJacobianMatrix(EquationSystem<AcVariableType, AcEquationType> equationSystem, MatrixFactory matrixFactory,
                            LfNetwork network) {
        super(equationSystem, matrixFactory);
        this.network = Objects.requireNonNull(network);
        network.addListener(networkListener);
    }

    @Override
    public void close() {
        super.close();
        network.removeListener(networkListener);
    }
}
