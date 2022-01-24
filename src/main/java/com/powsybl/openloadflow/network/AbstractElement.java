/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractElement implements LfElement {

    protected final LfNetwork network;

    protected int num = -1;

    protected boolean disabled = false;

    protected Object userObject;

    protected AbstractElement(LfNetwork network) {
        this.network = Objects.requireNonNull(network);
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
            for (LfNetworkListener listener : network.getListeners()) {
                listener.onDisableChange(this, disabled);
            }
        }
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public Object getUserObject() {
        return userObject;
    }

    public void setUserObject(Object userObject) {
        this.userObject = userObject;
    }

    @Override
    public String toString() {
        return getId();
    }
}
