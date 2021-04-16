/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TopologyLevel;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.factors.*;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.functions.BranchIntensity;
import com.powsybl.sensitivity.factors.functions.BusVoltage;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import com.powsybl.sensitivity.factors.variables.TargetVoltage;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SensitivityFactorReaderAdapter implements SensitivityFactorReader {

    private final Network network;

    private final SensitivityFactorsProvider sensitivityFactorsProvider;

    private final List<Contingency> contingencies;

    public SensitivityFactorReaderAdapter(Network network, SensitivityFactorsProvider sensitivityFactorsProvider,
                                          List<Contingency> contingencies) {
        this.network = Objects.requireNonNull(network);
        this.sensitivityFactorsProvider = Objects.requireNonNull(sensitivityFactorsProvider);
        this.contingencies = Objects.requireNonNull(contingencies);
    }

    @Override
    public void read(Handler handler) {
        ContingencyContext commonContingencyContext = new ContingencyContext(ContingencyContextType.ALL, null);
        for (SensitivityFactor factor : sensitivityFactorsProvider.getCommonFactors(network)) {
            read(handler, factor, commonContingencyContext);
        }

        ContingencyContext noContingencyContext = new ContingencyContext(ContingencyContextType.NONE, null);
        for (SensitivityFactor factor : sensitivityFactorsProvider.getAdditionalFactors(network)) {
            read(handler, factor, noContingencyContext);
        }

        for (Contingency contingency : contingencies) {
            ContingencyContext contingencyContext = new ContingencyContext(ContingencyContextType.SPECIFIC, contingency.getId());
            for (SensitivityFactor factor : sensitivityFactorsProvider.getAdditionalFactors(network, contingency.getId())) {
                read(handler, factor, contingencyContext);
            }
        }
    }

    private void read(Handler handler, SensitivityFactor factor, ContingencyContext contingencyContext) {
        if (factor instanceof BranchFlowPerInjectionIncrease) {
            BranchFlow branchFlow = ((BranchFlowPerInjectionIncrease) factor).getFunction();
            InjectionIncrease injectionIncrease = ((BranchFlowPerInjectionIncrease) factor).getVariable();
            handler.onSimpleFactor(factor, SensitivityFunctionType.BRANCH_ACTIVE_POWER, branchFlow.getBranchId(),
                    SensitivityVariableType.INJECTION_ACTIVE_POWER, injectionIncrease.getInjectionId(), contingencyContext);
        } else if (factor instanceof BranchFlowPerPSTAngle) {
            BranchFlow branchFlow = ((BranchFlowPerPSTAngle) factor).getFunction();
            PhaseTapChangerAngle phaseTapChangerAngle = ((BranchFlowPerPSTAngle) factor).getVariable();
            handler.onSimpleFactor(factor, SensitivityFunctionType.BRANCH_ACTIVE_POWER, branchFlow.getBranchId(),
                    SensitivityVariableType.TRANSFORMER_PHASE, phaseTapChangerAngle.getPhaseTapChangerHolderId(), contingencyContext);
        } else if (factor instanceof BranchIntensityPerPSTAngle) {
            BranchIntensity branchIntensity = ((BranchIntensityPerPSTAngle) factor).getFunction();
            PhaseTapChangerAngle phaseTapChangerAngle = ((BranchIntensityPerPSTAngle) factor).getVariable();
            handler.onSimpleFactor(factor, SensitivityFunctionType.BRANCH_CURRENT, branchIntensity.getBranchId(),
                    SensitivityVariableType.TRANSFORMER_PHASE, phaseTapChangerAngle.getPhaseTapChangerHolderId(), contingencyContext);
        } else if (factor instanceof BusVoltagePerTargetV) {
            BusVoltage busVoltage = ((BusVoltagePerTargetV) factor).getFunction();
            TargetVoltage targetVoltage = ((BusVoltagePerTargetV) factor).getVariable();
            Bus bus = busVoltage.getBusRef().resolve(network, TopologyLevel.BUS_BRANCH).orElseThrow(() -> new PowsyblException("The bus ref for '" + busVoltage.getId() + "' cannot be resolved."));
            handler.onSimpleFactor(factor, SensitivityFunctionType.BUS_VOLTAGE, bus.getId(),
                SensitivityVariableType.BUS_TARGET_VOLTAGE, targetVoltage.getEquipmentId(), contingencyContext);
        } else if (factor instanceof BranchFlowPerLinearGlsk) {
            BranchFlow branchFlow = ((BranchFlowPerLinearGlsk) factor).getFunction();
            LinearGlsk linearGlsk = ((BranchFlowPerLinearGlsk) factor).getVariable();
            List<WeightedSensitivityVariable> weightedVariables = linearGlsk.getGLSKs().entrySet().stream()
                    .map(e -> new WeightedSensitivityVariable(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
            handler.onMultipleVariablesFactor(factor, SensitivityFunctionType.BRANCH_ACTIVE_POWER, branchFlow.getBranchId(),
                    SensitivityVariableType.INJECTION_ACTIVE_POWER, linearGlsk.getId(), weightedVariables, contingencyContext);
        } else {
            throw new UnsupportedOperationException("Only factors of type BranchFlow are supported");
        }
    }
}
