# Phase 4 — terrain planet and improved cockpit (0.5.0)

Phase 3 was accepted in game. This phase adds a terrain-colored block planet and the requested cockpit redesign. Phase 2 remains skipped. Stop here for acceptance before Phase 5 aiming and superlaser destruction.

## What changed

The bridge is now a 29-by-33-block chamfered room with a panoramic forward viewport, glass observation floor, clear forward canopy, recessed guide lights, illuminated cyan/green side consoles, crew chairs, raised command dais and a rear wall display. The old Phase 3 cabin is cleared inside the new structure's footprint when you next enter. The spawn position faces the planet from the forward deck. Orbit's ambient lighting is brighter to make surface colors readable while retaining the midnight sky.

Entering orbit starts a server-side terrain scan while the player explores the bridge. The default is a 33-by-33 grid across the square extending 1500 blocks north/south/east/west of the recorded launch position. Each sample reads the top map-colored block, skipping map-transparent tops and including water/foliage. A fixed concrete/wool/stone/terracotta palette approximates those colors.

The result is an 80-block-radius hollow sphere beyond the viewport. A north-up square-to-disk mapping paints the front hemisphere, including the source square's corners; the rear repeats the same projection. Source dimension/coordinates, colors, grid/radius, projection version and geometry are saved per station. The planet is a color preview rather than a miniature copy of terrain elevation/buildings.

Scan, old-globe cleanup and construction run in stages with translated action-bar progress. One job advances per server tick in rotation; global defaults are at most 512 block placements/removals and a cooperative 3 ms processing budget. Chunk readiness is polled with a transient non-ticking ticket rather than waiting synchronously for generation. Each job retains at most one direct work ticket, plus Minecraft's additional chunk dependencies. On return/logout/death/dimension escape/server shutdown, work stops and tickets release. Canceled geometry remains indexed for bounded cleanup on a later visit, even if the configured radius changes.

## Launch and test

Close the previous client, then launch from the project directory:

```powershell
cd "C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you"
./scripts/dev.ps1 runClient --no-daemon
```

1. Confirm World Eater **0.5.0** in Mods. In a Creative test world, take **Death Star** from the mod's creative tab. Use it twice within ten seconds, as before.
2. Explore the redesigned bridge. The forward glass floor gives a view below the horizon; the arrival pad is close to that viewport. Watch the action-bar scan/build progress with the game unpaused.
3. Wait for the planet-ready message. A fresh 1500-block scan may take several minutes if much of the terrain is unexplored. Set client render distance to **16 chunks** for the whole globe. On dedicated servers, a sufficient server view distance is also needed (16 recommended).
4. Check that water, vegetation, sand/stone/snow or large colored structures near the launch region influence the planet. North should be up and east right. A mostly uniform source region can produce a mostly uniform globe.
5. Right-click the Death Star to return. Try leaving during a scan/build, then re-enter: the new visit should scan again and clean up any partial old shell before rebuilding. Inventory and return recovery retain Phase 3 behavior. In the world launched from the directory above, the emergency chat command remains `/worldeater return`.

The scan does not consume source terrain, but sampling unexplored locations generates normal chunks and increases world data. Existing Singularity Cannon black holes continue their independent destructive behavior. The Death Star still cannot fire; that is Phase 5.

## Validation

- Required Gradle wrapper build: **BUILD SUCCESSFUL**, 5 seconds.
- Isolated GameTest server: **all 6 required tests passed**, Gradle successful in 27 seconds.
- Existing black-hole and orbit regression tests retained, including actual dimension round trips and state recovery paths.
- New checks cover source-grid endpoints/negative coordinates, north-up projection, square corners on the hemisphere, water sampling through map-transparent glass, exact palette matching, and a full 80-block-radius globe from known red/blue/green/sand samples.
- The full build test first interrupts a smaller globe, changes radius, and verifies the old shell is cleared. It checks per-tick write limits, released work leases, painted hemisphere locations, a hollow interior, recorded metadata and unchanged source blocks.
- Release JAR contents verified: required planet/cockpit classes and updated dimension/translation resources included; GameTest classes/fixtures and test-only preset excluded.
- Client visual inspection, full default-radius scan timing in a normal unexplored world, actual network logout/restart/death cycles and simultaneous multiplayer visits were **not tested end to end**. The user should perform the acceptance steps above; automated server tests do not verify appearance or promise an FPS/tick-time target.

