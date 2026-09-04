package com.powsybl.openloadflow.sa.extensions;

import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.openloadflow.network.PiModel;

public class PhaseTapChangerResult {

    private final PhaseTapChanger phaseTapChanger;

    private int currentTap;

    private final String transformerId;

    private PiModel piModel;

    public PhaseTapChangerResult(PhaseTapChanger phaseTapChanger, String transformerId, PiModel piModel, int currentTap) {
        this.phaseTapChanger = phaseTapChanger;
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
}
