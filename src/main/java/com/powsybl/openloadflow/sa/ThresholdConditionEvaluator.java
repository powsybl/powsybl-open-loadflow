package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.condition.ThresholdCondition;

public class ThresholdConditionEvaluator {

    private final Network network;
    private final LfNetwork lfNetwork;
    private final ThresholdCondition condition;

    public ThresholdConditionEvaluator(Network network, LfNetwork lfNetwork, ThresholdCondition condition) {
        this.network = network;
        this.lfNetwork = lfNetwork;
        this.condition = condition;
    }

    public boolean evaluate() {
        Identifiable<?> identifiable = network.getIdentifiable(condition.getEquipmentId());

        // Check the contingency ?
        switch (identifiable.getType()) {
            case LINE, TWO_WINDINGS_TRANSFORMER -> {
                return evaluateBranchCondition((Branch<?>) identifiable, condition.getSide().toTwoSides(), condition.getVariable());
            }
            case THREE_WINDINGS_TRANSFORMER -> {
                return evaluateThreeWindingsTransformerCondition((ThreeWindingsTransformer) identifiable, condition.getSide(), condition.getVariable());
            }
            case GENERATOR -> {
                return evaluateGeneratorCondition(identifiable.getId(), condition.getVariable());
            }
            default -> throw new PowsyblException(String.format("Unsupported threshold condition on equipment %s of type %s.", condition.getEquipmentId(), condition.getComparisonType().name()));
        }
    }

    boolean evaluateGeneratorCondition(String generatorId, ThresholdCondition.Variable variable) {
        LfGenerator gen = lfNetwork.getGeneratorById(generatorId);

        // InitialTargetP represents the original target of the generator
        // while TargetP represents the target and an additional possible participation to the slack
        if (ThresholdCondition.Variable.TARGET_P.equals(variable)) {
            return evaluateThreshold(gen.getInitialTargetP(), condition.getThreshold(), condition.getComparisonType());

        } else if (ThresholdCondition.Variable.ACTIVE_POWER.equals(variable)) {
            return evaluateThreshold(gen.getTargetP(), condition.getThreshold(), condition.getComparisonType());
        } else {
            throw new PowsyblException(String.format("Cannot evaluate condition on variable %s on a generator", variable.name()));
        }
    }

    boolean evaluateBranchCondition(Branch<?> branch, TwoSides side, ThresholdCondition.Variable variable) {
        double s1;
        double s2;
        switch (variable) {
            case ACTIVE_POWER -> {
                s1 = lfNetwork.getBranchById(branch.getId()).getP1().eval() * PerUnit.SB;
                s2 = lfNetwork.getBranchById(branch.getId()).getP2().eval() * PerUnit.SB;
            }
            case REACTIVE_POWER -> {
                s1 = lfNetwork.getBranchById(branch.getId()).getQ1().eval() * PerUnit.SB;
                s2 = lfNetwork.getBranchById(branch.getId()).getQ2().eval() * PerUnit.SB;
            }
            case CURRENT -> {
                s1 = lfNetwork.getBranchById(branch.getId()).getI1().eval() * PerUnit.ib(branch.getTerminal1().getVoltageLevel().getNominalV());
                s2 = lfNetwork.getBranchById(branch.getId()).getI2().eval() * PerUnit.ib(branch.getTerminal2().getVoltageLevel().getNominalV());
            }
            default -> throw new PowsyblException(String.format("Unsupported variable %s for threshold condition on branch %s", variable.name(), branch.getId()));
        }

        if (TwoSides.ONE.equals(side)) {
            return evaluateThreshold(s1, condition.getThreshold(), condition.getComparisonType());
        } else {
            return evaluateThreshold(s2, condition.getThreshold(), condition.getComparisonType());
        }
    }

    boolean evaluateThreeWindingsTransformerCondition(ThreeWindingsTransformer transformer, ThreeSides side, ThresholdCondition.Variable variable) {
        double s1;
        double s2;
        double s3;
        switch (variable) {
            case ACTIVE_POWER -> {
                s1 = lfNetwork.getBranchById(LfLegBranch.getId(transformer.getId(), 1)).getP1().eval() * PerUnit.SB;
                s2 = lfNetwork.getBranchById(LfLegBranch.getId(transformer.getId(), 2)).getP1().eval() * PerUnit.SB;
                s3 = lfNetwork.getBranchById(LfLegBranch.getId(transformer.getId(), 3)).getP1().eval() * PerUnit.SB;
            }
            case REACTIVE_POWER -> {
                s1 = lfNetwork.getBranchById(LfLegBranch.getId(transformer.getId(), 1)).getQ1().eval() * PerUnit.SB;
                s2 = lfNetwork.getBranchById(LfLegBranch.getId(transformer.getId(), 2)).getQ1().eval() * PerUnit.SB;
                s3 = lfNetwork.getBranchById(LfLegBranch.getId(transformer.getId(), 3)).getQ1().eval() * PerUnit.SB;
            }
            case CURRENT -> {
                s1 = lfNetwork.getBranchById(LfLegBranch.getId(transformer.getId(), 1)).getI1().eval()
                    * PerUnit.ib(transformer.getLeg1().getTerminal().getVoltageLevel().getNominalV());
                s2 = lfNetwork.getBranchById(LfLegBranch.getId(transformer.getId(), 2)).getI1().eval()
                    * PerUnit.ib(transformer.getLeg2().getTerminal().getVoltageLevel().getNominalV());
                s3 = lfNetwork.getBranchById(LfLegBranch.getId(transformer.getId(), 3)).getI1().eval()
                    * PerUnit.ib(transformer.getLeg3().getTerminal().getVoltageLevel().getNominalV());
            }
            default -> throw new PowsyblException(String.format("Unsupported variable %s for threshold condition on transformer %s", variable.name(), transformer.getId()));
        }

        if (ThreeSides.ONE.equals(side)) {
            return evaluateThreshold(s1, condition.getThreshold(), condition.getComparisonType());
        } else if (ThreeSides.TWO.equals(side)) {
            return evaluateThreshold(s2, condition.getThreshold(), condition.getComparisonType());
        } else {
            return evaluateThreshold(s3, condition.getThreshold(), condition.getComparisonType());
        }
    }

    boolean evaluateThreshold(double value, double threshold, ThresholdCondition.ComparisonType type) {
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
