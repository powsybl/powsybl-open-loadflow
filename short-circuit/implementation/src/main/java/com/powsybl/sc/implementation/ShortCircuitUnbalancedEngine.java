/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sc.util.*;
import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitUnbalancedEngine extends AbstractShortCircuitEngine {

    public ShortCircuitUnbalancedEngine(Network network, ShortCircuitEngineParameters parameters) {
        super(network, parameters);
    }

    @Override
    public void run() {
        LfNetwork lfNetwork = lfNetworks.getFirst();

        if (parameters.getAnalysisType() == ShortCircuitEngineParameters.AnalysisType.SYSTEMATIC) {
            buildSystematicList(ShortCircuitFault.ShortCircuitType.MONOPHASED); // TODO : by default it is monophased, could be changed to choose type of systematic default
            // Biphased common support faults will not be supported yet in systematic
        }

        // We handle a pre-treatement of faults given in input:
        // - filtering of some inconsistencies on the bus identification
        // - addition of info in each fault to ease the identification in LfNetwork of iidm info
        solverFaultList = buildFaultListsFromInputs();
        List<CalculationLocation> solverLocationList = solverFaultList.stream().map(ShortCircuitFault::getCalculationLocation).toList();

        ImpedanceLinearResolutionParameters admittanceLinearResolutionParametersHomopolar = new ImpedanceLinearResolutionParameters(acLoadFlowParameters,
                parameters.getMatrixFactory(), solverLocationList, parameters.isVoltageUpdate(),
                getAdmittanceVoltageProfileTypeFromParam(), getAdmittancePeriodTypeFromParam(), AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN_HOMOPOLAR,
                parameters.isIgnoreShunts());

        ImpedanceLinearResolutionParameters admittanceLinearResolutionParametersDirect = new ImpedanceLinearResolutionParameters(acLoadFlowParameters,
                parameters.getMatrixFactory(), solverLocationList, parameters.isVoltageUpdate(),
                getAdmittanceVoltageProfileTypeFromParam(), getAdmittancePeriodTypeFromParam(), AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN,
                parameters.isIgnoreShunts());

        ImpedanceLinearResolution directResolution = new ImpedanceLinearResolution(lfNetwork, admittanceLinearResolutionParametersDirect);
        ImpedanceLinearResolution homopolarResolution = new ImpedanceLinearResolution(lfNetwork, admittanceLinearResolutionParametersHomopolar);

        directResolution.run();
        homopolarResolution.run();

        //Build the ShortCircuit results using the linear resolution computation results
        resultsPerFault.clear();
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.BIPHASED);
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT);
    }

    public void processAdmittanceLinearResolutionResults(LfNetwork lfNetwork, ImpedanceLinearResolution directResolution,
            ImpedanceLinearResolution homopolarResolution, ShortCircuitFault.ShortCircuitType shortCircuitType) {

        for (Map.Entry<LfBus, ImpedanceLinearResolution.ImpedanceLinearResolutionResult> entry
                : directResolution.results.entrySet()) {

            LfBus lfBus = entry.getKey();
            ImpedanceLinearResolution.ImpedanceLinearResolutionResult directResult = entry.getValue();

            ImpedanceLinearResolution.ImpedanceLinearResolutionResult homopolarResult =
                    homopolarResolution.results.get(lfBus);
            LfBus lfBus1 = directResult.getBus();

            List<ShortCircuitFault> matchingFaultsAtBus1 = new ArrayList<>(); //We build a list of all faults with bus1 matching with bus1 of ImpedanceLinearResolutionResult

            for (ShortCircuitFault scfe : solverFaultList) {
                if (lfBus1.getId().equals(scfe.getCalculationLocation().getLfBusInfo()) && scfe.getType() == shortCircuitType) {
                    matchingFaultsAtBus1.add(scfe);
                }
            }

            Complex v1dInit = directResult.getEth();
            Complex zdf = directResult.getZthEq();
            Complex zof = homopolarResult.getZthEq();

            for (ShortCircuitFault scf : matchingFaultsAtBus1) {

                Complex io;
                Complex id;
                Complex ii;

                ShortCircuitResult res;

                if (shortCircuitType == ShortCircuitFault.ShortCircuitType.MONOPHASED
                        || shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED
                        || shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND) {
                    if (shortCircuitType == ShortCircuitFault.ShortCircuitType.MONOPHASED) {
                        MonophasedShortCircuitCalculator monophasedCalculator = new MonophasedShortCircuitCalculator(zdf, zof, scf.getZf(), v1dInit);
                        monophasedCalculator.computeCurrents();

                        io = monophasedCalculator.getIo();
                        id = monophasedCalculator.getId();
                        ii = monophasedCalculator.getIi();

                    } else if (shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED) {
                        BiphasedShortCircuitCalculator biphasedCalculator = new BiphasedShortCircuitCalculator(zdf, zof, scf.getZf(), v1dInit);
                        biphasedCalculator.computeCurrents();

                        io = biphasedCalculator.getIo();
                        id = biphasedCalculator.getId();
                        ii = biphasedCalculator.getIi();
                    } else {
                        BiphasedGroundShortCircuitCalculator biphasedGrCalculator = new BiphasedGroundShortCircuitCalculator(zdf, zof, scf.getZf(), v1dInit);
                        biphasedGrCalculator.computeCurrents();

                        io = biphasedGrCalculator.getIo();
                        id = biphasedGrCalculator.getId();
                        ii = biphasedGrCalculator.getIi();
                    }

                    res = buildUnbalancedResult(id, io, ii, zdf, zof,
                            directResult, homopolarResult,
                            scf, lfBus1, v1dInit, lfNetwork);

                    res.updateFeedersResult(); // feeders are updated only if voltageUpdate is made. TODO : see if update of homopolar feeders are to be updated
                    resultsPerFault.put(scf, res);

                } else if (shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT) {

                    int numBiphasedResult = 0;
                    TwoBusImpedanceLinearResolutionResult biphasedHomopolarResult;
                    for (TwoBusImpedanceLinearResolutionResult biphasedDirectResult : directResult.getTwoBusResults()) {
                        biphasedHomopolarResult = homopolarResult.getTwoBusResults().get(numBiphasedResult);
                        numBiphasedResult++;

                        LfBus lfBus2 = biphasedDirectResult.getBus2();
                        if (lfBus2.getId().equals(scf.getCalculationLocation().getLfBus2Info())) {

                            Complex zo12 = biphasedHomopolarResult.getZ12();
                            Complex zo22 = biphasedHomopolarResult.getZ22();
                            Complex zo21 = biphasedHomopolarResult.getZ21();

                            Complex zd12 = biphasedDirectResult.getZ12();
                            Complex zd22 = biphasedDirectResult.getZ22();
                            Complex zd21 = biphasedDirectResult.getZ21();

                            BiphasedCommonSupportShortCircuitCalculator biCsSc;
                            if (scf.getBiphasedType() == ShortCircuitFault.ShortCircuitBiphasedType.C1_A2) {
                                biCsSc = new BiphasedC1A2Calculator(zdf, zof, scf.getZf(), v1dInit, biphasedDirectResult.getV2(),
                                        zo12, zo22, zo21,
                                        zd12, zd22, zd21);
                            } else if (scf.getBiphasedType() == ShortCircuitFault.ShortCircuitBiphasedType.C1_B2) {
                                biCsSc = new BiphasedC1B2Calculator(zdf, zof, scf.getZf(), v1dInit, biphasedDirectResult.getV2(),
                                        zo12, zo22, zo21,
                                        zd12, zd22, zd21);
                            } else if (scf.getBiphasedType() == ShortCircuitFault.ShortCircuitBiphasedType.C1_C2) {
                                biCsSc = new BiphasedC1C2Calculator(zdf, zof, scf.getZf(), v1dInit, biphasedDirectResult.getV2(),
                                        zo12, zo22, zo21,
                                        zd12, zd22, zd21);
                            } else {
                                throw new IllegalArgumentException(" short circuit fault of type : " + scf.getBiphasedType() + " not yet handled");
                            }

                            Complex v2dInit = biphasedDirectResult.getV2();

                            res = buildUnbalancedCommunSuppportResult(biCsSc.getId(), biCsSc.getIo(), biCsSc.getIi(),
                                    biCsSc.getI2d(), biCsSc.getI2o(), biCsSc.getI2i(),
                                    biCsSc.getVd(), biCsSc.getVo(), biCsSc.getVi(),
                                    biCsSc.getV2d(), biCsSc.getV2o(), biCsSc.getV2i(),
                                    zdf, zof,
                                    directResult, homopolarResult, scf,
                                    lfBus1, v1dInit, lfNetwork,
                                    lfBus2, v2dInit, biphasedDirectResult, biphasedHomopolarResult);

                            res.updateFeedersResult(); // feeders are updated only if voltageUpdate is made. TODO : see if update of homopolar feeders are to be updated
                            resultsPerFault.put(scf, res);

                        } else {
                            throw new IllegalArgumentException(" Post-processing of short circuit type = " + shortCircuitType + "not yet implemented");
                        }
                    }
                }
            }
        }
    }

    public ShortCircuitResult buildUnbalancedResult(Complex id, Complex io, Complex ii, Complex zdf, Complex zof,
                                                    ImpedanceLinearResolution.ImpedanceLinearResolutionResult directResult,
                                                    ImpedanceLinearResolution.ImpedanceLinearResolutionResult homopolarResult,
                                                    ShortCircuitFault scf, LfBus lfBus1, Complex v1dInit,
                                                    LfNetwork lfNetwork) {
        //get the voltage vectors
        // Vo :
        // [vox]      [ rof  -xof ]   [ iox ]
        // [voy] = -  [ xof   rof ] * [ ioy ]

        Complex vo = zof.multiply(io).multiply(-1.);
        Complex vd = zdf.multiply(id).multiply(-1.);
        Complex vi = zdf.multiply(ii).multiply(-1.);

        //record the results
        FeedersAtNetwork equationSystemFeedersDirect = directResult.getEqSysFeeders();
        FeedersAtNetwork equationSystemFeedersHomopolar = homopolarResult.getEqSysFeeders();

        ShortCircuitResult res = new ShortCircuitResult(scf, lfBus1,
                id, io, ii,
                zdf, zof, zdf,
                v1dInit, vd, vo, vi,
                equationSystemFeedersDirect, equationSystemFeedersHomopolar, parameters.getNorm(),
                directResult.getZthEq20Hz(), homopolarResult.getZthEq20Hz());

        if (parameters.isVoltageUpdate()) {
            res.setLfNetwork(lfNetwork);
            res.setTrueVoltageProfileUpdate();
            // The post-fault voltage values for the network busses are computed as follow :
            // [ Vof ] = -inv(Yo) * M * [ Iof ]
            // [ Vdf ] = -inv(Yd) * M * [ Idf ] + [ V(init) ]
            // [ Vif ] = -inv(Yd) * M * [ Iif ]
            // dMo = inv(Yo) * M
            // dMd = inv(Yd) * M

            int nbBusses = lfNetwork.getBuses().size();
            res.createEmptyFortescueVoltageVector(nbBusses);

            for (Map.Entry<Integer, Complex> zd : directResult.getBusToZknf().entrySet()) {
                int busNum = zd.getKey();

                //direct
                Complex zdBus = zd.getValue();
                Complex deltaVd = zdBus.multiply(id);

                //inverse
                Complex deltaVi = zdBus.multiply(ii);

                //homopolar
                Complex zo = homopolarResult.getBusToZknf().get(busNum);
                Complex deltaVo = zo.multiply(io);

                //System.out.println(" dVth(" + vdr.getKey() + ") = " + edVr + " + j(" + edVi + ")");

                res.fillVoltageInFortescueVector(busNum, deltaVd, deltaVo, deltaVi);
            }
        }

        return res;
    }

    public ShortCircuitResult buildUnbalancedCommunSuppportResult(Complex id, Complex io, Complex ii, Complex i2d, Complex i2o, Complex i2i, Complex dvd,
            Complex dvo, Complex dvi, Complex dv2d, Complex dv2o, Complex dv2i, Complex zdf, Complex zof,
                                                                  ImpedanceLinearResolution.ImpedanceLinearResolutionResult directResult,
                                                                  ImpedanceLinearResolution.ImpedanceLinearResolutionResult homopolarResult, ShortCircuitFault scf,
                                                                  LfBus lfBus1, Complex v1dInit, LfNetwork lfNetwork,
                                                                  LfBus lfBus2, Complex v2dInit,
                                                                  TwoBusImpedanceLinearResolutionResult biphasedDirectResult,
                                                                  TwoBusImpedanceLinearResolutionResult biphasedHomopolarResult) {

        //record the results
        FeedersAtNetwork equationSystemFeedersDirect = directResult.getEqSysFeeders();
        FeedersAtNetwork equationSystemFeedersHomopolar = homopolarResult.getEqSysFeeders();

        ShortCircuitResult res = new ShortCircuitResult(scf, lfBus1,
                id, io, ii,
                zdf, zof, zdf,
                v1dInit,
                dvd, dvo, dvi,
                equationSystemFeedersDirect, equationSystemFeedersHomopolar, parameters.getNorm(),
                i2d, i2o, i2i,
                v2dInit,
                dv2d, dv2o, dv2i,
                lfBus2, directResult.getZthEq20Hz(), homopolarResult.getZthEq20Hz());

        if (parameters.isVoltageUpdate()) {
            res.setLfNetwork(lfNetwork);
            res.setTrueVoltageProfileUpdate();
            // The post-fault voltage values for the network busses are computed as follow :
            // [ Vof ] = -inv(Yo) * M * [ Iof ]
            // [ Vdf ] = -inv(Yd) * M * [ Idf ] + [ V(init) ]
            // [ Vif ] = -inv(Yd) * M * [ Iif ]
            // dMo = inv(Yo) * M
            // dMd = inv(Yd) * M

            int nbBusses = lfNetwork.getBuses().size();
            res.createEmptyFortescueVoltageVector(nbBusses);

            for (Map.Entry<Integer, Complex> zd : directResult.getBusToZknf().entrySet()) {
                int busNum = zd.getKey();

                //direct
                Complex zdBus2 = biphasedDirectResult.getBus2ToZknf().get(busNum);
                Complex deltaVd = id.multiply(zd.getValue()).add(i2d.multiply(zdBus2));

                //inverse
                Complex deltaVi = ii.multiply(zd.getValue()).add(i2i.multiply(zdBus2));

                //homopolar
                Complex zoBus = homopolarResult.getBusToZknf().get(busNum);
                Complex zoBus2 = biphasedHomopolarResult.getBus2ToZknf().get(busNum);

                Complex deltaVo = io.multiply(zoBus).add(i2o.multiply(zoBus2));

                res.fillVoltageInFortescueVector(busNum, deltaVd, deltaVo, deltaVi);
            }
        }

        return res;
    }
}
