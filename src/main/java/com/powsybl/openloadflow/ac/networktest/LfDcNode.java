package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;

public interface LfDcNode extends LfElement {


    void addLfDcLine(LfDcLine lfdcline);

    double getP();

    void setP(Evaluable p);

    void setP(double p);

    double getV();

    void setV(Evaluable v);

    void setV(double v);

    default boolean isParticipating() {
        return false;
    }

    List<LfAcDcConverter> getVscConverterStations();

    double getTargetV();

    void setTargetV(double v);

    double getTargetP();

    double getNominalV();

    boolean isGrounded();

    List<LfDcLine> getDcLines();
}
