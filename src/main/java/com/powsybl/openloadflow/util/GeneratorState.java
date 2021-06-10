/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.network.LfGenerator;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */
public class GeneratorState {
    private final double targetP;

    public GeneratorState(LfGenerator lfGenerator) {
        this.targetP = lfGenerator.getTargetP();
    }

    public void restoreGeneratorState(LfGenerator lfGenerator) {
        lfGenerator.setTargetP(targetP);
    }

    /**
     * Get the map of the states of given generators, indexed by the generator itself
     * @param lfGenerators the generator for which the state is returned
     * @return the map of the states of given generators, indexed by the generator itself
     */
    public static Map<LfGenerator, GeneratorState> createGeneratorStates(Collection<LfGenerator> lfGenerators) {
        return lfGenerators.stream().collect(Collectors.toMap(Function.identity(), GeneratorState::new));
    }

    /**
     * Set the generator states based on the given map of states
     * @param lfGeneratorStates the map containing the generator states, indexed by generators
     */
    public static void restoreGeneratorStates(Map<LfGenerator, GeneratorState> lfGeneratorStates) {
        lfGeneratorStates.forEach((gen, state) -> state.restoreGeneratorState(gen));
    }
}
