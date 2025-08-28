package com.powsybl.openloadflow.ac.networktest;

public interface LfVoltageSourceConverter extends LfAcDcConverter {

    boolean isVoltageRegulatorOn();

    double getTargetQ();

    void setTargetQ(double q);
}
