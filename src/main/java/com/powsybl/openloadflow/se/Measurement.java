/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.se.equations.MeasuredQuantity;

import java.util.Objects;

/**
 * One meter reading, already normalized to the conventions the estimator works in:
 *
 * <ul>
 *   <li>per unit, on the network's own base;</li>
 *   <li>bus injections generation-positive: power fed into the network is positive,
 *       which equals the sum of the flows leaving the bus into its branches;</li>
 *   <li>branch quantities positive when flowing from the bus into the branch at the
 *       given side, the side being resolved against the branch orientation.</li>
 * </ul>
 *
 * <p>Exactly one of {@code bus} and {@code branch} is set, according to the quantity.</p>
 *
 * @param quantity what is measured
 * @param bus      the bus, for bus quantities; null otherwise
 * @param branch   the branch, for branch quantities; null otherwise
 * @param side     1 or 2 for branch quantities; 0 for bus quantities
 * @param valuePu  the reading, per unit
 * @param sigmaPu  the standard deviation of the reading, per unit; strictly positive
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public record Measurement(MeasuredQuantity quantity, LfBus bus, LfBranch branch, int side,
                          double valuePu, double sigmaPu) {

    public Measurement {
        Objects.requireNonNull(quantity);
        if (sigmaPu <= 0) {
            throw new IllegalArgumentException("Measurement standard deviation must be positive, was " + sigmaPu);
        }
    }

    /** A voltage magnitude reading at a bus, per unit of nominal voltage. */
    public static Measurement busVoltage(LfBus bus, double valuePu, double sigmaPu) {
        return new Measurement(MeasuredQuantity.BUS_V, Objects.requireNonNull(bus), null, 0, valuePu, sigmaPu);
    }

    /** An injection reading at a bus, generation-positive, per unit. */
    public static Measurement busInjection(LfBus bus, boolean active, double valuePu, double sigmaPu) {
        return new Measurement(active ? MeasuredQuantity.BUS_P : MeasuredQuantity.BUS_Q,
                Objects.requireNonNull(bus), null, 0, valuePu, sigmaPu);
    }

    /** A flow or current reading at one side of a branch, per unit. */
    public static Measurement branchFlow(LfBranch branch, MeasuredQuantity quantity, int side,
                                         double valuePu, double sigmaPu) {
        return new Measurement(quantity, null, Objects.requireNonNull(branch), side, valuePu, sigmaPu);
    }

    /** The weight this reading carries in the estimate: the inverse of its variance. */
    public double weight() {
        return 1.0 / (sigmaPu * sigmaPu);
    }
}
