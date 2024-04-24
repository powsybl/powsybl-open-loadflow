/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.criteria.*;
import com.powsybl.iidm.criteria.duration.AllTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.EqualityTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.IntervalTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.PermanentDurationCriterion;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.security.limitreduction.LimitReduction;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Olivier Perrin {@literal <olivier.perrin at rte-france.com>}
 */
class LimitReductionManagerTest {

    @Test
    void creationTest1() {
        LimitReduction limitReduction1 = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new IdentifiableCriterion(
                        new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(380., 410., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false))
                .build();
        LimitReduction limitReduction2 = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new IdentifiableCriterion(
                        new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(220., 240., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false))
                .build();
        LimitReduction limitReduction3 = LimitReduction.builder(LimitType.CURRENT, 0.95)
                .withNetworkElementCriteria(new IdentifiableCriterion(
                        new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(380., 410., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(300, 600, true, false))
                .build();
        LimitReduction limitReduction4 = LimitReduction.builder(LimitType.CURRENT, 0.95)
                .withNetworkElementCriteria(new IdentifiableCriterion(
                        new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(220., 240., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(300, 600, true, false))
                .build();
        LimitReductionManager limitReductionManager = LimitReductionManager.create(List.of(limitReduction1, limitReduction2, limitReduction3, limitReduction4));
        assertFalse(limitReductionManager.isEmpty());
        List<LimitReductionManager.TerminalLimitReduction> terminalLimitReductions = limitReductionManager.getTerminalLimitReductions();
        assertEquals(4, terminalLimitReductions.size());
        assertTerminalLimitReduction(0.9, Range.of(380., 410.), false, Range.of(0, 299), terminalLimitReductions.get(0));
        assertTerminalLimitReduction(0.9, Range.of(220., 240.), false, Range.of(0, 299), terminalLimitReductions.get(1));
        assertTerminalLimitReduction(0.95, Range.of(380., 410.), false, Range.of(300, 599), terminalLimitReductions.get(2));
        assertTerminalLimitReduction(0.95, Range.of(220., 240.), false, Range.of(300, 599), terminalLimitReductions.get(3));
    }

    private void assertTerminalLimitReduction(double expectedReduction, Range<Double> expectedNominalV,
                                              boolean expectedIsPermanent, Range<Integer> expectedAcceptableDuration,
                                              LimitReductionManager.TerminalLimitReduction actual) {
        assertEquals(expectedReduction, actual.getReduction(), 0.001);
        assertEquals(expectedNominalV.getMinimum(), actual.getNominalV().getMinimum(), 0.001);
        assertEquals(expectedNominalV.getMaximum(), actual.getNominalV().getMaximum(), 0.001);
        assertEquals(expectedIsPermanent, actual.isPermanent());
        if (expectedAcceptableDuration == null) {
            assertNull(actual.getAcceptableDuration());
        } else {
            assertEquals(expectedAcceptableDuration.getMinimum(), actual.getAcceptableDuration().getMinimum());
            assertEquals(expectedAcceptableDuration.getMaximum(), actual.getAcceptableDuration().getMaximum());
        }
    }

    @Test
    void creationTest2() {
        LimitReduction limitReduction1 = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(380., 410., true, true))),
                        new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(220., 240., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false))
                .build();
        LimitReduction limitReduction2 = LimitReduction.builder(LimitType.CURRENT, 0.95)
                .withNetworkElementCriteria(new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(380., 410., true, true))),
                        new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(220., 240., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(300, 600, true, false))
                .build();
        LimitReductionManager limitReductionManager = LimitReductionManager.create(List.of(limitReduction1, limitReduction2));
        assertFalse(limitReductionManager.isEmpty());
        List<LimitReductionManager.TerminalLimitReduction> terminalLimitReductions = limitReductionManager.getTerminalLimitReductions();
        assertEquals(4, terminalLimitReductions.size());
        assertTerminalLimitReduction(0.9, Range.of(380., 410.), false, Range.of(0, 299), terminalLimitReductions.get(0));
        assertTerminalLimitReduction(0.9, Range.of(220., 240.), false, Range.of(0, 299), terminalLimitReductions.get(1));
        assertTerminalLimitReduction(0.95, Range.of(380., 410.), false, Range.of(300, 599), terminalLimitReductions.get(2));
        assertTerminalLimitReduction(0.95, Range.of(220., 240.), false, Range.of(300, 599), terminalLimitReductions.get(3));
    }

