package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.condition.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ThresholdConditionEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThresholdConditionEvaluator.class);

    private ThresholdConditionEvaluator() {
    }

    public static boolean evaluate(Network network, LfNetwork lfNetwork, Condition condition) {
        AbstractThresholdCondition abstractThresholdCondition = (AbstractThresholdCondition) condition;
        // Check the contingency ?
        switch (condition.getType()) {
            case BranchThresholdCondition.NAME -> {
                return evaluateBranchCondition((BranchThresholdCondition) condition, lfNetwork);
            }
            case ThreeWindingsTransformerThresholdCondition.NAME -> {
                return evaluateThreeWindingsTransformerCondition((ThreeWindingsTransformerThresholdCondition) condition, network, lfNetwork);
            }
            case InjectionThresholdCondition.NAME -> {
                return evaluateGeneratorCondition((InjectionThresholdCondition) condition, lfNetwork);
            }
            default -> throw new PowsyblException(String.format("Unsupported threshold condition on equipment %s of type %s.", abstractThresholdCondition.getEquipmentId(), abstractThresholdCondition.getComparisonType().name()));
        }
    }

    private static boolean evaluateGeneratorCondition(InjectionThresholdCondition condition, LfNetwork lfNetwork) {
        LfGenerator gen = lfNetwork.getGeneratorById(condition.getEquipmentId());
        if (gen == null) {
            LOGGER.warn("Generator with id {} not found for condition evaluation", condition.getEquipmentId());
            return false;
        }

        // InitialTargetP represents the original target of the generator
        // while TargetP represents the target and an additional possible participation to the slack
        if (AbstractThresholdCondition.Variable.TARGET_P.equals(condition.getVariable())) {
            return evaluateThreshold(gen.getInitialTargetP(), condition.getThreshold(), condition.getComparisonType());
        } else if (AbstractThresholdCondition.Variable.ACTIVE_POWER.equals(condition.getVariable())) {
            return evaluateThreshold(gen.getTargetP(), condition.getThreshold(), condition.getComparisonType());
        } else {
            throw new PowsyblException(String.format("Unsupported variable %s for threshold condition on injection %s", condition.getVariable().name(), condition.getEquipmentId()));
        }
    }

    private static boolean evaluateBranchCondition(BranchThresholdCondition condition, LfNetwork lfNetwork) {
        double s1;
        double s2;
        LfBranch branch = lfNetwork.getBranchById(condition.getEquipmentId());
        if (branch == null) {
            LOGGER.warn("Branch with id {} not found for condition evaluation", condition.getEquipmentId());
            return false;
        }
        if (branch.getBus1() == null || branch.getBus2() == null) {
            return false;
        }
        switch (condition.getVariable()) {
            case ACTIVE_POWER -> {
                s1 = branch.getP1().eval() * PerUnit.SB;
                s2 = branch.getP2().eval() * PerUnit.SB;
            }
            case REACTIVE_POWER -> {
                s1 = branch.getQ1().eval() * PerUnit.SB;
                s2 = branch.getQ2().eval() * PerUnit.SB;
            }
            case CURRENT -> {
                s1 = branch.getI1().eval() * PerUnit.ib(branch.getBus1().getNominalV());
                s2 = branch.getI2().eval() * PerUnit.ib(branch.getBus2().getNominalV());
            }
            default -> throw new PowsyblException(String.format("Unsupported variable %s for threshold condition on branch %s", condition.getVariable().name(), branch.getId()));
        }

        if (TwoSides.ONE.equals(condition.getSide())) {
            return evaluateThreshold(s1, condition.getThreshold(), condition.getComparisonType());
        } else {
            return evaluateThreshold(s2, condition.getThreshold(), condition.getComparisonType());
        }
    }

    private static boolean evaluateThreeWindingsTransformerCondition(ThreeWindingsTransformerThresholdCondition condition, Network network, LfNetwork lfNetwork) {
        double value;
        ThreeSides side = condition.getSide();
        ThreeWindingsTransformer transformer = network.getThreeWindingsTransformer(condition.getEquipmentId());
        if (transformer == null) {
            LOGGER.warn("Three windings transformer with id {} not found for condition evaluation", condition.getEquipmentId());
            return false;
        }
        String legBranchId = LfLegBranch.getId(transformer.getId(), side.getNum());
        LfBranch lfBranch = lfNetwork.getBranchById(legBranchId);
        if (lfBranch == null) {
            LOGGER.warn("Leg branch {} not found for three windings transformer with id {} necessary for condition evaluation", legBranchId, condition.getEquipmentId());
            return false;
        }
        switch (condition.getVariable()) {
            case ACTIVE_POWER -> value = lfBranch.getP1().eval() * PerUnit.SB;
            case REACTIVE_POWER -> value = lfBranch.getQ1().eval() * PerUnit.SB;
            case CURRENT -> value = lfBranch.getI1().eval() * PerUnit.ib(transformer.getLeg(condition.getSide()).getTerminal().getVoltageLevel().getNominalV());
            default -> throw new PowsyblException(String.format("Unsupported variable %s for threshold condition on transformer %s", condition.getVariable().name(), transformer.getId()));
        }
        return evaluateThreshold(value, condition.getThreshold(), condition.getComparisonType());
    }

    private static boolean evaluateThreshold(double value, double threshold, AbstractThresholdCondition.ComparisonType type) {
        switch (type) {
            case EQUALS -> {
                return value == threshold;
            }
            case GREATER_THAN -> {
                return value > threshold;
            }
            case GREATER_THAN_OR_EQUALS -> {
                return value >= threshold;
            }
            case LESS_THAN -> {
                return value < threshold;
            }
            case LESS_THAN_OR_EQUALS -> {
                return value <= threshold;
            }
            case NOT_EQUAL -> {
                return value != threshold;
            }
            default -> throw new PowsyblException("Unsupported comparison type " + type.name());
        }
    }
}
