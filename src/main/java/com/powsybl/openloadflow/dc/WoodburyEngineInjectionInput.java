package com.powsybl.openloadflow.dc;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ParticipatingElement;

import java.util.HashMap;
import java.util.List;

public class WoodburyEngineInjectionInput {

    private final DenseMatrix injectionVectors;

    private final HashMap<PropagatedContingency, DenseMatrix> newInjectionVectorsByPropagatedContingency;

    private final HashMap<PropagatedContingency, List<ParticipatingElement>> newParticipatingElementsByPropagatedContingency;

    public WoodburyEngineInjectionInput(DenseMatrix injectionVectors, HashMap<PropagatedContingency, DenseMatrix> newInjectionVectorsByPropagatedContingency, HashMap<PropagatedContingency, List<ParticipatingElement>> newParticipatingElementsByPropagatedContingency) {
        this.injectionVectors = injectionVectors;
        this.newInjectionVectorsByPropagatedContingency = newInjectionVectorsByPropagatedContingency;
        this.newParticipatingElementsByPropagatedContingency = newParticipatingElementsByPropagatedContingency;
    }

    public DenseMatrix getInjectionVectors() {
        return injectionVectors;
    }

    public HashMap<PropagatedContingency, DenseMatrix> getNewInjectionVectorsByPropagatedContingency() {
        return newInjectionVectorsByPropagatedContingency;
    }

    public HashMap<PropagatedContingency, List<ParticipatingElement>> getNewParticipatingElementsByPropagatedContingency() {
        return newParticipatingElementsByPropagatedContingency;
    }
}
