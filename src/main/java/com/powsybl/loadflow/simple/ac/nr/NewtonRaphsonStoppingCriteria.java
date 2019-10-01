/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.nr;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface NewtonRaphsonStoppingCriteria {

    class TestResult {

        private final boolean stop;

        private final double norm;

        public TestResult(boolean stop, double norm) {
            this.stop = stop;
            this.norm = norm;
        }

        public boolean isStop() {
            return stop;
        }

        public double getNorm() {
            return norm;
        }
    }

    TestResult test(double[] fx);
}
