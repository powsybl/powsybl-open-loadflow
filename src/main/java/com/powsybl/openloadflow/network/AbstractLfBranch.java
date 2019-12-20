/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Identifiable;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfBranch<T extends Identifiable> implements LfBranch {

    protected final T branch;

    private final LfBus bus1;

    private final LfBus bus2;

    private final PiModel piModel;

    protected AbstractLfBranch(T branch, LfBus bus1, LfBus bus2, PiModel piModel) {
        this.branch = Objects.requireNonNull(branch);
        this.bus1 = bus1;
        this.bus2 = bus2;
        this.piModel = piModel;
    }

    @Override
    public String getId() {
        return branch.getId();
    }

    @Override
    public LfBus getBus1() {
        return bus1;
    }

    @Override
    public LfBus getBus2() {
        return bus2;
    }

    @Override
    public Optional<PiModel> getPiModel() {
        return Optional.ofNullable(piModel);
    }
}
