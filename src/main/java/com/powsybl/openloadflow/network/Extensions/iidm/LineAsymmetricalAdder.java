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
        return new LineAsymmetrical(line, rA, xA, isOpenA, rB, xB, isOpenB, rC, xC, isOpenC);
    }

    public LineAsymmetricalAdder withRa(double rA) {
        this.rA = rA;
        return this;
    }

    public LineAsymmetricalAdder withXa(double xA) {
        this.xA = xA;
        return this;
    }

    public LineAsymmetricalAdder withIsOpenA(boolean isOpenA) {
        this.isOpenA = isOpenA;
        return this;
    }

    public LineAsymmetricalAdder withRb(double rB) {
        this.rB = rB;
        return this;
    }

    public LineAsymmetricalAdder withXb(double xB) {
        this.xB = xB;
        return this;
    }

    public LineAsymmetricalAdder withIsOpenB(boolean isOpenB) {
        this.isOpenB = isOpenB;
        return this;
    }

    public LineAsymmetricalAdder withRc(double rC) {
        this.rC = rC;
        return this;
    }

    public LineAsymmetricalAdder withXc(double xC) {
        this.xC = xC;
        return this;
    }

    public LineAsymmetricalAdder withIsOpenC(boolean isOpenC) {
        this.isOpenC = isOpenC;
        return this;
    }

}
