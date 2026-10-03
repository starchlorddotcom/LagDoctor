# Lag Doctor

Created by **William Liu** ([starchlorddotcom](https://github.com/starchlorddotcom)).

A free, local-first Paper plugin that turns server slowdowns into evidence and next steps. Version 0.3.0 is a deliberately narrow portfolio MVP: recommendations, before/after comparisons, and a repeatable solo demo. No account, hosted service, subscription, telemetry, player punishment, or automatic world changes.

New in 0.3: Paper 26.3 / Java 25 support. See 26.3 upgrade guide (`docs/UPGRADE-0.3.md` in the source ZIP). Developed with substantial AI assistance.

Features from 0.2: healthy/slow evidence contrast, chunk-level entity scans, incident history, workload-aware comparisons and offline HTML reports. See upgrade and investigation guide (`docs/UPGRADE-0.2.md` in the source ZIP).

## Download

- **Paper 26.3 / Java 25+: [Lag Doctor 0.3.0 Beta](https://github.com/starchlorddotcom/LagDoctor/releases/tag/v0.3.0)**
- **Paper 1.21.11 / Java 21+: [Lag Doctor 0.2.0 Beta](https://github.com/starchlorddotcom/LagDoctor/releases/tag/v0.2.0)**
- [Hangar plugin listing](https://hangar.papermc.io/starchlorddotcom/LagDoctor)

Download the plugin JAR and the matching complete source ZIP from the release assets. The repository root still contains the original 0.2.0 distribution files; use the releases above for 0.3.0. The named `LagDoctor-0.3.0-source.zip` contains the Java source, Gradle wrapper, tests, documentation and example reports. Extract it before building or opening the documentation paths below. GitHub's automatic Source code archives are not the complete 0.3.0 package.

## Install

Target: **Paper 26.3, Java 25+**. Built against pinned Paper API `26.3.build.143-beta` with Java 25 bytecode. Paper 26.3 is currently beta. For Paper 1.21.11 use Lag Doctor 0.2.0 instead. Spigot, Folia, Bedrock, modpacks and other Minecraft versions are not supported in this release. Do not change an existing world's Minecraft version just to try this plugin; use the disposable local demo instead.

1. Build with JDK 25 and Gradle 9.2.1: `./gradlew test build` (`gradlew.bat` on Windows).
2. Stop the server, remove any older LagDoctor JAR, and copy `build/libs/LagDoctor-0.3.0.jar` to your Paper server's `plugins/` directory.
3. Restart the server. Wait about a minute for a full report.
4. Run `/lagdoctor report` as an operator, or `lagdoctor report` in the console. Alias: `/ld`. Permission: `lagdoctor.admin` (default: operators).

## Use

| Command | Result |
|---|---|
| `/ld report` | Recent tick performance, per-window evidence, explanations and safe next steps |
| `/ld baseline` | Save a full report in memory before changing one thing |
| `/ld compare` | Compare the baseline with a complete set of newer windows; overlapping data is rejected |
| `/ld scan` / `/ld scan stop` | Start or cancel a bounded scan of already-loaded entity chunks |
| `/ld hotspots` | Show the last scan with admin-only world/chunk locations |
| `/ld incidents [latest]` | Recent episodes and optionally the latest episode's retained evidence |
| `/ld export [locations]` | Write text + HTML reports; locations excluded unless requested |

Exports are text/HTML pairs in ten rotating slots; keep a copy of results you want to retain. Baselines are shared across admins and reset on restart or replacement. Default exports contain counts, times and server version, but no player identifiers, world names or coordinates. `/ld export locations` explicitly includes scan locations; admin scan output shows them directly. Nothing is uploaded. If you run spark, its report/sharing behavior is separate.

Three consecutive slow windows trigger a console notification with a five-minute cooldown. `config.yml` controls alerts and report length (3–30 windows; default 6). Restart after config edits. A window is **200 observed ticks**, approximately ten seconds at 20 TPS and longer during lag. Reports show actual elapsed time. Empty servers may pause unless configured otherwise.

## What the evidence means

Tick duration comes from Paper's tick-end event. A window is slow if its mean exceeds 50 ms, or at least 10% of ticks exceed 50 ms. Reports include mean, maximum, slow-tick fraction, and the **worst per-window p95**, not a falsely combined report p95. Effective TPS is observed ticks divided by elapsed time, capped at 20.

Rules look for coincident GC activity, new chunk activity, chunk loading, or a large loaded entity population. Evidence is ranked using healthy/slow contrast when sufficient windows exist; constant background populations are down-ranked. Confidence is explicitly limited: counts do not measure CPU cost; GC counters do not precisely measure pauses; a high heap sample is not a leak diagnosis. Thresholds are initial heuristics, not universal server limits. Unexplained slowdowns are reported as unresolved. Lag Doctor does not establish malicious intent, identify guilty players/plugins, diagnose hardware conclusively, or measure client FPS/network latency.

Paper already bundles spark. Use `/spark profiler start --timeout 60` during a slowdown to investigate main-thread stacks, then use Lag Doctor to compare a single controlled change. There is no dependency on undocumented spark APIs.

## Private solo demo

See the local demo guide (`docs/LOCAL-DEMO.md` in the source ZIP) for setup, two bounded lag scenarios, expected results, and a before/after walkthrough. No public server or other players needed.

## Implementation and verification

The tick listener writes to a fixed 200-value buffer. Every 200 ticks it sorts that buffer, reads JVM counters and Paper world counts, and retains at most 30 aggregate windows. The continuous collector does not iterate entities, inspect inventories, load chunks, or profile stack traces. The opt-in `/ld scan` separately inspects bounded samples of already-loaded entity chunks; see the upgrade guide for limits and measured cost. Exports run asynchronously from immutable snapshots. This design limits overhead; it is not a production-scale overhead benchmark.

`./gradlew test` exercises aggregation, time normalization, reset behavior, diagnostic thresholds, healthy/slow contrasts, uncertain/unresolved diagnoses, workload comparisons, incident recovery/retention, ranking, export escaping and location redaction. See validation notes (`docs/VALIDATION.md` in the source ZIP) for actual build/runtime results and limitations.

## Verified API references

- [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/)
- [Paper 26.3 tick-end event](https://jd.papermc.io/paper/26.3/com/destroystokyo/paper/event/server/ServerTickEndEvent.html)
- [Paper 26.3 World API](https://jd.papermc.io/paper/26.3/org/bukkit/World.html)
- [Paper's bundled spark and profiling guide](https://docs.papermc.io/paper/profiling/)
- [Paper downloads service](https://docs.papermc.io/misc/downloads-service/)

See design notes (`docs/DESIGN.md` in the source ZIP) for the product rationale, data path and next milestones.

License: MIT; see LICENSE. Minecraft and Paper are separate projects with their own terms.
