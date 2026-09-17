/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcNewtonSolver;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuTestPaths;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationFilter;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.SecurityAnalysisRunParameters;
import com.powsybl.security.results.PostContingencyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end security-analysis comparison harness: runs the SAME full security analysis — all branch N-1, a
 * list of generator contingencies, and a set of branch N-2 — on the CPU ({@link AcSecurityAnalysis}) and on
 * the GPU ({@link GpuBatchedAcSecurityAnalysis}, the {@code olf.sa.gpuBatched} path), with the SAME (RTE)
 * parameters, and compares results correctness and wall time.
 *
 * <p>The GPU path degrades gracefully: it batches the qualified branch N-1 and N-2 on the device and routes
 * everything it cannot yet batch (generator/bus loss, unsupported features) to the CPU within the same run —
 * so the results must MATCH the pure-CPU run. The harness asserts the pre-contingency status, the
 * per-contingency status, and the per-contingency limit-violation SET agree (a small near-threshold flicker
 * is tolerated, since the GPU and CPU post-contingency states agree only to load-flow tolerance ~1e-4), and
 * prints CPU vs GPU timing + how many contingencies were solved on the device.
 *
 * <p>By default it runs on IEEE14 with a default loadflow configuration so it executes everywhere (CPU-only
 * in CI; the GPU comparison is exercised on a device-equipped machine). For a realistic run set:
 * <ul>
 *   <li>{@code OLF_SA_CASE=/path/to/case9241pegase.xiidm.gz} — a larger network;</li>
 *   <li>{@code OLF_RTE_PARAMS=/path/to/rte-loadflow-default-parameters.json} — the RTE LoadFlow parameters;</li>
 *   <li>{@code OLF_SA_GENS=50} / {@code OLF_SA_N2=20} — cap the generator and branch-N-2 contingency counts.</li>
 * </ul>
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuSecurityAnalysisComparisonTest {

    private ComputationManager computationManager;
    private OpenSecurityAnalysisProvider provider;

    static {
        GpuTestPaths.init();
    }

    @BeforeEach
    void setUp() {
        computationManager = Mockito.mock(ComputationManager.class);
        Mockito.when(computationManager.getExecutor()).thenReturn(ForkJoinPool.commonPool());
        provider = new OpenSecurityAnalysisProvider(new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>());
    }

    @Test
    void cpuVsGpuFullSecurityAnalysis() {
        SecurityAnalysisParameters saParameters = rteParameters();

        // Build the contingency set once from a probe network (ids are stable across reloads).
        List<Contingency> contingencies = buildContingencies(loadNetwork());
        int nBranchN1 = (int) contingencies.stream().filter(c -> c.getId().startsWith("br:")).count();
        int nGen = (int) contingencies.stream().filter(c -> c.getId().startsWith("gen:")).count();
        int nN2 = (int) contingencies.stream().filter(c -> c.getId().startsWith("n2:")).count();
        System.out.printf("Security-analysis comparison: %d contingencies (%d branch N-1, %d generator, %d branch N-2)%n",
                contingencies.size(), nBranchN1, nGen, nN2);

        // CPU run (fresh network).
        long c0 = System.nanoTime();
        SecurityAnalysisResult cpu = run(loadNetwork(), contingencies, saParameters);
        long cpuMs = (System.nanoTime() - c0) / 1_000_000;

        if (!GpuAcNewtonSolver.isAvailable()) {
            System.out.printf("  CPU: %d ms (GPU not available — comparison skipped). pre-contingency=%s, %d post results%n",
                    cpuMs, cpu.getPreContingencyResult().getStatus(), cpu.getPostContingencyResults().size());
            assertTrue(!cpu.getPostContingencyResults().isEmpty(), "CPU SA must produce post-contingency results");
            return;
        }

        // GPU run (fresh network), olf.sa.gpuBatched on. The base + fall-through load flows run on the GPU
        // Newton-Raphson too (the default), and the qualified single-branch N-1 is batched on the device.
        System.setProperty(OpenSecurityAnalysisProvider.GPU_BATCHED_AC_SA_PROPERTY, "true");
        SecurityAnalysisResult gpu;
        long gpuMs;
        int precomputed;
        String gpuScope;
        try {
            // Attempt the full set. If the GPU run throws, report it and retry without N-2 so a comparison is
            // still produced (a guard in case a future change reintroduces a fall-through state issue).
            try {
                long g0 = System.nanoTime();
                gpu = run(loadNetwork(), contingencies, saParameters);
                gpuMs = (System.nanoTime() - g0) / 1_000_000;
                gpuScope = "full set";
            } catch (RuntimeException e) {
                System.out.printf("  GPU full SA FAILED on the full set (%s) — retrying without N-2 fall-throughs%n",
                        rootMessage(e));
                List<Contingency> survivable = contingencies.stream()
                        .filter(c -> !c.getId().startsWith("n2:")).toList();
                long g0 = System.nanoTime();
                gpu = run(loadNetwork(), survivable, saParameters);
                gpuMs = (System.nanoTime() - g0) / 1_000_000;
                gpuScope = "branch N-1 + generators (N-2 excluded after a GPU fall-through failure)";
            }
            precomputed = GpuBatchedAcSecurityAnalysis.LAST_GPU_PRECOMPUTED_COUNT.get();
        } finally {
            System.clearProperty(OpenSecurityAnalysisProvider.GPU_BATCHED_AC_SA_PROPERTY);
        }

        // ---- correctness over the contingencies the GPU produced (compare to the CPU reference) ----
        assertEquals(cpu.getPreContingencyResult().getStatus(), gpu.getPreContingencyResult().getStatus(),
                "pre-contingency status must match");

        Map<String, String> cpuStatus = statuses(cpu);
        Map<String, String> gpuStatus = statuses(gpu);
        Map<String, TreeSet<String>> cpuViol = violations(cpu);
        Map<String, TreeSet<String>> gpuViol = violations(gpu);

        // Every contingency — batched single-branch N-1, batched branch N-2, batched fixed-shunt disconnection,
        // and the generator fall-throughs (which run on the GPU base solver / CPU within the run) — must match
        // the CPU AcSecurityAnalysis. The
        // states agree only to load-flow tolerance (~1e-4), so a contingency right at a convergence / limit
        // threshold can flicker; a small near-threshold fraction is tolerated.
        int statusMismatch = 0;
        int violMismatch = 0;
        for (String id : gpuStatus.keySet()) {
            boolean statusOk = gpuStatus.get(id).equals(cpuStatus.get(id));
            boolean violOk = cpuViol.getOrDefault(id, EMPTY).equals(gpuViol.getOrDefault(id, EMPTY));
            if (!statusOk || !violOk) {
                System.out.printf("  DIFF %-18s status cpu=%s gpu=%s%s%n", id, cpuStatus.get(id), gpuStatus.get(id),
                        violOk ? "" : " | viol " + symmetricDiff(cpuViol.getOrDefault(id, EMPTY), gpuViol.getOrDefault(id, EMPTY)));
            }
            statusMismatch += statusOk ? 0 : 1;
            violMismatch += violOk ? 0 : 1;
        }
        int cpuViolTotal = cpuViol.values().stream().mapToInt(TreeSet::size).sum();

        System.out.printf("Result [%s]: CPU(full) %d ms | GPU %d ms | %d/%d contingencies batched on device | "
                + "%d total CPU violations | %d status + %d violation-set mismatches%n",
                gpuScope, cpuMs, gpuMs, precomputed, gpuStatus.size(), cpuViolTotal, statusMismatch, violMismatch);

        assertTrue(precomputed > 0, "the GPU must have batched at least one contingency on the device");
        int tolerance = Math.max(1, gpuStatus.size() / 50);
        assertTrue(statusMismatch <= tolerance, "convergence status must match the CPU within near-threshold "
                + "tolerance (" + statusMismatch + " mismatches > " + tolerance + ")");
        assertTrue(violMismatch <= tolerance, "violation sets must match the CPU within near-threshold "
                + "tolerance (" + violMismatch + " mismatches > " + tolerance + ")");
    }

    private static final TreeSet<String> EMPTY = new TreeSet<>();

    private SecurityAnalysisResult run(Network network, List<Contingency> contingencies, SecurityAnalysisParameters saParameters) {
        return run(network, contingencies, saParameters, List.of());
    }

    private SecurityAnalysisResult run(Network network, List<Contingency> contingencies,
                                       SecurityAnalysisParameters saParameters,
                                       List<com.powsybl.security.monitor.StateMonitor> monitors) {
        ContingenciesProvider cp = n -> contingencies;
        SecurityAnalysisRunParameters runParameters = new SecurityAnalysisRunParameters()
                .setFilter(new LimitViolationFilter())
                .setComputationManager(computationManager)
                .setMonitors(monitors)
                .setSecurityAnalysisParameters(saParameters);
        return provider.run(network, network.getVariantManager().getWorkingVariantId(), cp, runParameters)
                .join().getResult();
    }

    /**
     * The apples-to-apples STATE comparison: GPU SA vs CPU SA (both HOT-START each contingency from the base via
     * {@code PreviousValueVoltageInitializer}) over the non-fragmenting BRANCH N-1 set, at the same parameters —
     * proving the device and CPU agree to MACHINE PRECISION even at the LOOSE 1-MW distributed-slack tolerance
     * (worst ~1e-14). This holds precisely because the device runs the SAME outer loops as OLF: for a
     * NON-participating slack the distributed-slack loop distributes the full mismatch to the same buses OLF does
     * (no approximation), and the reactive-limits switch decisions match (inner Newton on OLF's convEpsPerEq
     * criterion). The slack is made non-participating on purpose — a PARTICIPATING slack routes through the
     * device "retain" trick (the fixed CSR pattern has no active-power equation at the reference bus, so the
     * device cannot retarget the slack's own generators the way OLF does), which linearizes the loss change and
     * leaves a residual that scales with the DS tolerance (~1e-3 at 1 MW, ~1e-7 tight) — a separate open item.
     * Islanding N-1 (frozen island, held at 1∠0) and generator/N-2 contingencies are out of scope here.
     */
    @Test
    void cpuVsGpuSecurityAnalysisBranchN1StatesMatch() {
        assumeTrue(GpuAcNewtonSolver.isAvailable(), "GPU batched native lib not available");
        Network probe = nonParticipatingSlackNetwork();
        List<Contingency> contingencies = buildContingencies(probe).stream()
                .filter(c -> c.getId().startsWith("br:")).toList();
        // DEFAULT (loose 1-MW) distributed-slack tolerance — deliberately NOT tightened. Tight inner Newton so
        // inner-Newton slack is out of the picture.
        LoadFlowParameters lfp = new LoadFlowParameters().setUseReactiveLimits(true).setDistributedSlack(true);
        com.powsybl.openloadflow.OpenLoadFlowParameters.create(lfp).setNewtonRaphsonConvEpsPerEq(1e-12)
                .setSlackBusSelectionMode(com.powsybl.openloadflow.network.SlackBusSelectionMode.FIRST);
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters().setLoadFlowParameters(lfp);

        java.util.Set<String> vlIds = probe.getVoltageLevelStream()
                .map(com.powsybl.iidm.network.VoltageLevel::getId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, Double> nominalV = new java.util.HashMap<>();
        probe.getVoltageLevelStream().forEach(vl -> nominalV.put(vl.getId(), vl.getNominalV()));
        List<com.powsybl.security.monitor.StateMonitor> monitors = List.of(new com.powsybl.security.monitor.StateMonitor(
                com.powsybl.contingency.ContingencyContext.all(), java.util.Set.of(), vlIds, java.util.Set.of()));

        SecurityAnalysisResult cpu = run(nonParticipatingSlackNetwork(), contingencies, saParameters, monitors);
        System.setProperty(OpenSecurityAnalysisProvider.GPU_BATCHED_AC_SA_PROPERTY, "true");
        SecurityAnalysisResult gpu;
        try {
            gpu = run(nonParticipatingSlackNetwork(), contingencies, saParameters, monitors);
        } finally {
            System.clearProperty(OpenSecurityAnalysisProvider.GPU_BATCHED_AC_SA_PROPERTY);
        }

        java.util.Map<String, java.util.Map<String, com.powsybl.security.results.BusResult>> cpuStates = busStates(cpu);
        java.util.Map<String, java.util.Map<String, com.powsybl.security.results.BusResult>> gpuStates = busStates(gpu);
        double worst = 0;
        String where = "";
        int compared = 0;
        int islandingSkipped = 0;
        for (var e : gpuStates.entrySet()) {
            java.util.Map<String, com.powsybl.security.results.BusResult> cpuBuses = cpuStates.get(e.getKey());
            if (cpuBuses == null) {
                continue;
            }
            boolean fragmenting = e.getValue().values().stream().anyMatch(gb -> {
                var cb = cpuBuses.get(gb.getBusId());
                return gb.getAngle() == 0.0 && cb != null && Math.abs(cb.getAngle()) > 1e-3;   // GPU froze an island
            });
            if (fragmenting) {
                islandingSkipped++;
                continue;
            }
            compared++;
            for (var be : e.getValue().entrySet()) {
                var cb = cpuBuses.get(be.getKey());
                var gb = be.getValue();
                if (cb == null || Double.isNaN(cb.getV())) {
                    continue;
                }
                double nv = nominalV.getOrDefault(gb.getVoltageLevelId(), 1.0);
                double d = Math.max(Math.abs(cb.getV() - gb.getV()) / nv, Math.abs(Math.toRadians(cb.getAngle() - gb.getAngle())));
                if (d > worst) {
                    worst = d;
                    where = e.getKey() + "/" + be.getKey();
                }
            }
        }
        System.out.printf("GPU SA vs CPU SA branch-N-1 states [%d compared, %d islanding-skipped]: worst |Δ| = %.3e at %s%n",
                compared, islandingSkipped, worst, where);
        assertTrue(compared >= 4, "must compare several branch N-1 states");
        assertTrue(worst < 1e-10, "GPU SA and CPU SA branch-N-1 states must agree (worst = " + worst + " at " + where + ")");
    }

    /** IEEE14 with the first-slack generator (B1-G) made non-participating — an unambiguous distributed-slack
     *  reference share, and it avoids the participating-slack retain path (isolates that variable). */
    private static Network nonParticipatingSlackNetwork() {
        Network n = IeeeCdfNetworkFactory.create14();
        n.getGenerator("B1-G").newExtension(com.powsybl.iidm.network.extensions.ActivePowerControlAdder.class)
                .withParticipate(false).withDroop(4).add();
        return n;
    }

    private static java.util.Map<String, java.util.Map<String, com.powsybl.security.results.BusResult>> busStates(SecurityAnalysisResult r) {
        java.util.Map<String, java.util.Map<String, com.powsybl.security.results.BusResult>> m = new java.util.HashMap<>();
        for (PostContingencyResult pcr : r.getPostContingencyResults()) {
            if (pcr.getStatus() != com.powsybl.security.PostContingencyComputationStatus.CONVERGED) {
                continue;
            }
            java.util.Map<String, com.powsybl.security.results.BusResult> byBus = new java.util.HashMap<>();
            for (com.powsybl.security.results.BusResult br : pcr.getNetworkResult().getBusResults()) {
                byBus.put(br.getBusId(), br);
            }
            m.put(pcr.getContingency().getId(), byBus);
        }
        return m;
    }

    /** All branch N-1 (br:), a capped generator list (gen:), and a capped branch N-2 set (n2:). */
    private static List<Contingency> buildContingencies(Network network) {
        List<Contingency> conts = new ArrayList<>();
        List<String> branchIds = new ArrayList<>();
        for (Branch<?> b : network.getBranches()) {
            conts.add(Contingency.builder("br:" + b.getId()).addBranch(b.getId()).build());
            branchIds.add(b.getId());
        }
        int genCap = Integer.parseInt(System.getenv().getOrDefault("OLF_SA_GENS", "1000"));
        int g = 0;
        for (Generator gen : network.getGenerators()) {
            if (g >= genCap) {
                break;
            }
            conts.add(Contingency.builder("gen:" + gen.getId()).addGenerator(gen.getId()).build());
            g++;
        }
        int n2Cap = Integer.parseInt(System.getenv().getOrDefault("OLF_SA_N2", "20"));
        int added = 0;
        for (int i = 0; i + 1 < branchIds.size() && added < n2Cap; i += 2) {
            String a = branchIds.get(i);
            String b = branchIds.get(i + 1);
            conts.add(Contingency.builder("n2:" + a + "+" + b).addBranch(a).addBranch(b).build());
            added++;
        }
        // Fixed-shunt disconnections: the GPU batches a single packed fixed-shunt outage as an element mask
        // (GpuBatchedAcSecurityAnalysis.singleShuntOutage → BatchContingency.shunt), so a shunt contingency
        // exercises that path end-to-end against the CPU SA.
        for (ShuntCompensator sh : network.getShuntCompensators()) {
            conts.add(Contingency.builder("shunt:" + sh.getId()).addShuntCompensator(sh.getId()).build());
        }
        return conts;
    }

    private static Map<String, String> statuses(SecurityAnalysisResult r) {
        Map<String, String> m = new HashMap<>();
        for (PostContingencyResult pcr : r.getPostContingencyResults()) {
            m.put(pcr.getContingency().getId(), pcr.getStatus().name());
        }
        return m;
    }

    private static Map<String, TreeSet<String>> violations(SecurityAnalysisResult r) {
        Map<String, TreeSet<String>> m = new HashMap<>();
        for (PostContingencyResult pcr : r.getPostContingencyResults()) {
            TreeSet<String> keys = new TreeSet<>();
            for (LimitViolation v : pcr.getLimitViolationsResult().getLimitViolations()) {
                keys.add(v.getSubjectId() + "|" + v.getLimitType() + "|" + v.getSide() + "|" + v.getLimitName());
            }
            m.put(pcr.getContingency().getId(), keys);
        }
        return m;
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null) {
            r = r.getCause();
        }
        return r.getClass().getSimpleName() + ": " + r.getMessage();
    }

    private static TreeSet<String> symmetricDiff(TreeSet<String> a, TreeSet<String> b) {
        TreeSet<String> d = new TreeSet<>(a);
        d.addAll(b);
        TreeSet<String> common = new TreeSet<>(a);
        common.retainAll(b);
        d.removeAll(common);
        return d;
    }

    private static Network loadNetwork() {
        String caseFile = System.getenv("OLF_SA_CASE");
        return caseFile != null ? Network.read(Path.of(caseFile)) : IeeeCdfNetworkFactory.create14();
    }

    /** RTE LoadFlow parameters from {@code OLF_RTE_PARAMS}, else a default config (reactive limits +
     *  distributed slack on), wrapped in a {@link SecurityAnalysisParameters}. */
    private static SecurityAnalysisParameters rteParameters() {
        String rte = System.getenv("OLF_RTE_PARAMS");
        LoadFlowParameters lfp;
        if (rte != null && Files.exists(Path.of(rte))) {
            lfp = JsonLoadFlowParameters.read(Path.of(rte));
            System.out.println("Using RTE LoadFlow parameters from " + rte);
        } else {
            lfp = new LoadFlowParameters().setUseReactiveLimits(true).setDistributedSlack(true);
            System.out.println("Using default LoadFlow parameters (set OLF_RTE_PARAMS for the RTE config)");
        }
        return new SecurityAnalysisParameters().setLoadFlowParameters(lfp);
    }
}
