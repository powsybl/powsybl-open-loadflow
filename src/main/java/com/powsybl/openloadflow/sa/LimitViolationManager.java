/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.security.SecurityAnalysisParameters;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.*;

/**
 * Limit violation manager. A reference limit violation manager could be specified to only report violations that
 * are more severe than reference one.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LimitViolationManager {

    private final LimitViolationManager reference;

    private SecurityAnalysisParameters.IncreasedViolationsParameters parameters;

    private final Map<Pair<String, Branch.Side>, LimitViolation> violations = new LinkedHashMap<>();

    public LimitViolationManager(LimitViolationManager reference, SecurityAnalysisParameters.IncreasedViolationsParameters parameters) {
        this.reference = reference;
        if (reference != null) {
            this.parameters = Objects.requireNonNull(parameters);
        }
    }

    public LimitViolationManager() {
        this(null, null);
    }

    public List<LimitViolation> getLimitViolations() {
        return new ArrayList<>(violations.values());
    }

    /**
     * Detect violations on branches and on buses
     * @param network network on which the violation limits are checked
     */
    public void detectViolations(LfNetwork network) {
        Objects.requireNonNull(network);

        // Detect violation limits on branches
        network.getBranches().stream().filter(b -> !b.isDisabled()).forEach(this::detectBranchViolations);

        // Detect violation limits on buses
        network.getBuses().stream().filter(b -> !b.isDisabled()).forEach(this::detectBusViolations);

        // Detect voltage angle limits
    }

    private static Pair<String, Branch.Side> getSubjectIdSide(LimitViolation limitViolation) {
        return Pair.of(limitViolation.getSubjectId(), limitViolation.getSide());
    }

    private void addLimitViolation(LimitViolation limitViolation) {
        var subjectIdSide = getSubjectIdSide(limitViolation);
        if (reference != null) {
            var referenceLimitViolation = reference.violations.get(subjectIdSide);
            if (referenceLimitViolation == null || !violationWeakenedOrEquivalent(referenceLimitViolation, limitViolation, parameters)) {
                violations.put(subjectIdSide, limitViolation);
            }
        } else {
            violations.put(subjectIdSide, limitViolation);
        }
    }

    private void detectBranchSideViolations(LfBranch branch, LfBus bus,
                                            BiFunction<LfBranch, LimitType, List<LfBranch.LfLimit>> limitsGetter,
                                            Function<LfBranch, Evaluable> iGetter,
                                            Function<LfBranch, Evaluable> pGetter,
                                            ToDoubleFunction<LfBranch> sGetter,
                                            Branch.Side side) {
        List<LfBranch.LfLimit> limits = limitsGetter.apply(branch, LimitType.CURRENT);
        if (!limits.isEmpty()) {
            double i = iGetter.apply(branch).eval();
            limits.stream()
                    .filter(temporaryLimit -> i > temporaryLimit.getValue())
                    .findFirst()
                    .map(temporaryLimit -> createLimitViolation(branch, temporaryLimit, LimitViolationType.CURRENT, PerUnit.ib(bus.getNominalV()), i, side))
                    .ifPresent(this::addLimitViolation);
        }

        limits = limitsGetter.apply(branch, LimitType.ACTIVE_POWER);
        if (!limits.isEmpty()) {
            double p = pGetter.apply(branch).eval();
            limits.stream()
                    .filter(temporaryLimit -> p > temporaryLimit.getValue())
                    .findFirst()
                    .map(temporaryLimit -> createLimitViolation(branch, temporaryLimit, LimitViolationType.ACTIVE_POWER, PerUnit.SB, p, side))
                    .ifPresent(this::addLimitViolation);
        }

        limits = limitsGetter.apply(branch, LimitType.APPARENT_POWER);
        if (!limits.isEmpty()) {
            //Apparent power is not relevant for fictitious branches and may be NaN
            double s = sGetter.applyAsDouble(branch);
            if (!Double.isNaN(s)) {
                limits.stream()
                        .filter(temporaryLimit -> s > temporaryLimit.getValue())
                        .findFirst()
                        .map(temporaryLimit -> createLimitViolation(branch, temporaryLimit, LimitViolationType.APPARENT_POWER, PerUnit.SB, s, side))
                        .ifPresent(this::addLimitViolation);
            }
        }
    }

    /**
     * Detect violation limits on one branch and add them to the given list
     * @param branch branch of interest
     */
    private void detectBranchViolations(LfBranch branch) {
        // detect violation limits on a branch
        // Only detect the most serious one (findFirst) : limit violations are ordered by severity
        if (branch.getBus1() != null) {
            detectBranchSideViolations(branch, branch.getBus1(), LfBranch::getLimits1, LfBranch::getI1, LfBranch::getP1, LfBranch::computeApparentPower1, Branch.Side.ONE);
        }

        if (branch.getBus2() != null) {
            detectBranchSideViolations(branch, branch.getBus2(), LfBranch::getLimits2, LfBranch::getI2, LfBranch::getP2, LfBranch::computeApparentPower2, Branch.Side.TWO);
        }
    }

    private static LimitViolation createLimitViolation(LfBranch branch, LfBranch.LfLimit temporaryLimit,
                                                       LimitViolationType type, double scale, double value,
                                                       Branch.Side side) {
        return new LimitViolation(branch.getId(), type, temporaryLimit.getName(),
                temporaryLimit.getAcceptableDuration(), temporaryLimit.getValue() * scale,
                1f, value * scale, side);
    }

    /**
     * Detect violation limits on one branch and add them to the given list
     * @param bus branch of interest
     */
    private void detectBusViolations(LfBus bus) {
        // detect violation limits on a bus
        double scale = bus.getNominalV();
        double busV = bus.getV();
        if (!Double.isNaN(bus.getHighVoltageLimit()) && busV > bus.getHighVoltageLimit()) {
            LimitViolation limitViolation1 = new LimitViolation(bus.getVoltageLevelId(), LimitViolationType.HIGH_VOLTAGE, bus.getHighVoltageLimit() * scale,
                    (float) 1., busV * scale);
            addLimitViolation(limitViolation1);
        }
        if (!Double.isNaN(bus.getLowVoltageLimit()) && busV < bus.getLowVoltageLimit()) {
            LimitViolation limitViolation2 = new LimitViolation(bus.getVoltageLevelId(), LimitViolationType.LOW_VOLTAGE, bus.getLowVoltageLimit() * scale,
                    (float) 1., busV * scale);
            addLimitViolation(limitViolation2);
        }
    }

    /**
     * Compares two limit violations
     * @param violation1 first limit violation
     * @param violation2 second limit violation
     * @return true if violation2 is weaker than or equivalent to violation1, otherwise false
     */
    public static boolean violationWeakenedOrEquivalent(LimitViolation violation1, LimitViolation violation2,
                                                        SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters) {
        if (violation2 != null && violation1.getLimitType() == violation2.getLimitType()) {
            if (violation2.getLimit() < violation1.getLimit()) {
                // the limit violated is smaller hence the violation is weaker, for flow violations only.
                // for voltage limits, we have only one limit by limit type.
                return true;
            }
            if (violation2.getLimit() == violation1.getLimit()) {
                // the limit violated is the same: we consider the violations equivalent if the new value is close to previous one.
                if (isFlowViolation(violation2)) {
                    return Math.abs(violation2.getValue()) <= Math.abs(violation1.getValue()) * (1 + violationsParameters.getFlowProportionalThreshold());
                } else if (violation2.getLimitType() == LimitViolationType.HIGH_VOLTAGE) {
                    double value = Math.min(violationsParameters.getHighVoltageAbsoluteThreshold(), violation1.getValue() * violationsParameters.getHighVoltageProportionalThreshold());
                    return violation2.getValue() <= violation1.getValue() + value;
                } else if (violation2.getLimitType() == LimitViolationType.LOW_VOLTAGE) {
                    return violation2.getValue() >= violation1.getValue() - Math.min(violationsParameters.getLowVoltageAbsoluteThreshold(), violation1.getValue() * violationsParameters.getLowVoltageProportionalThreshold());
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isFlowViolation(LimitViolation limit) {
        return limit.getLimitType() == LimitViolationType.CURRENT || limit.getLimitType() == LimitViolationType.ACTIVE_POWER || limit.getLimitType() == LimitViolationType.APPARENT_POWER;
    }
}
