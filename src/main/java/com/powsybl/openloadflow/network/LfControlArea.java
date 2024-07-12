package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.Set;

public interface LfControlArea extends PropertyBag {
    String getId();

    double getTargetAcInterchange();

    double getAcInterchange();

    Set<LfBus> getBuses();

    LfControlArea addBus(LfBus bus);

    LfNetwork getNetwork();

    LfControlArea addBoundaryP(Evaluable p);

}
