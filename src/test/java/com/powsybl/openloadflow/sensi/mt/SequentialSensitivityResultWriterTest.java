/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi.mt;

import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.sensitivity.SensitivityResultModelWriter;
import com.powsybl.sensitivity.SensitivityState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status.CONVERGED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SequentialSensitivityResultWriterTest {

    @Test
    void testPreContingencyOperatorStrategyStatusWrittenOnce() {
        List<Contingency> contingencies = List.of(new Contingency("c", new BranchContingency("l")));
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("os", ContingencyContext.none(), new TrueCondition(), List.of("a")));
        SensitivityResultModelWriter modelWriter = new SensitivityResultModelWriter(contingencies, operatorStrategies);

        try (SequentialSensitivityResultWriter writer = new SequentialSensitivityResultWriter(modelWriter)) {
            // as written by 5 threads each computing the pre-contingency operator strategy
            for (int i = 0; i < 5; i++) {
                writer.writeStateStatus(-1, 0, 0, 0, CONVERGED, "");
            }
        }

        assertEquals(1, countStatuses(modelWriter, new SensitivityState(null, "os")));
    }

    private static int countStatuses(SensitivityResultModelWriter modelWriter, SensitivityState state) {
        return modelWriter.getStateStatuses().stream()
                .filter(s -> s.getState().equals(state))
                .findFirst().orElseThrow()
                .getComponentsLoadFlowStatusList().size();
    }

    @Test
    void testComputationCompleteNotSupported() {
        SensitivityResultModelWriter modelWriter = new SensitivityResultModelWriter(List.of(), List.of());
        try (SequentialSensitivityResultWriter writer = new SequentialSensitivityResultWriter(modelWriter)) {
            assertThrows(UnsupportedOperationException.class, writer::computationComplete);
            assertThrows(UnsupportedOperationException.class, writer::isComputationComplete);
        }
        assertFalse(modelWriter.isComputationComplete());
    }
}
