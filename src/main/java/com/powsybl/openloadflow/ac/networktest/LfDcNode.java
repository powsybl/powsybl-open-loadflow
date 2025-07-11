package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.*;

public interface LfDcNode extends LfElement {

    Evaluable getVdc();

    Evaluable getPdc();

    void setP(Evaluable p);

    void setV(Evaluable v);

    Evaluable getCalculatedV();

    void setCalculatedV(Evaluable calculatedV);

    List<LfDcLine> getLfDcLines();

    void addLfDcLine(LfDcLine lfdcline);

    void setPdc(double pdc);

    void setVdc(double vdc);

    double getp();

    double getv();

    default boolean isParticipating() {
        return false;
    }

    void addVscConverterStation(LfVscConverterStationV2Impl vsccs, LfBus lfBus);

    List<LfVscConverterStationV2> getVscConverterStations();

    void setTargetV(double vdc);

    void setTargetP(double pdc);

    double getTargetV();

    double getTargetP();

}
