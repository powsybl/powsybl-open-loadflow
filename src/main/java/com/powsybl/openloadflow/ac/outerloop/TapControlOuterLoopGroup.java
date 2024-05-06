package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.AcOuterLoopGroupContext;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.tap.TransformerRatioManager;
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

public class TapControlOuterLoopGroup extends AbstractCompensationAndSolveOuterLoopGroup {

    public static final String NAME = "TapControlOuterLoopGroup";

    private static final Logger LOGGER = LoggerFactory.getLogger(TapControlOuterLoopGroup.class);

    private final LoadFlowParameters parameters;
    private final OpenLoadFlowParameters parametersExt;

    private TransformerRatioManager transformerRatioManager;

    static class ContextData {
        private double maxControlledNominalVoltage = Double.MIN_VALUE;
        private final List<LfBus> busesWithVoltageControlDisabled = new ArrayList<>();

        public void updateMaxControlledNominalVoltage(double newVoltage) {
            maxControlledNominalVoltage = Math.max(newVoltage, maxControlledNominalVoltage);
        }

        public double getMaxControlledNominalVoltage() {
            return maxControlledNominalVoltage;
        }

        public void addBusWithVoltageControlDisabled(LfBus bus) {
            busesWithVoltageControlDisabled.add(bus);
        }

        public List<LfBus> getBusesWithVoltageControlDisabled() {
            return busesWithVoltageControlDisabled;
        }
    }

    public TapControlOuterLoopGroup(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        super(makeCompensationLoop(parameters, parametersExt));
        this.parameters = parameters;
        this.parametersExt = parametersExt;
    }

    private static AcOuterLoop makeCompensationLoop(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        if (parameters.isDistributedSlack()) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), parametersExt.isLoadPowerFactorConstant(), parametersExt.isUseActiveLimits());
            return new DistributedSlackOuterLoop(activePowerDistribution, parametersExt.getSlackBusPMaxMismatch());
        } else {
            return new AcOuterLoop() {
                @Override
                public String getName() {
                    return "NOOP";
                }

                @Override
                public OuterLoopStatus check(AcOuterLoopContext context, ReportNode reportNode) {
                    return OuterLoopStatus.STABLE;
                }
            };
        }
    }

    private void addModelCheckers(List<AcOuterLoop> modelCheckers, double maxControlledNominalVoltage, TransformerRatioManager transformerRatioManager) {

        if (parametersExt.isSvcVoltageMonitoring()) {
            MonitoringVoltageOuterLoop monitoringVoltageOuterLoop = new MonitoringVoltageOuterLoop(maxControlledNominalVoltage);
            modelCheckers.add(monitoringVoltageOuterLoop);
        }

        if (parameters.isUseReactiveLimits()) {
            double effectiveMaxReactivePowerMismatch = switch (parametersExt.getNewtonRaphsonStoppingCriteriaType()) {
                case UNIFORM_CRITERIA -> parametersExt.getNewtonRaphsonConvEpsPerEq();
                case PER_EQUATION_TYPE_CRITERIA -> parametersExt.getMaxReactivePowerMismatch() / PerUnit.SB;
            };
            modelCheckers.add(new ReactiveLimitsOuterLoop(parametersExt.getReactiveLimitsMaxPqPvSwitch(),
                    effectiveMaxReactivePowerMismatch,
                    maxControlledNominalVoltage));
        }

        if (parameters.isTransformerVoltageControlOn()) {
            modelCheckers.add(new MonitoringTapOuterLoop(transformerRatioManager));
        }

    }

    @Override
    protected boolean prepareSolverAndModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<AcOuterLoop> modelCheckers) {

        ContextData contextData = new ContextData();
        groupContext.setData(contextData);

        // Transformer Voltage control is removed from the equations and replaced by continuous tap equations
        // Voltage control for "HT" groups is then disabled

        for (LfBus bus : groupContext.getNetwork().getBuses()) {
            if (!bus.isDisabled() && bus.isTransformerVoltageControlled()) {
                contextData.updateMaxControlledNominalVoltage(bus.getNominalV());
            }
        }

        boolean modelChanged = false;

        // The voltage control of generators that controlled at nominal voltage of
        // the set controlledNominalVoltages are disabled.
        // The transformer voltage controls are enabled.
        for (LfBus bus : groupContext.getNetwork().getControlledBuses(VoltageControl.Type.GENERATOR)) {
            if (bus.getNominalV() <= contextData.getMaxControlledNominalVoltage()) {
                var voltageControl = bus.getGeneratorVoltageControl().orElseThrow();
                voltageControl.getMergedControllerElements().forEach(controllerBus -> {
                    if (controllerBus.isGeneratorVoltageControlEnabled()) {
                        controllerBus.setGenerationTargetQ(controllerBus.getQ().eval());
                        controllerBus.setGeneratorVoltageControlEnabled(false);
                    }
                    contextData.addBusWithVoltageControlDisabled(bus);
                });
                modelChanged = true;
            }
        }

        for (LfBranch branch : groupContext.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            Optional<TransformerVoltageControl> voltageControl = branch.getVoltageControl();
            if (voltageControl.isPresent()) {
                branch.setVoltageControlEnabled(true);
                modelChanged = true;
            }
        }

        if (modelChanged) {
            groupContext.getNetwork().fixTransformerVoltageControls(false);

            transformerRatioManager = new TransformerRatioManager(groupContext, parametersExt.isTransformerVoltageControlStable());

            addModelCheckers(modelCheckers, contextData.getMaxControlledNominalVoltage(), transformerRatioManager);

            groupContext.runSolver(getVoltageInitializer(groupContext), nrReportNode);

            return true;
        } else {
            return false;
        }
        // TODO: Dans un test vérifier la bonne mise à jour du slack
    }

    @Override
    protected void cleanModel(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode, List<Pair<AcOuterLoop, AcOuterLoopContext>> checkersAndContexts) {

        ContextData contextData = (ContextData) groupContext.getData();

        // discretize transformer taps
        for (LfBranch controllerBranch : groupContext.getNetwork().<LfBranch>getControllerElements(VoltageControl.Type.TRANSFORMER)) {
            if (controllerBranch.isVoltageControlEnabled() && !controllerBranch.isDisabled()) {
                controllerBranch.setVoltageControlEnabled(false);

                // round the rho shift to the closest tap
                PiModel piModel = controllerBranch.getPiModel();
                // If stable mode take into account the initial position
                double r1Value = transformerRatioManager.updateContinousRatio(controllerBranch);
                piModel.roundR1ToClosestTap();
                double roundedR1Value = piModel.getR1();
                LOGGER.trace("Round voltage ratio of '{}': {} -> {}", controllerBranch.getId(), r1Value, roundedR1Value);
            }
        }

        // Re-enable generator voltage control
        for (LfBus controllerBus : contextData.getBusesWithVoltageControlDisabled()) {
            controllerBus.setGenerationTargetQ(0);
            controllerBus.setGeneratorVoltageControlEnabled(true);
        }

        checkersAndContexts.forEach(p -> p.getLeft().cleanup(p.getRight()));

    }

    @Override
    public boolean isMultipleUseAllowed() {
        return false;
    }

    @Override
    public String getName() {
        return "Tap Control Outer Loop Group";
    }
}
