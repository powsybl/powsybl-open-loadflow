/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl.extensions;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface OverloadManagementSystem {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    String getMonitoredLineId();

    double getThreshold();

    String getSwitchIdToOperate();

    boolean isSwitchOpen();
}
