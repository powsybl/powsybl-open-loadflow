package com.powsybl.openloadflow.dc;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.ParticipatingElement;

import java.util.List;
import java.util.Set;

public abstract class AbstractWoodburyEngineInputReader implements WoodburyEngineInputReader {

    public boolean hasRhsChangedDueToDisabledSlackBus(LoadFlowParameters lfParameters, Set<LfBus> disabledBuses, List<ParticipatingElement> participatingElements) {
        return lfParameters.isDistributedSlack() && participatingElements.stream().anyMatch(element -> disabledBuses.contains(element.getLfBus()));
    }

    protected void processHvdcLinesWithDisconnection(DcLoadFlowContext loadFlowContext, Set<LfBus> disabledBuses, ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult) {
        for (LfHvdc hvdc : loadFlowContext.getNetwork().getHvdcs()) {
            if (Networks.isIsolatedBusForHvdc(hvdc.getBus1(), disabledBuses) ^ Networks.isIsolatedBusForHvdc(hvdc.getBus2(), disabledBuses)) {
                connectivityAnalysisResult.getContingencies().forEach(contingency -> {
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation1().getId());
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation2().getId());
                });
            }
        }
    }
}
