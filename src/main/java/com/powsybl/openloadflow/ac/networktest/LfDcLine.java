package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.PiModel;

public interface LfDcLine extends LfElement {

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    double getR();

    PiModel getPiModel();
}
