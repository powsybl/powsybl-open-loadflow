package com.powsybl.openloadflow.network.impl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.SubjectType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class LfContingency {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfContingency.class);

    private final Contingency contingency;

    private final Set<LfBus> buses;

    private final Set<LfBranch> branches;

    private double activePowerLoss;

    public LfContingency(Contingency contingency, Set<LfBus> buses, Set<LfBranch> branches) {
        this.contingency = Objects.requireNonNull(contingency);
        this.buses = Objects.requireNonNull(buses);
        this.branches = Objects.requireNonNull(branches);
        double lose = 0;
        for (LfBus bus : buses) {
            lose += bus.getGenerationTargetP() - bus.getLoadTargetP();
        }
        this.activePowerLoss = lose;
    }

    public static LfContingency create(LfNetwork lfNetwork, Contingency contingency) {
        Set<LfBus> contingencyBuses = new HashSet<>();
        Set<LfBranch> contingencyBranches = new HashSet<>();
        for (ContingencyElement element : contingency.getElements()) {
            if (!element.getType().equals(ContingencyElementType.BRANCH)) {
                throw new UnsupportedOperationException("Only contingency on a branch is yet supported");
            }
            contingencyBranches.add(lfNetwork.getBranchById(element.getId()));
        }
        return new LfContingency(contingency, contingencyBuses, contingencyBranches);
    }

    public Contingency getContingency() {
        return contingency;
    }

    public Set<LfBus> getBuses() {
        return buses;
    }

    public Set<LfBranch> getBranches() {
        return branches;
    }

    public double getActivePowerLoss() {
        return activePowerLoss;
    }

    public void writeJson(Writer writer) {
        Objects.requireNonNull(writer);
        try (JsonGenerator jsonGenerator = new JsonFactory()
                .createGenerator(writer)
                .useDefaultPrettyPrinter()) {
            jsonGenerator.writeStartObject();

            jsonGenerator.writeStringField("id", contingency.getId());

            jsonGenerator.writeFieldName("buses");
            int[] sortedBuses = buses.stream().mapToInt(LfBus::getNum).sorted().toArray();
            jsonGenerator.writeArray(sortedBuses, 0, sortedBuses.length);

            jsonGenerator.writeFieldName("branches");
            int[] sortedBranches = branches.stream().mapToInt(LfBranch::getNum).sorted().toArray();
            jsonGenerator.writeArray(sortedBranches, 0, sortedBranches.length);

            jsonGenerator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deactivateRelatedEquations(EquationSystem equationSystem, List<Equation> deactivatedEquations, List<EquationTerm> deactivatedEquationTerms) {
        for (LfBranch branch : getBranches()) {
            LOGGER.trace("Remove equations and equations terms related to branch '{}'", branch.getId());

            // deactivate all equations related to a branch
            equationSystem.getEquations(SubjectType.BRANCH, branch.getNum()).stream()
                          .filter(Equation::isActive)
                          .forEach(equation -> {
                              equation.setActive(false);
                              deactivatedEquations.add(equation);
                          });

            // deactivate all equation terms related to a branch
            equationSystem.getEquationTerms(SubjectType.BRANCH, branch.getNum()).stream()
                          .filter(EquationTerm::isActive)
                          .forEach(equationTerm -> {
                              equationTerm.setActive(false);
                              deactivatedEquationTerms.add(equationTerm);
                          });
        }

        for (LfBus bus : getBuses()) {
            LOGGER.trace("Remove equations and equation terms related to bus '{}'", bus.getId());

            // deactivate all equations related to a bus
            equationSystem.getEquations(SubjectType.BUS, bus.getNum()).stream()
                          .filter(Equation::isActive)
                          .forEach(equation -> {
                              equation.setActive(false);
                              deactivatedEquations.add(equation);
                          });

            // deactivate all equation terms related to a bus
            equationSystem.getEquationTerms(SubjectType.BUS, bus.getNum()).stream()
                          .filter(EquationTerm::isActive)
                          .forEach(equationTerm -> {
                              equationTerm.setActive(false);
                              deactivatedEquationTerms.add(equationTerm);
                          });
        }
    }

    public static void deactivateEquations(LfContingency lfContingency, EquationSystem equationSystem, List<Equation> deactivatedEquations, List<EquationTerm> deactivatedEquationTerms) {
        lfContingency.deactivateRelatedEquations(equationSystem, deactivatedEquations, deactivatedEquationTerms);
    }

    public static void reactivateEquations(List<Equation> deactivatedEquations, List<EquationTerm> deactivatedEquationTerms) {
        // restore deactivated equations and equations terms from previous contingency
        if (!deactivatedEquations.isEmpty()) {
            for (Equation equation : deactivatedEquations) {
                equation.setActive(true);
            }
            deactivatedEquations.clear();
        }
        if (!deactivatedEquationTerms.isEmpty()) {
            for (EquationTerm equationTerm : deactivatedEquationTerms) {
                equationTerm.setActive(true);
            }
            deactivatedEquationTerms.clear();
        }
    }
}
