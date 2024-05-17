package com.powsybl.openloadflow.ac.outerloop.tap;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.VoltageControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 */
public class TransformerRatioManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformerRatioManager.class);

    private final boolean stable;

    public record ParallelRatioInfo(double rMax, double rMin, double rIni) {
        public ParallelRatioInfo(PiModel piModel) {
            this(piModel.getMaxR1(), piModel.getMinR1(), piModel.getR1());
        }

        public ParallelRatioInfo(double rMax, double rMin, double coefA, double coefB, int count) {
            this(rMax, rMin, (rMin * coefA + rMax * coefB) / count);
        }
    }

    public record TransfoRatioInfo(double rIni, ParallelRatioInfo groupeInfo) {
    }

    private final Map<String, TransfoRatioInfo> transfoRatioInfoMap = new HashMap<>();

    /**
     * Initializes the ratio states. In particular rMax, rMin and rIni.
     * If 'stable' is true then tMax, rMin and rIni are the average valus of the transformers in parallel
     * otherwise the transformer individual values.
     */
    public TransformerRatioManager(LfNetwork network, boolean stable) {
        this.stable = stable;
        for (LfBus bus : network.getControlledBuses(VoltageControl.Type.TRANSFORMER)) {
            bus.getTransformerVoltageControl()
                    .filter(voltageControl -> voltageControl.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .ifPresent(voltageControl -> {
                        var controllerBranches = voltageControl.getMergedControllerElements().stream()
                                .filter(b -> !b.isDisabled())
                                .filter(LfBranch::isVoltageControlEnabled)
                                .toList();
                        if (!controllerBranches.isEmpty()) { // If transformers in parallel control tension
                            if (stable) {
                                // parallel info
                                double coefA = 0;
                                double coefB = 0;
                                for (LfBranch transfo : controllerBranches) {
                                    PiModel piModel = transfo.getPiModel();
                                    double r1 = piModel.getR1();
                                    double r1Max = piModel.getMaxR1();
                                    double r1Min = piModel.getMinR1();
                                    if (r1Max != r1Min) {
                                        coefA += (r1Max - r1) / (r1Max - r1Min);
                                        coefB += (r1 - r1Min) / (r1Max - r1Min);
                                    } else {
                                        coefA += 1;
                                        coefB += 1;
                                    }
                                }
                                ParallelRatioInfo parallelRatioInfo = new ParallelRatioInfo(
                                        controllerBranches.stream().mapToDouble(b -> b.getPiModel().getMaxR1()).average().orElseThrow(),
                                        controllerBranches.stream().mapToDouble(b -> b.getPiModel().getMinR1()).average().orElseThrow(),
                                        coefA,
                                        coefB,
                                        controllerBranches.size());
                                controllerBranches.forEach(b -> transfoRatioInfoMap.put(b.getId(), new TransfoRatioInfo(b.getPiModel().getR1(), parallelRatioInfo)));
                            } else {
                                // individual info
                                controllerBranches.forEach(b -> transfoRatioInfoMap.put(b.getId(),
                                        new TransfoRatioInfo(b.getPiModel().getR1(), new ParallelRatioInfo(b.getPiModel()))));
                            }

                        }
                    });
        }
    }

    public TransfoRatioInfo getInfo(LfBranch transformer) {
        return transfoRatioInfoMap.get(transformer.getId());
    }

    /**
     * If stable is true, for tranformers in parallel, try to keep the initial difference between individual ratio
     * This algorithm maintains the individual tap positions if the tension is correct with initial settings.
     * It can also be seen as an approximate simulation of transformers acting with independent automates.
     * Assumes that all transformers in parallel have the same ratio (should be maintained by
     * ACEquationSystemCreator.createR1DistributionEquations)
     * If stable is false, the transformer is not modified and keeps its computed ratio.
     * @return the updated transormer's ratio
     */
    public double updateContinuousRatio(LfBranch transfo) {
        if (stable) {
            TransformerRatioManager.TransfoRatioInfo transfoRatioInfo = transfoRatioInfoMap.get(transfo.getId());
            double r1GroupMax = transfoRatioInfo.groupeInfo().rMax();
            double r1GroupMin = transfoRatioInfo.groupeInfo().rMin();
            double r1GroupIni = transfoRatioInfo.groupeInfo().rIni();
            double computedR = transfo.getPiModel().getR1(); // equations provide the same R for all branches
            double r1Ini = transfoRatioInfo.rIni();
            double updatedR = computedR >= r1GroupIni ?
                    r1Ini + (computedR - r1GroupIni) * (transfo.getPiModel().getMaxR1() - r1Ini) / (r1GroupMax - r1GroupIni)
                    :
                    r1Ini - (r1GroupIni - computedR) * (r1Ini - transfo.getPiModel().getMinR1()) / (r1GroupIni - r1GroupMin);
            transfo.getPiModel().setR1(updatedR);
            return updatedR;
        } else {
            // Do Nothing - keep computed R1
            return transfo.getPiModel().getR1();
        }
    }

    /**
     *  freezes the transformer to its limit if the ratio of a group of parallel transformers is above the max
     *  (or below the min) of the group
     * If stable mode is false, considers the max of the individual transformer.
     * @return true if the transformer has been frozen and false it voltage control remains disabled
     */
    public boolean freezeIfGroupAtBounds(LfBranch transfo) {
        if (transfo.isVoltageControlEnabled() && !transfo.isDisabled()) {
            // round the rho shift to the closest tap
            PiModel piModel = transfo.getPiModel();
            double r1 = piModel.getR1();
            TransformerRatioManager.TransfoRatioInfo transfoRatioInfo = getInfo(transfo);
            double r1GroupMin = transfoRatioInfo.groupeInfo().rMin();
            double r1GroupMax = transfoRatioInfo.groupeInfo().rMax();
            if (r1 < r1GroupMin || r1 > r1GroupMax) {
                LOGGER.info("Transformer " + transfo.getId() + " tap frozen");
                piModel.setR1(r1 > r1GroupMax ? r1GroupMax : r1GroupMin);
                piModel.roundR1ToClosestTap();
                transfo.setVoltageControlEnabled(false);
                return true;
            }
        }
        return false;
    }

}
