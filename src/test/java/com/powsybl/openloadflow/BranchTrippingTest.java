/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.tasks.AbstractTrippingTask;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.openloadflow.OpenSecurityAnalysis.ContingencyContext;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class BranchTrippingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BranchTrippingTest.class);

    private Network network;
    private List<ContingencyContext> referenceLfTripping;
    private List<ContingencyContext> referenceTripping;

    @BeforeEach
    void setUp() {
        network = OpenSecurityAnalysisTest.createNetwork();
        referenceLfTripping = getReferenceLfTripping();
        referenceTripping = getReferenceTripping();
    }

    private List<ContingencyContext> getReferenceTripping() {
        List<ContingencyContext> ref = Arrays.asList(
            new ContingencyContext(new Contingency("L1")),
            new ContingencyContext(new Contingency("L2")));
        ref.get(0).branchIdsToOpen.addAll(Arrays.asList("C", "B3"));
        ref.get(1).branchIdsToOpen.addAll(Arrays.asList("B1", "B4"));
        return ref;

    }

    private List<ContingencyContext> getReferenceLfTripping() {
        List<ContingencyContext> ref = Arrays.asList(
            new ContingencyContext(new Contingency("L1")),
            new ContingencyContext(new Contingency("L2")));
        ref.get(0).branchIdsToOpen.add("C");
        return ref;
    }

    @Test
    void testBranchTripping() {
        // Testing all contingencies
        ContingenciesProvider contingenciesProvider = network -> network.getBranchStream()
            .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId()))).collect(Collectors.toList());

        List<ContingencyContext> contingencyContexts = computeBranchesToOpen(contingenciesProvider);
        printResult(contingencyContexts);
        checkResult(contingencyContexts, referenceTripping);
    }

    @Test
    void testLfBranchTripping() {
        // Testing all contingencies
        ContingenciesProvider contingenciesProvider = network -> network.getBranchStream()
            .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId()) {
                @Override
                public AbstractTrippingTask toTask() {
                    return new LfBranchTripping(id, voltageLevelId);
                }
            }))
            .collect(Collectors.toList());

        List<ContingencyContext> contingencyContexts = computeBranchesToOpen(contingenciesProvider);
        printResult(contingencyContexts);
        checkResult(contingencyContexts, referenceLfTripping);
    }

    private List<ContingencyContext> computeBranchesToOpen(ContingenciesProvider contingenciesProvider) {
        List<ContingencyContext> contingencyContexts = new ArrayList<>();
        for (Contingency contingency : contingenciesProvider.getContingencies(network)) {
            ContingencyContext cc = new ContingencyContext(contingency);
            Set<Switch> switchesToOpen = new HashSet<>();
            for (ContingencyElement element : contingency.getElements()) {
                AbstractTrippingTask task = element.toTask();
                task.traverse(network, null, switchesToOpen, new HashSet<>());
            }
            switchesToOpen.forEach(s -> cc.branchIdsToOpen.add(s.getId()));
            contingencyContexts.add(cc);
        }
        return contingencyContexts;
    }

    private void printResult(List<ContingencyContext> result) {
        for (ContingencyContext cc : result) {
            LOGGER.info("Contingency {} containing {} branches to open: {}",
                cc.contingency.getId(), cc.branchIdsToOpen.size(), cc.branchIdsToOpen);
        }
    }

    private void checkResult(List<ContingencyContext> contingencyContexts, List<ContingencyContext> reference) {
        Assert.assertEquals(reference.size(), contingencyContexts.size());
        for (int i=0; i<contingencyContexts.size(); i++) {
            Assert.assertEquals(reference.get(i).contingency.getId(), contingencyContexts.get(i).contingency.getId());
            Assert.assertEquals(reference.get(i).branchIdsToOpen, contingencyContexts.get(i).branchIdsToOpen);
        }
    }

}
