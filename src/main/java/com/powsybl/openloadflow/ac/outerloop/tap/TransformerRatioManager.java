package com.powsybl.openloadflow.ac.outerloop.tap;

import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.VoltageControl;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 */
public class TransformerRatioManager {

    public record ParallelRatioInfo(double rMax, double rMin, double rIni) {
    }

    public record TransfoRatioInfo(double rIni, ParallelRatioInfo groupeInfo) {
    }

    private Map<String, TransfoRatioInfo> transfoRatioInfoMap = new HashMap<>();

    public TransformerRatioManager(AcOuterLoopContext context) {
        for (LfBus bus : context.getNetwork().getControlledBuses(VoltageControl.Type.TRANSFORMER)) {
            bus.getTransformerVoltageControl()
                    .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .ifPresent(voltageControl -> {
                        var controllerBranches = voltageControl.getMergedControllerElements().stream()
                                .filter(b -> !b.isDisabled())
                                .filter(b -> b.isVoltageControlEnabled())
                                .toList();
                        if (!controllerBranches.isEmpty()) { // If transformers in parallel control tension
                            ParallelRatioInfo parallelRatioInfo = new ParallelRatioInfo(
                                    controllerBranches.stream().mapToDouble(b -> b.getPiModel().getMaxR1()).average().orElseThrow(),
                                    controllerBranches.stream().mapToDouble(b -> b.getPiModel().getMinR1()).average().orElseThrow(),
                                    controllerBranches.stream().mapToDouble(b -> b.getPiModel().getR1()).average().orElseThrow());
                            controllerBranches.forEach(b -> transfoRatioInfoMap.put(b.getId(), new TransfoRatioInfo(b.getPiModel().getR1(), parallelRatioInfo)));
                        }
                    });
        }
    }

    public TransfoRatioInfo getInfo(LfBranch transfo) {
        return transfoRatioInfoMap.get(transfo.getId());
    }

    public void updateContinousRatio(LfBranch transfo) {
        TransformerRatioManager.TransfoRatioInfo transfoRatioInfo = transfoRatioInfoMap.get(transfo.getId());
        double r1GroupMax = transfoRatioInfo.groupeInfo().rMax();
        double r1GroupMin = transfoRatioInfo.groupeInfo().rMin();
        double r1GroupIni = transfoRatioInfo.groupeInfo().rIni();
        double computedR = transfo.getPiModel().getR1(); // equations provide the same R to all breanches
        double r1Ini = transfoRatioInfo.rIni();
        double updatedR = computedR >= r1GroupIni ?
                r1Ini + (computedR - r1GroupIni) * (transfo.getPiModel().getMaxR1() - r1Ini) / (r1GroupMax - r1GroupIni)
                :
                r1Ini - (r1GroupIni - computedR) * (r1Ini - transfo.getPiModel().getMinR1()) / (r1GroupIni - r1GroupMin);
        transfo.getPiModel().setR1(updatedR);
    }
}
