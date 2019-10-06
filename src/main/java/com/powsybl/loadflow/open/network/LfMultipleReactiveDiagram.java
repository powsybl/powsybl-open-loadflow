/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.open.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfMultipleReactiveDiagram implements LfReactiveDiagram {

    private final List<LfReactiveDiagram> diagrams = new ArrayList<>();

    public void addDiagram(LfReactiveDiagram diagram) {
        diagrams.add(Objects.requireNonNull(diagram));
    }

    @Override
    public double getMinQ(double p) {
        return diagrams.stream().mapToDouble(diagram -> diagram.getMinQ(p / diagrams.size())).sum();
    }

    @Override
    public double getMaxQ(double p) {
        return diagrams.stream().mapToDouble(diagram -> diagram.getMaxQ(p / diagrams.size())).sum();
    }

    @Override
    public double getMaxRangeQ() {
        return diagrams.stream().mapToDouble(LfReactiveDiagram::getMaxRangeQ).sum();
    }
}
