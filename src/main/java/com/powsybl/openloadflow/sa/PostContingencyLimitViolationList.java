/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.sa;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Branch;
import com.powsybl.security.LimitViolation;

import java.util.*;

/**
 * Build from base list (pre contingency LimitViolation) list.
 *
 * Keep in post list with filtering as following :
 * - all permanent LimitViolation if are not present in base list
 * - all temporary LimitViolation if only permanent are present in base list
 * - all LimitViolation with less AcceptableDuration than base
 *
 * @author Thomas Adam <tadam at silicom.fr>
 */
class PostContingencyLimitViolationList extends ArrayList<LimitViolation> {

    private static final String PERMANENT_NAME = "permanent";

    static class InternalId implements Comparable <InternalId> {
        private final String id;
        private final Branch.Side side;

        public InternalId(LimitViolation limitViolation) {
            this.id = limitViolation.getSubjectId();
            this.side = limitViolation.getSide();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass())  {
                return false;
            }
            InternalId that = (InternalId) o;
            return Objects.equals(id, that.id) && side == that.side;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, side);
        }

        @Override
        public int compareTo(InternalId internalId) {
            // compareTo should return < 0 if this is supposed to be
            // less than other, > 0 if this is supposed to be greater than
            // other and 0 if they are supposed to be equal
            int last = this.id.compareTo(internalId.id);
            return last == 0 ? this.side.compareTo(internalId.side) : last;
        }
    }

    private final Map<InternalId, List<LimitViolation>> baseMap = new HashMap<>();

    public PostContingencyLimitViolationList(final List<LimitViolation> list) {
        for (LimitViolation limitViolation : list) {
            addBaseLimit(limitViolation);
        }
    }

    @Override
    public boolean add(LimitViolation postViolation) {
        boolean status = false;

        InternalId id = new InternalId(postViolation);
        List<LimitViolation> baseViolations = baseMap.get(id);
        // Check if postViolation is new
        if (Objects.nonNull(baseViolations)) {
            if (baseViolations.size() > 1) {
                // Base already have Temporary limit(s)
                // Check if post is temporary
                if (isTemporary(postViolation)) {
                    // Get more serious base violation
                    OptionalInt minDuration = baseViolations.stream().mapToInt(LimitViolation::getAcceptableDuration).min();
                    // Check if post is more serious than base
                    if (postViolation.getAcceptableDuration() < minDuration.getAsInt()) {
                        status = super.add(postViolation);
                    } else {
                        // postViolation is not kept -> less serious than base
                    }
                } else {
                    throw new PowsyblException("Cannot get permanent LimitViolation here");
                }
            } else if (baseViolations.size() == 1) {
                // Base has only permanent limit
                // Check if post is temporary
                if (isTemporary(postViolation)) {
                    status = super.add(postViolation);
                } else {
                    // Check if post is more serious than base
                    if (postViolation.getAcceptableDuration() < baseViolations.get(0).getAcceptableDuration()) {
                        status = super.add(postViolation);
                    } else {
                        // postViolation is not kept -> less serious than base
                    }
                }
            } else {
                throw new PowsyblException("Cannot get empty list here");
            }
        } else {
            status = super.add(postViolation);
        }
        return status;
    }

    private void addBaseLimit(LimitViolation limitViolation) {
        InternalId id = new InternalId(limitViolation);
        if (baseMap.containsKey(id)) {
            baseMap.get(id).add(limitViolation);
        } else {
            List<LimitViolation> violations = new ArrayList<>();
            violations.add(limitViolation);
            baseMap.put(id, violations);
        }
    }

    private boolean isTemporary(LimitViolation limitViolation) {
        return !isPermanent(limitViolation);
    }

    private boolean isPermanent(LimitViolation limitViolation) {
        return PERMANENT_NAME.equals(limitViolation.getLimitName());
    }
}
