# Lag Doctor 0.2.0 — public beta

Created by William Liu (@starchlorddotcom).

Lag Doctor helps Minecraft Java server owners investigate lag with evidence, safe next steps, and before/after comparisons.

## Features

- Tick-time monitoring with mean, p95, maximum and slow-tick percentage.
- Evidence-based hypotheses using GC, chunk activity and entity counts, with healthy/slow comparisons and explicit uncertainty.
- Optional scans of loaded entity chunks, including type counts and locations for admins.
- Recent incident history with recovery detection.
- Baseline comparisons that flag changed workload.
- Offline HTML and text reports, with locations omitted by default.
- Private, repeatable solo demos for sustained lag and recurring spikes.

## Installation

Requires Paper 1.21.11 and Java 21 or newer. Stop the server, remove any older LagDoctor JAR from its plugins directory, copy LagDoctor-0.2.0.jar into plugins, and restart. Run /ld report after about a minute. Read README.md and docs/UPGRADE-0.2.md for commands and limitations.

## Validation and limitations

18 automated tests pass. Live Paper testing verified entity scan counts and locations, scan controls, incident recovery, baseline comparisons and report exports. This is an early beta: counts and associations do not prove CPU cost or cause. Large-server overhead and Paper 26.x compatibility have not been validated. HTML content and escaping were tested; visual rendering was not verified.

No account, telemetry, automatic destructive fixes, player punishment, or undocumented spark integration. MIT licensed. Minecraft and Paper have their own terms and are not bundled.
