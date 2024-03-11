package com.powsybl.openloadflow.util;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.Variable;

public interface Derivable <V extends Enum<V> & Quantity> extends Evaluable {

    double der(Variable<V> variable);

    double calculateSensi(DenseMatrix x, int column);

    boolean isActive();

    double eval(StateVector sv);
}
