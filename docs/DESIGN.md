# Original v0.1 design notes for a portfolio walkthrough

Historical rationale. See [v0.2 changes](UPGRADE-0.2.md) for the current scan, evidence, incident and export behavior.

## Problem and first release

A server owner sees low TPS but needs a next step, not another graph. Lag Doctor measures a small set of signals, prints the observations behind its suggestions, and helps test whether a change improved tick performance. Paper Java Edition is the only platform in this MVP. The plugin is free under MIT and works offline after installation.

The key product decision is to separate **observed slowdown** from **suspected cause**. Slow tick timing is direct evidence of a tick-budget problem. Counts of entities and chunk events are only context. GC collection time is a JVM counter with collector-dependent semantics. Consequently, cause hypotheses have lower confidence than slowdown detection, and an unresolved diagnosis is an expected useful result.

## Data path

1. `ServerTickEndEvent` supplies tick duration in milliseconds. One scalar is stored per tick.
2. `ChunkLoadEvent` increments load/new-chunk counters; it never requests a chunk load.
3. Every 200 ticks, the collector reads heap and GC counters plus Paper's aggregate world entity/chunk counts on the server thread.
4. A fixed buffer is sorted once to compute a nearest-rank p95. The immutable window goes into a bounded deque.
5. Commands run pure diagnostic rules against window snapshots. The baseline is another immutable snapshot; comparison rejects overlap.
6. An export assembles text on the server thread, then writes only that snapshot asynchronously. At most one export is in flight, and ten fixed filename slots bound retained report data.

No internal Minecraft implementation classes, packet interception, spark integration API, per-entity enumeration, or remote services are required. The classic Bukkit plugin descriptor is supported by Paper, and Paper-specific public APIs supply the metrics. Folia is intentionally not declared supported because this collector assumes a single main server thread.

## Rules and limitations

| Signal in a slow window | Threshold | Meaning |
|---|---|---|
| GC time / elapsed time | ≥5% | JVM collection activity coincided with the issue; not precise pause attribution |
| New chunk events / seconds | ≥2 | Newly generated chunks became loaded; not a generation-time measurement |
| All chunk load events / seconds | ≥10 | Chunk turnover deserves investigation |
| Loaded entity count | ≥1000 | Large population, including inactive entities; not entity CPU cost |
| Sampled used/max heap | ≥85%, independent of slowdown | Watch post-GC trends; not a leak diagnosis |

These thresholds are intentionally conservative, transparent MVP heuristics. They need testing across actual workloads before any claim of detection accuracy. No rule identifies a specific plugin, farm, world or player. A sampled count can miss activity earlier in its window. JVM pauses, server pauses and host scheduling can also affect elapsed-time TPS differently from measured tick duration. Startup windows and intentional demos must be interpreted accordingly.

Recommendations ask owners to measure one reversible change, such as pausing a known farm or reducing a distance setting, with comparable activity. Automatic fixes, entity deletion, player punishment, RAM purchasing advice, and accusations of malicious behavior are outside scope.

## Demo versus evidence

The local demo deliberately delays scheduled tasks to create a deterministic symptom. It validates timing, alerts, labels and comparisons. It does **not** validate GC, entity or chunk cause attribution. Those rule branches have synthetic unit tests; realistic workload validation remains future work. This distinction is part of the product's credibility.

## Useful next milestones

- Validate each new Paper release independently; 0.3.0 targets pinned Paper 26.3 beta build 143. Do not infer support for other 26.x versions.
- Collect consented, anonymized real-world profiles and compare hypotheses to actual hot stacks.
- Benchmark overhead with large world/entity counts and several plugins.
- Add world-level investigations only with bounded collection and explicit privacy decisions.
- Improve readable presentation and compare workload context more deeply before expanding the rule list.
