/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of LfSynchronousNetwork that provides a filtered view of LfNetwork buses for a single synchronous
 * component.
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public class LfSynchronousNetworkImpl implements LfSynchronousNetwork {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfSynchronousNetworkImpl.class);

    private static final SlackBusSelector SLACK_BUS_SELECTOR_FALLBACK = new MostMeshedSlackBusSelector();

    private final LfNetwork lfNetwork;

    private final int numSC;

    private final SlackBusSelector slackBusSelector;

    private final ReferenceBusSelector referenceBusSelector;

    private final int maxSlackBusCount;

    private LfBus referenceBus;

    private List<LfBus> slackBuses = new ArrayList<>();

    private Set<LfBus> excludedSlackBuses = Collections.emptySet();

    private LfGenerator referenceGenerator;

    public LfSynchronousNetworkImpl(LfNetwork lfNetwork, int numSC, SlackBusSelector slackBusSelector, ReferenceBusSelector referenceBusSelector, int maxSlackBusCount) {
        this.lfNetwork = lfNetwork;
        this.numSC = numSC;
        this.slackBusSelector = slackBusSelector;
        this.referenceBusSelector = referenceBusSelector;
        this.maxSlackBusCount = maxSlackBusCount;
    }

    @Override
    public int getNumSC() {
        return numSC;
    }

    @Override
    public LfBus getReferenceBus() {
        updateSlackBusesAndReferenceBus();
        return referenceBus;
    }

    @Override
    public LfGenerator getReferenceGenerator() {
        updateSlackBusesAndReferenceBus();
        return referenceGenerator;
    }

    @Override
    public List<LfBus> getSlackBuses() {
        updateSlackBusesAndReferenceBus();
        return slackBuses;
    }

    @Override
    public Set<LfBus> getExcludedSlackBuses() {
        return excludedSlackBuses;
    }

    @Override
    public void setExcludedSlackBuses(Set<LfBus> excludedSlackBuses) {
        Objects.requireNonNull(excludedSlackBuses);
        // Filter buses that are only in this synchronous network
        Set<LfBus> filteredExcludedSlackBuses = new HashSet<>(excludedSlackBuses);
        filteredExcludedSlackBuses.retainAll(getBuses());
        if (!filteredExcludedSlackBuses.equals(this.excludedSlackBuses)) {
            this.excludedSlackBuses = filteredExcludedSlackBuses;
            invalidateSlackAndReference();
        }
    }

    @Override
    public LfNetwork.Validity validateBuses(LoadFlowModel loadFlowModel, ReportNode reportNode) {
        List<LfBus> buses = getBuses();
        // DC or AC, if no generator, network is dead
        boolean hasAtLeastOneBusGenerator = false;
        for (LfBus bus : buses) {
            if (!bus.getGenerators().isEmpty() || !bus.getConverters().isEmpty()) {
                hasAtLeastOneBusGenerator = true;
                break;
            }
        }
        if (!hasAtLeastOneBusGenerator) {
            // we don't report because this is too much on real networks
            LOGGER.debug("Network {} has no generator and will be considered dead", this);
            return LfNetwork.Validity.INVALID_NO_GENERATOR;
        }
        // AC requires at least one bus under voltage control
        if (loadFlowModel == LoadFlowModel.AC) {
            boolean hasAtLeastOneBusGeneratorVoltageControlEnabled = false;
            for (LfBus bus : buses) {
                if (bus.isGeneratorVoltageControlEnabled() || bus.isVoltageSourceConverterVoltageControlled()) {
                    hasAtLeastOneBusGeneratorVoltageControlEnabled = true;
                    break;
                }
            }
            if (!hasAtLeastOneBusGeneratorVoltageControlEnabled) {
                LOGGER.warn("Network {} must have at least one bus with generator voltage control enabled", this);
                if (reportNode != null) {
                    Reports.reportNetworkMustHaveAtLeastOneBusGeneratorVoltageControlEnabled(reportNode);
                }
                return LfNetwork.Validity.INVALID_NO_GENERATOR_VOLTAGE_CONTROL;
            }
        }
        return LfNetwork.Validity.VALID;
    }

    @Override
    public List<LfBus> getBuses() {
        return lfNetwork.getBuses().stream().filter(b -> b.getNumSC() == numSC).toList();
    }

    public void invalidateSlackAndReference() {
        if (slackBuses != null) {
            for (var slackBus : slackBuses) {
                slackBus.setSlack(false);
            }
        }
        slackBuses = null;

        if (referenceBus != null) {
            referenceBus.setReference(false);
        }
        referenceBus = null;

        if (referenceGenerator != null) {
            referenceGenerator.setReference(false);
        }
        referenceGenerator = null;
    }

    public void updateSlackBusesAndReferenceBus() {
        // Usually (slackBuses == null) == (referenceBus == null), as they are invalidated and updated by the same
        // methods. However, in the code block below, after setting the slack buses, if the referenceBusSelector is a
        // ReferenceBusFirstSlackSelector, it requests the slack buses of this LfSynchronousComponent which calls this
        // method again.
        // At this stage (slackBuses != null) and (referenceBus == null). The condition below allows the method to
        // return directly to prevent an infinite loop.
        if (slackBuses == null && referenceBus == null) {
            List<LfBus> scBuses = getBuses();
            List<LfBus> selectableBuses = excludedSlackBuses.isEmpty() ? scBuses :
                    scBuses.stream().filter(bus -> !excludedSlackBuses.contains(bus)).toList();
            SelectedSlackBus selectedSlackBus = slackBusSelector.select(selectableBuses, maxSlackBusCount);
            slackBuses = selectedSlackBus.getBuses();
            if (slackBuses.isEmpty()) { // ultimate fallback
                selectedSlackBus = SLACK_BUS_SELECTOR_FALLBACK.select(selectableBuses, maxSlackBusCount);
                if (selectedSlackBus.getBuses().isEmpty()) {
                    throw new PowsyblException("No slack bus could be selected");
                }
                slackBuses = selectedSlackBus.getBuses();
            }
            LOGGER.info("Network {}, slack buses are {} (method='{}')", this, slackBuses, selectedSlackBus.getSelectionMethod());
            for (LfBus slackBus : slackBuses) {
                slackBus.setSlack(true);
            }
            // reference bus must be selected after slack bus, because of ReferenceBusFirstSlackSelector implementation requiring slackBuses
            SelectedReferenceBus selectedReferenceBus = referenceBusSelector.select(this);
            referenceBus = selectedReferenceBus.getLfBus();
            LOGGER.info("Network {}, reference bus is {} (method='{}')", this, referenceBus, selectedReferenceBus.getSelectionMethod());
            referenceBus.setReference(true);
            if (selectedReferenceBus instanceof SelectedGeneratorReferenceBus generatorReferenceBus) {
                referenceGenerator = generatorReferenceBus.getLfGenerator();
                LOGGER.info("Network {}, reference generator is {}", this, referenceGenerator.getId());
                referenceGenerator.setReference(true);
            }

            // If this synchronous network is the main one, updating the LfNetwork connectivity graph main vertex
            // is part of its responsibility.
            if (lfNetwork.getSynchronousNetworks().getFirst() == this && !lfNetwork.isConnectivityNull()) {
                lfNetwork.getConnectivity().setMainComponentVertex(slackBuses.getFirst());
            }

        }
    }

    @Override
    public void copyStateFrom(LfSynchronousNetwork other) {
        // Only the excluded slack buses are persistent state: slack/reference selection is lazily redone on the copy.
        Set<LfBus> otherExcludedSlackBuses = other.getExcludedSlackBuses();
        if (!otherExcludedSlackBuses.isEmpty()) {
            setExcludedSlackBuses(otherExcludedSlackBuses.stream()
                    .map(bus -> lfNetwork.getBusById(bus.getId()))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
    }

    @Override
    public String toString() {
        return "{CC" + lfNetwork.getNumCC() + " SC" + numSC + '}';
    }
}
