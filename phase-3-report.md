# Phase 3 — Death Star orbit cockpit (0.4.0)

Phase 2 was skipped at the user's request. Phase 3 now supplies a Death Star item, a midnight void dimension, a furnished block cockpit, saved round-trip travel, and recovery handling. This phase stops here for in-game acceptance. The block planet is Phase 4; aiming and superlaser destruction are Phase 5.

## Run and see it

Close the previous development client, then launch from this directory:

```powershell
cd "C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you"
./scripts/dev.ps1 runClient --no-daemon
```

1. Confirm Mods shows World Eater **0.4.0**. Open a Creative test world and find **Death Star** in the World Eater creative tab (gray sphere icon).
2. Right-click once for the warning, then again within ten seconds. Allow at least half a second between uses for the short cooldown. The warning explicitly explains that this preview cannot fire.
3. You should arrive inside a 17-by-19-block command bridge, with gray walls, a lit dark floor, console, chairs, broad glass viewport and glass over the forward roof. The green floor pad is the arrival point. The sky should be dark with vanilla stars; there is no planet yet.
4. Your original inventory is temporarily stored; a Death Star return item is provided in Adventure mode. Right-click it to return. In the world launched from the project directory above, the emergency chat command is `/worldeater return` (no operator permission needed).
5. Check your position, original inventory/armor/offhand, selected slot, game mode, abilities, health, hunger, effects, XP and Mass. Repeat the visit, save/quit while aboard, then rejoin: recovery should return you within about one second. Test a real death/respawn, graceful server shutdown/restart and dimension-command escape on disposable worlds.

If your return spot is obstructed or its floor was consumed, a 5-by-5 bedrock rescue platform is built above the local terrain. A missing original dimension falls back to Overworld spawn. Return does not rebuild previously destroyed terrain. Existing persistent black holes continue their normal work while you visit orbit.

## Validation

- Required wrapper build via `scripts/dev.ps1`: **BUILD SUCCESSFUL**, 8 seconds.
- Isolated `runGameTestServer`: **all 4 required tests passed**, Gradle successful in 27 seconds. Includes the three existing singularity tests plus the orbit suite.
- Orbit checks: dimension/biome/type loading, actual cross-dimension travel, first-use warning and expired confirmation, temporary inventory isolation, exact vanilla state restoration, player serialization, death attachment copying, drop/XP suppression, toss restoration, login and respawn recovery handlers, dimension-command escape, non-operator return permissions, retained operator protection on stop, separate station allocation, viewport/void blocks, and unsafe-return platform.
- Client visuals, actual network logout/rejoin, full death/respawn and server restart while visiting, and simultaneous multiplayer clients were **not run end to end**. Test handlers and serialization cover the code paths but do not substitute for these manual acceptance checks.
- Verified release JAR includes Death Star classes/assets, orbit dimension/type/biome and translations, and excludes development test classes/structure and the test-only flat preset.

To reproduce the build and tests from this directory:

```powershell
cd "C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you"
./scripts/dev.ps1 build --no-daemon
./scripts/dev.ps1 runGameTestServer --no-daemon
```

The GameTest server uses only `work/run-gametest`. Vanilla GameTestServer discards datapack dimension entries when baking its flat preset, so a test-only preparation task adds the production orbit definition to that preset in the isolated test world's datapack. Normal client worlds and the release JAR receive no preset override.

## Files created or changed

Paths below are relative to `C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you`.

New Java files:

- `src/main/java/com/worldeater/DeathStarItem.java`: double-use warning/confirmation, access checks, entry/return trigger.
- `src/main/java/com/worldeater/OrbitSession.java`: serialized snapshot, explicit state restoration, safe return/platform, lifecycle recovery, no-duplication handling, emergency return command.
- `src/main/java/com/worldeater/OrbitStations.java`: persistent per-player station allocation.
- `src/main/java/com/worldeater/OrbitCockpit.java`: bounded cockpit construction and arrival point.
- `src/main/java/com/worldeater/client/OrbitEffects.java`: client sky/fog/cloud effects.
- `src/main/java/com/worldeater/gametest/OrbitGameTests.java`: orbit integration suite (excluded from release).

Changed Java files:

- `WorldEater.java`: item/attachment and event registrations; creative tab entry.
- `WorldEaterConfig.java`: translated `orbit_operator_only` server setting (true by default).
- `Mass.java`: pause decay during saved orbit visits.
- `SingularityCannonItem.java`: disallow firing inside orbit.
- `SingularityTickets.java`: keep permission checks on the destructive stop subcommand while allowing emergency return.
- `client/WorldEaterClient.java`: register custom orbit effects through NeoForge's client event.

New or changed resources/tooling:

- `src/main/resources/data/worldeater/dimension/orbit.json`, `dimension_type/orbit.json`, `worldgen/biome/orbit.json`: data-driven void dimension, fixed midnight and black sky/fog.
- `src/main/resources/assets/worldeater/models/item/death_star.json`, `textures/item/death_star.png`: replaceable generated placeholder asset.
- `src/main/resources/assets/worldeater/lang/en_us.json`: all new names, warnings, feedback and config label.
- `scripts/generate-assets.ps1`: reproducible 16-by-16 Death Star art/model generation; existing generated placeholders retained.
- `src/gametest/resources/flat.json`: vanilla 1.21.1 preset input used only for isolated test preparation.
- `build.gradle`: test-only datapack preparation task.
- `gradle.properties`: mod version 0.4.0.
- `README.md`, `docs/research.md`, this report: usage, limits, implementation evidence and acceptance steps.
- `outputs/`: current JAR, source archive, reports, README/research copies and successful build/test logs; earlier phase exports retained.

## Scope and limitations

This is an interior command cockpit, not an exterior Death Star sphere. No block planet, targeting, charge beam or superlaser terrain destruction is included. No blade/armor/legendary weapon work was added. Item art and cockpit blocks are replaceable placeholders, with no custom shaders or mixins.

Entry is single-player or operator-only by default, configurable with `orbit_operator_only`. Each player has a separate persistent station, 1024 blocks apart, up to 4096 assignments. The cockpit is rebuilt on entry, and bounds recovery keeps visitors inside it. Normal player chunk loading supplies the station; Phase 3 adds no persistent tickets.

The snapshot covers the listed vanilla fields and this mod's Mass attachment, not arbitrary other mods' custom capabilities, advancements or statistics. Fall distance and velocity reset on return. Entry while riding, sleeping or spectating is rejected. Original inventory is stored for the visit, effects resume with their captured durations, and temporary visit inventory is discarded on return. Graceful save/logout/restart is supported; sudden power-loss durability follows Minecraft's save behavior. Save flushes use the public player-list API.

No unresolved guessed API remains in the compiled code. Exact Minecraft 1.21.1 / NeoForge 21.1.252 sources established all newly used hooks. The compiler caught that PlayerList.save is protected; implementation uses public saveAll. Client appearance and complete multiplayer lifecycle remain explicitly unverified above.
