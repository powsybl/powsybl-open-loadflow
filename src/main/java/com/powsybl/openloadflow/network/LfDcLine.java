package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

public interface LfDcLine extends LfElement {

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    double getR();

    Evaluable getI1();

    void setI1(Evaluable i1);

    Evaluable getI2();

    void setI2(Evaluable i2);

    Evaluable getP1();

    void setP1(Evaluable p1);

    Evaluable getP2();

    void setP2(Evaluable p2);

    void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport);

    void updateFlows(double i1, double i2, double p1, double p2);
}
