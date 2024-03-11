package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.util.Derivable;

import java.util.Objects;
import java.util.stream.Stream;

public class InjectionDerivable<V extends Enum<V> & Quantity> implements Derivable<V> {

    private final Equation<V, ?> equation;

    public InjectionDerivable(Equation<V, ?> equation) {
        Objects.requireNonNull(equation);
        this.equation = equation;
    }

    private Stream<? extends EquationTerm<V, ?>> getBranchTermStream() {
        return equation.getTerms().stream().filter(EquationTerm::isActive)
                .filter(t -> t.getElementType() == ElementType.BRANCH || t.getElementType() == ElementType.HVDC);
    }

    @Override
    public double der(Variable<V> variable) {
        return -getBranchTermStream().mapToDouble(t -> t.der(variable)).sum();
    }

    @Override
    public double calculateSensi(DenseMatrix x, int column) {
        return -getBranchTermStream().mapToDouble(t -> t.calculateSensi(x, column)).sum();
    }

    @Override
    public double eval() {
        return -getBranchTermStream().mapToDouble(EquationTerm::eval).sum();
    }

    @Override
    public boolean isActive() {
        return getBranchTermStream().anyMatch(EquationTerm::isActive);
    }

    @Override
    public double eval(StateVector sv) {
        return -getBranchTermStream().mapToDouble(t -> t.eval(sv)).sum();
    }
}
