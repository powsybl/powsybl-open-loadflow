package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.util.Evaluable;
import java.util.List;

public interface LfDcNode extends LfElement {


    void setP(Evaluable p);

    void setV(Evaluable v);

    void addLfDcLine(LfDcLine lfdcline);

    void setPdc(double pdc);

    void setVdc(double vdc);

    default boolean isParticipating() {
        return false;
    }

    void addVscConverterStation(LfAcDcVscConverterStationImpl vsccs, LfBus lfBus);

    List<LfAcDcVscConverterStation> getVscConverterStations();

    double getTargetV();

    void setTargetV(double vdc);

    double getTargetP();

    void setTargetP(double pdc);

    double getNominalV();

}
