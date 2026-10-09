# Lag Doctor 0.2: deeper investigations

## Upgrade

Stop Paper, move the old LagDoctor JAR outside `plugins/`, copy `build/libs/LagDoctor-0.2.0.jar` into `plugins/`, and start Paper. Keep the existing config. Never leave two LagDoctor versions in the plugin directory. The build still targets Paper 1.21.11 and Java 21 bytecode; this release does not add a 26.x compatibility claim.

## New workflow

1. During an issue, run `/ld report`. The report distinguishes a measured slowdown from possible explanations.
2. Run `/ld scan` if entities warrant investigation. It samples loaded chunks over several ticks and reports up to ten concentrations with world, chunk coordinates, block X/Z, entity types, and chunk load level. `/ld hotspots` shows the last completed scan, with its timestamp. These are counts, not ranked CPU costs.
3. Use `/ld incidents` to see recent slowdown episodes even after they leave the rolling report. `/ld incidents latest` includes retained diagnostic evidence. Two healthy windows confirm recovery; one healthy window followed by renewed lag remains one incident. History is in memory, retains twelve completed incidents plus the current one, and resets on restart. Each incident keeps up to six most recent slow windows.
4. Save `/ld baseline`, make one reversible change, wait for a full new report, then `/ld compare`. Comparisons now warn about differences in entities, chunks and exploration rates as well as players. They show slow-tick percentage-point changes and whether the before/after window ranges overlap.
5. Run `/ld export` and open the resulting `.html` in a browser. It includes metrics, a tick-pattern chart, window evidence, recommendations, comparisons, incident history and the last entity scan. A text companion is also written.

## Stronger evidence without false certainty

Signals still need to cross transparent thresholds. When at least two healthy and two slow windows are available, their averages are contrasted. A signal averaging at least twice as high during slow windows, with threshold hits in at least half of those windows, is labelled a stronger association. A population that remains high in healthy windows gets weak contrast. Too few windows gets limited evidence. These grades are not probabilities, statistical significance or proof of causation. A known demo suppresses cause ranking so incidental activity is not mistaken for the injected delay.

Comparison warnings use a 25% change with absolute floors: 100 entities, 50 loaded chunks, 2 chunk loads/sec, or 1 new chunk/sec. These are sampled context checks, not workload normalization. A smaller change can still matter. Overlapping data remains rejected, and matching counts do not guarantee matching behavior.

## Scan limits and privacy

Scans require the existing operator permission and have a 60-second global cooldown. They select up to 256 chunks, evenly spaced through the API's loaded-chunk arrays and divided equally across up to sixteen worlds. This is not randomized or comprehensive sampling. A world over 4,096 loaded chunks or 20,000 entities is omitted to limit snapshot work; use spark for those worlds. Worlds with spare quota do not donate it to other worlds.

Each scheduled tick performs one world selection OR one chunk inspection. A 30-second wall-time limit ends a partial scan. A step taking more than 5 ms ends further collection; an individual API call cannot be preempted, so this is not a hard latency guarantee. Loaded state is rechecked on the main thread before reading entities; entity data that is not already loaded is skipped. The scanner does not load/generate chunks, spawn entities, modify blocks, teleport players, or automatically change anything. `/ld scan stop` cancels a scan and keeps the previous completed result.

Precise locations are shown to authorized admins in scan output. **Default text and HTML exports omit world names and coordinates.** Use `/ld export locations` only when you want them included. No player identifiers are collected. Reports are self-contained and script-free, make no remote requests, escape text, and retain ten rotating text/HTML pairs. Existing exports containing locations remain on disk until overwritten or removed by you.

No scan is automatically started when lag occurs. This keeps the continuous collector lightweight; the optional scan costs are reported separately. Scans can affect the tick being measured, and results are gathered over time rather than at one atomic instant. A later scan cannot prove what caused an earlier incident.

## Verified public API basis

The scanner uses Paper's documented [Chunk entity-loading state and entity enumeration](https://jd.papermc.io/paper/1.21.11/org/bukkit/Chunk.html) and [World loaded-chunk access](https://jd.papermc.io/paper/1.21.11/org/bukkit/World.html). In particular, it checks `isEntitiesLoaded()` before `getEntities()` because the latter may otherwise force entity loading. All these accesses remain on the server thread.
