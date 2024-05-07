/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.openloadflow.util.ComplexMatrix;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class Tfo3PhasesAdder extends AbstractExtensionAdder<TwoWindingsTransformer, Tfo3Phases> {

    private boolean isOpenPhaseA1 = false;
    private boolean isOpenPhaseB1 = false;
    private boolean isOpenPhaseC1 = false;
    private boolean isOpenPhaseA2 = false;
    private boolean isOpenPhaseB2 = false;
    private boolean isOpenPhaseC2 = false;

    private StepWindingConnectionType stepWindingConnectionType = StepWindingConnectionType.NONE;

    private ComplexMatrix ya;
    private ComplexMatrix yb;
    private ComplexMatrix yc;

    public Tfo3PhasesAdder(TwoWindingsTransformer t2w) {
        super(t2w);
    }

    @Override
    public Class<? super Tfo3Phases> getExtensionClass() {
        return Tfo3Phases.class;
    }

    @Override
    protected Tfo3Phases createExtension(TwoWindingsTransformer t2w) {
        return new Tfo3Phases(t2w, ya, yb, yc,
                stepWindingConnectionType,
                isOpenPhaseA1, isOpenPhaseB1, isOpenPhaseC1,
                isOpenPhaseA2, isOpenPhaseB2, isOpenPhaseC2);
    }

    public Tfo3PhasesAdder withIsOpenPhaseA1(boolean isOpenA1) {
        this.isOpenPhaseA1 = isOpenA1;
        return this;
    }

    public Tfo3PhasesAdder withIsOpenPhaseB1(boolean isOpenB1) {
        this.isOpenPhaseB1 = isOpenB1;
        return this;
    }

    public Tfo3PhasesAdder withIsOpenPhaseC1(boolean isOpenC1) {
        this.isOpenPhaseC1 = isOpenC1;
        return this;
    }

    public Tfo3PhasesAdder withIsOpenPhaseA2(boolean isOpenA2) {
        this.isOpenPhaseA2 = isOpenA2;
        return this;
    }

    public Tfo3PhasesAdder withIsOpenPhaseB2(boolean isOpenB2) {
        this.isOpenPhaseB2 = isOpenB2;
        return this;
    }

    public Tfo3PhasesAdder withIsOpenPhaseC2(boolean isOpenC2) {
        this.isOpenPhaseC2 = isOpenC2;
        return this;
    }

    public Tfo3PhasesAdder withYa(ComplexMatrix ya) {
        this.ya = ya;
        return this;
    }

    public Tfo3PhasesAdder withYb(ComplexMatrix yb) {
        this.yb = yb;
        return this;
    }

    public Tfo3PhasesAdder withYc(ComplexMatrix yc) {
        this.yc = yc;
        return this;
    }

    public Tfo3PhasesAdder withStepWindingConnectionType(StepWindingConnectionType stepWindingConnectionType) {
        this.stepWindingConnectionType = stepWindingConnectionType;
        return this;
    }
}