    @Test
    void durationCriteriaTest1() {
        LimitReduction all = LimitReduction.builder(LimitType.CURRENT, 0.97)
                .build();
        LimitReduction interval = LimitReduction.builder(LimitType.CURRENT, 0.8)
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.lowerThan(120, true))
                .build();
        LimitReduction equality = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withLimitDurationCriteria(new EqualityTemporaryDurationCriterion(300))
                .build();
        LimitReductionManager limitReductionManager = LimitReductionManager.create(List.of(all, interval, equality));
        assertFalse(limitReductionManager.isEmpty());
        List<LimitReductionManager.TerminalLimitReduction> terminalLimitReductions = limitReductionManager.getTerminalLimitReductions();
        assertEquals(3, terminalLimitReductions.size());
        assertTerminalLimitReduction(0.97, Range.of(0., Double.MAX_VALUE), true, Range.of(0, Integer.MAX_VALUE), terminalLimitReductions.get(0));
        assertTerminalLimitReduction(0.8, Range.of(0., Double.MAX_VALUE), false, Range.of(0, 120), terminalLimitReductions.get(1));
        assertTerminalLimitReduction(0.9, Range.of(0., Double.MAX_VALUE), false, Range.of(300, 300), terminalLimitReductions.get(2));
    }

    @Test
    void durationCriteriaTest2() {
        LimitReduction allTemporary = LimitReduction.builder(LimitType.CURRENT, 0.95)
                .withLimitDurationCriteria(new AllTemporaryDurationCriterion())
                .build();
        LimitReduction permanentAndTemporary = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withLimitDurationCriteria(new PermanentDurationCriterion(),
                        IntervalTemporaryDurationCriterion.lowerThan(120, true))
                .build();
        LimitReductionManager limitReductionManager = LimitReductionManager.create(List.of(allTemporary, permanentAndTemporary));
        assertFalse(limitReductionManager.isEmpty());
        List<LimitReductionManager.TerminalLimitReduction> terminalLimitReductions = limitReductionManager.getTerminalLimitReductions();
        assertEquals(2, terminalLimitReductions.size());
        assertTerminalLimitReduction(0.95, Range.of(0., Double.MAX_VALUE), false, Range.of(0, Integer.MAX_VALUE), terminalLimitReductions.get(0));
        assertTerminalLimitReduction(0.9, Range.of(0., Double.MAX_VALUE), true, Range.of(0, 120), terminalLimitReductions.get(1));
    }

    @Test
    void unsupportedContingencyContextsTest() {
        LimitReduction none = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withContingencyContext(ContingencyContext.none())
                .build();
        LimitReduction onlyContingencies = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withContingencyContext(ContingencyContext.onlyContingencies())
                .build();
        LimitReduction specific = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withContingencyContext(ContingencyContext.specificContingency("contingency1"))
                .build();
        LimitReductionManager limitReductionManager = LimitReductionManager.create(List.of(none, onlyContingencies, specific));
        assertTrue(limitReductionManager.isEmpty());
        List<LimitReductionManager.TerminalLimitReduction> terminalLimitReductions = limitReductionManager.getTerminalLimitReductions();
        assertEquals(0, terminalLimitReductions.size());
    }

    @Test
    void monitoringOnlyTest() {
        LimitReduction monitoringOnly = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withMonitoringOnly(true)
                .build();
        LimitReductionManager limitReductionManager = LimitReductionManager.create(List.of(monitoringOnly));
        assertTrue(limitReductionManager.isEmpty());
        List<LimitReductionManager.TerminalLimitReduction> terminalLimitReductions = limitReductionManager.getTerminalLimitReductions();
        assertEquals(0, terminalLimitReductions.size());
    }

    @Test
    void unsupportedLimitTypesTest() {
        LimitReduction activePower = LimitReduction.builder(LimitType.ACTIVE_POWER, 0.9).build();
        LimitReduction apparentPower = LimitReduction.builder(LimitType.APPARENT_POWER, 0.9).build();
        LimitReductionManager limitReductionManager = LimitReductionManager.create(List.of(activePower, apparentPower));
        assertTrue(limitReductionManager.isEmpty());
        List<LimitReductionManager.TerminalLimitReduction> terminalLimitReductions = limitReductionManager.getTerminalLimitReductions();
        assertEquals(0, terminalLimitReductions.size());
    }

    @Test
    void unsupportedNetworkElementCriteriaTest() {
        IdentifiableCriterion identifiableCriterion = new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(380., 410., true, true)));
        LineCriterion lineCriterion = new LineCriterion(null, new TwoNominalVoltageCriterion(
                VoltageInterval.between(40., 100., true, true),
                null));
        LimitReduction lineCriterionReduction = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(lineCriterion)
                .build();
        LimitReduction danglingLineCriterionReduction = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new DanglingLineCriterion(new SingleCountryCriterion(List.of(Country.BE)), null))
                .build();
        LimitReduction networkElementIdListCriterionReduction = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new NetworkElementIdListCriterion(Set.of("Id1", "Id2")))
                .build();
        LimitReduction threeWindingsTransformerCriterionReduction = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new ThreeWindingsTransformerCriterion(null, null))
                .build();
        LimitReduction tieLineCriterionReduction = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new TieLineCriterion(null, null))
                .build();
        LimitReduction twoWindingsTransformerCriterionReduction = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new TwoWindingsTransformerCriterion(null, null))
                .build();
        LimitReduction reductionWithALineCriterion = LimitReduction.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(identifiableCriterion, lineCriterion)
                .build();
        LimitReductionManager limitReductionManager = LimitReductionManager.create(List.of(lineCriterionReduction,
                danglingLineCriterionReduction, networkElementIdListCriterionReduction,
                threeWindingsTransformerCriterionReduction, tieLineCriterionReduction,
                twoWindingsTransformerCriterionReduction,
                reductionWithALineCriterion));
        assertTrue(limitReductionManager.isEmpty());
        List<LimitReductionManager.TerminalLimitReduction> terminalLimitReductions = limitReductionManager.getTerminalLimitReductions();
        assertEquals(0, terminalLimitReductions.size());
    }

    @Test
    void noMoreThanTwoDurationCriteriaTest() {
        LimitReduction limitReduction = LimitReduction.builder(LimitType.CURRENT, 0.95)
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false),
                        IntervalTemporaryDurationCriterion.between(600, 900, true, false),
                        IntervalTemporaryDurationCriterion.between(900, 1200, true, false))
                .build();
        LimitReductionManager limitReductionManager = LimitReductionManager.create(List.of(limitReduction));
        assertTrue(limitReductionManager.isEmpty());
        List<LimitReductionManager.TerminalLimitReduction> terminalLimitReductions = limitReductionManager.getTerminalLimitReductions();
        assertEquals(0, terminalLimitReductions.size());
    }

    @Test
    void twoDurationCriteriaOfSameTypeTest() {
        LimitReduction limitReduction = LimitReduction.builder(LimitType.CURRENT, 0.95)
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false),
                        IntervalTemporaryDurationCriterion.between(600, 900, true, false))
                .build();
        LimitReductionManager limitReductionManager = LimitReductionManager.create(List.of(limitReduction));
        assertTrue(limitReductionManager.isEmpty());
        List<LimitReductionManager.TerminalLimitReduction> terminalLimitReductions = limitReductionManager.getTerminalLimitReductions();
        assertEquals(0, terminalLimitReductions.size());
    }
}
