package com.powsybl.openloadflow.ac.networktest;

//import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.Evaluable;

public interface LfDcLine extends LfElement {

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    void setP1(Evaluable p1);

    Evaluable getP1();

    void setP2(Evaluable p2);

    Evaluable getP2();

    double getR();

    Evaluable getClosedI1();

    void setClosedI1(Evaluable closedI1);

    Evaluable getClosedI2();

    void setClosedI2(Evaluable closedI2);

    void setClosedP1(Evaluable closedP1);

    void setClosedP2(Evaluable closedP2);

    Evaluable getClosedP1();

    Evaluable getClosedP2();

    void setClosedV1(Evaluable closedV1);

    void setClosedV2(Evaluable closedV2);

    Evaluable getClosedV1();

    Evaluable getClosedV2();

    void setI1(Evaluable i1);

    void setI2(Evaluable i2);

    Evaluable getI1();

    Evaluable getI2();

    void setV1(Evaluable v1);

    void setV2(Evaluable v2);

    Evaluable getV1();

    Evaluable getV2();

    PiModel getPiModel();
}
