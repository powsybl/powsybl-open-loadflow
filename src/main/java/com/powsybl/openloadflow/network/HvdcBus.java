package com.powsybl.openloadflow.network;

import java.util.Optional;

public interface HvdcBus {

    boolean canTransferActivePower();

    Optional<LfBus> getLfBus();

    boolean isInternal();
}
