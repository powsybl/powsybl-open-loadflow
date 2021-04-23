package com.powsybl.openloadflow.reduction.equations;

/**
 * @author JB Heyberger <jean-baptiste.heyberger at rte-france.com>
 */
public class ReductionEquationSystemCreationParameters {
    private final boolean wardMethod;

    private final boolean indexTerms;

    public ReductionEquationSystemCreationParameters(boolean wardMethod, boolean indexTerms) {
        this.wardMethod = wardMethod;
        this.indexTerms = indexTerms;
    }

    public boolean isWardMethod() {
        return wardMethod;
    }

    public boolean isIndexTerms() {
        return indexTerms;
    }

}
