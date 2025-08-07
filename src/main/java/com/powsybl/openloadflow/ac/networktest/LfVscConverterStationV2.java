package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;


public interface LfVscConverterStationV2 extends LfGenerator {

    void addBus(LfBus bus);

    LfBus getaBus();

    void addDcNode(LfDcNode lfDcNode);

    LfDcNode getDcNode();

    void setTargetVdcControl(double v);

    double getTargetVdcControl();

    void setTargetPacControl(double p);

    void setTargetVac(double V);

    double getTargetVac();

    boolean isPControlled();

    boolean isControllingVAc();

    void setPac(Evaluable p);

    void setNum(int num);

    int getNum();

    List<Double> getLossFactors();

    boolean isDcNodeConnectedSide1();
}
