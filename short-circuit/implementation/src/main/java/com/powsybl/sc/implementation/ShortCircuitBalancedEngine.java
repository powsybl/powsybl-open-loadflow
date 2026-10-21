/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sc.util.*;
import org.apache.commons.math3.complex.Complex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitBalancedEngine extends AbstractShortCircuitEngine {

    public ShortCircuitBalancedEngine(Network network, ShortCircuitEngineParameters parameters) {
        super(network, parameters);
    }

    @Override
    public void run() { //can handle both selective and systematic analysis with one single matrix inversion
        LfNetwork lfNetwork = lfNetworks.getFirst();

        // building a contingency list with all voltage levels
        if (parameters.getAnalysisType() == ShortCircuitEngineParameters.AnalysisType.SYSTEMATIC) {
            buildSystematicList(ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        }

        solverFaultList = buildFaultListsFromInputs();
        List<CalculationLocation> solverLocationList = solverFaultList.stream().map(ShortCircuitFault::getCalculationLocation).toList();

        ImpedanceLinearResolutionParameters linearResolutionParameters = new ImpedanceLinearResolutionParameters(acLoadFlowParameters,
                parameters.getMatrixFactory(), solverLocationList, parameters.isVoltageUpdate(), getAdmittanceVoltageProfileTypeFromParam(),
                getAdmittancePeriodTypeFromParam(), AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN, parameters.isIgnoreShunts());

        ImpedanceLinearResolution directResolution = new ImpedanceLinearResolution(lfNetwork, linearResolutionParameters);

        directResolution.run();

        //Build the ShortCircuit results using the Thevenin computation results
        resultsPerFault.clear();
        solverFaultList.forEach(fault -> {
            switch (fault.getShortCircuitFaultType()) {
                case BUS -> processBusShortCircuitFaults(fault, lfNetwork, directResolution);
                case BRANCH -> processBranchShortCircuitFaults(fault, lfNetwork, directResolution);
            }
        });

    }

    /**
     * Computes Id = Eth / (Zth + Zf) for a fault.
     */
    private static Complex computeFaultCurrent(Complex vInit, Complex zth, Complex zf) {
        return vInit.divide(zf.add(zth));
    }

    /**
     * Fills the ShortCircuitResult with delta Voltages used to compute post-fault voltages.
     * Only computed if enabled in the parameters.
     * busToZknf maps each bus number to the Zknf contribution used to derive its delta-V:
     * deltaV(bus) = -Id * zknf(bus).
     */
    private void updateVoltageProfileIfNeeded(ShortCircuitResult res, LfNetwork lfNetwork, Complex id, Map<Integer, Complex> busToZknf) {
        if (!parameters.isVoltageUpdate()) {
            return;
        }
        res.setLfNetwork(lfNetwork);
        res.setTrueVoltageProfileUpdate();

        int nbBusses = lfNetwork.getBuses().size();
        res.createEmptyFortescueVoltageVector(nbBusses);

        for (Map.Entry<Integer, Complex> zd : busToZknf.entrySet()) {
            Complex deltaV = zd.getValue().multiply(id).multiply(-1.);
            res.fillVoltageInFortescueVector(zd.getKey(), deltaV);
        }
    }

    protected void processBusShortCircuitFaults(ShortCircuitFault shortCircuitFault, LfNetwork lfNetwork, ImpedanceLinearResolution directResolution) {
        String busId = shortCircuitFault.getCalculationLocation().getLfBusInfo();

        LfBus lfBus = lfNetwork.getBusById(busId);
        if (lfBus == null) {
            throw new IllegalStateException("Bus not found: " + busId);
        }

        ImpedanceLinearResolution.ImpedanceLinearResolutionResult linearResolutionResult = directResolution.results.get(lfBus);
        if (linearResolutionResult == null) {
            throw new IllegalStateException("No impedance resolution result found for bus: " + busId);
        }

        // For each contingency that matches the given bus of the linear resolution we compute:
        // If = Eth / (Zth + Zf) gives:

        // values that does not change for a given bus in input
        Complex vInit = linearResolutionResult.getEth();
        Complex zth = linearResolutionResult.getZthEq();

        Complex zfToGround = shortCircuitFault.getZf().getZg();
        Complex id = computeFaultCurrent(vInit, zth, zfToGround);

        // The post-fault voltage values at faulted bus are computed as follow :
        // [Vk_r] = [Vk_r_init] - i_nf_r * [zknf_r] + i_nf_i * [zknf_i]
        // [Vk_i] = [Vk_i_init] - i_nf_r * [zknf_i] - i_nf_i * [zknf_Vr]
        Complex zknf = linearResolutionResult.getZknf();
        Complex dv = id.multiply(zknf).multiply(-1.);
        Complex zth20hz = linearResolutionResult.getZthEq20Hz();

        ShortCircuitResult res = new ShortCircuitResult(shortCircuitFault, lfBus, id, zth, vInit, dv,
                linearResolutionResult.getEqSysFeeders(), parameters.getNorm(), zth20hz);

        updateVoltageProfileIfNeeded(res, lfNetwork, id, linearResolutionResult.getBusToZknf());

        res.updateFeedersResult(); // feeders are updated only if voltageUpdate is made
        resultsPerFault.put(shortCircuitFault, res);
    }

    protected void processBranchShortCircuitFaults(ShortCircuitFault shortCircuitFault, LfNetwork lfNetwork, ImpedanceLinearResolution directResolution) {
        String bus1Id = shortCircuitFault.getCalculationLocation().getLfBusInfo();
        String bus2Id = shortCircuitFault.getCalculationLocation().getLfBus2Info();
        LfBranch lfLine = lfNetwork.getBranchById(shortCircuitFault.getElementId());

        LfBus lfBus1 = lfNetwork.getBusById(bus1Id);
        if (lfBus1 == null) {
            throw new IllegalStateException("Bus not found: " + bus1Id);
        }

        ImpedanceLinearResolution.ImpedanceLinearResolutionResult linearResolutionResult1 = directResolution.results.get(lfBus1);
        if (linearResolutionResult1 == null) {
            throw new IllegalStateException("No impedance resolution result found for bus: " + bus1Id);
        }

        LfBus lfBus2 = lfNetwork.getBusById(bus2Id);
        if (lfBus2 == null) {
            throw new IllegalStateException("Bus not found: " + bus2Id);
        }

        // z12 and z22 come from the two-bus result stored on bus1's resolution
        TwoBusImpedanceLinearResolutionResult twoBusResult = findTwoBusResult(linearResolutionResult1, lfBus2, bus1Id, bus2Id);

        Complex vInit1 = linearResolutionResult1.getEth();
        Complex vInit2 = twoBusResult.getV2();

        Complex zthBus1 = linearResolutionResult1.getZthEq(); // Z11
        Complex zthBus2 = twoBusResult.getZ22(); // Z22
        Complex zthBus1Bus2 = twoBusResult.getZ12(); // Z12
        Complex zthBus2Bus1 = twoBusResult.getZ21(); // Z21
        Complex zLine = new Complex(lfLine.getPiModel().getR(), lfLine.getPiModel().getX());

        double r = shortCircuitFault.getCalculationLocation().getProportionalLocationOnLine() / 100.0;
        double s = 1 - r;

        // Fix me: Following formulae imply that nominalV1 = nominalV2
        // r: proportionFromBus1, s: proportionFromBus2
        // vInit = vInit1 * r + vInit2 * s
        Complex vInit = vInit1.multiply(r).add(vInit2.multiply(s));

        // Zth = Z11*s*s + Z22*r*r + (Z12+Z21)*r*s + ZLine*r*s
        Complex zth = zthBus1.multiply(s * s)
                .add(zthBus2.multiply(r * r))
                .add(zthBus1Bus2.multiply(r * s))
                .add(zthBus2Bus1.multiply(r * s))
                .add(zLine.multiply(r * s));

        Complex zfToGround = shortCircuitFault.getZf().getZg();
        Complex id = computeFaultCurrent(vInit, zth, zfToGround);

        // dv = -(s*Zq1 + r*Zq2)*Id
        Complex dv = zthBus1.multiply(s)
                .add(zthBus2.multiply(r))
                .multiply(id.negate());

        Complex zth20hzBus1 = linearResolutionResult1.getZthEq20Hz();
        Complex zth20hzBus2 = twoBusResult.getZ22At20Hz();
        Complex zth20hzBus1Bus2 = twoBusResult.getZ12At20Hz();
        Complex zth20hzBus2Bus1 = twoBusResult.getZ21At20Hz();
        Complex zLine20hz = get20HzLineImpedance(lfLine);
        Complex zth20hz = zth20hzBus1.multiply(s * s)
                .add(zth20hzBus2.multiply(r * r))
                .add(zth20hzBus1Bus2.multiply(r * s))
                .add(zth20hzBus2Bus1.multiply(r * s))
                .add(zLine20hz.multiply(r * s));

        ShortCircuitResult res = new ShortCircuitResult(shortCircuitFault, lfBus1, id, zth, vInit, dv,
                linearResolutionResult1.getEqSysFeeders(), parameters.getNorm(), zth20hz);

        Map<Integer, Complex> combinedBusToZknf = null;
        if (parameters.isVoltageUpdate()) {
            int nbBusses = lfNetwork.getBuses().size();
            combinedBusToZknf = new HashMap<>();
            for (int busNum = 0; busNum < nbBusses; busNum++) {
                Complex zdBus1 = linearResolutionResult1.getBusToZknf().get(busNum);
                Complex zdBus2 = twoBusResult.getBus2ToZknf().get(busNum);
                combinedBusToZknf.put(busNum, zdBus1.multiply(s).add(zdBus2.multiply(r)));
            }
        }
        updateVoltageProfileIfNeeded(res, lfNetwork, id, combinedBusToZknf);

        res.updateFeedersResult(); // feeders are updated only if voltageUpdate is made
        resultsPerFault.put(shortCircuitFault, res);
    }

    private static TwoBusImpedanceLinearResolutionResult findTwoBusResult(
            ImpedanceLinearResolution.ImpedanceLinearResolutionResult resultAtBus1, LfBus lfBus2, String bus1Id, String bus2Id) {
        List<TwoBusImpedanceLinearResolutionResult> twoBusResults =
                resultAtBus1.getTwoBusResults();
        if (twoBusResults == null) {
            throw new IllegalStateException("No two-bus impedance resolution results found for bus: " + bus1Id);
        }
        return twoBusResults.stream()
                .filter(r -> r.getBus2() == lfBus2)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No two-bus impedance resolution result found between bus " + bus1Id + " and bus " + bus2Id));
    }

    private Complex get20HzLineImpedance(LfBranch branch) {
        double freqCoef = 20. / 50.;
        return new Complex(branch.getPiModel().getR(), branch.getPiModel().getX() * freqCoef);
    }
}
