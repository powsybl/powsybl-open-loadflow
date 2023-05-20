/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface OverloadManagementSystemAdder<T> {

    OverloadManagementSystemAdder<T> withLineIdToMonitor(String lineId);

    OverloadManagementSystemAdder<T> withThreshold(double threshold);

    OverloadManagementSystemAdder<T> withSwitchIdToOperate(String switchId);

    OverloadManagementSystemAdder<T> withSwitchOpen(boolean open);

    T add();
}
