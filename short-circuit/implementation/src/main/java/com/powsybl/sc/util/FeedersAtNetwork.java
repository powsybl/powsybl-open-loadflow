/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.openloadflow.network.LfBus;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
// This class aims at organizing the information related to the feeders for a given equation system
public class FeedersAtNetwork {

    public final Map<LfBus, FeedersAtBus> busToFeeders = new HashMap<>();

}
