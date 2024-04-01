/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public interface LfHvdc extends LfElement {

    LfBus getBus1();

    LfBus getBus2();

    LfBus getOtherBus(LfBus bus);

    void setP1(Evaluable p1);

    Evaluable getP1();

    void setP2(Evaluable p2);

    Evaluable getP2();

    double getDroop();

    double getP0();

    boolean isAcEmulation();

    void setAcEmulation(boolean acEmulation);

    LfVscConverterStation getConverterStation1();

    LfVscConverterStation getConverterStation2();

    void setConverterStation1(LfVscConverterStation converterStation1);

    void setConverterStation2(LfVscConverterStation converterStation2);

    void updateState();

    double getPMaxFromCS1toCS2();

    double getPMaxFromCS2toCS1();
}
