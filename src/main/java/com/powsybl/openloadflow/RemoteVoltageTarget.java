package com.powsybl.openloadflow;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class RemoteVoltageTarget {

    private RemoteVoltageTarget() {
    }

    public record LfIncompatibleTarget(LfBus controlledBus1, LfBus controlledBus2, double targetVoltagePlausibilityIndicator) {
        public IncompatibleTarget toIidm() {
            return new IncompatibleTarget(controlledBus1.getId(),
                    controlledBus2.getId(),
                    controlledBus1.getHighestPriorityTargetV().orElseThrow() * controlledBus1.getNominalV(),
                    controlledBus2.getHighestPriorityTargetV().orElseThrow() * controlledBus2.getNominalV(),
                    targetVoltagePlausibilityIndicator);
        }
    }

    public record IncompatibleTarget(String controlledBus1Id, String controlledBus2Id, double controlledBus1IdTargetV, double controlledBus2IdTargetV, double targetVoltagePlausibilityIndicator) {
    }

    public record LfUnrealisticTarget(LfBus controllerBus, double estimatedDvController) {
        public UnrealisticTarget toIidm() {
            return new UnrealisticTarget(controllerBus.getId(), controllerBus().getGenerators().stream().map(LfGenerator::getOriginalId).toList(), estimatedDvController);
        }
    }

    /**
     * @param controllerBusId id of the busview controller bus that has an unreaslitic targt
     * @param generatorIds ids of the generators with voltage control on this bus. If kept in remote voltage control, those bus may create convergence problems if voltageControlRobustMode is not activated.
     * @param estimatedDvController estimated drop in voltage of the bus, in per unit
     */
    public record UnrealisticTarget(String controllerBusId, List<String> generatorIds, double estimatedDvController) {
    }

    public record LfIncompatibleTargetResolution(LfBus controlledBusToFix, Set<? extends LfElement> elementsToDisable, LfIncompatibleTarget largestLfIncompatibleTarget) {
        public IncompatibleTargetResolution toIidm() {
            Set<String> elementsToDisableIds = new TreeSet<>();
            controlledBusToFix().getGeneratorVoltageControl()
                    .ifPresent(vc -> elementsToDisableIds.addAll(vc.getControllerElements().stream()
                            .flatMap(c -> c.getGenerators().stream())
                            .map(LfGenerator::getOriginalId)
                            .toList()));
            controlledBusToFix().getShuntVoltageControl()
                    .ifPresent(vc -> elementsToDisableIds.addAll(vc.getControllerElements().stream()
                            .flatMap(s -> s.getOriginalIds().stream())
                            .toList()));
            controlledBusToFix().getTransformerVoltageControl()
                    .ifPresent(vc -> elementsToDisableIds.addAll(vc.getControllerElements().stream()
                            .flatMap(t -> t.getOriginalIds().stream())
                            .toList()));
            return new IncompatibleTargetResolution(controlledBusToFix.getId(), elementsToDisableIds, largestLfIncompatibleTarget.toIidm());
        }
    }

    /**
     * @param controlledBusToFixId  id of the busview bus that has an incompatible control
     * @param elementsToDisableIds  ids of elements controlling controlledBusToFixId. To fix the problem, their target should be adjusted. Elements can be generators, shunts or two winding transformers
     * @param largestIncompatibleTarget the strongest incompatible target in wich the controlledBusToFixId is involved
     */
    public record IncompatibleTargetResolution(String controlledBusToFixId, Set<String> elementsToDisableIds, IncompatibleTarget largestIncompatibleTarget) {
    }


    record LfResult(List<LfIncompatibleTarget> lfIncompatibleTarget, List<LfUnrealisticTarget> lfUnrealisticTargets) {
        public LfResult() {
            this(new ArrayList<>(), new ArrayList<>());
        }
    }

    public record Result(List<IncompatibleTargetResolution> incompatibleTargetResolutions, List<UnrealisticTarget> unrealisticTargets) {

    }
}
