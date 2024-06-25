package com.powsybl.openloadflow.dc;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

import java.util.Set;

public interface WoodburyEngineRhsReader {

    interface Handler {
        void onRhs(PropagatedContingency contingency, DenseMatrix rhsOverride, Set<String> elementToReconnect);
    }

    void process(Handler handler);
}
