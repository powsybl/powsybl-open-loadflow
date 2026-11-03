/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.commons.PowsyblException;

/**
 * Stored LODF: for each outage, the {@code maxFactorsPerOutage} largest factors with {@code |LODF| >= threshold}. The -1
 * of the outaged branch itself is always kept and not counted.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
public class LodfParameters {

    public static final double DEFAULT_THRESHOLD = 0.05;

    public static final int DEFAULT_MAX_FACTORS_PER_OUTAGE = Integer.MAX_VALUE;

    public static final int DEFAULT_OUTAGE_BATCH_SIZE = 500;

    private double threshold = DEFAULT_THRESHOLD;

    private int maxFactorsPerOutage = DEFAULT_MAX_FACTORS_PER_OUTAGE;

    private int outageBatchSize = DEFAULT_OUTAGE_BATCH_SIZE;

    public double getThreshold() {
        return threshold;
    }

    /**
     * @param threshold minimum {@code |LODF|} kept, 0.05 keeps the branches receiving at least 5% of the lost flow, 0 keeps all
     */
    public LodfParameters setThreshold(double threshold) {
        if (Double.isNaN(threshold) || threshold < 0) {
            throw new PowsyblException("LODF threshold must be positive or zero: " + threshold);
        }
        this.threshold = threshold;
        return this;
    }

    public int getMaxFactorsPerOutage() {
        return maxFactorsPerOutage;
    }

    public LodfParameters setMaxFactorsPerOutage(int maxFactorsPerOutage) {
        if (maxFactorsPerOutage < 0) {
            throw new PowsyblException("LODF max factors per outage must be positive or zero: " + maxFactorsPerOutage);
        }
        this.maxFactorsPerOutage = maxFactorsPerOutage;
        return this;
    }

    public int getOutageBatchSize() {
        return outageBatchSize;
    }

    /**
     * @param outageBatchSize number of outages solved together, the dense states matrix takes equation count x batch size
     *                        x 8 bytes. Capped to the largest dense matrix (2 GB). It does not change the result.
     */
    public LodfParameters setOutageBatchSize(int outageBatchSize) {
        if (outageBatchSize < 1) {
            throw new PowsyblException("LODF outage batch size must be strictly positive: " + outageBatchSize);
        }
        this.outageBatchSize = outageBatchSize;
        return this;
    }
}
