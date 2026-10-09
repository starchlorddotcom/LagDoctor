# Private solo demo

Use a disposable local world. You do not need a Minecraft client or other players; all commands work in the server console. Python 3 and JDK 25 are required. Initial setup downloads Paper and Minecraft files, then creates local worlds. Allow a few minutes and a few GB of disk space.

## Prepare and start

From the project directory:

```sh
./gradlew test build
# Read https://aka.ms/MinecraftEULA first. This flag confirms YOUR agreement:
python3 scripts/prepare-local-demo.py --accept-eula
cd .local-server-26.3
java -Xms1G -Xmx2G -jar paper.jar --nogui
```

On macOS with the installed Temurin 25 JDK, prefix build commands with `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home` and use that JDK's `bin/java` if the default Java is incompatible with Gradle.

The older `.local-server` (1.21.11) is left untouched. The helper pins Paper 26.3 beta build 143 and verifies its SHA-256. It refuses to overwrite an existing `.local-server-26.3`. It binds to **127.0.0.1**, keeps online authentication and whitelist enabled, disables RCON/query, uses view/simulation distance 4, and disables empty-server pausing so measurements continue with zero players. Never port-forward this demo. An existing prepared server can simply be started again.

If you want to join alone, use console `whitelist add YOUR_NAME`, then connect a Java Edition 26.3 client to `localhost`. Console operation does not require joining or operator status.

## Scenario A: sustained tick pressure and recovery

1. Wait until the server says `Done`, then wait 90 seconds for startup activity to leave the report.
2. Run `lagdoctor report`. Expect predominantly healthy windows on an idle machine.
3. Run `lagdoctor demo steady`. This intentionally waits 65 ms per tick for at most 90 wall-clock seconds. It does not spawn entities or change blocks.
4. After about 85 seconds, run `lagdoctor report`, then `lagdoctor baseline`, then `lagdoctor export`. Expect a mean above 50 ms, reduced effective TPS, slowdown explanations and explicit DEMO labels. The six-window report needs about 78 seconds under this load.
5. Run `lagdoctor demo stop` (or let its 90-second limit expire).
6. Wait 75 seconds, then run `lagdoctor compare` and `lagdoctor export`. Expect a lower mean tick time and recovered TPS; overlapping windows must be gone. The comparison remains labelled as a demo because its baseline was intentionally delayed.

The injected delay may be reported as unresolved because it is not GC, chunk generation, or entity work. That is correct: Lag Doctor must not invent a real-world cause. Incidental startup GC or chunk activity can produce additional hypotheses; their evidence is printed.

## Scenario B: repeated spikes

Run `lagdoctor demo spikes`, wait 65 seconds, then `lagdoctor report`. Every tenth tick waits 120 ms; expect elevated p95/max and about 10% slow ticks even though mean MSPT may stay below 50. Stop with `lagdoctor demo stop`. This shows why averages alone hide recurring stalls.

Both scenarios are console-only, require `local-demo-enabled: true` AND `server-ip=127.0.0.1`, and stop automatically within roughly 90 seconds (at the next scheduled tick). The production default disables demo commands. Restart/disable cancels the task; affected report windows retain labels until they age out.

## Real workload investigations

These deterministic demos verify detection and comparison, not attribution accuracy. The pure Java tests use synthetic, explicitly controlled evidence for GC/chunk/entity hypotheses. For a real farm, exploration or plugin investigation, obtain a spark profile during the issue, change one thing, and use full separate before/after windows with comparable players and activity. Do not create huge farms or force memory exhaustion just to trigger a heuristic.

## Stop and troubleshoot

- Type `stop` in the server console to save and shut down cleanly.
- `collecting`: wait at least 200 server ticks.
- Baseline refusal: wait until all six windows have completed.
- Comparison overlap: wait for six wholly new windows after the baseline.
- No data on an empty custom server: set `pause-when-empty-seconds=-1`, restart.
- Port already used: stop your other local server or set a different `server-port` before starting.
- Demo refused: check the exact loopback address, config flag, and use console, not chat/RCON.
- To install a newer build into this demo: stop the server, replace its plugin JAR with `build/libs/LagDoctor-0.3.0.jar`, restart.

## v0.2 investigation features

Use `/ld scan`, `/ld hotspots`, `/ld incidents latest`, and `/ld export` during either scenario. With zero players and no loaded entity chunks, the scan can correctly be empty. To demonstrate locations, use a disposable world and a small tagged fixture:

```text
forceload add 0 0
summon minecraft:armor_stand 2 64 2 {NoGravity:1b,Tags:["lagdoctor_demo_fixture"]}
summon minecraft:armor_stand 3 64 2 {NoGravity:1b,Tags:["lagdoctor_demo_fixture"]}
lagdoctor scan
```

Allow a few seconds for chunk/entity loading before scanning. This locates two armor stands without attempting to create entity lag. `/ld export` hides the location; `/ld export locations` includes it. Wait sixty seconds between scans. After the timing demo ends, wait two healthy windows and inspect `/ld incidents` for RECOVERED.

Clean up only these fixtures using `kill @e[type=minecraft:armor_stand,tag=lagdoctor_demo_fixture]`. If you newly force-loaded this chunk for the demo, release that ticket with `forceload remove 0 0`; preserve any pre-existing ticket. These are explicit local test commands, not automatic plugin interventions.
