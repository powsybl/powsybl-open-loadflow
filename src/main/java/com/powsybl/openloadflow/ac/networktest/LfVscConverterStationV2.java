package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;
public interface LfVscConverterStationV2 extends LfGenerator {

    double getLossFactor();

    void addBus(LfBus bus);

    LfBus getBus();

    void addDcNode(LfDcNode lfDcNode);

    LfDcNode getDcNode();


    void setTargetPdc(double p);


    void setTargetVdc(double v);

    double getTargetPdc();

    double getTargetVdc();

    boolean isPControlled();

    void setPdc(Evaluable p);

    Evaluable getPdc();

    void setNum(int num);

    int getNum();
}
