/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.Generator;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */
public class GeneratorState {
    private final double targetP;
    private final double targetQ;
    private final boolean isVoltageRegulatorOn;

    public GeneratorState(Generator generator) {
        this.targetP = generator.getTargetP();
        this.targetQ = generator.getTargetQ();
        this.isVoltageRegulatorOn = generator.isVoltageRegulatorOn();
    }

    public void restoreGeneratorState(Generator generator) {
        generator.setTargetP(targetP);
        generator.setTargetQ(targetQ);
        generator.setVoltageRegulatorOn(isVoltageRegulatorOn);
    }

    /**
     * Get the map of the states of given generators, indexed by the generator itself
     * @param generators the generator for which the state is returned
     * @return the map of the states of given generators, indexed by the generator itself
     */
    public static Map<Generator, GeneratorState> createGeneratorStates(Collection<Generator> generators) {
        return generators.stream().collect(Collectors.toMap(Function.identity(), GeneratorState::new));
    }

    /**
     * Set the generator states based on the given map of states
     * @param generatorStates the map containing the generator states, indexed by generators
     */
    public static void restoreGeneratorStates(Map<Generator, GeneratorState> generatorStates) {
        generatorStates.forEach((gen, state) -> state.restoreGeneratorState(gen));
    }
}
