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
 * Generic load model: sum_n(cn * v^n)
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfLoadModel {

    public static class Term {

        private final double c;

        private final double n;

        public Term(double c, double n) {
            this.c = c;
            this.n = n;
        }

        public double getC() {
            return c;
        }

        public double getN() {
            return n;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Term term = (Term) o;
            return Double.compare(term.c, c) == 0 && Double.compare(term.n, n) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(c, n);
        }
    }

    private final Map<Double, Term> pTermsByExponent = new TreeMap<>();

    private final Map<Double, Term> qTermsByExponent = new TreeMap<>();

    public LfLoadModel(List<Term> pTerms, List<Term> qTerms) {
        Objects.requireNonNull(pTerms);
        Objects.requireNonNull(qTerms);
        for (Term term : pTerms) {
            addTerm(pTermsByExponent, term);
        }
        for (Term term : qTerms) {
            addTerm(qTermsByExponent, term);
        }
    }

    private static void addTerm(Map<Double, Term> termsByExponent, Term term) {
        Objects.requireNonNull(term);
        if (termsByExponent.containsKey(term.getC())) {
            throw new PowsyblException("A term with exponent " + term.getC() + " already exists");
        }
        termsByExponent.put(term.getC(), term);
    }

    public Collection<Term> getTermsP() {
        return pTermsByExponent.values();
    }

    public Optional<Term> getTermP(double n) {
        return Optional.ofNullable(pTermsByExponent.get(n));
    }

    public Collection<Term> getTermsQ() {
        return pTermsByExponent.values();
    }

    public Optional<Term> getTermQ(double n) {
        return Optional.ofNullable(qTermsByExponent.get(n));
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
        return Objects.equals(pTermsByExponent, that.pTermsByExponent)
                && Objects.equals(qTermsByExponent, that.qTermsByExponent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pTermsByExponent, qTermsByExponent);
    }
}