To reproduce checks:

```powershell
cd "C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you"
./scripts/dev.ps1 build --no-daemon
./scripts/dev.ps1 runGameTestServer --no-daemon
```

## Files created or changed

Paths below are relative to `C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you`.

New files:

- `src/main/java/com/worldeater/PlanetBuildJob.java`: staged color scan, old-shell cleanup, bounded sphere painting, progress and metadata checkpoints.
- `src/main/java/com/worldeater/PlanetChunkLease.java`: transient chunk request/poll/release without managed blocking.
- `src/main/java/com/worldeater/PlanetPalette.java`: fixed opaque block palette matched by map color.
- `src/main/java/com/worldeater/PlanetProjection.java`: full-square north-up hemisphere mapping, geometry and sampling coordinates.
- `src/main/java/com/worldeater/OrbitPlanets.java`: global scheduling, cancellation, progress and lifecycle cleanup.
- `src/main/java/com/worldeater/gametest/PlanetGameTests.java`: projection/sampling and full-size interrupted-build integration tests; excluded from release.
- `docs/phase-4-report.md`: this report.

Updated files:

- `src/main/java/com/worldeater/OrbitCockpit.java`: redesigned geometry, lights, consoles, observation deck and cabin bounds.
- `src/main/java/com/worldeater/OrbitSession.java`: new spawn/view angle, expanded bounds, start/cancel planet work with visits.
- `src/main/java/com/worldeater/OrbitStations.java`: save/load per-player planet metadata and dirty shell geometry.
- `src/main/java/com/worldeater/WorldEaterConfig.java`: scan radius, grid, globe radius and global block/time budgets.
- `src/main/java/com/worldeater/WorldEater.java`: tick/logout/server-stop event registration.
- `src/main/java/com/worldeater/gametest/OrbitGameTests.java`: new viewport/deck checks and early-return cancellation check.
- `src/main/resources/assets/worldeater/lang/en_us.json`: translated progress, warnings, completion/failure messages and config labels.
- `src/main/resources/data/worldeater/dimension_type/orbit.json`: brighter ambient illumination; existing dimension height remains unchanged.
- `gradle.properties`: version 0.5.0.
- `README.md`, `docs/research.md`: current usage, configuration, researched APIs, scope and limitations.
- `outputs/`: JAR, complete source archive, current docs, successful logs; earlier phase exports retained.

## Configuration and limitations

New server config defaults: `planet_scan_radius=1500` (16–3000), `planet_sample_grid=33` (9–65), `planet_radius=80` (60–100), `planet_blocks_per_tick=512` (32–2048), `planet_time_budget_ms=3.0` (0.25–10). Existing orbit access restrictions and return behavior remain. Higher scan resolution/radius can greatly increase generation time and disk usage.

Budgets apply to the mod's staged processing; Minecraft chunk dependencies, engine generation, lighting and callbacks are additional work. The cockpit itself is a bounded structure built on entry. No lighting bypass, mixins, shaders, aiming, charging or superlaser destruction are added. The current chamber has decorative controls, not a working weapon console.

Unfinished jobs cancel on logout/shutdown and restart on a new visit; they do not resume automatically while the player is away. Completed and partial globes remain in their separate orbit stations until the next visit replaces them. Each new visit rescans current terrain. Neighboring stations retain the previous 1024-block spacing.

No unresolved API guess remains in compiled code. Newly used APIs were checked against the exact local Minecraft 1.21.1 / NeoForge 21.1.252 sources. Notably, getChunkFuture managed-blocks on the main thread in this version; the implementation instead polls getChunkNow after a region ticket request. This provides a responsive processing loop, not a guarantee against all engine generation stalls.
