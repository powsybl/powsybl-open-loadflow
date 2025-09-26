package com.powsybl.openloadflow.network;

public interface LfVoltageSourceConverter extends LfAcDcConverter {

    boolean isVoltageRegulatorOn();

    double getTargetQ();
}
