package com.powsybl.openloadflow.dc;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

public interface RhsReader {

    void process(Handler handler);

    public interface Handler {
        void onRhs(PropagatedContingency contingency, DenseMatrix preContingencyState, DenseMatrix postContingencyState);
    }
}
