/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.openloadflow.util.ComplexMatrix;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class Tfo3Phases extends AbstractExtension<TwoWindingsTransformer> {

    public static final String NAME = "threePhaseTfo";

    private Boolean isOpenPhaseA1;
    private Boolean isOpenPhaseB1;
    private Boolean isOpenPhaseC1;
    private Boolean isOpenPhaseA2;
    private Boolean isOpenPhaseB2;
    private Boolean isOpenPhaseC2;

    // in case of a Delta-Wye or Wye-Delta connexion,
    // this attribute informs if this is a forward (step-up) or backward (step-down) connexion
    private StepWindingConnectionType stepWindingConnectionType;

    private ComplexMatrix ya;
    private ComplexMatrix yb;
    private ComplexMatrix yc;

    @Override
    public String getName() {
        return NAME;
    }

    public Tfo3Phases(TwoWindingsTransformer twoWindingsTransformer,
                      ComplexMatrix ya, ComplexMatrix yb, ComplexMatrix yc,
                      StepWindingConnectionType stepWindingConnectionType,
                      boolean isPhaseOpenA1,
                      boolean isPhaseOpenB1,
                      boolean isPhaseOpenC1,
                      boolean isPhaseOpenA2,
                      boolean isPhaseOpenB2,
                      boolean isPhaseOpenC2) {
        super(twoWindingsTransformer);
        this.isOpenPhaseA1 = isPhaseOpenA1;
        this.isOpenPhaseB1 = isPhaseOpenB1;
        this.isOpenPhaseC1 = isPhaseOpenC1;
        this.isOpenPhaseA2 = isPhaseOpenA2;
        this.isOpenPhaseB2 = isPhaseOpenB2;
        this.isOpenPhaseC2 = isPhaseOpenC2;
        this.ya = ya;
        this.yb = yb;
        this.yc = yc;
        this.stepWindingConnectionType = stepWindingConnectionType;
    }

    public Boolean getOpenPhaseA1() {
        return isOpenPhaseA1;
    }

    public Boolean getOpenPhaseB1() {
        return isOpenPhaseB1;
    }

    public Boolean getOpenPhaseC1() {
        return isOpenPhaseC1;
    }

    public ComplexMatrix getYa() {
        return ya;
    }

    public ComplexMatrix getYb() {
        return yb;
    }

    public ComplexMatrix getYc() {
        return yc;
    }

    public StepWindingConnectionType getStepWindingConnectionType() {
        return stepWindingConnectionType;
    }
}
