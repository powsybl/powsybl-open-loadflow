package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.HvdcBus;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Optional;

public class ExternalHvdcBus implements HvdcBus {

    private final boolean canTransferActivePower;

    public ExternalHvdcBus(boolean canTransferActivePower) {
        this.canTransferActivePower = canTransferActivePower;
    }

    @Override
    public boolean canTransferActivePower() {
        return canTransferActivePower;
    }

    @Override
    public Optional<LfBus> getLfBus() {
        return Optional.empty();
    }

    @Override
    public boolean isInternal() {
        return false;
    }
}
