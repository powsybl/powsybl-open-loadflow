/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter from new API to old legacy one.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SensitivityFactorsProviderAdapter implements SensitivityFactorsProvider {

    private final List<SensitivityFactor> commonFactors = new ArrayList<>();

    private final List<SensitivityFactor> additionalFactors = new ArrayList<>();

    private final Map<String, List<SensitivityFactor>> additionalFactorsByContingencyId = new HashMap<>();

    private final Map<SensitivityFactor, SensitivityFactor2> factorMapping = new HashMap<>();

    public SensitivityFactorsProviderAdapter(List<SensitivityFactor2> factors, List<SensitivityVariableSet> variableSets) {
        Objects.requireNonNull(factors);
        Objects.requireNonNull(variableSets);
        Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream().collect(Collectors.toMap(SensitivityVariableSet::getId, e -> e));
        for (SensitivityFactor2 factor : factors) {
            switch (factor.getFunctionType()) {
                case BRANCH_ACTIVE_POWER:
                    switch (factor.getVariableType()) {
                        case INJECTION_ACTIVE_POWER:
                            if (factor.isVariableSet()) {
                                SensitivityVariableSet variableSet = variableSetsById.get(factor.getVariableId());
                                if (variableSet == null) {
                                    throw new PowsyblException("Variable set '" + factor.getVariableId() + "' not found");
                                }
                                Map<String, Float> glskMap = variableSet.getVariables().stream().collect(Collectors.toMap(WeightedSensitivityVariable::getId, e -> (float) e.getWeight()));
                                BranchFlowPerLinearGlsk oldFactor = new BranchFlowPerLinearGlsk(new BranchFlow(factor.getFunctionId(), factor.getFunctionId(), factor.getFunctionId()),
                                        new LinearGlsk(variableSet.getId(), variableSet.getId(), glskMap));
                                addFactor(factor, oldFactor);
                            } else {
                                throw new UnsupportedOperationException();
                            }
                            break;
                        case TRANSFORMER_PHASE:
                            BranchFlowPerPSTAngle oldFactor = new BranchFlowPerPSTAngle(new BranchFlow(factor.getFunctionId(), factor.getFunctionId(), factor.getFunctionId()),
                                    new PhaseTapChangerAngle(factor.getVariableId(), factor.getVariableId(), factor.getVariableId()));
                            addFactor(factor, oldFactor);
                            break;
                        default:
                            throw new UnsupportedOperationException();
                    }
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }

    private void addFactor(SensitivityFactor2 factor, SensitivityFactor oldFactor) {
        ContingencyContext contingencyContext = factor.getContingencyContext();
        if (contingencyContext.getContextType() == ContingencyContextType.ALL) {
            commonFactors.add(oldFactor);
            factorMapping.put(oldFactor, factor);
        } else if (contingencyContext.getContextType() == ContingencyContextType.SPECIFIC) {
            additionalFactorsByContingencyId.computeIfAbsent(contingencyContext.getContingencyId(), s -> new ArrayList<>())
                .add(oldFactor);
            factorMapping.put(oldFactor, factor);
        } else {
            additionalFactors.add(oldFactor);
            factorMapping.put(oldFactor, factor);
        }
    }

    @Override
    public List<SensitivityFactor> getCommonFactors(Network network) {
        return commonFactors;
    }

    @Override
    public List<SensitivityFactor> getAdditionalFactors(Network network) {
        return additionalFactors;
    }

    @Override
    public List<SensitivityFactor> getAdditionalFactors(Network network, String contingencyId) {
        return additionalFactorsByContingencyId.getOrDefault(contingencyId, Collections.emptyList());
    }

    public List<SensitivityValue2> getValues(SensitivityAnalysisResult result) {
        List<SensitivityValue2> values = new ArrayList<>(factorMapping.size());
        for (SensitivityValue oldValue : result.getSensitivityValues()) {
            SensitivityFactor2 newFactor = Objects.requireNonNull(factorMapping.get(oldValue.getFactor()));
            values.add(new SensitivityValue2(newFactor, null, oldValue.getValue(), oldValue.getFunctionReference()));
        }
        for (Map.Entry<String, List<SensitivityValue>> e : result.getSensitivityValuesContingencies().entrySet()) {
            String contingencyId = e.getKey();
            List<SensitivityValue> oldValues = e.getValue();
            for (SensitivityValue oldValue : oldValues) {
                SensitivityFactor2 newFactor = Objects.requireNonNull(factorMapping.get(oldValue.getFactor()));
                values.add(new SensitivityValue2(newFactor, contingencyId, oldValue.getValue(), oldValue.getFunctionReference()));
            }
        }
        return values;
    }
}
