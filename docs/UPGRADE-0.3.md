# Lag Doctor 0.3.0 — Paper 26.3

This release targets **Paper 26.3 and Java 25+**, using pinned Paper API
`26.3.build.143-beta`. The older 0.2.0 release remains available for Paper
1.21.11. Do not install both JARs at once. Spigot, Folia and other Minecraft
versions are not supported by this build.

## Install or upgrade

1. Back up the server and stop it. Paper 26.3 is currently beta; test on a
   separate copy first. World upgrades cannot be reversed by swapping JARs.
2. Use Java 25 for Paper 26.3.
3. Move any previous LagDoctor JAR out of `plugins/`, and copy
   `LagDoctor-0.3.0.jar` into that directory.
4. Keep the existing `plugins/LagDoctor/config.yml` and reports. Restart.
5. Wait about a minute, then run `/ld report`. The header should show
   `Lag Doctor 0.3.0` and `Paper 26.3`.

Commands, permissions, configuration keys, report format and diagnostic rules
are unchanged from 0.2.0. Baselines and incident history are in memory and reset
when the server restarts. The demo remains off by default.

## Build and local validation

Build with JDK 25: `./gradlew test build`. The output is
`build/libs/LagDoctor-0.3.0.jar`. The descriptor requires API 26.3, and the
classfiles target Java 25.

The [local demo helper](../scripts/prepare-local-demo.py) pins Paper 26.3 build
143 and verifies its SHA-256 before creating `.local-server-26.3`. It preserves
the previous `.local-server` directory. Use only one demo at a time because
both use the same loopback port. See [validation results](VALIDATION.md).

This is a compatibility release, not a new lag-removal engine. Diagnostics are
heuristic; no world changes, telemetry or automatic fixes are introduced.
Development used substantial AI assistance.

References: [Paper Java requirements](https://docs.papermc.io/paper/getting-started/),
[26.3 announcement](https://papermc.io/news/26-3/),
[API setup](https://docs.papermc.io/paper/dev/project-setup/).
