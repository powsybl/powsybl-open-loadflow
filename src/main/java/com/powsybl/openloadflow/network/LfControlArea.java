package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.Set;
import java.util.function.Supplier;

public interface LfControlArea extends PropertyBag {
    String getId();

    double getInterchangeTarget();

    double getInterchange();

    Set<LfBus> getBuses();

    LfControlArea addBus(LfBus bus);

    LfControlArea addBoundaryP(Supplier<Evaluable> p);

    LfNetwork getNetwork();

}
