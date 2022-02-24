/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.impl.LfVscConverterStationImpl;
import com.powsybl.openloadflow.util.Evaluable;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public interface LfHvdc extends LfElement {

    LfBus getBus1();

    LfBus getBus2();

    void setP1(Evaluable p1);

    Evaluable getP1();

    void setP2(Evaluable p2);

    Evaluable getP2();

    double getDroop();

    double getP0();

    LfVscConverterStationImpl getConverterStation1();

    LfVscConverterStationImpl getConverterStation2();

    void setConverterStation1(LfVscConverterStationImpl converterStation1);

    void setConverterStation2(LfVscConverterStationImpl converterStation2);
}
