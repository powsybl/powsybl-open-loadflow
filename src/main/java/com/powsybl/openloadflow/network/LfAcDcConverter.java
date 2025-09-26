package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;

public interface LfAcDcConverter extends LfElement {

    Evaluable getCalculatedPac();

    void setCalculatedPac(Evaluable p);

    Evaluable getCalculatedIconv();

    void setCalculatedIconv(Evaluable iconv);

    Evaluable getCalculatedQac();

    void setCalculatedQac(Evaluable q);

    LfBus getBus1();

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    double getTargetP();

    double getTargetVdc();

    double getTargetVac();

    List<Double> getLossFactors();

    AcDcConverter.ControlMode getControlMode();

    boolean isBipolar();

    double getPac();

    void setPac(double pac);

    double getQac();

    void setQac(double qac);

    void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport);

    void updateFlows(double iConv, double pAc, double qAc);
}
