"""Generate a reproducible non-islanding single-branch N-1 contingency set for the
test6 GPU batched security-analysis benchmark, straight from the IIDM network.

Loads case9241pegase (9241 buses, 16049 branches), keys each branch on its unordered
endpoint bus-number pair (IIDM ids encode 1-based bus numbers: LINE-i-j / TWT-i-j,
buses BUS-n), drops parallel pairs (ambiguous) and bridges (a bridge outage islands
the grid — the GPU batched path rejects those to the CPU), and writes one IIDM branch
id per line.

Writes:
  contingencies.txt   one IIDM branch id per line   (consumed by GpuBatchedN1FixedIterTest via OLF_GPU_CONT_FILE)

Usage: python gen_contingencies.py <case9241pegase.xiidm.gz> <out_dir> [N]
"""
import sys
import gzip
import os
import re
import warnings

warnings.filterwarnings("ignore")

iidm_path = sys.argv[1]
out_dir = sys.argv[2]
N = int(sys.argv[3]) if len(sys.argv) > 3 else 500

# ---- 1. IIDM: id -> bus pair (from bus1/bus2 attributes, robust to id format) ----
with gzip.open(iidm_path, "rt") as f:
    xml = f.read()
tag_re = re.compile(r'<iidm:(line|twoWindingsTransformer)\b([^>]*?)/?>', re.S)
id_re = re.compile(r'\bid="([^"]+)"')
b1_re = re.compile(r'\bbus1="BUS-(\d+)"')
b2_re = re.compile(r'\bbus2="BUS-(\d+)"')
pair_to_id = {}
pair_count = {}
for _kind, attrs in tag_re.findall(xml):
    mid, m1, m2 = id_re.search(attrs), b1_re.search(attrs), b2_re.search(attrs)
    if not (mid and m1 and m2):
        continue          # a disconnected branch (no bus1/bus2) — skip
    pair = (min(int(m1.group(1)), int(m2.group(1))), max(int(m1.group(1)), int(m2.group(1))))
    pair_count[pair] = pair_count.get(pair, 0) + 1
    pair_to_id[pair] = mid.group(1)
print(f"IIDM: {len(pair_to_id)} unique-pair branches "
      f"({sum(1 for v in pair_count.values() if v > 1)} pairs are parallel)")

# ---- 2. bridges (islanding) on the simple connectivity graph of the pairs ----
import networkx as nx
g = nx.Graph()
g.add_edges_from(pair_to_id)
bridges = set(frozenset(e) for e in nx.bridges(g))
print(f"graph: {g.number_of_nodes()} buses, {g.number_of_edges()} unique pairs, {len(bridges)} bridges")

# ---- 3. keep unique-pair, non-bridge (non-islanding) branches ----
selected = []
for pair in sorted(pair_to_id):
    if pair_count[pair] > 1:
        continue                                   # parallel — ambiguous
    if frozenset(pair) in bridges:
        continue                                   # islanding
    selected.append(pair_to_id[pair])
    if len(selected) >= N:
        break
print(f"non-islanding, unique-pair contingencies: {len(selected)} (cap {N})")

# ---- 4. write the id list ----
os.makedirs(out_dir, exist_ok=True)
out = os.path.join(out_dir, "contingencies.txt")
with open(out, "w") as f:
    f.write("\n".join(selected) + "\n")
print(f"wrote {out}")
