/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class TheveninEquivalent {

    private static final Logger LOGGER = LoggerFactory.getLogger(TheveninEquivalent.class);

    private final List<LfNetwork> lfNetworks;

    private final TheveninEquivalentParameters parameters;

    private final ImpedanceLinearResolution impedanceLinearResolution;

    public TheveninEquivalent(Network network, TheveninEquivalentParameters parameters) {
        lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), new LfNetworkParameters());
        ShortCircuitExtensions.add(network, lfNetworks);
        this.parameters = Objects.requireNonNull(parameters);
        LfNetwork lfNetwork = lfNetworks.get(0);
        impedanceLinearResolution = new ImpedanceLinearResolution(lfNetwork, generateAdmittanceLinearResolutionParam(network, parameters));
    }

    public ImpedanceLinearResolution getImpedanceLinearResolution() {
        return impedanceLinearResolution;
    }

    public void run() {
        impedanceLinearResolution.run();
    }

    private static Pair<String, Integer> buildFaultBranchFromBusId(String busId, Network network) {
        Bus bus = network.getBusBreakerView().getBus(busId);
        String branchId = "";
        int branchSide = 0;
        boolean isFound = false;
        for (Branch<?> branch : network.getBranches()) {
            Bus bus1 = branch.getTerminal1().getBusBreakerView().getBus();
            Bus bus2 = branch.getTerminal2().getBusBreakerView().getBus();
            if (bus == bus1) {
                branchId = branch.getId();
                branchSide = 1;
                isFound = true;
                break;
            } else if (bus == bus2) {
                branchId = branch.getId();
                branchSide = 2;
                isFound = true;
                break;
            }
        }

        if (!isFound) {
            LOGGER.warn(" input CC Bus {} could not be associated with a bipole", busId);
        }

        return new Pair<>(branchId, branchSide);
    }

    private static Pair<String, Integer> buildFaultT3WbranchFromBusId(String busId, Network tmpNetwork) {
        Bus bus = tmpNetwork.getBusBreakerView().getBus(busId);
        String branchId = "";
        int legNum = 0;
        boolean isFound = false;
        for (ThreeWindingsTransformer t3w : tmpNetwork.getThreeWindingsTransformers()) {
            Bus bus1 = t3w.getLeg1().getTerminal().getBusBreakerView().getBus();
            Bus bus2 = t3w.getLeg2().getTerminal().getBusBreakerView().getBus();
            Bus bus3 = t3w.getLeg3().getTerminal().getBusBreakerView().getBus();

            if (bus == bus1) {
                branchId = t3w.getId();
                legNum = 1;
                isFound = true;
                break;
            } else if (bus == bus2) {
                branchId = t3w.getId();
                legNum = 2;
                isFound = true;
                break;
            } else if (bus == bus3) {
                branchId = t3w.getId();
                legNum = 3;
                isFound = true;
                break;
            }
        }

        if (!isFound) {
            LOGGER.warn(" input CC Bus {} could not be associated with a tripole", busId);
        }

        return new Pair<>(branchId, legNum);
    }

    private static ImpedanceLinearResolutionParameters generateAdmittanceLinearResolutionParam(Network network, TheveninEquivalentParameters parameters) {

        boolean voltageUpdate = parameters.isVoltageUpdate();

        AcLoadFlowParameters acLoadFlowParameters = parameters.getAcLoadFlowParameters();

        AdmittanceEquationSystem.AdmittanceVoltageProfileType admittanceVoltageProfileType = AdmittanceEquationSystem.AdmittanceVoltageProfileType.CALCULATED;
        if (parameters.getTheveninVoltageProfileType() == TheveninEquivalentParameters.TheveninVoltageProfileType.NOMINAL) {
            admittanceVoltageProfileType = AdmittanceEquationSystem.AdmittanceVoltageProfileType.NOMINAL;
        }

        AdmittanceEquationSystem.AdmittancePeriodType periodType = AdmittanceEquationSystem.AdmittancePeriodType.ADM_TRANSIENT;
        if (parameters.getTheveninPeriodType() == TheveninEquivalentParameters.TheveninPeriodType.THEVENIN_SUB_TRANSIENT) {
            periodType = AdmittanceEquationSystem.AdmittancePeriodType.ADM_SUB_TRANSIENT;
        } else if (parameters.getTheveninPeriodType() == TheveninEquivalentParameters.TheveninPeriodType.THEVENIN_STEADY_STATE) {
            periodType = AdmittanceEquationSystem.AdmittancePeriodType.ADM_STEADY_STATE;
        }

        List<CalculationLocation> locations = new ArrayList<>();
        for (CalculationLocation calculationLocation : parameters.getLocations()) {
            String busName = calculationLocation.getBusLocation();
            Pair<String, Integer> branchFaultInfo = buildFaultBranchFromBusId(busName, network);

            if (branchFaultInfo.getKey().equals("")) {
                // Bus not found in branches, try three windings transformers
                branchFaultInfo = buildFaultT3WbranchFromBusId(busName, network);
            }

            calculationLocation.setIidmBusInfo(branchFaultInfo);
            locations.add(calculationLocation);
        }

        return new ImpedanceLinearResolutionParameters(acLoadFlowParameters, parameters.getMatrixFactory(),
                locations, voltageUpdate, admittanceVoltageProfileType, periodType,
                AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN, parameters.isTheveninIgnoreShunts());
    }
}
