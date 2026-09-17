/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.iidm.criteria.AtLeastOneNominalVoltageCriterion;
import com.powsybl.iidm.criteria.IdentifiableCriterion;
import com.powsybl.iidm.criteria.VoltageInterval;
import com.powsybl.iidm.criteria.duration.AllTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.EqualityTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.IntervalTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.LimitDurationCriterion;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.security.limitscaling.LimitScaling;
import org.apache.commons.lang3.DoubleRange;
import org.apache.commons.lang3.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 *
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class LimitScalingManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(LimitScalingManager.class);

    /**
     * @param acceptableDuration can be null
     */
    public record TerminalLimitScaling(Range<Double> nominalV, boolean isPermanent, Range<Integer> acceptableDuration,
                                         double scaling) {

    }

    private final List<TerminalLimitScaling> terminalLimitScalings = new ArrayList<>();

    public boolean isEmpty() {
        return terminalLimitScalings.isEmpty();
    }

    public List<TerminalLimitScaling> getTerminalLimitScalings() {
        return terminalLimitScalings;
    }

    public void addTerminalLimitScaling(TerminalLimitScaling terminalLimitScaling) {
        this.terminalLimitScalings.add(terminalLimitScaling);
    }

    public static LimitScalingManager create(List<LimitScaling> limitScalings) {
        LimitScalingManager limitScalingManager = new LimitScalingManager();
        Range<Integer> acceptableDurationRange;
        boolean permanent;
        for (LimitScaling limitScaling : limitScalings) {
            if (isSupported(limitScaling)) {
                // Compute the duration data
                permanent = false;
                acceptableDurationRange = null;
                if (limitScaling.getDurationCriteria().isEmpty()) {
                    // When no duration criterion is present, the scaling applies to permanent and temporary limits
                    permanent = true;
                    acceptableDurationRange = Range.of(0, Integer.MAX_VALUE);
                } else { // size 1 or 2 only (when 2, they are not of the same type).
                    for (LimitDurationCriterion limitDurationCriterion : limitScaling.getDurationCriteria()) {
                        LimitDurationCriterion.LimitDurationType type = limitDurationCriterion.getType();
                        if (Objects.requireNonNull(type) == LimitDurationCriterion.LimitDurationType.PERMANENT) {
                            permanent = true;
                        } else if (type == LimitDurationCriterion.LimitDurationType.TEMPORARY) {
                            acceptableDurationRange = getAcceptableDurationRange(limitDurationCriterion);
                        }
                    }
                }
                // Compute the nominal voltage ranges. When no network element criteria is present,
                // the scaling applies to all network elements.
                Collection<DoubleRange> nominalVoltageRanges = limitScaling.getNetworkElementCriteria().isEmpty() ?
                        List.of(DoubleRange.of(0, Double.MAX_VALUE)) :
                        limitScaling.getNetworkElementCriteria().stream().map(IdentifiableCriterion.class::cast)
                                .map(IdentifiableCriterion::getNominalVoltageCriterion)
                                .map(AtLeastOneNominalVoltageCriterion::getVoltageInterval)
                                .map(VoltageInterval::asRange)
                                .distinct()
                                .toList();

                for (DoubleRange nominalVoltageRange : nominalVoltageRanges) {
                    limitScalingManager.addTerminalLimitScaling(new TerminalLimitScaling(nominalVoltageRange, permanent, acceptableDurationRange, limitScaling.getValue()));
                }
            }
        }
        return limitScalingManager;
    }

    private static Range<Integer> getAcceptableDurationRange(LimitDurationCriterion limitDurationCriterion) {
        Range<Integer> acceptableDurationRange;
        if (limitDurationCriterion instanceof AllTemporaryDurationCriterion) {
            acceptableDurationRange = Range.of(0, Integer.MAX_VALUE);
        } else if (limitDurationCriterion instanceof EqualityTemporaryDurationCriterion equalityTemporaryDurationCriterion) {
            acceptableDurationRange = Range.of(equalityTemporaryDurationCriterion.getDurationEqualityValue(),
                    equalityTemporaryDurationCriterion.getDurationEqualityValue());
        } else { // intervalTemporaryDurationCriterion
            IntervalTemporaryDurationCriterion intervalTemporaryDurationCriterion = (IntervalTemporaryDurationCriterion) limitDurationCriterion;
            acceptableDurationRange = intervalTemporaryDurationCriterion.asRange();
        }
        return acceptableDurationRange;
    }

    private static boolean isSupported(LimitScaling limitScaling) {
        if (limitScaling.getContingencyContext().getContextType() != ContingencyContextType.ALL) {
            // Contingency context NONE with empty contingency lists could be supported too.
            LOGGER.warn("Only contingency context ALL is yet supported.");
            return false;
        }
        if (limitScaling.isMonitoringOnly()) {
            // This means that post-contingency limit violations with scalings must not be used for the conditions of
            // operator strategy.
            LOGGER.warn("Limit scalings for monitoring only is not yet supported.");
            return false;
        }
        if (limitScaling.getLimitType() != LimitType.CURRENT) {
            // Note: a list of limit types could be a good feature?
            LOGGER.warn("Only limit scalings for current limits are yet supported.");
            return false;
        }
        if (limitScaling.getNetworkElementCriteria().stream().anyMatch(Predicate.not(IdentifiableCriterion.class::isInstance))) {
            LOGGER.warn("Only no network element criterion or identifiable criteria are yet supported.");
            return false;
        }
        if (limitScaling.getDurationCriteria().size() > 2) {
            LOGGER.warn("More than two duration criteria provided.");
            return false;
        }
        if (limitScaling.getDurationCriteria().size() == 2
                && limitScaling.getDurationCriteria().get(0).getType() == limitScaling.getDurationCriteria().get(1).getType()) {
            LOGGER.warn("When two duration criteria are provided, they cannot be of the same type");
            return false;
        }
        if (!limitScaling.getOperationalLimitsGroupIdsSelection().isEmpty()) {
            LOGGER.warn("Limit scaling with only a specified operational limits groups to be applied on are not yet supported.");
            return false;
        }

        return true;
    }
}
