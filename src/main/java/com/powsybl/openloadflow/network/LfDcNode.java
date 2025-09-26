package com.powsybl.openloadflow.network;

public interface LfDcNode extends LfElement {

    void addLfDcLine(LfDcLine lfdcline);

    double getV();

    void setV(double v);

    void addConverter(LfAcDcConverter converter);

    boolean isNeutralPole();

    void setNeutralPole(boolean isNeutralPole);

    double getNominalV();

    boolean isGrounded();

    void setGround(boolean isGrounded);

    void updateState(LfNetworkStateUpdateParameters parameters);
}
