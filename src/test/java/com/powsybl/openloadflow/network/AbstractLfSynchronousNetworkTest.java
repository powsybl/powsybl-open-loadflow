/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public abstract class AbstractLfSynchronousNetworkTest {

    LfNetwork lfNetwork;

    @BeforeEach
    void setUp() {
        // Three-AC-zone network: SC0=b1, SC1=b2, SC2=b3, linked by a multi-terminal DC network.
        Network network = AcDcNetworkFactory.createMtDcNetworkWithThreeAcZones();
        LfNetworkParameters params = new LfNetworkParameters().setAcDcNetwork(true);
        lfNetwork = Networks.load(network, params).getFirst();
    }

    // --- getNumSC ---

    @Test
    void testGetNumScReturnsTheExpectedValue() {
        assertEquals(0, lfNetwork.getSynchronousNetworks().getFirst().getNumSC());
        assertEquals(1, lfNetwork.getSynchronousNetworks().get(1).getNumSC());
        assertEquals(2, lfNetwork.getSynchronousNetworks().get(2).getNumSC());
    }

    // --- getBuses ---

    @Test
    void testGetBusesReturnsOnlyTheBusesInsideTheSynchronousComponent() {
        LfSynchronousNetwork firstSynchronousNetwork = lfNetwork.getSynchronousNetworks().getFirst();
        List<LfBus> firstScBuses = firstSynchronousNetwork.getBuses();
        assertEquals(List.of(lfNetwork.getBusById("b1_vl_0")), firstScBuses);

        LfSynchronousNetwork secondSynchronousNetwork = lfNetwork.getSynchronousNetworks().get(1);
        List<LfBus> secondScBuses = secondSynchronousNetwork.getBuses();
        assertEquals(List.of(lfNetwork.getBusById("b2_vl_0")), secondScBuses);

        LfSynchronousNetwork thirdSynchronousNetwork = lfNetwork.getSynchronousNetworks().get(2);
        List<LfBus> thirdScBuses = thirdSynchronousNetwork.getBuses();
        assertEquals(List.of(lfNetwork.getBusById("b3_vl_0")), thirdScBuses);
    }

    // --- getReferenceBus ---

    @Test
    void testGetReferenceBusBelongsToItsSynchronousComponent() {
        for (LfSynchronousNetwork syncNet : lfNetwork.getSynchronousNetworks()) {
            LfBus referenceBus = syncNet.getReferenceBus();
            assertNotNull(referenceBus);
            assertEquals(syncNet.getNumSC(), referenceBus.getNumSC());
            assertTrue(referenceBus.isReference());
        }
    }

    // --- getSlackBuses ---

    @Test
    void testGetSlackBusesIsNotEmptyAndEachOneBelongToTheSynchronousComponent() {
        for (LfSynchronousNetwork syncNet : lfNetwork.getSynchronousNetworks()) {
            List<LfBus> slackBuses = syncNet.getSlackBuses();
            assertFalse(slackBuses.isEmpty());
            for (LfBus slackBus : slackBuses) {
                assertEquals(syncNet.getNumSC(), slackBus.getNumSC());
                assertTrue(slackBus.isSlack());
            }
        }
    }

    // --- getReferenceGenerator ---

    @Test
    void testGetReferenceGeneratorIsNullWithReferenceBusFirstSlackSelector() {
        // ReferenceBusFirstSlackSelector picks the first slack bus as reference, not via a generator,
        // so getReferenceGenerator() must return null.
        for (LfSynchronousNetwork syncNet : lfNetwork.getSynchronousNetworks()) {
            assertNull(syncNet.getReferenceGenerator());
        }
    }

    @Test
    void testGetReferenceGeneratorIsNotNullWithReferenceBusGeneratorPrioritySelector() {
        // By setting reference bus selector to ReferenceBusGeneratorPrioritySelector, each synchronous network with a
        // generator will have a reference generator
        Network n = HvdcNetworkFactory.createLcc();
        n.getGenerator("g3").remove(); // First synchronous component will have a reference generator, not the second one

        LfNetworkParameters params = new LfNetworkParameters()
                .setReferenceBusSelector(ReferenceBusSelector.fromMode(ReferenceBusSelectionMode.GENERATOR_REFERENCE_PRIORITY));

        List<LfNetwork> lfNetworks = Networks.load(n, params);  // 2 LfNetwork as we do not have AC-DC networks

        // First synchronous component
        LfNetwork firstLfNetwork = lfNetworks.getFirst();
        LfSynchronousNetwork firstSyncNet = firstLfNetwork.getSynchronousNetworks().getFirst();
        assertEquals(firstLfNetwork.getGeneratorById("g1"), firstSyncNet.getReferenceGenerator());

        // Second synchronous component (has no generator)
        LfNetwork secondLfNetwork = lfNetworks.getLast();
        LfSynchronousNetwork secondSyncNet = secondLfNetwork.getSynchronousNetworks().getFirst();
        assertNull(secondSyncNet.getReferenceGenerator());
    }

    // --- setExcludedSlackBuses ---

    @Test
    void testSetExcludedSlackBusesFiltersOutBusesThatAreNotPartOfTheSynchronousComponent() {
        LfSynchronousNetwork synchronousNetwork = lfNetwork.getSynchronousNetwork(0);
        Set<LfBus> excludedSlackBuses = Set.of(
                lfNetwork.getBusById("b1_vl_0"), // Belongs to this synchronous network
                lfNetwork.getBusById("b2_vl_0") // Does not belong to this synchronous network
        );

        synchronousNetwork.setExcludedSlackBuses(excludedSlackBuses);
        // Check excluded slack bus only include b1_vl_0
        assertEquals(Set.of(lfNetwork.getBusById("b1_vl_0")), synchronousNetwork.getExcludedSlackBuses());
    }

    @Test
    void testSetExcludedSlackBusesExcludesTheBusFromSlackBusSelection() {
        // createLcc() has a first synchronous component has two buses, so excluding one still leaves selectable buses.
        Network n = HvdcNetworkFactory.createLcc();
        LfNetwork lfNet = Networks.load(n, new LfNetworkParameters()).getFirst();
        LfSynchronousNetwork syncNet = lfNet.getSynchronousNetwork(0);

        LfBus originalSlackBus = syncNet.getSlackBuses().getFirst();
        syncNet.setExcludedSlackBuses(Set.of(originalSlackBus));

        List<LfBus> newSlackBuses = syncNet.getSlackBuses();
        assertFalse(newSlackBuses.contains(originalSlackBus));
        assertFalse(originalSlackBus.isSlack());
    }

    @Test
    void testSetExcludedSlackBusesDoesNotInvalidateWhenSetIsUnchanged() {
        LfSynchronousNetwork syncNet = lfNetwork.getSynchronousNetwork(0);
        // Trigger initial selection.
        LfBus slackBus = syncNet.getSlackBuses().getFirst();

        // Passing the same (empty) excluded set must not invalidate the current selection.
        syncNet.setExcludedSlackBuses(Collections.emptySet());

        assertTrue(slackBus.isSlack());
        assertEquals(slackBus, syncNet.getSlackBuses().getFirst());
    }

    // --- invalidateSlackAndReference ---

    @Test
    void testInvalidateSlackAndReferenceAllowsRecomputation() {
        LfSynchronousNetwork syncNet = lfNetwork.getSynchronousNetwork(0);
        List<LfBus> slackBusesBefore = syncNet.getSlackBuses();
        LfBus referenceBusBefore = syncNet.getReferenceBus();

        syncNet.invalidateSlackAndReference();

        // After recomputation the same buses should be selected (the network is unchanged).
        assertEquals(slackBusesBefore, syncNet.getSlackBuses());
        assertEquals(referenceBusBefore, syncNet.getReferenceBus());
    }

    // --- validateBuses ---

    @ParameterizedTest
    @EnumSource(LoadFlowModel.class)
    void testValidateBusesReturnsInvalidIfThereIsNoGeneratorNorVoltageSourceConverter(LoadFlowModel loadFlowModel) {
        Network n = HvdcNetworkFactory.createLcc();
        // Second synchronous component is now without generator. The synchronous network is invalid.
        n.getGenerator("g3").remove();

        LfNetwork secondLfNetwork = Networks.load(n, new LfNetworkParameters()).getLast();

        LfSynchronousNetwork synchronousNetwork = secondLfNetwork.getSynchronousNetwork(1);
        LfNetwork.Validity validity = synchronousNetwork.validateBuses(loadFlowModel, ReportNode.NO_OP);

        assertEquals(LfNetwork.Validity.INVALID_NO_GENERATOR, validity);
    }

    @Test
    void testValidateBusesReturnsInvalidIfThereIsNoGeneratorNorVoltageSourceConverterControllingVoltageInAcMode() {
        Network network = AcDcNetworkFactory.createMtDcNetworkWithThreeAcZones();
        // There is no generator nor VSC controlling AC voltage. The synchronous network is invalid for AC load flow.
        network.getGenerator("g1").setTargetQ(0).setVoltageRegulatorOn(false);

        LfNetworkParameters params = new LfNetworkParameters().setAcDcNetwork(true);
        LfNetwork lfNet = Networks.load(network, params).getFirst();

        LfSynchronousNetwork synchronousNetwork = lfNet.getSynchronousNetwork(0);
        LfNetwork.Validity validity = synchronousNetwork.validateBuses(LoadFlowModel.AC, ReportNode.NO_OP);

        assertEquals(LfNetwork.Validity.INVALID_NO_GENERATOR_VOLTAGE_CONTROL, validity);
    }

    @Test
    void testValidateBusesReturnsValidIfThereIsNoGeneratorNorVoltageSourceConverterControllingVoltageInDcMode() {
        Network network = AcDcNetworkFactory.createMtDcNetworkWithThreeAcZones();
        // There is no generator nor VSC controlling AC voltage. The synchronous network is still valid for DC load flow.
        network.getGenerator("g1").setTargetQ(0).setVoltageRegulatorOn(false);

        LfNetworkParameters params = new LfNetworkParameters().setAcDcNetwork(true);
        LfNetwork lfNet = Networks.load(network, params).getFirst();

        LfSynchronousNetwork synchronousNetwork = lfNet.getSynchronousNetwork(0);
        LfNetwork.Validity validity = synchronousNetwork.validateBuses(LoadFlowModel.DC, ReportNode.NO_OP);

        assertEquals(LfNetwork.Validity.VALID, validity);
    }

    @Test
    void testValidateBusesReturnsValidIfThereIsAGeneratorControllingVoltageInAc() {
        Network network = AcDcNetworkFactory.createMtDcNetworkWithThreeAcZones();
        // Generator g1 controls AC voltage. The synchronous network is valid for AC load flow.

        LfNetworkParameters params = new LfNetworkParameters().setAcDcNetwork(true);
        LfNetwork lfNet = Networks.load(network, params).getFirst();

        LfSynchronousNetwork synchronousNetwork = lfNet.getSynchronousNetwork(0);
        LfNetwork.Validity validity = synchronousNetwork.validateBuses(LoadFlowModel.AC, ReportNode.NO_OP);

        assertEquals(LfNetwork.Validity.VALID, validity);
    }

    @Test
    void testValidateBusesReturnsValidIfThereIsAVoltageSourceConverterControllingVoltageInAc() {
        Network network = AcDcNetworkFactory.createMtDcNetworkWithThreeAcZones();
        // VSC conv14 controls AC voltage. The synchronous network is valid for AC load flow, even if there is no true
        // generator.
        network.getGenerator("g1").remove();
        network.getVoltageSourceConverter("conv14").setVoltageSetpoint(380).setVoltageRegulatorOn(true);

        LfNetworkParameters params = new LfNetworkParameters().setAcDcNetwork(true);
        LfNetwork lfNet = Networks.load(network, params).getFirst();

        LfSynchronousNetwork synchronousNetwork = lfNet.getSynchronousNetwork(0);
        LfNetwork.Validity validity = synchronousNetwork.validateBuses(LoadFlowModel.AC, ReportNode.NO_OP);

        assertEquals(LfNetwork.Validity.VALID, validity);
    }

}
