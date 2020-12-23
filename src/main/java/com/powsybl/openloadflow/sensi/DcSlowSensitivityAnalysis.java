package com.powsybl.openloadflow.sensi;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.sa.LfContingency;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysis;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class DcSlowSensitivityAnalysis extends AbstractDcSensitivityAnalysis {
    @Override
    protected boolean throwExceptionIfNullInjection() {
        return false;
    }

    @Override
    protected boolean computeSensitivityOnContingency() {
        return true;
    }

    public DcSlowSensitivityAnalysis(final MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    // todo: could be a static method of LfContingency (or a new constructor) ?
    private LfContingency createLfContingency(LfNetwork lfNetwork, Contingency contingency) {
        Set<LfBus> contingencyBuses = new HashSet<>();
        Set<LfBranch> contingencyBranches = new HashSet<>();
        for (ContingencyElement element : contingency.getElements()) {
            if (!element.getType().equals(ContingencyElementType.BRANCH)) {
                throw new UnsupportedOperationException("Currently, we only manage contingency on branches");
            }
            contingencyBranches.add(lfNetwork.getBranchById(element.getId()));
        }
        return new LfContingency(contingency, contingencyBuses, contingencyBranches);
    }

    public List<SensitivityValue> analyseContingency(Network network, LfNetwork lfNetwork, GraphDecrementalConnectivity<LfBus> connectivity, EquationSystem equationSystem,  Contingency contingency,
                                                     List<SensitivityFactor> factors, Map<String, Double> functionReferenceByBranch, LoadFlowParameters loadFlowParameters, OpenSensitivityAnalysisParameters sensiParametersExt) {
        List<Equation> deactivatedEquations = new LinkedList<>();
        List<EquationTerm> deactivatedEquationTerms = new LinkedList<>();
        OpenSecurityAnalysis.deactivateEquations(createLfContingency(lfNetwork, contingency), equationSystem, deactivatedEquations, deactivatedEquationTerms);
        contingency.getElements().forEach(element -> {
            if (!element.getType().equals(ContingencyElementType.BRANCH)) {
                throw new UnsupportedOperationException("Currently, we only manage contingency on branches");
            }
            LfBranch lfBranch = lfNetwork.getBranchById(element.getId());
            connectivity.cut(lfBranch.getBus1(), lfBranch.getBus2());
        });
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = indexFactorsByVariableConfig(network, connectivity, factors, lfNetwork, loadFlowParameters);
        List<SensitivityValue> contingencyValues = analyse(lfNetwork, equationSystem, factorsByVarConfig, functionReferenceByBranch, sensiParametersExt);
        OpenSecurityAnalysis.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);
        connectivity.reset();
        return contingencyValues;
    }

    public List<SensitivityValue> analyse(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig, Map<String, Double> functionReferenceByBranch,
                                                     OpenSensitivityAnalysisParameters sensiParametersExt) {
        // initialize right hand side
        DenseMatrix rhs = initRhs(lfNetwork, equationSystem, factorsByVarConfig);

        // create jacobian matrix either using base network calculated voltages or nominal voltages
        VoltageInitializer voltageInitializer = sensiParametersExt.isUseBaseCaseVoltage() ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        // FIXME: If the equation system represents two distinct connected component, the jacobian wont be invertible
        // FIXME: We need to add a slack in each connected component
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
        LfNetwork lfNetwork = lfNetworks.get(0);
        LazyConnectivity connectivity = new LazyConnectivity(lfNetwork);

        // run DC load
        Map<String, Double> functionReferenceByBranch = getFunctionReferenceByBranch(Collections.singletonList(lfNetwork), lfParameters, lfParametersExt);

        Map<String, List<SensitivityValue>> contingenciesValue = new HashMap<>();

        // todo: some factors may not be in the network anymore, if we lost a connected component due to the contingency

        // create DC equation system for sensitivity analysis
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(),
                new DcEquationSystemCreationParameters(false, true, true, lfParametersExt.isDcUseTransformerRatio()));

        // index factors by variable configuration to compute minimal number of DC state
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = indexFactorsByVariableConfig(network, null, factors, lfNetwork, lfParameters);

        if (factorsByVarConfig.isEmpty()) {
            return Pair.of(Collections.emptyList(), Collections.emptyMap());
        }

        List<SensitivityValue> sensitivityValues = analyse(lfNetwork, equationSystem, factorsByVarConfig, functionReferenceByBranch, sensiParametersExt);

        for (Contingency contingency : contingencies) {
            List<SensitivityValue> contingencyValues = analyseContingency(network, lfNetwork, connectivity.getConnectivity(), equationSystem, contingency, factors, functionReferenceByBranch, lfParameters, sensiParametersExt);
            contingenciesValue.put(contingency.getId(), contingencyValues);
        }

        return Pair.of(sensitivityValues, contingenciesValue);
    }
}
