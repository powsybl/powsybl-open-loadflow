package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.AcOuterLoopGroupContext;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.TransformerVoltageControl;
import com.powsybl.openloadflow.network.VoltageControl;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TapControlOuterLoopGroup extends AbstractACOuterLoopGroup {

    public static final String NAME = "TapControlOuterLoopGroup";

    private static final Logger LOGGER = LoggerFactory.getLogger(TapControlOuterLoopGroup.class);

    public TapControlOuterLoopGroup(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        super(getTapControlOuterLoopGroup(parameters, parametersExt), "Tap Control OuterLoop Group");
    }

    private static List<AcOuterLoop> getTapControlOuterLoopGroup(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {

        List<AcOuterLoop> result = new ArrayList<>();

        if (parameters.isDistributedSlack()) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), parametersExt.isLoadPowerFactorConstant(), parametersExt.isUseActiveLimits());
            DistributedSlackOuterLoop distributedSlackOuterLoop = new DistributedSlackOuterLoop(activePowerDistribution, parametersExt.getSlackBusPMaxMismatch());
            result.add(distributedSlackOuterLoop);
        }

        if (parametersExt.isSvcVoltageMonitoring()) {
            MonitoringVoltageOuterLoop monitoringVoltageOuterLoop = new MonitoringVoltageOuterLoop();
            result.add(monitoringVoltageOuterLoop);
        }

        if (parameters.isUseReactiveLimits()) {
            double effectiveMaxReactivePowerMismatch = switch (parametersExt.getNewtonRaphsonStoppingCriteriaType()) {
                case UNIFORM_CRITERIA -> parametersExt.getNewtonRaphsonConvEpsPerEq();
                case PER_EQUATION_TYPE_CRITERIA -> parametersExt.getMaxReactivePowerMismatch() / PerUnit.SB;
            };
            result.add(new ReactiveLimitsOuterLoop(parametersExt.getReactiveLimitsMaxPqPvSwitch(), effectiveMaxReactivePowerMismatch));
        }

        // TODO: Add the loop that controls tap limits

        return result;
    }

    @Override
    protected boolean prepareSolverAndModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> outerLoopsAndContexts) {

        // Transformer Voltage control is removed from the equationsand replaced by continuous tap equations
        // Voltage control for "HT" groups is then disabled

        double maxControlledNominalVoltage = Double.MIN_VALUE;
        for (LfBus bus : groupContext.getNetwork().getBuses()) {
            if (!bus.isDisabled() && bus.isTransformerVoltageControlled()) {
                maxControlledNominalVoltage = Math.max(maxControlledNominalVoltage, bus.getNominalV());
            }
        }

        boolean modelChanged = false;

        // The voltage control of generators that controlled at nominal voltage of
        // the set controlledNominalVoltages are disabled.
        // The transformer voltage controls are enabled.
        for (LfBus bus : groupContext.getNetwork().getControlledBuses(VoltageControl.Type.GENERATOR)) {
            if (bus.getNominalV() <= maxControlledNominalVoltage) {
                var voltageControl = bus.getGeneratorVoltageControl().orElseThrow();
                voltageControl.getMergedControllerElements().forEach(controllerBus -> {
                    if (controllerBus.isGeneratorVoltageControlEnabled()) {
                        controllerBus.setGenerationTargetQ(controllerBus.getQ().eval());
                        controllerBus.setGeneratorVoltageControlEnabled(false);
                    }
                });
                modelChanged = true;
            }
        }

        for (LfBranch branch : groupContext.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            Optional<TransformerVoltageControl> voltageControl = branch.getVoltageControl();
            if (voltageControl.isPresent()) {
                double targetV = voltageControl.get().getTargetValue();
                double v = voltageControl.get().getControlledBus().getV();
                double diffV = targetV - v;
                branch.setVoltageControlEnabled(true);
                modelChanged = true;
            }
        }

        if (modelChanged) {
            groupContext.getNetwork().fixTransformerVoltageControls();

            for (Pair<AcOuterLoop, AcOuterLoopContext> outerLoopAndContext : outerLoopsAndContexts) {
                AcOuterLoop outerLoop = outerLoopAndContext.getLeft();
                AcOuterLoopContext loopContext = outerLoopAndContext.getRight();
                if (outerLoop instanceof MonitoringVoltageOuterLoop) {
                    // Provide the voltage limit to that loop
                    ((MonitoringVoltageOuterLoop) outerLoop).initialize(loopContext, maxControlledNominalVoltage);
                } else {
                    outerLoop.initialize(loopContext);
                }
                outerLoop.initialize(loopContext);
            }

            groupContext.runSolver(getVoltageInitializer(groupContext), nrReportNode);

            return true;
        } else {
            return false;
        }

        // TODO: POur la loop suivante, attention a bien remettre tous les bus en PV
        // TODO: Dans un test vérifier la bonne mise à jour du slack
    }

    @Override
    protected void cleanModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> outerLoopsAndContexts) {

        // discretize transformer taps
        for (LfBranch controllerBranch : groupContext.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            controllerBranch.setVoltageControlEnabled(false);

            // round the rho shift to the closest tap
            PiModel piModel = controllerBranch.getPiModel();
            double r1Value = piModel.getR1();
            // TODO: Enable behaviour change (to match Hades)
            piModel.roundR1ToClosestTap();
            double roundedR1Value = piModel.getR1();
            LOGGER.trace("Round voltage ratio of '{}': {} -> {}", controllerBranch.getId(), r1Value, roundedR1Value);
        }

    }

    @Override
    protected OuterLoopStatus getStableStatus() {
        return OuterLoopStatus.FULL_STABLE;
    }

    @Override
    public boolean isMultipleUseAllowed() {
        return false;
    }
}
