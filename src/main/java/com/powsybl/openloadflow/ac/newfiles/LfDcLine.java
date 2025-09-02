package com.powsybl.openloadflow.ac.newfiles;

import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;
import com.powsybl.openloadflow.network.LfNetworkUpdateReport;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.Evaluable;

public interface LfDcLine extends LfElement {

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    double getR();

    PiModel getPiModel();

    void setI1(Evaluable i1);

    Evaluable getI1();

    void setI2(Evaluable i2);

    Evaluable getI2();

    void setP1(Evaluable p1);

    Evaluable getP1();

    void setP2(Evaluable p2);

    Evaluable getP2();

    void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport);

    void updateFlows(double i1, double i2, double p1, double p2);

}
