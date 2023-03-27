package com.powsybl.openloadflow.network.Extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Line;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LineAsymmetrical extends AbstractExtension<Line> {
    //
    // We suppose that an asymmetrical line is modelled by:
    // - its A,B,C connexion status (phase connected / disconnected)
    // - the values of its physical attributes. We use the following values in order of priority if defined:
    //    1- Y012 admittance matrix
    //    2- R0,X0,R2,X2 (R1 and X1 are the values from the balanced Pi-model)
    //    3- YABC admittance matrix
    //    4- RA,XA,RB,XB,RC,XC
    //
    // From those values we define the fortescue admittance matrix that will be used the the load-flow equations

    public static final String NAME = "lineAsymmetrical";

    private Boolean isOpenPhaseA;
    private Boolean isOpenPhaseB;
    private Boolean isOpenPhaseC;

    // Attributes are defined in order of priority for processing
    private final LineAsymmetricalAdmittanceMatrix yFortescue;
    private final LineAsymmetricalPiValues piValuesFortescue;
    private final LineAsymmetricalAdmittanceMatrix yAbc;
    private final LineAsymmetricalPiValues piValuesAbc;

    @Override
    public String getName() {
        return NAME;
    }

    public LineAsymmetrical(Line line,
                            boolean isPhaseOpenA,
                            boolean isPhaseOpenB,
                            boolean isPhaseOpenC,
                            LineAsymmetricalAdmittanceMatrix yFortescue,
                            LineAsymmetricalPiValues piValuesFortescue,
                            LineAsymmetricalAdmittanceMatrix yAbc,
                            LineAsymmetricalPiValues piValuesAbc) {
        super(line);
        this.yFortescue = yFortescue;
        this.piValuesFortescue = piValuesFortescue;
        this.yAbc = yAbc;
        this.piValuesAbc = piValuesAbc;
        this.isOpenPhaseA = isPhaseOpenA;
        this.isOpenPhaseB = isPhaseOpenB;
        this.isOpenPhaseC = isPhaseOpenC;

    }

    public void setOpenPhaseA(boolean isOpen) {
        this.isOpenPhaseA = isOpen;
    }

    public LineAsymmetricalAdmittanceMatrix getyAbc() {
        return yAbc;
    }

    public LineAsymmetricalAdmittanceMatrix getyFortescue() {
        return yFortescue;
    }

    public LineAsymmetricalPiValues getPiValuesAbc() {
        return piValuesAbc;
    }

    public LineAsymmetricalPiValues getPiValuesFortescue() {
        return piValuesFortescue;
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
