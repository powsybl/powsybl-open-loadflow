package com.powsybl.openloadflow.ac.newfiles;

import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;
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

    List<LfAcDcConverter> getConverters();

    void addConverter(LfAcDcConverter converter);

    void setNeutralPole(boolean isNeutralPole);

    boolean isNeutralPole();

    double getTargetV();

    void setTargetV(double v);

    double getTargetP();

    double getNominalV();

    boolean isGrounded();

    void setGround(boolean isGrounded);

    List<LfDcLine> getDcLines();

    void updateState(LfNetworkStateUpdateParameters parameters);
}
