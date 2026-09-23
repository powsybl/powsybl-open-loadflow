package com.powsybl.openloadflow.sa.extensions;

import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.openloadflow.network.PiModel;

import java.util.Optional;

public class PhaseTapChangerInfo {

    private final PhaseTapChanger phaseTapChanger;

    private int currentTap;

    private final String transformerId;

    private final ThreeSides side;

    private PiModel piModel;

    public PhaseTapChangerInfo(PhaseTapChanger phaseTapChanger, String transformerId, ThreeSides side, PiModel piModel, int currentTap) {
        this.phaseTapChanger = phaseTapChanger;
        this.side = side;
        this.currentTap = currentTap;
        this.transformerId = transformerId;
        this.piModel = piModel;
    }

    public int getCurrentTap() {
        return currentTap;
    }

    public void setCurrentTap(int currentTap) {
        this.currentTap = currentTap;
    }

    public PhaseTapChanger getPhaseTapChanger() {
        return phaseTapChanger;
    }

    public PiModel getPiModel() {
        return piModel;
    }

    public void setPiModel(PiModel piModel) {
        this.piModel = piModel;
    }

    public String getTransformerId() {
        return transformerId;
    }

    public Optional<ThreeSides> getSide() {
        return Optional.ofNullable(side);
    }
}
