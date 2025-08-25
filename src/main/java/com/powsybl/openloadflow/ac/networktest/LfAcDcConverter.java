package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.network.*;

import java.util.List;


public interface LfAcDcConverter extends LfElement {

    LfBus getBus1();

    LfBus getBus2();

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    double getTargetP();

    double getTargetQ();

    double getTargetVdc();

    double getTargetVac();

    List<Double> getLossFactors();

    AcDcConverter.ControlMode getControlMode();

    void setTargetP(double p);

    void setTargetQ(double q);

    boolean isVoltageRegulatorOn();

    boolean isBipolar();

    void setPac(double pdc);

    double getPac();

    void setIConv(double iConv);

    double getIConv();
}
