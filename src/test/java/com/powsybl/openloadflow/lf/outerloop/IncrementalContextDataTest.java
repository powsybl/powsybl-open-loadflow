/*
 * Copyright (c) 2026, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.OpenLoadFlowParameters.IncrementalControlInteractionScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class IncrementalContextDataTest {

    @Test
    void testMismatchPredictionRejectsCoupledOvershoot() {
        // two controlled elements both 1.5 below target, half deadband 0.5
        var prediction = new IncrementalContextData.MismatchPrediction<>(List.of("a", "b"), e -> 1.5, e -> 0.5,
                IncrementalControlInteractionScope.ALL_CONTROLLED_ELEMENTS);

        // first controller fixes "a" and helps "b" through cross effect
        assertTrue(prediction.applyIfImproved("a", e -> e.equals("a") ? 1.5 : 1.0));
        assertEquals(0, prediction.getMismatch("a"), 1e-12);
        assertEquals(0.5, prediction.getMismatch("b"), 1e-12);

        // second controller sized on "b" alone would make "a" overshoot out of its deadband
        assertFalse(prediction.applyIfImproved("b", e -> e.equals("b") ? 1.5 : 1.0));
        assertEquals(0, prediction.getMismatch("a"), 1e-12);
        assertEquals(0.5, prediction.getMismatch("b"), 1e-12);
    }

    @Test
    void testMismatchPredictionSameControlledElementScope() {
        // two controlled elements both 1.5 below target, half deadband 0.5
        var prediction = new IncrementalContextData.MismatchPrediction<>(List.of("a", "b"), e -> 1.5, e -> 0.5,
                IncrementalControlInteractionScope.SAME_CONTROLLED_ELEMENT);

        // the cross effect on "b" of the move done for "a" is ignored
        assertTrue(prediction.applyIfImproved("a", e -> e.equals("a") ? 1.5 : 1.0));
        assertEquals(0, prediction.getMismatch("a"), 1e-12);
        assertEquals(1.5, prediction.getMismatch("b"), 1e-12);

        // so is the cross effect on "a" of the move done for "b"
        assertTrue(prediction.applyIfImproved("b", e -> e.equals("b") ? 1.5 : 1.0));
        assertEquals(0, prediction.getMismatch("a"), 1e-12);
        assertEquals(0, prediction.getMismatch("b"), 1e-12);

        // a move worsening its own controlled element is still rejected
        assertFalse(prediction.applyIfImproved("a", e -> 1.0));
        assertEquals(0, prediction.getMismatch("a"), 1e-12);
    }

    @Test
    void testSortByDecreasingExcess() {
        Map<String, Double> mismatches = Map.of("a", 1.0, "b", -3.0, "c", 2.0);
        Map<String, Double> halfDeadbands = Map.of("a", 0.1, "b", 0.1, "c", 1.5);
        var prediction = new IncrementalContextData.MismatchPrediction<>(List.of("a", "b", "c"), mismatches::get, halfDeadbands::get,
                IncrementalControlInteractionScope.ALL_CONTROLLED_ELEMENTS);
        assertEquals(List.of("b", "a", "c"), prediction.sortByDecreasingExcess(List.of("a", "b", "c")));
    }
}
