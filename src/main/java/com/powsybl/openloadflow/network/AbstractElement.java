/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractElement extends AbstractPropertyBag implements LfElement {

    protected final LfNetwork network;

    protected int num = -1;

    protected boolean disabled = false;

    protected AbstractElement(LfNetwork network) {
        this.network = Objects.requireNonNull(network);
    }

    @Override
    public List<String> getOriginalIds() {
        return List.of(getId());
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        if (disabled != this.disabled) {
            this.disabled = disabled;
            notifyDisable();
        }
    }

    protected void notifyDisable() {
        for (LfNetworkListener listener : network.getListeners()) {
            listener.onDisableChange(this, disabled);
        }
    }

    public LfNetwork getNetwork() {
        return network;
    }

    @Override
    public String toString() {
        return getId();
    }
}
