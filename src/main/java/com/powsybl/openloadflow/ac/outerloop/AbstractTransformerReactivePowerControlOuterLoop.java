/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
abstract class AbstractTransformerReactivePowerControlOuterLoop implements AcOuterLoop {

    private static final String TYPE = "TransformerReactivePowerControl";

    @Override
    public String getType() {
        return TYPE;
    }

}
