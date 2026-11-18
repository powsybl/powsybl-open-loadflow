/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.na.equations.*;
import com.powsybl.na.equations.ac.AcEquationSystemCreator;
import com.powsybl.na.equations.ac.AcEquationType;
import com.powsybl.na.equations.ac.AcStateVectorUtil;
import com.powsybl.na.equations.ac.AcVariableType;
import com.powsybl.na.network.FirstSlackBusSelector;
import com.powsybl.na.network.LfNetwork;
import com.powsybl.na.network.impl.Networks;
import com.powsybl.na.network.util.UniformValueVoltageInitializer;
import com.powsybl.na.test.networks.EurostagFactory;
import com.powsybl.openloadflow.ac.AcTargetVector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class NewtonRaphsonTest {

    @Test
    void findLargestMismatchesTest() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.get(0);

        EquationSystem<AcVariableType, AcEquationType> equationSystem = new AcEquationSystemCreator(mainNetwork).create();
        AcStateVectorUtil.initStateVector(mainNetwork, equationSystem, new UniformValueVoltageInitializer());
        double[] targets = TargetVector.createArray(mainNetwork, equationSystem, new TargetVector.Initializer<AcVariableType, AcEquationType>() {
            @Override
            public void initialize(SingleEquation<AcVariableType, AcEquationType> equation, LfNetwork network, double[] targets) {
                AcTargetVector.init(equation, network, targets);
            }

            @Override
            public void initialize(EquationArray<AcVariableType, AcEquationType> equationArray, LfNetwork network, double[] targets) {
                AcTargetVector.init(equationArray, network, targets);
            }
        });
        try (var equationVector = new EquationVector<>(equationSystem)) {
            Vectors.minus(equationVector.getArray(), targets);
            var largestMismatches = NewtonRaphson.findLargestMismatches(equationSystem, equationVector.getArray(), 3);
            assertEquals(3, largestMismatches.size());
            assertEquals(-7.397518453004565, largestMismatches.get(0).getValue(), 0);
            assertEquals(5.999135514403292, largestMismatches.get(1).getValue(), 0);
            assertEquals(1.9259062775721603, largestMismatches.get(2).getValue(), 0);
        }
    }
}
