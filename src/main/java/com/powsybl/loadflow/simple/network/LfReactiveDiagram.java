/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfReactiveDiagram {

    double getMinQ(double p);

    double getMaxQ(double p);

    static LfReactiveDiagram merge(LfReactiveDiagram diagram1, LfReactiveDiagram diagram2) {
        Objects.requireNonNull(diagram1);
        Objects.requireNonNull(diagram2);
        return new LfReactiveDiagram() {
            @Override
            public double getMinQ(double p) {
                return diagram1.getMinQ(p) + diagram2.getMinQ(p);
            }

            @Override
            public double getMaxQ(double p) {
                return diagram1.getMaxQ(p) + diagram2.getMaxQ(p);
            }
        };
    }
}
