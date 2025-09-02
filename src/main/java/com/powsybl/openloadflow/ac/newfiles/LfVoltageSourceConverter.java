package com.powsybl.openloadflow.ac.newfiles;

public interface LfVoltageSourceConverter extends LfAcDcConverter {

    boolean isVoltageRegulatorOn();

    double getTargetQ();

    void setTargetQ(double q);
}
