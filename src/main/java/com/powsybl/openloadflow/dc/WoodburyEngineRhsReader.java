package com.powsybl.openloadflow.dc;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

public interface WoodburyEngineRhsReader {

    interface Handler {
        void onRhs(PropagatedContingency contingency, DenseMatrix rhsOverride);
    }

    void process(Handler handler);
}
