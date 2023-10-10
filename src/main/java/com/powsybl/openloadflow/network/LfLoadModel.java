/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;

import java.util.*;

/**
 * Generic load model as a sum of exponential terms: sum_i(ci * v^ni)
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfLoadModel {

    public record ExpTerm(double c, double n) {
    }

    private final Map<Double, ExpTerm> expTermsP = new TreeMap<>(); // by exponent

    private final Map<Double, ExpTerm> expTermsQ = new TreeMap<>(); // by exponent

    public LfLoadModel(List<ExpTerm> expTermsP, List<ExpTerm> expTermsQ) {
        Objects.requireNonNull(expTermsP);
        Objects.requireNonNull(expTermsQ);
        for (ExpTerm expTerm : expTermsP) {
            addExpTerm(this.expTermsP, expTerm);
        }
        for (ExpTerm expTerm : expTermsQ) {
            addExpTerm(this.expTermsQ, expTerm);
        }
    }

    private static void addExpTerm(Map<Double, ExpTerm> expTerms, ExpTerm term) {
        Objects.requireNonNull(term);
        if (expTerms.containsKey(term.n())) {
            throw new PowsyblException("A term with exponent " + term.n() + " already exists");
        }
        expTerms.put(term.n(), term);
    }

    public Collection<ExpTerm> getExpTermsP() {
        return expTermsP.values();
    }

    public Optional<ExpTerm> getExpTermP(double n) {
        return Optional.ofNullable(expTermsP.get(n));
    }

    public Collection<ExpTerm> getExpTermsQ() {
        return expTermsP.values();
    }

    public Optional<ExpTerm> getExpTermQ(double n) {
        return Optional.ofNullable(expTermsQ.get(n));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LfLoadModel that = (LfLoadModel) o;
        return Objects.equals(expTermsP, that.expTermsP)
                && Objects.equals(expTermsQ, that.expTermsQ);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expTermsP, expTermsQ);
    }
}
