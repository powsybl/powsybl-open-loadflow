package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.Evaluable;

public interface LfDcLine extends LfElement {

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    double getR();

    PiModel getPiModel();
}
