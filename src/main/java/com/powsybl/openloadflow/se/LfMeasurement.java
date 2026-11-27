/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.network.LfElement;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One meter reading, already normalized to the conventions the estimator works in:
 *
 * <ul>
 *   <li>per unit, on the network's own base;</li>
 *   <li>bus injections generation-positive: power fed into the network is positive;</li>
 *   <li>branch quantities positive when flowing from the bus into the branch at the
 *       given side, the side being resolved against the branch orientation.</li>
 * </ul>
 *
 * @param type     what is measured, and at which side for a branch quantity
 * @param element  the bus or branch the reading is on
 * @param valuePu  the reading, per unit
 * @param sigmaPu  the standard deviation of the reading, per unit; strictly positive
 * @param sourceMeasurementIds the IIDM measurements behind this reading; several if they were summed
 *                             or merged, empty for a pseudo-measurement or a measurement without an id
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public record LfMeasurement(AcEquationType type, LfElement element, double valuePu, double sigmaPu,
                            List<String> sourceMeasurementIds) {

    private static final Set<AcEquationType> MEASURABLE = EnumSet.of(
            AcEquationType.BUS_TARGET_V, AcEquationType.BUS_TARGET_P, AcEquationType.BUS_TARGET_Q,
            AcEquationType.BRANCH_TARGET_P1, AcEquationType.BRANCH_TARGET_P2,
            AcEquationType.BRANCH_TARGET_Q1, AcEquationType.BRANCH_TARGET_Q2,
            AcEquationType.BRANCH_TARGET_I1, AcEquationType.BRANCH_TARGET_I2);

    public LfMeasurement {
        Objects.requireNonNull(type);
        Objects.requireNonNull(element);
        sourceMeasurementIds = List.copyOf(Objects.requireNonNull(sourceMeasurementIds));
        if (!MEASURABLE.contains(type)) {
            throw new IllegalArgumentException(type + " is not a measurable quantity");
        }
        if (element.getType() != type.getElementType()) {
            throw new IllegalArgumentException(type + " cannot be measured on " + element.getType() + " " + element.getId());
        }
        if (sigmaPu <= 0) {
            throw new IllegalArgumentException("LfMeasurement standard deviation must be positive, was " + sigmaPu);
        }
    }

    public static LfMeasurement of(AcEquationType type, LfElement element, double valuePu, double sigmaPu,
                                   String... sourceMeasurementIds) {
        return new LfMeasurement(type, element, valuePu, sigmaPu, List.of(sourceMeasurementIds));
    }

    /** The weight this reading carries in the estimate: the inverse of its variance. */
    public double weight() {
        return 1.0 / (sigmaPu * sigmaPu);
    }
}
