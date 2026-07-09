/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.vector.gpu;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.HvdcAcEmulationSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.HvdcAcEmulationSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide1ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide2ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ShuntCompensatorActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ShuntCompensatorReactiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.EquationArray;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTermArray;
import com.powsybl.openloadflow.equations.SingleEquation;
import com.powsybl.openloadflow.equations.SingleEquationTerm;
import com.powsybl.openloadflow.equations.VariableEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.GeneratorVoltageControl;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.TransformerVoltageControl;
import com.powsybl.openloadflow.network.VoltageControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extracts the {@link GpuAcData} the GPU Newton-Raphson consumes from an OLF equation
 * system, mirroring exactly the wiring AcEquationSystemCreator produced (closed/open
 * branch, shunt and HVDC AC-emulation terms).
 *
 * <p>STABLE row indexing: every equation row is identified by its diagonal-paired
 * VARIABLE row — a bus's P row is its BUS_PHI variable row, its Q row its BUS_V row —
 * never by OLF's equation column, which is renumbered on every activation change
 * (PV→PQ switching). The extracted structure is therefore the FIXED SUPERSET pattern,
 * identical across outer-loop activation switching, so a cached GPU context survives
 * it. What activation actually decides is carried per solve instead:
 * {@link #extractRowMode} (power equation vs slack/PV identity per row, applied as a
 * device-side mask) and {@link #extractTargets} (targets re-ordered to the stable rows).
 *
 * <p>VALIDATES first, and throws {@link PowsyblException} on anything the GPU path does
 * not cover — unsupported variable types (e.g. transformer controls), unsupported
 * equation types (e.g. zero-impedance branches), unsupported active equation terms
 * (e.g. ZIP load models), and equation-array (vectorized) systems — so an unsupported
 * network fails loudly instead of converging to a wrong state. Disconnection-allowed
 * branches (security-analysis contingency candidates) are NOT rejected: each fixed
 * configuration is solved correctly and a structure change (outage) triggers a context
 * rebuild, so the closed/open switch is a between-solve event the extractor handles.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public final class GpuAcDataExtractor {

    private static final Set<Class<?>> SUPPORTED_PQ_TERMS = Set.of(
            ClosedBranchSide1ActiveFlowEquationTerm.class, ClosedBranchSide1ReactiveFlowEquationTerm.class,
            ClosedBranchSide2ActiveFlowEquationTerm.class, ClosedBranchSide2ReactiveFlowEquationTerm.class,
            OpenBranchSide1ActiveFlowEquationTerm.class, OpenBranchSide1ReactiveFlowEquationTerm.class,
            OpenBranchSide2ActiveFlowEquationTerm.class, OpenBranchSide2ReactiveFlowEquationTerm.class,
            ShuntCompensatorActiveFlowEquationTerm.class, ShuntCompensatorReactiveFlowEquationTerm.class,
            HvdcAcEmulationSide1ActiveFlowEquationTerm.class, HvdcAcEmulationSide2ActiveFlowEquationTerm.class);

    // the closed-branch equation-term arrays of the VECTORIZED equation system
    // (AcVectorizedEquationSystemCreator) — the GPU covers exactly the same physics
    private static final Set<String> SUPPORTED_ARRAY_EVALUATORS = Set.of(
            "ac_p_array_closed_1", "ac_p_array_closed_2", "ac_q_array_closed_1", "ac_q_array_closed_2");

    private GpuAcDataExtractor() {
    }

    public static GpuAcData extract(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es) {
        // Default to OLF's own defaults (slack distribution + reactive limits ON). The batched SA path passes
        // the RUN's actual outer-loop set so a disabled outer loop is never run on the device (an outer loop
        // must not run unconditionally); the single-solve path ignores the DS/RL packs entirely.
        return extract(network, es, true, true);
    }

    /**
     * Extract the GPU data, building the distributed-slack (DS) and reactive-limits (RL) outer-loop packs
     * ONLY when the corresponding outer loop is enabled for this run. The batched device solver gates each
     * outer-loop pass on its pack being non-empty, so an empty pack means "do not run that outer loop" —
     * mirroring OLF, which only runs an outer loop when it is in the configured outer-loop list. Building a
     * pack from network capability alone (participating gens / PV buses) would make the device distribute
     * slack or switch PV→PQ even when the run disabled it, diverging from the CPU under any non-trivial
     * mismatch (latent on balanced networks, where the retarget is negligible).
     */
    public static GpuAcData extract(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es,
                                    boolean distributedSlack, boolean reactiveLimits) {
        validate(network, es);

        // Cache each bus's BUS_PHI/BUS_V column once: a bus appears in several branches (degree ~3),
        // so resolving these per branch-endpoint repeats the same variable lookups ~2x its degree.
        // Indexed by LfBus.getNum() (contiguous 0..nBuses-1); -1 means absent.
        int nBuses = network.getBuses().size();
        int[] phiCol = new int[nBuses];
        int[] vCol = new int[nBuses];
        for (LfBus bus : network.getBuses()) {
            int bn = bus.getNum();
            phiCol[bn] = col(es, bn, AcVariableType.BUS_PHI);
            vCol[bn] = col(es, bn, AcVariableType.BUS_V);
        }

        // Per-branch transformer control columns (BRANCH_RHO1 ratio, BRANCH_ALPHA1 phase), -1 when
        // the branch does not derive that control. A controlling transformer's rho column also
        // becomes the controlled bus's Q-balance row (qRow below) — the strong-diagonal pairing.
        int nBranches = network.getBranches().size();
        int[] branchRhoCol = branchVarCols(es, nBranches, AcVariableType.BRANCH_RHO1);
        int[] branchAlphaCol = branchVarCols(es, nBranches, AcVariableType.BRANCH_ALPHA1);
        int[] dummyPCol = branchVarCols(es, nBranches, AcVariableType.DUMMY_P);
        int[] dummyQCol = branchVarCols(es, nBranches, AcVariableType.DUMMY_Q);
        int[] qRow = computeQRow(network, es, vCol, branchRhoCol);

        List<double[]> ziIn = new ArrayList<>();
        List<int[]> ziIdx = new ArrayList<>();

        // closed-branch element index per branch number (-1 = not a closed branch), used to wire the
        // DISTR_Q reactive contributions to their closed-branch q1/q2 outputs.
        int[] cbElemOf = new int[nBranches];
        java.util.Arrays.fill(cbElemOf, -1);

        List<double[]> cbIn = new ArrayList<>();
        List<int[]> cbIdx = new ArrayList<>();
        List<double[]> obIn = new ArrayList<>();
        List<int[]> obIdx = new ArrayList<>();
        int[] obElemOf = new int[nBranches];                 // open-branch element index per branch (-1 = none)
        java.util.Arrays.fill(obElemOf, -1);
        for (LfBranch br : network.getBranches()) {
            if (br.isDisabled()) {
                continue;
            }
            LfBus b1 = br.getBus1();
            LfBus b2 = br.getBus2();
            PiModel pi = br.getPiModel();
            if (br.isZeroImpedance(LoadFlowModel.AC)) {
                // bus coupler / non-impedant branch: no flow terms — DUMMY_P/Q variables couple the buses'
                // P/Q balances, and a spanning-tree edge adds the ZERO_V/ZERO_PHI voltage/angle coupling.
                if (b1 == null || b2 == null) {
                    throw new PowsyblException("GPU AC solver: zero-impedance branch " + br.getId()
                            + " is open on one side (not supported)");
                }
                int b1n = b1.getNum();
                int b2n = b2.getNum();
                int dpRow = dummyPCol[br.getNum()];
                int dqRow = dummyQCol[br.getNum()];
                if (dpRow < 0 || dqRow < 0) {
                    throw new PowsyblException("GPU AC solver: zero-impedance branch " + br.getId()
                            + " has no DUMMY_P/DUMMY_Q variables (unsupported non-impedant shape, e.g. both "
                            + "ends voltage-controlled or a 3-winding star)");
                }
                ziIn.add(new double[] {PiModel.R2 / pi.getR1()});
                ziIdx.add(new int[] {phiCol[b1n], qRow[b1n], phiCol[b2n], qRow[b2n], dpRow, dqRow,
                    vCol[b1n], vCol[b2n], phiCol[b1n], phiCol[b2n],
                    br.isSpanningTreeEdge(LoadFlowModel.AC) ? 1 : 0});
                continue;
            }
            if (b1 != null && b2 != null) {
                int ph1 = phiCol[b1.getNum()];
                int v1 = vCol[b1.getNum()];
                int ph2 = phiCol[b2.getNum()];
                int v2 = vCol[b2.getNum()];
                int a1col = branchAlphaCol[br.getNum()];
                int r1col = branchRhoCol[br.getNum()];
                cbElemOf[br.getNum()] = cbIn.size();
                cbIn.add(new double[] {pi.getY(), pi.getG1(), pi.getG2(), pi.getB1(), pi.getB2(),
                    pi.getKsi(), pi.getR1(), pi.getA1()});
                // Prow = bus PHI col; Qrow = qRow[bus] (its V col normally, the controlling
                // transformer's rho col when this bus is voltage-controlled). a1col/r1col carry the
                // branch's own control columns, -1 when not derived (then those scatter rows skip).
                cbIdx.add(new int[] {ph1, qRow[b1.getNum()], ph2, qRow[b2.getNum()],
                    ph1, v1, ph2, v2, a1col, r1col});
            } else if (b2 != null) {                       // side 1 open: p2/q2 at bus2, variable v2
                obElemOf[br.getNum()] = obIn.size();
                obIn.add(new double[] {pi.getY(), pi.getKsi(), pi.getG1(), pi.getB1(), pi.getG2(), pi.getB2(), pi.getR1()});
                obIdx.add(new int[] {-1, -1,
                    phiCol[b2.getNum()], qRow[b2.getNum()],
                    -1, vCol[b2.getNum()]});
            } else if (b1 != null) {                       // side 2 open: p1/q1 at bus1, variable v1
                obElemOf[br.getNum()] = obIn.size();
                obIn.add(new double[] {pi.getY(), pi.getKsi(), pi.getG1(), pi.getB1(), pi.getG2(), pi.getB2(), pi.getR1()});
                obIdx.add(new int[] {
                    phiCol[b1.getNum()], qRow[b1.getNum()],
                    -1, -1,
                    vCol[b1.getNum()], -1});
            }
        }

        List<double[]> shIn = new ArrayList<>();
        List<int[]> shIdx = new ArrayList<>();
        int[] shElemOf = new int[nBuses];                    // shunt element index per bus (-1 = none)
        java.util.Arrays.fill(shElemOf, -1);
        for (LfBus bus : network.getBuses()) {
            bus.getShunt().ifPresent(sh -> {
                shElemOf[bus.getNum()] = shIn.size();
                shIn.add(new double[] {sh.getG(), sh.getB()});
                shIdx.add(new int[] {
                    phiCol[bus.getNum()], qRow[bus.getNum()], vCol[bus.getNum()]});
            });
        }

        List<double[]> hvIn = new ArrayList<>();
        List<int[]> hvIdx = new ArrayList<>();
        for (LfHvdc hvdc : network.getHvdcs()) {
            if (hvdc.getBus1() == null || hvdc.getBus2() == null || !hvdc.isAcEmulation()) {
                continue;
            }
            double k = hvdc.getAcEmulationControl().getDroop() * 180 / Math.PI;
            int ph1 = phiCol[hvdc.getBus1().getNum()];
            int ph2 = phiCol[hvdc.getBus2().getNum()];
            hvIn.add(new double[] {hvdc.getAcEmulationControl().getP0(), k,
                hvdc.getConverterStation1().getLossFactor() / 100, hvdc.getConverterStation2().getLossFactor() / 100,
                hvdc.getR()});
            hvIdx.add(new int[] {ph1, ph2, ph1, ph2});
        }

        List<int[]> dq = new ArrayList<>();      // {elem, side, dqRow, kind} per DISTR_Q contribution
        List<Double> dqW = new ArrayList<>();    // weight per contribution
        buildDistrQContributions(network, es, vCol, cbElemOf, shElemOf, obElemOf, dummyQCol, dq, dqW);
        int[] dqElem = new int[dq.size()];
        int[] dqSide = new int[dq.size()];
        int[] dqRow = new int[dq.size()];
        int[] dqKind = new int[dq.size()];       // 0 = closed-branch q1/q2, 1 = shunt q, 2 = open-branch q,
                                                 // 3 = zero-impedance DUMMY_Q (linear, elem holds the var column)
        double[] dqWeight = new double[dq.size()];
        for (int i = 0; i < dq.size(); i++) {
            dqElem[i] = dq.get(i)[0];
            dqSide[i] = dq.get(i)[1];
            dqRow[i] = dq.get(i)[2];
            dqKind[i] = dq.get(i)[3];
            dqWeight[i] = dqW.get(i);
        }

        List<int[]> dr = new ArrayList<>();      // {drRow, drCol} per DISTR_RHO contribution
        List<Double> drC = new ArrayList<>();    // coefficient per contribution
        buildDistrRhoContributions(network, es, branchRhoCol, dr, drC);
        int[] drRow = new int[dr.size()];
        int[] drCol = new int[dr.size()];
        double[] drCoef = new double[dr.size()];
        for (int i = 0; i < dr.size(); i++) {
            drRow[i] = dr.get(i)[0];
            drCol[i] = dr.get(i)[1];
            drCoef[i] = drC.get(i);
        }

        // Reactive-limits (PV↔PQ) pack: every LOCAL voltage-capable controller bus. Two kinds are packed so the
        // device outer loop can move a bus in EITHER direction (matching OLF, which re-derives the PV/PQ set for
        // each contingency from scratch):
        //  - base PV (BUS_TARGET_V active, BUS_TARGET_Q inactive): its branch q scatters to its own BUS_V row;
        //    the outer loop computes its generator Q and flips the row PV→PQ on a limit violation.
        //  - base PQ (BUS_TARGET_Q active, BUS_TARGET_V inactive) but voltage-CAPABLE (a generator voltage
        //    control that OLF disabled during the base loadflow because it hit a Q limit): MUST be packed too, so
        //    a contingency that RELIEVES its reactive loading can switch it BACK to PV. Omitting these froze such
        //    a bus at its base-case limit for every scenario — a real ~1e-3 error vs OLF when a contingency eased
        //    it (e.g. losing a nearby transformer). The device seeds the pinned limit type from the base state
        //    (v below target ⇒ maxQ-pinned, above ⇒ minQ-pinned) so the switch-back fires.
        // No remote control here (the batch path rejects DISTR_Q), so every voltage-controlled bus is local.
        List<Integer> rlVrow = new ArrayList<>();
        List<Double> rlMinQ = new ArrayList<>();
        List<Double> rlMaxQ = new ArrayList<>();
        List<Double> rlLoadQ = new ArrayList<>();
        List<Double> rlVtarget = new ArrayList<>();
        if (reactiveLimits) {                                   // empty pack when the RL outer loop is disabled
            for (LfBus bus : network.getBuses()) {
                int bn = bus.getNum();
                boolean vActive = row(es, bn, AcEquationType.BUS_TARGET_V) >= 0;
                boolean qActive = row(es, bn, AcEquationType.BUS_TARGET_Q) >= 0;
                boolean basePv = vActive && !qActive;          // voltage-controlling in the base case
                boolean basePqCapable = qActive && !vActive    // switched to PQ in the base case but still capable
                        && !bus.isGeneratorVoltageControlEnabled();
                if (vCol[bn] >= 0 && bus.getGeneratorVoltageControl().isPresent() && (basePv || basePqCapable)) {
                    rlVrow.add(vCol[bn]);
                    rlMinQ.add(bus.getMinQ());
                    rlMaxQ.add(bus.getMaxQ());
                    rlLoadQ.add(bus.getLoadTargetQ());
                    rlVtarget.add(bus.getGeneratorVoltageControl().orElseThrow().getTargetValue());
                }
            }
        }

        // Distributed-slack pack: the single slack/reference bus (BUS_TARGET_PHI active, BUS_TARGET_P
        // inactive — its P is free) provides the mismatch; every OTHER bus with participating generators
        // takes a share of it into its BUS_TARGET_P target. Default balance type PROPORTIONAL_TO_GENERATION_
        // P_MAX: per-bus factor = Σ (maxP / droop) over its participating generators, normalized to sum 1.
        //
        // PARTICIPATING SLACK: if the slack/reference bus ITSELF has participating generators, OLF gives them
        // a share too (it resets the reference generator and redistributes the full mismatch over ALL
        // participants incl. the slack). The GPU has no BUS_TARGET_P at the slack to retarget, but it does not
        // need one: by including the slack's factor in the NORMALIZATION DENOMINATOR (without emitting a row),
        // the non-slack shares shrink to f_i/Σ_all and the slack bus naturally retains the remaining
        // f_slack/Σ_all of the mismatch — exactly OLF's allocation.
        //
        // SATURATION: per-bus headroom/footroom (Σ over participating gens of maxTargetP−targetP / minTargetP−
        // targetP, with OLF's no-sign-change rule, useActiveLimits assumed true = the default) bound how much a
        // bus may absorb. The batch kernel clamps each bus's CUMULATIVE delta to [footroom, headroom]; an
        // over-allocation is clamped and its residual reappears at the slack, redistributed next outer pass to
        // unsaturated buses ∝ their fixed factors — converging to OLF's water-filling fixed point.
        int dsSlackPhiRow = -1;
        double dsSlackTargetP = 0;
        double dsSlackFactor = 0;                                       // slack's own participation (denominator only)
        List<Integer> dsPhiRow = new ArrayList<>();
        List<Double> dsFactorRaw = new ArrayList<>();
        List<Double> dsBaseTargetP = new ArrayList<>();                 // each bus's initial BUS_TARGET_P
        List<Double> dsMaxDelta = new ArrayList<>();                    // headroom ≥ 0
        List<Double> dsMinDelta = new ArrayList<>();                    // footroom ≤ 0
        for (LfBus bus : network.getBuses()) {
            if (!distributedSlack) {                                    // empty pack when the DS outer loop is disabled
                break;
            }
            int bn = bus.getNum();
            if (row(es, bn, AcEquationType.BUS_TARGET_PHI) >= 0) {       // the slack/reference bus
                dsSlackPhiRow = phiCol[bn];
                dsSlackTargetP = bus.getTargetP();
                for (LfGenerator g : bus.getGenerators()) {
                    if (g.isParticipating() && g.getDroop() != 0) {
                        dsSlackFactor += g.getMaxP() / g.getDroop();
                    }
                }
                continue;
            }
            if (row(es, bn, AcEquationType.BUS_TARGET_P) < 0) {
                continue;                                               // no active P balance to retarget
            }
            double factor = 0;
            double headroom = 0;
            double footroom = 0;
            for (LfGenerator g : bus.getGenerators()) {
                if (g.isParticipating() && g.getDroop() != 0) {
                    factor += g.getMaxP() / g.getDroop();
                    double minTp = g.getMinTargetP();
                    double maxTp = g.getMaxTargetP();
                    double tp = g.getTargetP();
                    if (tp < 0) {                                       // OLF: distribution never flips a gen's sign
                        maxTp = Math.min(maxTp, 0);
                    } else {
                        minTp = Math.max(minTp, 0);
                    }
                    headroom += maxTp - tp;
                    footroom += minTp - tp;
                }
            }
            if (factor > 0) {
                dsPhiRow.add(phiCol[bn]);
                dsFactorRaw.add(factor);
                dsBaseTargetP.add(bus.getTargetP());
                dsMaxDelta.add(headroom);
                dsMinDelta.add(footroom);
            }
        }
        double dsSum = dsFactorRaw.stream().mapToDouble(Double::doubleValue).sum() + dsSlackFactor;
        double[] dsFactor = new double[dsPhiRow.size()];
        for (int i = 0; i < dsFactor.length; i++) {
            dsFactor[i] = dsFactorRaw.get(i) / dsSum;
        }
        if (dsSlackPhiRow < 0 || dsPhiRow.isEmpty()) {                  // no slack or nobody to distribute to
            dsPhiRow.clear();
            dsFactor = new double[0];
            dsBaseTargetP.clear();
            dsMaxDelta.clear();
            dsMinDelta.clear();
            dsSlackPhiRow = -1;
        }

        return new GpuAcData(flatD(cbIn, 8), flatI(cbIdx, 10), flatD(obIn, 7), flatI(obIdx, 6),
                flatD(shIn, 2), flatI(shIdx, 3), flatD(hvIn, 5), flatI(hvIdx, 4),
                dqElem, dqSide, dqWeight, dqRow, dqKind, flatD(ziIn, 1), flatI(ziIdx, 11),
                drRow, drCol, drCoef,
                toIntArray(rlVrow), toDoubleArray(rlMinQ), toDoubleArray(rlMaxQ), toDoubleArray(rlLoadQ),
                toDoubleArray(rlVtarget),
                toIntArray(dsPhiRow), dsFactor, dsSlackPhiRow, dsSlackTargetP,
                toDoubleArray(dsBaseTargetP), toDoubleArray(dsMaxDelta), toDoubleArray(dsMinDelta));
    }

    /**
     * Extract what ACTIVATION decides — the per-row power-equation/identity mode and the
     * OLF-column→stable-row target mapping. Rebuilt only when an activation event fired
     * (the structure stays valid across PV→PQ switching). Walks every active equation, so
     * an unsupported equation type ACTIVATED at runtime (e.g. zero-impedance) fails loudly
     * here even though the full structural validation does not re-run.
     */
    public static GpuAcActivation extractActivation(LfNetwork network,
                                                    EquationSystem<AcVariableType, AcEquationType> es, int n) {
        int[] mode = new int[n];
        java.util.Arrays.fill(mode, 1);
        int[] targetMap = new int[n];
        java.util.Arrays.fill(targetMap, -1);
        int nBranches = network.getBranches().size();
        int[] branchRhoCol = branchVarCols(es, nBranches, AcVariableType.BRANCH_RHO1);
        int[] vCol = new int[network.getBuses().size()];
        for (LfBus bus : network.getBuses()) {
            vCol[bus.getNum()] = col(es, bus.getNum(), AcVariableType.BUS_V);
        }
        int[] qRow = computeQRow(network, es, vCol, branchRhoCol);
        for (LfBus bus : network.getBuses()) {
            int p = row(es, bus.getNum(), AcEquationType.BUS_TARGET_P);
            if (p >= 0) {
                targetMap[col(es, bus.getNum(), AcVariableType.BUS_PHI)] = p;
            }
            int q = row(es, bus.getNum(), AcEquationType.BUS_TARGET_Q);
            if (q >= 0) {
                targetMap[qRow[bus.getNum()]] = q;          // Q row = V row, or the controlling rho row
            }
        }
        for (SingleEquation<AcVariableType, AcEquationType> e : es.getIndex().getSortedSingleEquationsToSolve()) {
            // POWER-equation rows (mode stays 1) whose stable row is a non-bus-P/Q variable — assembled by
            // the GPU DISTR_Q / zero-impedance passes; just map the target onto that variable's row.
            AcVariableType powerRowVar = switch (e.getType()) {
                case DISTR_Q -> AcVariableType.BUS_V;        // controller's BUS_V row
                case DISTR_RHO -> AcVariableType.BRANCH_RHO1; // controller's BRANCH_RHO1 row
                case ZERO_V -> AcVariableType.DUMMY_P;       // zero-imp v1−rho·v2 sits on the DUMMY_P row
                case ZERO_PHI -> AcVariableType.DUMMY_Q;     // zero-imp phi1−phi2 sits on the DUMMY_Q row
                default -> null;
            };
            if (powerRowVar != null) {
                targetMap[col(es, e.getElementNum(), powerRowVar)] = e.getColumn();
                continue;
            }
            // IDENTITY rows (mode 0): the device row-mode mask writes diag 1 and F = x − target.
            AcVariableType vt = switch (e.getType()) {
                case BUS_TARGET_V -> AcVariableType.BUS_V;
                case BUS_TARGET_PHI -> AcVariableType.BUS_PHI;
                case BRANCH_TARGET_RHO1 -> AcVariableType.BRANCH_RHO1;   // ratio held constant: r1−target identity
                case DUMMY_TARGET_P -> AcVariableType.DUMMY_P;           // non-tree zero-imp: dummyP held = 0
                case DUMMY_TARGET_Q -> AcVariableType.DUMMY_Q;           // non-tree zero-imp: dummyQ held = 0
                case BUS_TARGET_P, BUS_TARGET_Q -> null;     // power rows, mapped above
                default -> throw new PowsyblException("GPU AC solver: unsupported equation type "
                        + e.getType() + " activated");
            };
            if (vt != null) {
                int r = col(es, e.getElementNum(), vt);
                mode[r] = 0;
                targetMap[r] = e.getColumn();
            }
        }
        return new GpuAcActivation(mode, targetMap);
    }

    /**
     * Per-row variable kind for the MAX_VOLTAGE_CHANGE device step scaling: {@code 0} = BUS_PHI (clamp
     * the angle step to maxDphi), {@code 1} = BUS_V (clamp the magnitude step to maxDv), {@code 2} = any
     * other variable (BRANCH_RHO1, …) — scaled by the global step factor but never itself a clamp source,
     * mirroring {@code MaxVoltageChangeStateVectorScaling} (which only inspects BUS_V / BUS_PHI). Indexed by
     * the variable row, which is the column index of the Newton step.
     */
    public static int[] extractVarType(EquationSystem<AcVariableType, AcEquationType> es, int n) {
        int[] varType = new int[n];
        java.util.Arrays.fill(varType, 2);
        for (Variable<AcVariableType> v : es.getIndex().getSortedVariablesToFind()) {
            varType[v.getRow()] = switch (v.getType()) {
                case BUS_PHI -> 0;
                case BUS_V -> 1;
                default -> 2;
            };
        }
        return varType;
    }

    private static void validate(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es) {
        for (Variable<AcVariableType> v : es.getIndex().getSortedVariablesToFind()) {
            // BRANCH_RHO1 (continuous transformer voltage control) is supported for a single
            // transformer locally controlling one of its own terminal buses; computeQRow throws
            // on the unsupported shapes (phase control, remote / multi-transformer DISTR_RHO).
            AcVariableType vtype = v.getType();
            if (vtype != AcVariableType.BUS_V && vtype != AcVariableType.BUS_PHI
                    && vtype != AcVariableType.BRANCH_RHO1
                    && vtype != AcVariableType.DUMMY_P && vtype != AcVariableType.DUMMY_Q) {
                throw new PowsyblException("GPU AC solver: unsupported variable type " + vtype
                        + " (only plain AC power flow + transformer voltage control + zero-impedance "
                        + "branches are supported)");
            }
        }
        // vectorized (equation-array) systems: the arrays carry the closed-branch terms
        // (same physics as the GPU closed-branch kernel); per-element scalar terms
        // (open-branch, shunt, HVDC) attach to the array elements
        for (EquationArray<AcVariableType, AcEquationType> arr : es.getIndex().getSortedEquationArraysToSolve()) {
            if (arr.getType() != AcEquationType.BUS_TARGET_P && arr.getType() != AcEquationType.BUS_TARGET_Q) {
                throw new PowsyblException("GPU AC solver: unsupported equation-array type " + arr.getType());
            }
            for (EquationTermArray<AcVariableType, AcEquationType> ta : arr.getTermArrays()) {
                String name = ((EquationTermArray.EquationTermArrayElementImpl<AcVariableType, AcEquationType>) ta.getElement(0))
                        .getEvaluator().getName();
                if (!SUPPORTED_ARRAY_EVALUATORS.contains(name)) {
                    throw new PowsyblException("GPU AC solver: unsupported equation-term array '" + name
                            + "' in " + arr.getType());
                }
            }
            for (int elementNum = 0; elementNum < arr.getElementCount(); elementNum++) {
                for (SingleEquationTerm<AcVariableType, AcEquationType> t : arr.getSingleEquationTerms(elementNum)) {
                    // a VariableEquationTerm is a zero-impedance DUMMY_P/Q coupling term (GPU zi family)
                    if (t.isActive() && !SUPPORTED_PQ_TERMS.contains(t.getClass())
                            && !isDummyCouplingTerm(t)) {
                        throw new PowsyblException("GPU AC solver: unsupported equation term "
                                + t.getClass().getSimpleName() + " in " + arr.getType());
                    }
                }
            }
        }
        for (SingleEquation<AcVariableType, AcEquationType> e : es.getIndex().getSortedSingleEquationsToSolve()) {
            switch (e.getType()) {
                case BUS_TARGET_P, BUS_TARGET_Q -> {
                    for (SingleEquationTerm<AcVariableType, AcEquationType> t : e.getTerms()) {
                        // a VariableEquationTerm here is a zero-impedance branch's DUMMY_P/DUMMY_Q coupling
                        // term, assembled by the GPU zero-impedance family — accept it.
                        if (t.isActive() && !SUPPORTED_PQ_TERMS.contains(t.getClass())
                                && !isDummyCouplingTerm(t)) {
                            throw new PowsyblException("GPU AC solver: unsupported equation term "
                                    + t.getClass().getSimpleName() + " in " + e.getType());
                        }
                    }
                }
                case DISTR_Q -> {
                    // reactive-distribution row: OLF sums per-controller reactive terms wrapped in
                    // MultiplyByScalarEquationTerm. The GPU re-assembles it from network topology, so the
                    // real coverage guard is buildDistrQContributions (it throws on shunt / open-branch /
                    // remote shapes it cannot wire). Accept the equation here.
                }
                case ZERO_V, ZERO_PHI, DUMMY_TARGET_P, DUMMY_TARGET_Q -> {
                    // zero-impedance branch coupling (v1−rho·v2 / phi1−phi2) and the held-dummy identities —
                    // assembled by the GPU zero-impedance family from network topology. Accept.
                }
                case DISTR_RHO -> {
                    // transformer ratio distribution ((1/count)·Σ r1 − r1_k) — a linear coupling of the
                    // controller BRANCH_RHO1 variables, re-assembled by the GPU DISTR_RHO family. Accept.
                }
                case BUS_TARGET_V, BUS_TARGET_PHI, BRANCH_TARGET_RHO1 -> {
                    // identity rows on a single variable (v−target, phi−target, or r1−target when a
                    // transformer ratio is held constant) — applied device-side by the row-mode mask
                    for (SingleEquationTerm<AcVariableType, AcEquationType> t : e.getTerms()) {
                        if (t.isActive() && !(t instanceof VariableEquationTerm)) {
                            throw new PowsyblException("GPU AC solver: unsupported equation term "
                                    + t.getClass().getSimpleName() + " in " + e.getType());
                        }
                    }
                }
                default -> throw new PowsyblException("GPU AC solver: unsupported equation type " + e.getType());
            }
        }
        // NOTE: a branch flagged disconnection-allowed (a security-analysis contingency candidate) is NOT
        // rejected. Disconnection is a discrete event BETWEEN solves (a contingency), never a switch WITHIN
        // a single Newton/outer-loop sequence — so for any one fixed configuration the extracted pattern is
        // correct. A currently-closed candidate is modelled as a normal closed branch; when a contingency
        // outages it, its terms deactivate and it drops out of the index packs, so the structure changes and
        // GpuNewtonRaphson rebuilds its context (sameStructureAs is false) while the batch masks it
        // per-scenario. The throw that used to live here was therefore over-conservative.
    }

    /** A P/Q-balance term over only DUMMY_P/DUMMY_Q variables — a zero-impedance coupling term the GPU
     *  zero-impedance family assembles (its wrapper class may be a Variable or a MultiplyByScalar term). */
    private static boolean isDummyCouplingTerm(SingleEquationTerm<AcVariableType, AcEquationType> t) {
        return !t.getVariables().isEmpty() && t.getVariables().stream()
                .allMatch(v -> v.getType() == AcVariableType.DUMMY_P || v.getType() == AcVariableType.DUMMY_Q);
    }

    private static int col(EquationSystem<AcVariableType, AcEquationType> es, int bus, AcVariableType t) {
        return es.getVariableSet().getVariable(bus, t).getRow();
    }

    private static int row(EquationSystem<AcVariableType, AcEquationType> es, int bus, AcEquationType t) {
        return es.getEquation(bus, t).map(e -> e.getColumn()).orElse(-1);
    }

    /** Variable rows of every {@code type} variable, indexed by its element (branch) number; -1 when absent. */
    private static int[] branchVarCols(EquationSystem<AcVariableType, AcEquationType> es, int nBranches,
                                       AcVariableType type) {
        int[] cols = new int[nBranches];
        java.util.Arrays.fill(cols, -1);
        for (Variable<AcVariableType> v : es.getIndex().getSortedVariablesToFind()) {
            if (v.getType() == type) {
                cols[v.getElementNum()] = v.getRow();
            }
        }
        return cols;
    }

    /**
     * The stable Q-balance row of each bus (indexed by bus number). Normally a bus's reactive
     * balance sits on its own BUS_V variable row. When a bus is voltage-controlled by a transformer
     * — signalled by BOTH its BUS_TARGET_V and BUS_TARGET_Q being active (a generator PV bus has V
     * active but Q inactive, so it is excluded) — the BUS_V row instead carries the v−target identity,
     * and the reactive balance moves onto the controlling transformer's BRANCH_RHO1 column, which is
     * the extra degree of freedom. That rho column is then the strong diagonal of the Q row.
     *
     * <p>Only the simple local single-transformer case is supported; throws on shunt voltage control,
     * remote control, and ratio distribution across several transformers (DISTR_RHO).
     */
    private static int[] computeQRow(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es,
                                     int[] vCol, int[] branchRhoCol) {
        int[] qRow = vCol.clone();
        for (LfBus bus : network.getBuses()) {
            int bn = bus.getNum();
            // A remote generator voltage controller bus has its BUS_TARGET_Q deactivated — its reactive
            // injection feeds the DISTR_Q rows, never its own BUS_V row — so suppress its per-bus Q scatter.
            if (bus.getGeneratorVoltageControl().isPresent() && !bus.isGeneratorVoltageControlled()) {
                qRow[bn] = -1;
                continue;
            }
            boolean vActive = row(es, bn, AcEquationType.BUS_TARGET_V) >= 0;
            boolean qActive = row(es, bn, AcEquationType.BUS_TARGET_Q) >= 0;
            if (!(vActive && qActive)) {
                continue;                                   // plain PQ / generator PV bus: Q stays on its V row
            }
            // A pilot bus controlled by REMOTE generators: its reactive balance moves onto the free
            // controller's BUS_V row (the controller whose DISTR_Q is inactive — the extra freedom).
            GeneratorVoltageControl gvc = bus.getGeneratorVoltageControl()
                    .filter(c -> c.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .filter(c -> bus.isGeneratorVoltageControlled() && !c.isLocalControl())
                    .orElse(null);
            if (gvc != null) {
                LfBus freeController = gvc.getMergedControllerElements().stream()
                        .filter(c -> row(es, c.getNum(), AcEquationType.DISTR_Q) < 0)
                        .findFirst()
                        .orElseThrow(() -> new PowsyblException("GPU AC solver: remote generator voltage control "
                                + "of bus " + bus.getId() + " has no free controller (all DISTR_Q active)"));
                qRow[bn] = vCol[freeController.getNum()];
                continue;
            }
            // A pilot bus voltage-controlled by transformers: its reactive balance moves onto the FREE
            // controller's BRANCH_RHO1 row (the controller whose DISTR_RHO is inactive — the extra freedom).
            // With one local transformer this is item 4; with several it is ratio distribution (DISTR_RHO),
            // and the controller may even be remote (then the pilot-Q diagonal is zero → cuDSS pivoting).
            TransformerVoltageControl tvc = bus.getTransformerVoltageControl()
                    .filter(c -> c.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .orElseThrow(() -> new PowsyblException("GPU AC solver: bus " + bus.getId()
                            + " has active voltage and reactive targets but no supported voltage control "
                            + "(shunt voltage control is not supported)"));
            LfBranch freeController = tvc.getMergedControllerElements().stream()
                    .filter(c -> row(es, c.getNum(), AcEquationType.DISTR_RHO) < 0)
                    .findFirst()
                    .orElseThrow(() -> new PowsyblException("GPU AC solver: transformer voltage control of bus "
                            + bus.getId() + " has no free controller (all DISTR_RHO active)"));
            int rhoRow = branchRhoCol[freeController.getNum()];
            if (rhoRow < 0) {
                throw new PowsyblException("GPU AC solver: controlling transformer " + freeController.getId()
                        + " has no BRANCH_RHO1 variable");
            }
            qRow[bn] = rhoRow;
        }
        return qRow;
    }

    /**
     * Build the weighted closed-branch reactive contributions of every active DISTR_Q row (remote
     * generator voltage control). For a control group, the row at controller {@code Gk}'s BUS_V row is
     * {@code DISTR_Q_k = qPct_k·Σ_j q_Gj − q_Gk}, so each controller {@code Gj}'s reactive injection
     * (the q1/q2 flow of each branch incident to {@code Gj}) contributes with weight
     * {@code qPct_k − (j==k ? 1 : 0)}. A controller's reactive injection is its closed-branch q1/q2 (kind 0),
     * a fixed shunt's q (kind 1), a half-open incident branch's connected-side q (kind 2), or a zero-impedance
     * spanning-tree branch's linear DUMMY_Q variable term (kind 3) — mirroring the terms {@code createReactiveTerms}
     * sums. The pilot bus's own reactive balance is handled by qRow (routed to the free controller's BUS_V row);
     * the controllers' own per-bus Q scatter is suppressed there too.
     */
    private static void buildDistrQContributions(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es,
                                                 int[] vCol, int[] cbElemOf, int[] shElemOf, int[] obElemOf,
                                                 int[] dummyQCol, List<int[]> out, List<Double> weights) {
        for (LfBus pilot : network.getBuses()) {
            GeneratorVoltageControl gvc = pilot.getGeneratorVoltageControl()
                    .filter(c -> c.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .filter(c -> pilot.isGeneratorVoltageControlled() && !c.isLocalControl())
                    .orElse(null);
            if (gvc == null) {
                continue;
            }
            List<LfBus> controllers = gvc.getMergedControllerElements();
            for (LfBus gk : controllers) {
                if (row(es, gk.getNum(), AcEquationType.DISTR_Q) < 0) {
                    continue;                                // the free controller carries the pilot's Q, no DISTR_Q
                }
                int dqRow = vCol[gk.getNum()];
                double qpk = gk.getRemoteControlReactivePercent();
                for (LfBus gj : controllers) {
                    double weight = qpk - (gj == gk ? 1.0 : 0.0);
                    if (gj.getControllerShunt().isPresent()) {
                        throw new PowsyblException("GPU AC solver: DISTR_Q controller " + gj.getId()
                                + " has a voltage-controlling shunt (SHUNT_B) reactive contribution (not supported)");
                    }
                    if (gj.getShunt().isPresent()) {         // a fixed shunt's reactive joins the q sum
                        out.add(new int[] {shElemOf[gj.getNum()], 0, dqRow, 1});
                        weights.add(weight);
                    }
                    for (LfBranch brj : gj.getBranches()) {
                        if (brj.isDisabled()) {
                            continue;
                        }
                        int side = brj.getBus1() == gj ? 0 : 1;
                        int e = cbElemOf[brj.getNum()];
                        if (e >= 0) {                        // closed branch: weighted q1/q2 (kind 0)
                            out.add(new int[] {e, side, dqRow, 0});
                            weights.add(weight);
                            continue;
                        }
                        int oe = obElemOf[brj.getNum()];
                        if (oe >= 0) {                       // half-open branch incident on the controller:
                            // its connected-side reactive flow (shunt + series) joins the q sum (kind 2)
                            out.add(new int[] {oe, side, dqRow, 2});
                            weights.add(weight);
                            continue;
                        }
                        if (brj.isZeroImpedance(LoadFlowModel.AC)) {
                            // a zero-impedance coupler: only a spanning-tree edge carries reactive flow, as the
                            // linear DUMMY_Q variable (+1 if the controller is bus1, −1 if bus2) — exactly the
                            // term createReactiveTerms adds. Non-tree edges contribute nothing (matches OLF).
                            if (!brj.isSpanningTreeEdge(LoadFlowModel.AC)) {
                                continue;
                            }
                            int dqCol = dummyQCol[brj.getNum()];
                            if (dqCol < 0) {
                                throw new PowsyblException("GPU AC solver: DISTR_Q controller " + gj.getId()
                                        + " has a zero-impedance branch " + brj.getId() + " without a DUMMY_Q variable");
                            }
                            out.add(new int[] {dqCol, side, dqRow, 3});  // elem slot holds the DUMMY_Q var column
                            weights.add(weight * (side == 0 ? 1.0 : -1.0));
                            continue;
                        }
                        throw new PowsyblException("GPU AC solver: DISTR_Q controller " + gj.getId()
                                + " has an unsupported branch " + brj.getId() + " reactive contribution");
                    }
                }
            }
        }
    }

    /**
     * Build the LINEAR DISTR_RHO contributions (ratio distribution when several transformers control one
     * bus). A controller branch {@code Gk} with an active DISTR_RHO equation gets, on its BRANCH_RHO1 row,
     * {@code DISTR_RHO_k = (1/count)·Σ_j r1_j − r1_k}, i.e. each controller {@code Gj}'s ratio column
     * contributes with coefficient {@code 1/count − (j==k ? 1 : 0)}, where {@code count} is the number of
     * enabled controllers. The free controller (no DISTR_RHO) carries the pilot's Q balance via qRow.
     */
    private static void buildDistrRhoContributions(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> es,
                                                   int[] branchRhoCol, List<int[]> out, List<Double> coefs) {
        for (LfBus pilot : network.getBuses()) {
            TransformerVoltageControl tvc = pilot.getTransformerVoltageControl()
                    .filter(c -> c.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .filter(c -> pilot.isTransformerVoltageControlled())
                    .orElse(null);
            if (tvc == null) {
                continue;
            }
            List<LfBranch> controllers = tvc.getMergedControllerElements();
            long count = controllers.stream().filter(c -> !c.isDisabled()).count();
            for (LfBranch gk : controllers) {
                if (row(es, gk.getNum(), AcEquationType.DISTR_RHO) < 0) {
                    continue;                                // the free controller carries the pilot's Q, no DISTR_RHO
                }
                int drRow = branchRhoCol[gk.getNum()];
                for (LfBranch gj : controllers) {
                    if (gj.isDisabled()) {
                        continue;
                    }
                    double coef = 1.0 / count - (gj == gk ? 1.0 : 0.0);
                    out.add(new int[] {drRow, branchRhoCol[gj.getNum()]});
                    coefs.add(coef);
                }
            }
        }
    }

    /**
     * The transformers that participate in a ratio-distribution (DISTR_RHO) group. Outaging one of these
     * REFORMS the {@code 1/count} coefficients (OLF redistributes the ratio across the remaining controllers),
     * which the fixed batch pattern cannot represent — so the batched N-1 path rejects such a contingency to
     * the CPU. Returns the controllers of every transformer-voltage-controlled pilot whose group has more than
     * one enabled member (a single local controller is item 4, not DISTR_RHO, and is mask-safe).
     */
    public static Set<LfBranch> distrRhoControllerBranches(LfNetwork network,
                                                           EquationSystem<AcVariableType, AcEquationType> es) {
        Set<LfBranch> controllers = new java.util.HashSet<>();
        for (LfBus pilot : network.getBuses()) {
            TransformerVoltageControl tvc = pilot.getTransformerVoltageControl()
                    .filter(c -> c.getMergeStatus() == VoltageControl.MergeStatus.MAIN)
                    .filter(c -> pilot.isTransformerVoltageControlled())
                    .orElse(null);
            if (tvc == null) {
                continue;
            }
            List<LfBranch> group = tvc.getMergedControllerElements();
            boolean distrRho = group.stream().anyMatch(c -> row(es, c.getNum(), AcEquationType.DISTR_RHO) >= 0);
            if (distrRho) {
                controllers.addAll(group);
            }
        }
        return controllers;
    }

    private static double[] flatD(List<double[]> rows, int stride) {
        double[] out = new double[rows.size() * stride];
        for (int i = 0; i < rows.size(); i++) {
            System.arraycopy(rows.get(i), 0, out, i * stride, stride);
        }
        return out;
    }

    private static int[] flatI(List<int[]> rows, int stride) {
        int[] out = new int[rows.size() * stride];
        for (int i = 0; i < rows.size(); i++) {
            System.arraycopy(rows.get(i), 0, out, i * stride, stride);
        }
        return out;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static double[] toDoubleArray(List<Double> list) {
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
