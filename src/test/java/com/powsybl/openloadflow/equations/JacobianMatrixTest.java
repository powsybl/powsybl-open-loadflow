/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfNetwork;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class JacobianMatrixTest {

    @Test
    void test() {
        LfNetwork network = LfNetwork.load(EurostagTutorialExample1Factory.create(), new FirstSlackBusSelector()).get(0);
        EquationSystem equationSystem = DcEquationSystem.create(network, new DcEquationSystemCreationParameters(true, true, false, true));
        double[] x = equationSystem.createEquationVector();
        System.out.println(Arrays.toString(x));
        equationSystem.updateEquations(x);
        try (JacobianMatrix j = new JacobianMatrix(equationSystem, new DenseMatrixFactory())) {
            j.getMatrix().print(System.out);
        }
    }
}
