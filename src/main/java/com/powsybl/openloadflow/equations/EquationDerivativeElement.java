package com.powsybl.openloadflow.equations;

import java.util.Objects;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class EquationDerivativeElement<V extends Enum<V> & Quantity> {
    final int termArrayNum;
    final int termNum;
    final Derivative<V> derivative;

    EquationDerivativeElement(int termArrayNum, int termNum, Derivative<V> derivative) {
        this.termArrayNum = termArrayNum;
        this.termNum = termNum;
        this.derivative = Objects.requireNonNull(derivative);
    }
}
