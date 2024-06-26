package com.powsybl.openloadflow.dc;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

import java.util.Collection;

public interface WoodburyEngineRhsReader {

    interface Handler {
        void onContingency(PropagatedContingency contingency, Collection<ComputedContingencyElement> contingencyElements, DenseMatrix preContingencyStates);
    }

    void process(Handler handler);
}
