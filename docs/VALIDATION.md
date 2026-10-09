# Validation record

## v0.3 Paper 26.3 compatibility — 2026-10-03

- JDK 25.0.4.1 / macOS arm64; pinned Paper API `26.3.build.143-beta`. `./gradlew test build`: all 18 tests passed, zero failures/errors.
- Fresh isolated Paper **26.3 build 143** enabled LagDoctor **0.3.0**, collected tick windows and reported `Paper 26.3`; plugin descriptor targets API 26.3 and classfile major 69 (Java 25).
- Scan found exactly two tagged ARMOR_STAND fixtures in chunk [0,0]; selected/inspected 25 already-loaded chunks, skipped 0. Collection 7.89 ms total, largest step 4.17 ms. Concurrent scan was rejected; cancellation worked. This small local fixture is not a large-server performance benchmark.
- Default TXT/HTML export omitted world/chunk locations, explicit `export locations` included them, and both retained entity counts. HTML parsed successfully without scripts.
- Console-only repeated-spike demo produced about 121–122 ms per-window p95 and 10% slow ticks in complete injected windows, triggered a sustained-slowdown warning and OPEN incident, and correctly suppressed causal attribution. Early comparison rejected overlapping windows.
- After six fresh windows, the comparison measured a synthetic-demo recovery from 9.22 ms mean / 7.2% slow ticks to 0.36 ms / 0%. The incident became RECOVERED; demo labels remained. Fixtures were removed and the isolated server was stopped. These numbers demonstrate detection, not automatic lag reduction.
- API calls used by the plugin compiled unchanged; no diagnostic algorithm changes were needed. Paper 26.3 is itself beta. Other 26.x releases, large servers, Folia and player-client interactions are not claimed as validated.


## v0.2 upgrade — 2026-10-03

- Final build: `./gradlew test build`, **18 tests passed**, no failures/errors. Ten new tests cover evidence contrast, demo suppression, workload warnings, mixed comparison windows, incident hysteresis/retention, density ranking, HTML escaping and default location redaction.
- Paper 1.21.11 build 132 / Temurin 25 / macOS arm64: upgraded JAR loaded successfully. Old JAR was preserved outside the plugin folder. Existing config remained usable.
- Live fixture: twelve tagged armor stands in overworld chunk [0,0], with four explicitly force-loaded fixture chunks. Scan inspected 36 already-loaded chunks and found exactly **12 ARMOR_STAND** in the correct entity-ticking chunk. Total collection **5.34 ms**, largest step **0.83 ms**. This is one small local sample, not a large-server overhead benchmark.
- Concurrent scan was rejected; immediate repeat reported remaining cooldown. Final-JAR restart also verified explicit scan cancellation and empty incident history after restart.
- Final JAR sequential exports advanced through slot 0 then slot 1 successfully. SHA-256: `2aee07de46f0f090b62efc91d1c5939495286409d5b7cbd7ff4e0a89f55de933`.
- Repeated-spike demo produced an OPEN incident, then RECOVERED after two healthy windows; eight slow windows retained in episode totals, up to six in detailed evidence. Demo cause ranking was suppressed.
- Live TXT and HTML exports verified: default omitted world/chunk locations and retained type counts; explicit `export locations` included the correct chunk. HTML parsed successfully with no scripts, external resource references or embedded frames. Unit tests separately checked malicious-looking names are escaped.
- Browser visual inspection was unavailable: the browser tool rejects local `file:` URLs. Layout has not been visually verified; content, escaping and offline structure were checked directly.
- Fixture cleanup removed exactly twelve tagged armor stands and released the four fixture chunk tickets; server stopped cleanly.
- Example exports are in `docs/examples/v0.2/`, including `report-redacted.html`, `report-with-locations.html` and `recovered-incident.html`. They are genuine local demo captures, not evidence of real-world cause accuracy.
- Limits: entity scanner API-error/deadline/large-world guard paths are implemented but have not been stress-tested on a large server. Non-operator player interactions and Paper 26.x remain unverified. Stronger associations are heuristic contrasts, not independently validated attribution.

## Historical v0.1 checks

Tested locally on 2026-10-03 (Australia/Sydney) using macOS arm64, Eclipse Temurin 25, Paper **1.21.11 build 132**, 1–2 GB JVM heap, zero connected players, loopback binding, and no other installed third-party plugins. Paper's bundled spark was enabled normally.

## Build and automated checks

- `./gradlew test build`: successful. Eight JUnit tests, zero failures/errors.
- Compiled with `--release 21`; no runtime libraries are bundled or downloaded by the plugin.
- Tested aggregation mean/p95/max, >50 ms threshold boundaries, elapsed-time TPS, counter reset, healthy high-count false-positive avoidance, GC/chunk/entity hypothesis branches, unresolved causes, overlapping baseline rejection, and comparison direction/demo labels.
- Local setup script smoke-tested in an isolated temporary directory: SHA-256-verified server, plugin copy, explicit EULA handling, loopback properties, disabled empty-server pausing and flat generation settings.
- Existing `.local-server` was refused without overwriting it.
- Gradle wrapper download has a pinned distribution SHA-256.

## Real Paper checks

- Plugin loaded/enabled successfully, with no Lag Doctor exceptions.
- Idle report: mean 0.47 ms, zero slow ticks. First report included startup elapsed time, so effective TPS was 19.38; later clean idle windows should be used for comparisons.
- Saved a full six-window baseline and confirmed early comparison rejected overlapping data.
- `demo steady`: automatically stopped at its 90-second limit. Full injected windows averaged about 66 ms/tick with 100% slow ticks; sustained alert fired after three slow windows.
- Export after the demo: 56.42 ms mean across six windows (including recovery), 15.66 effective TPS, five slow windows out of six, explicit demo label and unresolved-cause explanation. See `examples/steady-demo.txt`.
- Replaced the baseline with the slowed report, waited for six separate windows, and verified recovery: **56.42 → 0.14 ms mean**, **15.66 → 20.00 effective TPS**, **84.8% → 0% slow ticks**. The comparison retained its demo qualification. See `examples/recovery-comparison.txt`.
- JAR inspected for plugin descriptor, default config and implementation classes; classfile major version 65 confirms Java 21 bytecode.

- Repeated-spike scenario: six of six windows flagged with **12.20 ms mean**, **120.66 ms worst-window p95**, **125.36 ms max**, and **10.0% slow ticks**, while effective TPS stayed 20 due to tick catch-up. This verifies detection of repeated stalls hidden by averages/TPS. Manual demo stop and report export succeeded. See `examples/spikes-demo.txt`.

- Server stopped cleanly and Lag Doctor disabled without errors; the prepared local server is left stopped.

## Interpretation and remaining limits

These are functional checks, not a production performance benchmark or proof that the rules identify real bottlenecks. The GC/chunk/entity rules have controlled unit evidence but have not been validated against independently profiled live farms, exploration, or heap pressure. Large servers, many worlds, player chat permissions and non-operator client interactions have not been load-tested. Java 21 bytecode was verified by compilation; this runtime session used Java 25.

An initial flat-world setup emitted vanilla `No key layers` warnings. The setup helper now supplies explicit layers; that correction was checked in generated properties. It did not prevent plugin loading. Paper also warns that 1.21.11 is older than its current release line; 26.x compatibility is not claimed. Use this pinned version for a disposable demo, not as advice to downgrade an existing server.
