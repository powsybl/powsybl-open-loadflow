package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.Evaluable;

public interface LfDcLine extends LfElement {

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    Evaluable getP1();

    void setP1(Evaluable p1);

    Evaluable getP2();

    void setP2(Evaluable p2);

    double getR();

    void setClosedP1(Evaluable closedP1);

    void setClosedP2(Evaluable closedP2);

    Evaluable getI1();

    void setI1(Evaluable i1);

    Evaluable getI2();

    void setI2(Evaluable i2);

    Evaluable getV1();

    void setV1(Evaluable v1);

    Evaluable getV2();

    void setV2(Evaluable v2);

    PiModel getPiModel();
}
