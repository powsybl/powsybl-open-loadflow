package com.powsybl.openloadflow.ac.newfiles;

import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import java.util.List;

public interface LfAcDcConverter extends LfElement {

    LfBus getBus1();

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    double getTargetP();

    void setTargetP(double p);

    double getTargetVdc();

    double getTargetVac();

    List<Double> getLossFactors();

    AcDcConverter.ControlMode getControlMode();

    boolean isBipolar();

    double getPac();

    void setPac(double pac);

    double getQac();

    void setQac(double qac);

    double getIConv();

    void setIConv(double iConv);


}
