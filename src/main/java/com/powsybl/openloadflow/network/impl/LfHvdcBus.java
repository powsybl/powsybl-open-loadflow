package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.HvdcBus;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfVscConverterStation;

import java.util.Objects;
import java.util.Optional;

public class LfHvdcBus implements HvdcBus {

    LfBus bus;
    LfVscConverterStation vscConverterStation;

    public LfHvdcBus(LfBus bus, LfVscConverterStation vscConverterStation) {
        this.bus = Objects.requireNonNull(bus);
        this.vscConverterStation = vscConverterStation;
    }

    @Override
    public boolean canTransferActivePower() {
        // TODO Update criteria when LCC is added
        if (bus.getGenerators().stream()
                .filter(g -> !g.isDisabled())
                .anyMatch(g -> !(g == vscConverterStation))) {
            return true;
        }
        if (bus.getBranches().stream()
                .anyMatch(b -> !b.isDisabled())
        ) {
            return true;
        }
        if (!bus.getLoads().isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    public Optional<LfBus> getLfBus() {
        return Optional.of(bus);
    }

    @Override
    public boolean isInternal() {
        return true;
    }
}
