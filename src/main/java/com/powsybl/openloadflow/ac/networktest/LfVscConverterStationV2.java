package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;

import com.powsybl.openloadflow.util.Evaluable;


public interface LfVscConverterStationV2 extends LfGenerator {

    void addBus(LfBus bus);

    LfBus getaBus();

    void addDcNode(LfDcNode lfDcNode);

    LfDcNode getDcNode();

    void setTargetPdc(double p);

    void setTargetVdc(double v);

    double getTargetPdc();

    double getTargetVdc();

    void setTargetVac(double V);

    double getTargetVac();
    boolean isPControlled();

    boolean isControllingVAc();

    void setPdc(Evaluable p);

    void setNum(int num);

    int getNum();
}
