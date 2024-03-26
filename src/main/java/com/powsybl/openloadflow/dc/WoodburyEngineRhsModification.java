package com.powsybl.openloadflow.dc;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ParticipatingElement;

import java.util.HashMap;
import java.util.List;

public class WoodburyEngineRhsModification {

    private final HashMap<PropagatedContingency, DenseMatrix> newInjectionVectorsByPropagatedContingency;

    private final HashMap<WoodburyEngine.ConnectivityAnalysisResult, DenseMatrix> newInjectionVectorsForAConnectivity;

    private final HashMap<PropagatedContingency, List<ParticipatingElement>> newParticipatingElementsByPropagatedContingency;

    private final HashMap<WoodburyEngine.ConnectivityAnalysisResult, List<ParticipatingElement>> newParticipantElementsForAConnectivity;

    public WoodburyEngineRhsModification() {
        this.newInjectionVectorsByPropagatedContingency = new HashMap<>();
        this.newInjectionVectorsForAConnectivity = new HashMap<>();
        this.newParticipatingElementsByPropagatedContingency = new HashMap<>();
        this.newParticipantElementsForAConnectivity = new HashMap<>();
    }

    public HashMap<PropagatedContingency, DenseMatrix> getNewInjectionVectorsByPropagatedContingency() {
        return newInjectionVectorsByPropagatedContingency;
    }

    public HashMap<PropagatedContingency, List<ParticipatingElement>> getNewParticipatingElementsByPropagatedContingency() {
        return newParticipatingElementsByPropagatedContingency;
    }

    public HashMap<WoodburyEngine.ConnectivityAnalysisResult, DenseMatrix> getNewInjectionVectorsForAConnectivity() {
        return newInjectionVectorsForAConnectivity;
    }

    public HashMap<WoodburyEngine.ConnectivityAnalysisResult, List<ParticipatingElement>> getNewParticipantElementsForAConnectivity() {
        return newParticipantElementsForAConnectivity;
    }
}
