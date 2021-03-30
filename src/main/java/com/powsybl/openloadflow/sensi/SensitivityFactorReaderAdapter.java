/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.factors.*;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.functions.BranchIntensity;
import com.powsybl.sensitivity.factors.functions.BusVoltage;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import com.powsybl.sensitivity.factors.variables.TargetV;

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
        for (SensitivityFactor factor : sensitivityFactorsProvider.getCommonFactors(network)) {
            read(handler, factor);
        }
        // FIXME additional factors are not yet supported
        if (!sensitivityFactorsProvider.getAdditionalFactors(network).isEmpty()) {
            throw new UnsupportedOperationException("Factors specific to base case not yet supported");
        }
        for (Contingency contingency : contingencies) {
            if (!sensitivityFactorsProvider.getAdditionalFactors(network, contingency.getId()).isEmpty()) {
                throw new UnsupportedOperationException("Factors specific to one contingency not yet supported");
            }
        }
    }

    private void read(Handler handler, SensitivityFactor factor) {
        if (factor instanceof BranchFlowPerInjectionIncrease) {
            BranchFlow branchFlow = ((BranchFlowPerInjectionIncrease) factor).getFunction();
            InjectionIncrease injectionIncrease = ((BranchFlowPerInjectionIncrease) factor).getVariable();
            handler.onSimpleFactor(factor, SensitivityFunctionType.BRANCH_ACTIVE_POWER, branchFlow.getBranchId(),
                    SensitivityVariableType.INJECTION_ACTIVE_POWER, injectionIncrease.getInjectionId());
        } else if (factor instanceof BranchFlowPerPSTAngle) {
            BranchFlow branchFlow = ((BranchFlowPerPSTAngle) factor).getFunction();
            PhaseTapChangerAngle phaseTapChangerAngle = ((BranchFlowPerPSTAngle) factor).getVariable();
            handler.onSimpleFactor(factor, SensitivityFunctionType.BRANCH_ACTIVE_POWER, branchFlow.getBranchId(),
                    SensitivityVariableType.TRANSFORMER_PHASE, phaseTapChangerAngle.getPhaseTapChangerHolderId());
        } else if (factor instanceof BranchIntensityPerPSTAngle) {
            BranchIntensity branchIntensity = ((BranchIntensityPerPSTAngle) factor).getFunction();
            PhaseTapChangerAngle phaseTapChangerAngle = ((BranchIntensityPerPSTAngle) factor).getVariable();
            handler.onSimpleFactor(factor, SensitivityFunctionType.BRANCH_CURRENT, branchIntensity.getBranchId(),
                    SensitivityVariableType.TRANSFORMER_PHASE, phaseTapChangerAngle.getPhaseTapChangerHolderId());
        } else if (factor instanceof BranchFlowPerLinearGlsk) {
            BranchFlow branchFlow = ((BranchFlowPerLinearGlsk) factor).getFunction();
            LinearGlsk linearGlsk = ((BranchFlowPerLinearGlsk) factor).getVariable();
            List<WeightedSensitivityVariable> weightedVariables = linearGlsk.getGLSKs().entrySet().stream()
                    .map(e -> new WeightedSensitivityVariable(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
            handler.onMultipleVariablesFactor(factor, SensitivityFunctionType.BRANCH_ACTIVE_POWER, branchFlow.getBranchId(),
                    SensitivityVariableType.INJECTION_ACTIVE_POWER, linearGlsk.getId(), weightedVariables);
        } else if (factor instanceof BusVoltagePerTargetV) {
            BusVoltage busVoltage = ((BusVoltagePerTargetV) factor).getFunction();
            TargetV targetV = ((BusVoltagePerTargetV) factor).getVariable();
            String busVoltageId = busVoltage.getBusRef().resolve(network).orElseThrow(() -> new PowsyblException("Bus for BusVoltage '" + busVoltage.getId() + "' not found")).getId();
            handler.onSimpleFactor(factor, SensitivityFunctionType.BUS_VOLTAGE, busVoltageId,
                SensitivityVariableType.BUS_TARGET_VOLTAGE, targetV.getEquipmentId());
        } else {
            throw new UnsupportedOperationException("Only factors of type BranchFlow are supported");
        }
    }
}
