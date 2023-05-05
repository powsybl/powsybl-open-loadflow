package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Line;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LineAsymmetrical extends AbstractExtension<Line> {
    //
    // We suppose that an asymmetrical line is modelled by:
    // - its A,B,C connection status (phase connected / disconnected)
    // - the values of its physical attributes Rz,Xz,Rn,Xn (Rp and Xp are the values from the balanced Pi-model)
    //
    // From those values we define the Fortescue admittance matrix that will be used in the load-flow equations

    public static final String NAME = "lineAsymmetrical";

    private Boolean isOpenPhaseA;
    private Boolean isOpenPhaseB;
    private Boolean isOpenPhaseC;

    @Override
    public String getName() {
        return NAME;
    }

    public LineAsymmetrical(Line line,
                            boolean isPhaseOpenA,
                            boolean isPhaseOpenB,
                            boolean isPhaseOpenC) {
        super(line);
        this.isOpenPhaseA = isPhaseOpenA;
        this.isOpenPhaseB = isPhaseOpenB;
        this.isOpenPhaseC = isPhaseOpenC;

    }

    public void setOpenPhaseA(boolean isOpen) {
        this.isOpenPhaseA = isOpen;
    }

    public void setOpenPhaseB(boolean isOpen) {
        this.isOpenPhaseB = isOpen;
    }

    public void setOpenPhaseC(boolean isOpen) {
        this.isOpenPhaseC = isOpen;
    }

    public Boolean getOpenPhaseA() {
        return isOpenPhaseA;
    }

    public Boolean getOpenPhaseB() {
        return isOpenPhaseB;
    }

    public Boolean getOpenPhaseC() {
        return isOpenPhaseC;
    }
}
