package com.powsybl.openloadflow.sa.extensions;

import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.openloadflow.network.PiModel;

public class PhaseTapChangerResult {

    private PhaseTapChanger phaseTapChanger;

    private int currentTap;

    private String transformerId;

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

    public void setPhaseTapChanger(PhaseTapChanger phaseTapChanger) {
        this.phaseTapChanger = phaseTapChanger;
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

    public void setTransformerId(String transformerId) {
        this.transformerId = transformerId;
    }
}
