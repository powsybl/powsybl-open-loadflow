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
import com.powsybl.openloadflow.network.impl.LfContingency;
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

    private class ContingencyManager {
        LfNetwork network;
        EquationSystem equationSystem;
        private GraphDecrementalConnectivity<LfBus> connectivity;
        private List<Equation> deactivatedEquations = new LinkedList<>();
        private List<EquationTerm> deactivatedEquationTerms = new LinkedList<>();
        private List<Equation> createdEquations = new LinkedList<>();

        public ContingencyManager(LfNetwork network, EquationSystem equationSystem, GraphDecrementalConnectivity<LfBus> connectivity) {
            this.network = network;
            this.equationSystem = equationSystem;
            this.connectivity = connectivity;
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

        public void applyContingency(LfContingency contingency) {
            LfContingency.deactivateEquations(contingency, equationSystem, deactivatedEquations, deactivatedEquationTerms);
            contingency.getContingency().getElements().forEach(element -> {
                if (!element.getType().equals(ContingencyElementType.BRANCH)) {
                    throw new UnsupportedOperationException("Only contingency on a branch is yet supported");
                }
                LfBranch lfBranch = network.getBranchById(element.getId());
                connectivity.cut(lfBranch.getBus1(), lfBranch.getBus2());
            });

            // We need to define a slack for each connected component
            Map<Integer, LfBus> slackPerConnectedComponent = new HashMap<>();
            LfBus slackBus = network.getSlackBus();
            slackPerConnectedComponent.put(connectivity.getComponentNumber(slackBus), slackBus);
            for (ContingencyElement element : contingency.getContingency().getElements()) {
                LfBranch contingencyBranch = network.getBranchById(element.getId());

                slackPerConnectedComponent.putIfAbsent(connectivity.getComponentNumber(contingencyBranch.getBus1()), contingencyBranch.getBus1());
                slackPerConnectedComponent.putIfAbsent(connectivity.getComponentNumber(contingencyBranch.getBus2()), contingencyBranch.getBus2());
            }

            for (LfBus ccSlackBus : slackPerConnectedComponent.values()) {
                modifySlackBus(equationSystem, ccSlackBus, createdEquations, deactivatedEquations);
            }
        }

        public void reset() {
            LfContingency.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);
            for (Equation equation : createdEquations) {
                equation.setActive(false);
            }
            connectivity.reset();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DcSlowSensitivityAnalysis.class);

    public List<SensitivityValue> analyseContingency(Network network, LfNetwork lfNetwork, GraphDecrementalConnectivity<LfBus> connectivity,
                                                     EquationSystem equationSystem, Contingency contingency, List<SensitivityFactor> factors,
                                                     Map<String, Double> functionReferenceByBranch, LoadFlowParameters loadFlowParameters) {

        LOGGER.info("Slow sensitivity analysis for contingency " + contingency.getId());

        ContingencyManager manager = new ContingencyManager(lfNetwork, equationSystem, connectivity);
        manager.applyContingency(LfContingency.create(lfNetwork, contingency));
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = indexFactorsByVariableConfig(network, connectivity, factors, lfNetwork, loadFlowParameters);
        List<SensitivityValue> contingencyValues = analyse(lfNetwork, equationSystem, factorsByVarConfig, functionReferenceByBranch, loadFlowParameters);
        manager.reset();
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
