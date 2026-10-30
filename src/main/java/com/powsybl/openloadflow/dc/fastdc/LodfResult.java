/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.math.matrix.SparseMatrix;

import java.util.List;

/**
 * @param lodfMatrix rows: monitored branches, columns: outaged branches; factors below the threshold are not stored
 * @param undefinedOutageIndices outages with an empty column: branch not closed, connectivity break or zero impedance
 *
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
public record LodfResult(SparseMatrix lodfMatrix, List<Integer> undefinedOutageIndices) {
}
