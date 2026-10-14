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
import com.powsybl.security.limitscaling.LimitScaling;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Olivier Perrin {@literal <olivier.perrin at rte-france.com>}
 */
class LimitScalingManagerTest {

    @Test
    void creationTest1() {
        LimitScaling limitScaling1 = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new IdentifiableCriterion(
                        new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(380., 410., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false))
                .build();
        LimitScaling limitScaling2 = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new IdentifiableCriterion(
                        new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(220., 240., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false))
                .build();
        LimitScaling limitScaling3 = LimitScaling.builder(LimitType.CURRENT, 0.95)
                .withNetworkElementCriteria(new IdentifiableCriterion(
                        new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(380., 410., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(300, 600, true, false))
                .build();
        LimitScaling limitScaling4 = LimitScaling.builder(LimitType.CURRENT, 0.95)
                .withNetworkElementCriteria(new IdentifiableCriterion(
                        new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(220., 240., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(300, 600, true, false))
                .build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(limitScaling1, limitScaling2, limitScaling3, limitScaling4));
        assertFalse(limitScalingManager.isEmpty());
        List<LimitScalingManager.TerminalLimitScaling> terminalLimitScalings = limitScalingManager.getTerminalLimitScalings();
        assertEquals(4, terminalLimitScalings.size());
        assertTerminalLimitScaling(0.9, Range.of(380., 410.), false, Range.of(0, 299), terminalLimitScalings.get(0));
        assertTerminalLimitScaling(0.9, Range.of(220., 240.), false, Range.of(0, 299), terminalLimitScalings.get(1));
        assertTerminalLimitScaling(0.95, Range.of(380., 410.), false, Range.of(300, 599), terminalLimitScalings.get(2));
        assertTerminalLimitScaling(0.95, Range.of(220., 240.), false, Range.of(300, 599), terminalLimitScalings.get(3));
    }

    private void assertTerminalLimitScaling(double expectedScaling, Range<Double> expectedNominalV,
                                              boolean expectedIsPermanent, Range<Integer> expectedAcceptableDuration,
                                              LimitScalingManager.TerminalLimitScaling actual) {
        assertEquals(expectedScaling, actual.scaling(), 0.001);
        assertEquals(expectedNominalV.getMinimum(), actual.nominalV().getMinimum(), 0.001);
        assertEquals(expectedNominalV.getMaximum(), actual.nominalV().getMaximum(), 0.001);
        assertEquals(expectedIsPermanent, actual.isPermanent());
        if (expectedAcceptableDuration == null) {
            assertNull(actual.acceptableDuration());
        } else {
            assertEquals(expectedAcceptableDuration.getMinimum(), actual.acceptableDuration().getMinimum());
            assertEquals(expectedAcceptableDuration.getMaximum(), actual.acceptableDuration().getMaximum());
        }
    }

    @Test
    void creationTest2() {
        LimitScaling limitScaling1 = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(380., 410., true, true))),
                        new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(220., 240., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false))
                .build();
        LimitScaling limitScaling2 = LimitScaling.builder(LimitType.CURRENT, 0.95)
                .withNetworkElementCriteria(new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(380., 410., true, true))),
                        new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(220., 240., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(300, 600, true, false))
                .build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(limitScaling1, limitScaling2));
        assertFalse(limitScalingManager.isEmpty());
        List<LimitScalingManager.TerminalLimitScaling> terminalLimitScalings = limitScalingManager.getTerminalLimitScalings();
        assertEquals(4, terminalLimitScalings.size());
        assertTerminalLimitScaling(0.9, Range.of(380., 410.), false, Range.of(0, 299), terminalLimitScalings.get(0));
        assertTerminalLimitScaling(0.9, Range.of(220., 240.), false, Range.of(0, 299), terminalLimitScalings.get(1));
        assertTerminalLimitScaling(0.95, Range.of(380., 410.), false, Range.of(300, 599), terminalLimitScalings.get(2));
        assertTerminalLimitScaling(0.95, Range.of(220., 240.), false, Range.of(300, 599), terminalLimitScalings.get(3));
    }

    @Test
    void durationCriteriaTest1() {
        LimitScaling all = LimitScaling.builder(LimitType.CURRENT, 0.97)
                .build();
        LimitScaling interval = LimitScaling.builder(LimitType.CURRENT, 0.8)
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.lowerThan(120, true))
                .build();
        LimitScaling equality = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withLimitDurationCriteria(new EqualityTemporaryDurationCriterion(300))
                .build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(all, interval, equality));
        assertFalse(limitScalingManager.isEmpty());
        List<LimitScalingManager.TerminalLimitScaling> terminalLimitScalings = limitScalingManager.getTerminalLimitScalings();
        assertEquals(3, terminalLimitScalings.size());
        assertTerminalLimitScaling(0.97, Range.of(0., Double.MAX_VALUE), true, Range.of(0, Integer.MAX_VALUE), terminalLimitScalings.get(0));
        assertTerminalLimitScaling(0.8, Range.of(0., Double.MAX_VALUE), false, Range.of(0, 120), terminalLimitScalings.get(1));
        assertTerminalLimitScaling(0.9, Range.of(0., Double.MAX_VALUE), false, Range.of(300, 300), terminalLimitScalings.get(2));
    }

    @Test
    void durationCriteriaTest2() {
        LimitScaling allTemporary = LimitScaling.builder(LimitType.CURRENT, 0.95)
                .withLimitDurationCriteria(new AllTemporaryDurationCriterion())
                .build();
        LimitScaling permanentAndTemporary = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withLimitDurationCriteria(new PermanentDurationCriterion(),
                        IntervalTemporaryDurationCriterion.lowerThan(120, true))
                .build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(allTemporary, permanentAndTemporary));
        assertFalse(limitScalingManager.isEmpty());
        List<LimitScalingManager.TerminalLimitScaling> terminalLimitScalings = limitScalingManager.getTerminalLimitScalings();
        assertEquals(2, terminalLimitScalings.size());
        assertTerminalLimitScaling(0.95, Range.of(0., Double.MAX_VALUE), false, Range.of(0, Integer.MAX_VALUE), terminalLimitScalings.get(0));
        assertTerminalLimitScaling(0.9, Range.of(0., Double.MAX_VALUE), true, Range.of(0, 120), terminalLimitScalings.get(1));
    }

    @Test
    void unsupportedContingencyContextsTest() {
        LimitScaling none = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withContingencyContext(ContingencyContext.none())
                .build();
        LimitScaling onlyContingencies = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withContingencyContext(ContingencyContext.onlyContingencies())
                .build();
        LimitScaling specific = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withContingencyContext(ContingencyContext.specificContingency("contingency1"))
                .build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(none, onlyContingencies, specific));
        assertTrue(limitScalingManager.isEmpty());
        List<LimitScalingManager.TerminalLimitScaling> terminalLimitScalings = limitScalingManager.getTerminalLimitScalings();
        assertEquals(0, terminalLimitScalings.size());
    }

    @Test
    void monitoringOnlyTest() {
        LimitScaling monitoringOnly = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withMonitoringOnly(true)
                .build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(monitoringOnly));
        assertTrue(limitScalingManager.isEmpty());
        List<LimitScalingManager.TerminalLimitScaling> terminalLimitScalings = limitScalingManager.getTerminalLimitScalings();
        assertEquals(0, terminalLimitScalings.size());
    }

    @Test
    void unsupportedLimitTypesTest() {
        LimitScaling activePower = LimitScaling.builder(LimitType.ACTIVE_POWER, 0.9).build();
        LimitScaling apparentPower = LimitScaling.builder(LimitType.APPARENT_POWER, 0.9).build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(activePower, apparentPower));
        assertTrue(limitScalingManager.isEmpty());
        List<LimitScalingManager.TerminalLimitScaling> terminalLimitScalings = limitScalingManager.getTerminalLimitScalings();
        assertEquals(0, terminalLimitScalings.size());
    }

    @Test
    void unsupportedNetworkElementCriteriaTest() {
        IdentifiableCriterion identifiableCriterion = new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(380., 410., true, true)));
        LineCriterion lineCriterion = new LineCriterion(null, new TwoNominalVoltageCriterion(
                VoltageInterval.between(40., 100., true, true),
                null));
        LimitScaling lineCriterionScaling = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(lineCriterion)
                .build();
        LimitScaling boundaryLineCriterionScaling = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new BoundaryLineCriterion(new SingleCountryCriterion(List.of(Country.BE)), null))
                .build();
        LimitScaling networkElementIdListCriterionScaling = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new NetworkElementIdListCriterion(Set.of("Id1", "Id2")))
                .build();
        LimitScaling threeWindingsTransformerCriterionScaling = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new ThreeWindingsTransformerCriterion(null, null))
                .build();
        LimitScaling tieLineCriterionScaling = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new TieLineCriterion(null, null))
                .build();
        LimitScaling twoWindingsTransformerCriterionScaling = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new TwoWindingsTransformerCriterion(null, null))
                .build();
        LimitScaling scalingWithALineCriterion = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(identifiableCriterion, lineCriterion)
                .build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(lineCriterionScaling,
                boundaryLineCriterionScaling, networkElementIdListCriterionScaling,
                threeWindingsTransformerCriterionScaling, tieLineCriterionScaling,
                twoWindingsTransformerCriterionScaling,
                scalingWithALineCriterion));
        assertTrue(limitScalingManager.isEmpty());
        List<LimitScalingManager.TerminalLimitScaling> terminalLimitScalings = limitScalingManager.getTerminalLimitScalings();
        assertEquals(0, terminalLimitScalings.size());
    }

    @Test
    void noMoreThanTwoDurationCriteriaTest() {
        LimitScaling limitScaling = LimitScaling.builder(LimitType.CURRENT, 0.95)
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false),
                        IntervalTemporaryDurationCriterion.between(600, 900, true, false),
                        IntervalTemporaryDurationCriterion.between(900, 1200, true, false))
                .build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(limitScaling));
        assertTrue(limitScalingManager.isEmpty());
        List<LimitScalingManager.TerminalLimitScaling> terminalLimitScalings = limitScalingManager.getTerminalLimitScalings();
        assertEquals(0, terminalLimitScalings.size());
    }

    @Test
    void twoDurationCriteriaOfSameTypeTest() {
        LimitScaling limitScaling = LimitScaling.builder(LimitType.CURRENT, 0.95)
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false),
                        IntervalTemporaryDurationCriterion.between(600, 900, true, false))
                .build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(limitScaling));
        assertTrue(limitScalingManager.isEmpty());
        List<LimitScalingManager.TerminalLimitScaling> terminalLimitScalings = limitScalingManager.getTerminalLimitScalings();
        assertEquals(0, terminalLimitScalings.size());
    }

    @Test
    void limitScalingsSpecifiedOperationalLimitsGroupNotSupportedTest() {
        String limitGroup1 = "limitGroup1";
        LimitScaling limitScaling = LimitScaling.builder(LimitType.CURRENT, 0.9)
                .withNetworkElementCriteria(new IdentifiableCriterion(
                        new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(220., 240., true, true))))
                .withLimitDurationCriteria(IntervalTemporaryDurationCriterion.between(0, 300, true, false))
                .withOperationalLimitsGroupIdSelection(limitGroup1)
                .build();
        LimitScalingManager limitScalingManager = LimitScalingManager.create(List.of(limitScaling));
        assertTrue(limitScalingManager.isEmpty());
    }
}
