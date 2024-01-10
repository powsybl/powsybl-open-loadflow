/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic load model as a sum of exponential terms: sum_i(ci * v^ni)
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfLoadModel {

    public record ExpTerm(double c, double n) {
    }

    private final List<ExpTerm> expTermsP;

    private final List<ExpTerm> expTermsQ;

    public LfLoadModel(List<ExpTerm> expTermsP, List<ExpTerm> expTermsQ) {
        this.expTermsP = Objects.requireNonNull(expTermsP);
        this.expTermsQ = Objects.requireNonNull(expTermsQ);
    }

    public List<ExpTerm> getExpTermsP() {
        return expTermsP;
    }

    public Optional<ExpTerm> getExpTermP(double n) {
        return expTermsP.stream().filter(expTerm -> expTerm.n == n).findFirst();
    }

    public List<ExpTerm> getExpTermsQ() {
        return expTermsQ;
    }

    public Optional<ExpTerm> getExpTermQ(double n) {
        return expTermsQ.stream().filter(expTerm -> expTerm.n == n).findFirst();
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
