package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.*;

public interface LfDcNode extends LfElement {

    Evaluable getV();

    Evaluable getP();

    void setP(Evaluable p);

    void setV(Evaluable v);

    Evaluable getCalculatedV();

    void setCalculatedV(Evaluable calculatedV);

    List<LfDcLine> getLfDcLines();

    void addLfDcLine(LfDcLine lfdcline);

    void setP(double P);

    void setV(double V);

    double getp();

    double getv();

    default boolean isParticipating() {
        return false;
    }

    void addVscConverterStation(LfVscConverterStationV2Impl vsccs, LfBus lfBus);

    List<LfVscConverterStationV2> getVscConverterStations();

    void setTargetV(double V);

    void setTargetP(double P);

    double getTargetV();

    double getTargetP();

}
