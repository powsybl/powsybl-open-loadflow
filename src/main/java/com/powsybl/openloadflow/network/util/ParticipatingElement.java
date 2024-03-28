/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfLoad;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class ParticipatingElement {

    private final Object element;

    private double factor;

    public ParticipatingElement(Object element, double factor) {
        this.element = element;
        this.factor = factor;
    }

    public Object getElement() {
        return element;
    }

    public LfGenerator getLfGeneratorElement() {
        if (element instanceof LfGenerator lfGenerator) {
            return lfGenerator;
        } else {
            return null;
        }
    }

    public double getFactor() {
        return factor;
    }

    public static double participationFactorNorm(List<ParticipatingElement> participatingElements) {
        return participatingElements.stream()
                .mapToDouble(participatingGenerator -> participatingGenerator.factor)
                .sum();
    }

    public static void normalizeParticipationFactors(List<ParticipatingElement> participatingElements) {
        double factorSum = participationFactorNorm(participatingElements);
        for (ParticipatingElement participatingElement : participatingElements) {
            participatingElement.factor /= factorSum;
        }
    }

    public LfBus getLfBus() {
        if (element instanceof LfGenerator generator) {
            return generator.getBus();
        } else if (element instanceof LfLoad load) {
            return load.getBus();
        } else {
            return null;
        }
    }
}

