package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;


public interface LfAcDcConverter extends LfElement {

    LfBus getBus1();

    LfBus getBus2();

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    double getTargetP();

    double getTargetQ();

    double getTargetVdcControl();

    double getTargetVac();

    List<Double> getLossFactors();

    ConverterStationMode getConverterMode();

    AcDcConverter.ControlMode getControlMode();

    void setTargetP(double p);

    void setTargetQ(double q);

    void setPac(Evaluable p);

    boolean isVoltageRegulatorOn();
}
