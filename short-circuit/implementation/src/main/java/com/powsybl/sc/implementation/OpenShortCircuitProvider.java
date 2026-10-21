/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.google.auto.service.AutoService;
import com.google.common.base.Stopwatch;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.sc.util.CalculationLocation;
import com.powsybl.sc.util.FeedersAtBusResult;
import com.powsybl.shortcircuit.*;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
@AutoService(ShortCircuitAnalysisProvider.class)
public class OpenShortCircuitProvider implements ShortCircuitAnalysisProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenShortCircuitProvider.class);

    private final MatrixFactory matrixFactory;

    public OpenShortCircuitProvider() {
        this(new SparseMatrixFactory());
    }

    public OpenShortCircuitProvider(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    @Override
    public String getName() {
        return "OpenShortCircuit";
    }

    @Override
    public String getVersion() {
        return "0.1";
    }

    @Override
    public CompletableFuture<ShortCircuitAnalysisResult> run(Network network, List<Fault> faults, ShortCircuitParameters parameters,
            ComputationManager computationManager, List<FaultParameters> faultParameters) {

        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        Stopwatch stopwatch = Stopwatch.createStarted();

        LoadFlowParameters lfParameters = new LoadFlowParameters();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(matrixFactory));

        LoadFlowResult lfResult = loadFlowRunner.run(network, lfParameters);

        // building of fault lists
        List<ShortCircuitFault> faultsList = new ArrayList<>();
        Map<ShortCircuitFault, Fault> scFaultToFault = new HashMap<>(); // for now we use this map to get the correspondence between short circuit provider and internal modelling of fault

        Pair<Boolean, Boolean> faultTypes = buildFaultLists(network, faults, faultsList, scFaultToFault);
        boolean existBalancedFaults = faultTypes.getKey();
        boolean existUnbalancedFaults = faultTypes.getValue();

        //Parameters that could be added in the short circuit provider API later:
        // Voltage Profile
        ShortCircuitEngineParameters.VoltageProfileType voltageProfile = ShortCircuitEngineParameters.VoltageProfileType.NOMINAL;

        // Selective or Systematic short circuit analysis
        ShortCircuitEngineParameters.AnalysisType at = ShortCircuitEngineParameters.AnalysisType.SELECTIVE;

        // selection of the period of analysis
        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;

        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        ShortCircuitNorm shortCircuitNorm = new ShortCircuitNormNone();

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory, at, faultsList, true, voltageProfile, false, periodType, shortCircuitNorm);

        // lists to store the results
        List<FaultResult> faultResults = new ArrayList<>();

        if (existBalancedFaults) {
            runBalancedAnalysis(network, scbParameters, scFaultToFault, faultResults);
        }

        if (existUnbalancedFaults) {
            runUnbalancedAnalysis(network, scbParameters, scFaultToFault, faultResults);
        }

        LOGGER.info("Short circuit calculation done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return CompletableFuture.completedFuture(new ShortCircuitAnalysisResult(faultResults));
    }

    public void runUnbalancedAnalysis(Network network, ShortCircuitEngineParameters scbParameters, Map<ShortCircuitFault, Fault> scFaultToFault, List<FaultResult> faultResults) {
        ShortCircuitUnbalancedEngine scuEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);
        scuEngine.run();

        // the results per faults might be inconsistent if many busses per voltage level
        // TODO : see how this could be improved by allowing results per electrical bus on the short circuit provider
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> scResult : scuEngine.resultsPerFault.entrySet()) {
            ShortCircuitFault scFault = scResult.getKey();

            double iccMagnitude = scResult.getValue().getIk().abs();

            Fault fault = scFaultToFault.get(scFault);

            List<FeederResult> feederResults = new ArrayList<>();
            List<LimitViolation> limitViolations = new ArrayList<>();
            MagnitudeFaultResult magnitudeFaultResult = new MagnitudeFaultResult(fault, 0., feederResults, limitViolations, iccMagnitude, FaultResult.Status.SUCCESS);
            faultResults.add(magnitudeFaultResult);
        }
    }

    public void runBalancedAnalysis(Network network, ShortCircuitEngineParameters scbParameters, Map<ShortCircuitFault, Fault> scFaultToFault, List<FaultResult> faultResults) {
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);
        scbEngine.run();

        // the results per faults might be inconsistent if many busses per voltage level
        // TODO : see how this could be improved by allowing results per electrical bus on the short circuit provider
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> scFaultResult : scbEngine.resultsPerFault.entrySet()) {
            ShortCircuitFault scFault = scFaultResult.getKey();
            ShortCircuitResult scResult = scFaultResult.getValue();

            double iccMagnitude = scResult.getIk().abs();

            Fault fault = scFaultToFault.get(scFault);

            List<FeederResult> feederResultsProvider = new ArrayList<>();
            fillFeederResults(feederResultsProvider, scResult);

            List<LimitViolation> limitViolations = new ArrayList<>();

            MagnitudeFaultResult magnitudeFaultResult = new MagnitudeFaultResult(fault, 0., feederResultsProvider, limitViolations, iccMagnitude, FaultResult.Status.SUCCESS);
            faultResults.add(magnitudeFaultResult);
        }
    }

    public void fillFeederResults(List<FeederResult> feederResultsProvider, ShortCircuitResult scResult) {
        for (Map.Entry<LfBus, FeedersAtBusResult> busAndFeedersAtBusResult : scResult.getFeedersResultDirect().entrySet()) {
            LfBus lfBus = busAndFeedersAtBusResult.getKey();
            FeedersAtBusResult feedersAtBusResult = busAndFeedersAtBusResult.getValue();
            for (com.powsybl.sc.util.FeederResult feederResult : feedersAtBusResult.getBusFeedersResult()) {
                Complex iCont = feederResult.getIContribution();

                double magnitude = Math.sqrt(3.) * iCont.abs() * 100. / lfBus.getNominalV(); // same dimension as Ik3

                String feederId = lfBus.getId() + "_" + feederResult.getFeeder().getId();

                MagnitudeFeederResult magnitudeFeederResult = new MagnitudeFeederResult(feederId, magnitude);
                feederResultsProvider.add(magnitudeFeederResult);
            }
        }

    }

    public Pair<Boolean, Boolean> buildFaultLists(Network network, List<Fault> faults, List<ShortCircuitFault> balancedFaultsList, Map<ShortCircuitFault, Fault> scFaultToFault) {
        boolean existBalancedFaults = false;
        boolean existUnbalancedFaults = false;

        for (Fault fault : faults) {
            ShortCircuitFault.ShortCircuitType scType = ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND; // Default type

            if (fault.getFaultType() == Fault.FaultType.SINGLE_PHASE) {
                existUnbalancedFaults = true;
                scType = ShortCircuitFault.ShortCircuitType.MONOPHASED;
            } else if (fault.getFaultType() == Fault.FaultType.THREE_PHASE) {
                existBalancedFaults = true;
            } else {
                LOGGER.warn("Short circuit of unknown type, fault: {} is ignored", fault.getId());
                continue;
            }

            // TODO : transform parallel input into a series input
            if (fault.getConnectionType() == Fault.ConnectionType.PARALLEL) {
                LOGGER.warn("Short circuit connection of type PARALLEL not yet supported, fault: {} is ignored", fault.getId());
                continue;
            }

            fillFaultLists(fault, network, scType, balancedFaultsList, scFaultToFault);
        }

        return new Pair<>(existBalancedFaults, existUnbalancedFaults);
    }

    private void checkForBranchNominalVoltageCoherency(Network network, String busId1, String busId2) {
        // TODO: to remove when different nominal voltages at branch sides are correctly handled
        Bus bus1 = network.getBusBreakerView().getBus(busId1);
        Bus bus2 = network.getBusBreakerView().getBus(busId2);

        double nominalV1 = bus1.getVoltageLevel().getNominalV();
        double nominalV2 = bus2.getVoltageLevel().getNominalV();

        if (nominalV1 != nominalV2) {
            LOGGER.warn("Default on a Branch connecting two Buses of different nominal voltages: A fictitious ideal transformer is added at side 1 of the branch");
        }
    }

    private void fillFaultLists(Fault fault, Network network, ShortCircuitFault.ShortCircuitType scType, List<ShortCircuitFault> balancedFaultsList, Map<ShortCircuitFault, Fault> scFaultToFault) {
        Complex zFaultToGround = new Complex(fault.getRToGround(), fault.getXToGround());
        ShortCircuitFaultImpedance scz = new ShortCircuitFaultImpedance(zFaultToGround);

        ShortCircuitFault sc;
        // TODO : see how to get lfBus from iidm Bus
        String elementId = fault.getElementId();

        if (fault instanceof BranchFault branchFault) { // Branch fault
            Pair<String, String> branchBusIds = getBranchBusIdsFromElementId(elementId, fault.getId(), network);

            if (branchBusIds == null) {
                return;
            }

            CalculationLocation location = new CalculationLocation(branchBusIds.getKey(), branchBusIds.getValue(), branchFault.getProportionalLocation());
            sc = new ShortCircuitFault(location, branchFault.getId(), elementId, scz, scType);
            checkForBranchNominalVoltageCoherency(network, branchBusIds.getKey(), branchBusIds.getValue());
        } else { //Bus fault
            String busId = getBusId(elementId, fault.getId(), network);

            if (busId == null) {
                return;
            }

            sc = new ShortCircuitFault(busId, fault.getId(), elementId, scz, scType);
        }

        balancedFaultsList.add(sc);
        scFaultToFault.put(sc, fault);
    }

    private static Pair<String, String> getBranchBusIdsFromElementId(String elementId, String faultId, Network network) {
        Identifiable<?> element = network.getIdentifiable(elementId);

        if (element == null) {
            LOGGER.warn("Element '{}' not found in the network. Fault '{}' is ignored.", elementId, faultId);
            return null;
        }

        if (!(element instanceof Branch<?> branch)) {
            LOGGER.warn("Element '{}' is not a branch. Fault '{}' is ignored.", elementId, faultId);
            return null;
        }

        Bus bus1 = branch.getTerminal1().getBusBreakerView().getBus();
        Bus bus2 = branch.getTerminal2().getBusBreakerView().getBus();

        if (bus1 == null) {
            LOGGER.warn(
                    "Terminal1 of branch '{}' is not connected to a bus. Fault '{}' is ignored.",
                    elementId, faultId);
            return null;
        }

        if (bus2 == null) {
            LOGGER.warn(
                    "Terminal2 of branch '{}' is not connected to a bus. Fault '{}' is ignored.",
                    elementId, faultId);
            return null;
        }

        return new Pair<>(bus1.getId(), bus2.getId());
    }

    private static String getBusId(String elementId, String faultId, Network network) {
        Identifiable<?> element = network.getIdentifiable(elementId);

        if (element == null) {
            LOGGER.warn("Element '{}' not found in network. Fault '{}' is ignored.", elementId, faultId);
            return null;
        }

        if (element instanceof Bus bus) {
            return bus.getId();
        }

        LOGGER.warn("'{}' is not the id of a bus. Fault '{}' is ignored.", elementId, faultId);
        return null;
    }
}
