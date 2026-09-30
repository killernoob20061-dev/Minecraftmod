# Phase 1 revision — persistent world-eating black holes

## Outcome and requested design change

Version **0.3.0** changes the cannon to the behavior clarified by the user: shoot a black hole, settle at impact, and keep expanding/consuming, including progressively loaded terrain. This explicitly replaces the original brief lifetime and six-block growth cap. No Phase 2 work was started.

The hole advances through saved, chunk-ordered spherical shells. Growth waits for the bounded work to complete a shell, avoiding ever-larger full-volume rescans. It retains a core ticket and at most one direct work ticket; chunk dependencies may keep more actual chunks resident. The default never expires and aims toward the dimension's world border. The old small-limit config values do not silently carry over because the new mode uses distinct keys.

## Files changed/created since 0.2.0

Paths are relative to the project directory.

- `src/main/java/com/worldeater/SingularityEntity.java`: impact anchoring, shell consumption, steady growth, offline actor/Mass handling, persistent state and ticket release.
- `src/main/java/com/worldeater/ExpandingShellCursor.java` (new): resumable shell iterator, chunk ordering, finite-height interior skipping and large-radius arithmetic.
- `src/main/java/com/worldeater/SingularityAnchors.java` (new): saved anchor/work-ticket index.
- `src/main/java/com/worldeater/SingularityTickets.java` (new): NeoForge ticket registration, load throttle, startup validation, orphan cleanup and operator stop command.
- `src/main/java/com/worldeater/SingularityCannonItem.java`: saved anchors included in dimension cap; launch nearer the eye to avoid passing through nearby walls.
- `src/main/java/com/worldeater/WorldEaterConfig.java`: persistent defaults and growth/flight/chunk request controls.
- `src/main/java/com/worldeater/WorldEater.java`: ticket/lifecycle/command listeners and increased entity tracking range.
- `src/main/java/com/worldeater/gametest/PhaseOneGameTests.java`: persistent behavior replaces expiry expectations while retaining prior checks.
- `src/main/java/com/worldeater/gametest/PersistentGameTests.java` (new): shell coverage/resume, large radius, impact, offline feeding, persistence and ticket cleanup/stop checks.
- `src/main/resources/assets/worldeater/lang/en_us.json`: new settings and stop/limit messages.
- `gradle.properties`, `src/main/templates/META-INF/neoforge.mods.toml`: version 0.3.0 and revised description.
- `README.md`, `docs/research.md`, this report: current behavior, commands, configuration and verification limits.

Textures, renderer mesh, Mass attachment implementation, Gradle pins and wrapper remain unchanged. Runtime registrations still use DeferredRegister; ticket controllers use their required dedicated NeoForge registration event. There are no mixins.

## Validation

The isolated GameTest server reported **all 3 required tests passed**, process exit 0. Tests cover:

1. Existing Mass clamp/decay/player NBT, block and scan caps, radius cap when explicitly configured, protected blocks, canceled break events, mob rewards, item pull and cannon cooldown. The persistent hole remains after the former 200-tick cutoff.
2. Exact shell coverage versus a brute-force oracle for radii 1–20, offset/negative coordinates and clipped bounds; no duplicated blocks; cursor continuation after serialization; incremental arithmetic at a radius beyond 30 million blocks.
3. Actual block impact and fixed anchoring; entity save/load preserves the next candidate; feeding without an attached/live owner; expansion beyond six blocks with no optional cap; buffered offline Mass; actual remote chunk ticket acquisition and removal; core ticket removal; the stop operation clears entities and anchors.

Tests use `work/run-gametest` and do not load the user's play world. Test classes/fixtures stay out of the distributable JAR. See `outputs/gametest-persistent.log` and `outputs/build-persistent.log` for the server test and final build evidence. Initial compilation of the new implementation also passed.

Serialization and real chunk ticket operations were tested. A complete shutdown/relaunch with a surviving black hole, visual inspection of the revised client, multiplayer sessions and long-duration performance were not tested. The tests are not evidence that consuming an entire world has been completed.

## Test in game

Close the old client, then in PowerShell:

```powershell
cd "C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you"
./scripts/dev.ps1 runClient --no-daemon
```

Use a disposable Creative world and check **World Eater 0.3.0** in Mods. Fire the Singularity Cannon into a nearby hillside. The shot should stop at the hit location, grow and consume terrain in batches. It should still exist after ten seconds and should grow beyond its former six-block maximum. An empty-air shot settles after about five seconds. The Mass meter should rise during feeding.

Leave the area/change dimensions, return, then save/quit/rejoin to check continued consumption and persistence. Those are the remaining manual lifecycle/visual acceptance checks.

In that Minecraft world's chat, use `/worldeater stop` as an operator (or with cheats in single-player) to remove all holes across dimensions and release tickets. This does not restore terrain.

## Limits and uncertainty

- The effect persists while the server runs and resumes saved work when the world loads. It does not simulate destruction while the game/server is shut down or paused.
- No small radius cap is enabled by default. World-border extent and build height still define the reachable world. Full-world consumption is not a practical completion-time promise.
- Bedrock, fluids, block entities and claim-protected blocks remain according to the existing settings/rules. Cleared inner shells are not continually rescanned for newly placed blocks.
- Time budgets cannot interrupt vanilla chunk generation, block updates or protection callbacks. Chunk-load requests are rate-limited, but an individual operation may still cause a hitch. Neighbor physics can change extra blocks beyond the mod's direct count.
- At large sizes, entity pull/damage is processed through bounded core/frontier windows. Normal entity tracking limits how far away the central sphere can be seen; distant rendering is not yet solved or tested.
- Offline block-break events use the shooter's UUID via a NeoForge fake player; protection-plugin compatibility needs real-server checks. Current operator permission still matters when operator-only destruction is enabled.
- Pending offline Mass is capped. Stopping/removing a hole discards any undelivered Mass it was holding. Removing a hole is not an undo for destruction.
- Startup reconciliation and orphan-ticket cleanup are implemented, but sudden-crash atomicity across entity/chunk/index saves is not guaranteed.

The new defaults, config keys and full controls are documented in README. Await the user's revised Phase 1 acceptance before proceeding to Phase 2.
