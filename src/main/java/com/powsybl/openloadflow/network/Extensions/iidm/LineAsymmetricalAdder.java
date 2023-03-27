package com.powsybl.openloadflow.network.Extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Line;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LineAsymmetricalAdder extends AbstractExtensionAdder<Line, LineAsymmetrical> {

    private double rA = 0.;
    private double xA = 1.;
    private double rB = 0.;
    private double xB = 1.;
    private double rC = 0.;
    private double xC = 1.; // TODO : replace this default value by direct value
    private LineAsymmetricalAdmittanceMatrix yFortescue = null;
    private LineAsymmetricalAdmittanceMatrix yAbc = null;
    private LineAsymmetricalPiValues piValuesFortescue = null;
    private LineAsymmetricalPiValues piValuesAbc = null;

    private boolean isOpenA = false;
    private boolean isOpenB = false;
    private boolean isOpenC = false;

    public LineAsymmetricalAdder(Line line) {
        super(line);
    }

    @Override
    public Class<? super LineAsymmetrical> getExtensionClass() {
        return LineAsymmetrical.class;
    }

    @Override
    protected LineAsymmetrical createExtension(Line line) {
        return new LineAsymmetrical(line, isOpenA, isOpenB, isOpenC, yFortescue, piValuesFortescue, yAbc, piValuesAbc);
    }

    public LineAsymmetricalAdder withIsOpenA(boolean isOpenA) {
        this.isOpenA = isOpenA;
        return this;
    }

    public LineAsymmetricalAdder withIsOpenB(boolean isOpenB) {
        this.isOpenB = isOpenB;
        return this;
    }

    public LineAsymmetricalAdder withIsOpenC(boolean isOpenC) {
        this.isOpenC = isOpenC;
        return this;
    }

    public LineAsymmetricalAdder withYfortescue(LineAsymmetricalAdmittanceMatrix yFortescue) {
        this.yFortescue = yFortescue;
        return this;
    }

    public LineAsymmetricalAdder withPiValuesFortescue(LineAsymmetricalPiValues piValuesFortescue) {
        this.piValuesFortescue = piValuesFortescue;
        return this;
    }

    public LineAsymmetricalAdder withYabc(LineAsymmetricalAdmittanceMatrix yAbc) {
        this.yAbc = yAbc;
        return this;
    }

    public LineAsymmetricalAdder withPiValuesAbc(LineAsymmetricalPiValues piValuesAbc) {
        this.piValuesAbc = piValuesAbc;
        return this;
    }

}
