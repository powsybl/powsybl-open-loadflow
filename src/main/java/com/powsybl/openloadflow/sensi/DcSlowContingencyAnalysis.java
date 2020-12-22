package com.powsybl.openloadflow.sensi;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import org.jgrapht.alg.util.Pair;

import javax.annotation.Nullable;
import java.util.*;

public class DcSlowContingencyAnalysis extends AbstractDcSensitivityAnalysis {
    @Override
    protected boolean throwExceptionIfNullInjection() {
        return false;
    }

    @Override
    protected boolean computeSensitivityOnContingency() {
        return true;
    }

    public DcSlowContingencyAnalysis(final MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    private void disconnectContingency(Network network, Contingency contingency) {
        if (contingency == null) {
            return;
        }

        contingency.getElements().forEach(element -> {
            if (!element.getType().equals(ContingencyElementType.BRANCH)) {
                throw new UnsupportedOperationException("Currently, only Branches are accepted as contingencies element");
            }
            network.getLine(element.getId()).getTerminal1().disconnect();
            network.getLine(element.getId()).getTerminal2().disconnect();
        });
    }

    public List<SensitivityValue> analyseContingency(Network network, List<SensitivityFactor> factors, @Nullable Contingency contingency, Map<String, Double> functionReferenceByBranch,
                                                      LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, OpenSensitivityAnalysisParameters sensiParametersExt) {
        String workingVariant = network.getVariantManager().getWorkingVariantId();
        String localVariant = workingVariant + (contingency == null ? "" : contingency.getId());
        if (!localVariant.equals(workingVariant)) {
            network.getVariantManager().cloneVariant(workingVariant, localVariant); // todo: make sure it is not used
            network.getVariantManager().setWorkingVariant(localVariant);
            // todo: find a way to avoid forgetting to switch back variant
        }

        disconnectContingency(network, contingency);

        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);
        // todo: some factors may not be in the network anymore, if we lost a connected component due to the contingency

        // create DC equation system for sensitivity analysis
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(),
                new DcEquationSystemCreationParameters(false, true, true, lfParametersExt.isDcUseTransformerRatio()));

        // index factors by variable configuration to compute minimal number of DC state
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = indexFactorsByVariableConfig(network, factors, lfNetwork, lfParameters);

        network.getVariantManager().setWorkingVariant(workingVariant);

        if (factorsByVarConfig.isEmpty()) {
            return Collections.emptyList();
        }

        // initialize right hand side
        DenseMatrix rhs = initRhs(lfNetwork, equationSystem, factorsByVarConfig);

        // create jacobian matrix either using base network calculated voltages or nominal voltages
        VoltageInitializer voltageInitializer = sensiParametersExt.isUseBaseCaseVoltage() ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);

        // solve system
        DenseMatrix states = solveTransposed(rhs, j);

        // calculate sensitivity values
        return calculateSensitivityValues(lfNetwork, equationSystem, factorsByVarConfig, states, functionReferenceByBranch);
    }

    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, List<SensitivityFactor> factors, List<Contingency> contingencies, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                                                     OpenSensitivityAnalysisParameters sensiParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParametersExt);
        Objects.requireNonNull(sensiParametersExt);

        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        // run DC load
        Map<String, Double> functionReferenceByBranch = getFunctionReferenceByBranch(lfNetworks, lfParameters, lfParametersExt);

        List<SensitivityValue> sensitivityValues = analyseContingency(network, factors, null, functionReferenceByBranch, lfParameters, lfParametersExt, sensiParametersExt);
        Map<String, List<SensitivityValue>> contingenciesValue = new HashMap<>();

        for (Contingency contingency : contingencies) {
            List<SensitivityValue> contingencyValues = analyseContingency(network, factors, contingency, functionReferenceByBranch, lfParameters, lfParametersExt, sensiParametersExt);
            contingenciesValue.put(contingency.getId(), contingencyValues);
        }

        return Pair.of(sensitivityValues, contingenciesValue);
    }
}
