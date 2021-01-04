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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class DcSlowSensitivityAnalysis extends AbstractDcSensitivityAnalysis {

    public DcSlowSensitivityAnalysis(final MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DcSlowSensitivityAnalysis.class);

    // todo: could be a static method of LfContingency (or a new constructor) ?
    private LfContingency createLfContingency(LfNetwork lfNetwork, Contingency contingency) {
        Set<LfBus> contingencyBuses = new HashSet<>();
        Set<LfBranch> contingencyBranches = new HashSet<>();
        for (ContingencyElement element : contingency.getElements()) {
            if (!element.getType().equals(ContingencyElementType.BRANCH)) {
                throw new UnsupportedOperationException("Only contingency on a branch is yet supported");
            }
            contingencyBranches.add(lfNetwork.getBranchById(element.getId()));
        }
        return new LfContingency(contingency, contingencyBuses, contingencyBranches);
    }

    private void modifySlackBus(EquationSystem equationSystem, LfBus slackBus, List<Equation> createdEquations, List<Equation> deactivatedEquations) {
        Equation phiEquation = equationSystem.createEquation(slackBus.getNum(), EquationType.BUS_PHI);
        if (!phiEquation.isActive()) {
            phiEquation.setActive(true);
            createdEquations.add(phiEquation);
        }

        if (phiEquation.getTerms().size() == 0) {
            phiEquation.addTerm(new BusPhaseEquationTerm(slackBus, equationSystem.getVariableSet()));
            createdEquations.add(phiEquation);
        }

        Equation equation = equationSystem.createEquation(slackBus.getNum(), EquationType.BUS_P);
        if (equation.isActive()) {
            equation.setActive(false);
            deactivatedEquations.add(equation);
        }
    }

    public List<SensitivityValue> analyseContingency(Network network, LfNetwork lfNetwork, GraphDecrementalConnectivity<LfBus> connectivity,
                                                     EquationSystem equationSystem,  Contingency contingency, List<SensitivityFactor> factors,
                                                     Map<String, Double> functionReferenceByBranch, LoadFlowParameters loadFlowParameters) {

        LOGGER.info("Slow sensitivity analysis for contingency " + contingency.getId());

        List<Equation> deactivatedEquations = new LinkedList<>();
        List<EquationTerm> deactivatedEquationTerms = new LinkedList<>();
        List<Equation> createdEquations = new LinkedList<>();
        OpenSecurityAnalysis.deactivateEquations(createLfContingency(lfNetwork, contingency), equationSystem, deactivatedEquations, deactivatedEquationTerms);
        contingency.getElements().forEach(element -> {
            if (!element.getType().equals(ContingencyElementType.BRANCH)) {
                throw new UnsupportedOperationException("Only contingency on a branch is yet supported");
            }
            LfBranch lfBranch = lfNetwork.getBranchById(element.getId());
            connectivity.cut(lfBranch.getBus1(), lfBranch.getBus2());
        });

        // We need to define a slack for each connected component
        Map<Integer, LfBus> slackPerConnectedComponent = new HashMap<>();
        LfBus slackBus = lfNetwork.getSlackBus();
        slackPerConnectedComponent.put(connectivity.getComponentNumber(slackBus), slackBus);
        for (ContingencyElement element : contingency.getElements()) {
            LfBranch contingencyBranch = lfNetwork.getBranchById(element.getId());

            slackPerConnectedComponent.putIfAbsent(connectivity.getComponentNumber(contingencyBranch.getBus1()), contingencyBranch.getBus1());
            slackPerConnectedComponent.putIfAbsent(connectivity.getComponentNumber(contingencyBranch.getBus2()), contingencyBranch.getBus2());
        }

        for (LfBus ccSlackBus : slackPerConnectedComponent.values()) {
            modifySlackBus(equationSystem, ccSlackBus, createdEquations, deactivatedEquations);
        }

        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = indexFactorsByVariableConfig(network, connectivity, factors, lfNetwork, loadFlowParameters);
        List<SensitivityValue> contingencyValues = analyse(lfNetwork, equationSystem, factorsByVarConfig, functionReferenceByBranch, loadFlowParameters);
        OpenSecurityAnalysis.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);
        for (Equation equation : createdEquations) {
            equation.setActive(false);
        }
        connectivity.reset();
        return contingencyValues;
    }

    public List<SensitivityValue> analyse(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration,
            SensitivityFactorGroup> factorsByVarConfig, Map<String, Double> functionReferenceByBranch, LoadFlowParameters lfParameters) {
        // initialize right hand side
        DenseMatrix rhs = initRhs(lfNetwork, equationSystem, factorsByVarConfig);

        // create jacobian matrix either using base network calculated voltages or nominal voltages
        VoltageInitializer voltageInitializer = lfParameters.getVoltageInitMode() == LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();

        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);

        // solve system
        DenseMatrix states = solveTransposed(rhs, j);

        // calculate sensitivity values
        return calculateSensitivityValues(lfNetwork, equationSystem, factorsByVarConfig, states, functionReferenceByBranch);
    }

    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, List<SensitivityFactor> factors,
                                                                                     List<Contingency> contingencies, LoadFlowParameters lfParameters,
                                                                                     OpenLoadFlowParameters lfParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParametersExt);

        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(network, lfNetwork, contingencies);
        checkSensitivities(network, lfNetwork, factors);

        LazyConnectivity connectivity = new LazyConnectivity(lfNetwork);

        // run DC load
        Map<String, Double> functionReferenceByBranch = getFunctionReferenceByBranch(Collections.singletonList(lfNetwork), lfParameters, lfParametersExt);

        Map<String, List<SensitivityValue>> contingenciesValue = new HashMap<>();

        // create DC equation system for sensitivity analysis
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(),
                new DcEquationSystemCreationParameters(false, true, true, lfParametersExt.isDcUseTransformerRatio()));

        // index factors by variable configuration to compute minimal number of DC state
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = indexFactorsByVariableConfig(network, null, factors, lfNetwork, lfParameters);
        if (factorsByVarConfig.isEmpty()) {
            return Pair.of(Collections.emptyList(), Collections.emptyMap());
        }

        /// compute pre-contingency sensitivity values
        List<SensitivityValue> sensitivityValues = analyse(lfNetwork, equationSystem, factorsByVarConfig, functionReferenceByBranch, lfParameters);

        // compute post-contingency sensitivity values
        for (Contingency contingency : contingencies) {
            List<SensitivityValue> contingencyValues = analyseContingency(network, lfNetwork, connectivity.getConnectivity(),
                    equationSystem, contingency, factors, functionReferenceByBranch, lfParameters);
            contingenciesValue.put(contingency.getId(), contingencyValues);
        }

        return Pair.of(sensitivityValues, contingenciesValue);
    }
}
