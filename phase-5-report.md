# Phase 5 — lower planet and working superlaser (0.6.0)

The requested lower planet and Phase 5 are implemented. Phases 3–4 were accepted in game; Phase 2 remains skipped. Phase 6 polish has not started.

## Visible changes

The bridge now sits at **Y=224**, above the entire planet at every supported radius. Planet center Y is radius + 8: the default 80-radius planet spans Y=8–168, and even the 100-radius maximum ends at Y=208. The existing dimension height remains unchanged. The old bridge at Y=96 is removed on the next visit. The forward glass floor extends closer to the arrival point, the arrival view tilts downward, and a green beam emitter sits beneath the deck.

The Death Star return item remains in hotbar slot 1. A new **Superlaser Trigger** is provided in slot 2. Its placeholder texture/model is script-generated and replaceable.

## Launch and test

This version really removes source-world terrain. Use a disposable or backed-up world, as the entry warning states. For a quick first test, set `superlaser_radius = 32` or `64` while the world is stopped. The required default remains **1000**, which can involve hundreds of millions of blocks and run for a long time. The development server config is:

`C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you\run\config\worldeater-server.toml`

A world-specific serverconfig override may take precedence. New config keys are added on first launch of this version; use the mod config screen or edit the generated TOML with the world stopped.

Close the previous Minecraft client, then launch:

```powershell
cd "C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you"
./scripts/dev.ps1 runClient --no-daemon
```

1. Confirm World Eater **0.6.0**. Take Death Star from the mod tab and use it twice within ten seconds. The warning reports the destruction radius and explicitly asks for a world backup.
2. Look down through the forward glass deck. The whole globe should sit below the ship. Wait for the terrain scan and planet build; 16-chunk client and sufficient server view distance are recommended.
3. Select **Superlaser Trigger in slot 2**. Aim at the globe: a green ring marks the surface hit, and the action bar shows source X/Z and blast radius. Targeting works beyond vanilla reach and through the cockpit glass.
4. Hold right-click for **four seconds**. Releasing early or looking away cancels. Full charge fires a green particle beam, opens a crater in the model, and starts source-world destruction at that target. Each prepared globe can fire once.
5. Watch the progress. Work advances through chunks outward from the target, with a spherical boundary. When it finishes, the planet disappears with explosion particles and the visitor returns with their saved player state. A destroyed return spot gets the existing rescue platform.
6. Death Star in slot 1 can return you early. An already fired wave continues while you are elsewhere or offline. A normal server restart restores its saved cursor. Another orbit visit is blocked until your previous shot finishes or is stopped.

For the world launched from the project directory above, these are **Minecraft chat commands**:

- `/worldeater stoplaser`: operator-only; cancel all active/paused laser jobs and release tickets. Removed blocks are not restored.
- `/worldeater resumelaser`: operator-only; retry jobs paused after a processing error.
- `/worldeater return`: emergency return from orbit; a fired blast keeps running.
- `/worldeater stop`: existing black-hole stop command; separate from superlaser jobs.

## Implementation

A server-side quadratic ray/sphere intersection computes the surface point. The same square/disk mapping used to color the globe converts that point to source X/Z. Source Y is resolved from the current target chunk's WORLD_SURFACE height on impact. Permission, active session, completed/unspent planet and target validity are checked at use and again before firing. Multiplayer fire requires operator permission when either orbit access or operator-only destruction is enabled.

Each shot has saved stages: target acquisition, model crater, source shockwave, planet finale, done. It stores the shooter, original game mode, target dimension/position, fixed radius/speed, model geometry, hit point, progress and cursor. The source traversal sorts intersecting chunks outward, then scans columns top-down below their heightmaps and inside the 3D sphere. It avoids scanning sky or repeatedly visiting every earlier shell. The front is chunk-granular, not perfectly smooth.

One laser job advances per server tick in rotation, with global removal/check/time budgets. Each holds at most one direct non-ticking work ticket, with additional Minecraft generation dependencies. Tickets are released when switching chunks/stages, stopping, pausing or shutting down; the saved job reacquires its current chunk on restart. Player logout/death/return does not cancel a fired shot. Source dimension failure pauses the job rather than redirecting destruction to another world.

Source removal respects the world border, unbreakable and block-entity preservation settings, spawn/adventure restrictions and cancellable block-break events. A FakePlayer carries the shooter's identity in the source dimension while the real player is in orbit/offline. Preserved containers remain; disabling that protection also erases their inventories. Ordinary drops and neighbor cascades are suppressed. Vanilla heightmap and lighting updates remain enabled: no unverified lighting bypass or direct chunk-section editing is used, so there is no separate deferred relight pass.

## Config defaults

| Key | Default | Bounds / behavior |
|---|---:|---|
| `superlaser_radius` | 1000 | 4–2000; captured for the shot. |
| `superlaser_charge_ticks` | 80 | 20–400; 20 ticks approximately one second. |
| `superlaser_speed` | 32 | 1–256 blocks/second maximum; actual processing can lag behind. |
| `superlaser_blocks_per_tick` | 4096 | 16–16384 directly removed model/source blocks globally. |
| `superlaser_scans_per_tick` | 32768 | 256–131072 candidate/cursor steps globally. |
| `superlaser_time_budget_ms` | 4 | 0.25–10 ms cooperative processing budget. |

Existing `skip_unbreakable`, `skip_block_entities`, `operator_only_destruction` and `orbit_operator_only` settings apply. Planet generation settings remain separate. Minecraft generation, lighting, callbacks, cockpit construction and initial sorted-plan creation can exceed the mod's cooperative deadline; no hard total tick-time guarantee is claimed.

## Validation

- Final required Gradle wrapper build: **BUILD SUCCESSFUL**, 7 seconds.
- Isolated GameTest server: **all 9 required tests passed**, Gradle successful in 31 seconds.
- Existing black-hole, orbit round-trip, state recovery, full 80-radius planet and interrupted-build replacement tests continue to pass.
- New tests cover long-range sphere hits/misses; exact spherical candidate coverage and cursor reload; all supported globe radii below the bridge; early-release cancellation; full charge and one-shot enforcement; block/check caps; source removal; protected bedrock/container/canceled blocks; water removal; unchanged blocks outside the radius; saved-job reload mid-wave; departure and shutdown ticket release; operator stop permissions; model cleanup and automatic return with exact inventory restoration.
- A final display-only correction keeps terrain progress at 100% during the finale; the final build includes it.
- Release JAR verified to include the trigger, laser classes and assets and to exclude GameTest code/fixtures and the test-only world preset.

To reproduce:

```powershell
cd "C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you"
./scripts/dev.ps1 build --no-daemon
./scripts/dev.ps1 runGameTestServer --no-daemon
```

All destructive automated tests used only `work/run-gametest`, with 4- and 8-block blast radii. No shot was fired in the user's ordinary play worlds. Full default-1000-radius timing, new client appearance/particles, actual network disconnect/process-restart cycles and protection-mod compatibility were **not tested end to end**. Serialization and lifecycle-handler tests do not substitute for those acceptance checks. Graceful persistence follows Minecraft saves; player, job and chunk files are not a crash-atomic transaction.

## Files created or changed

Paths below are relative to `C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you`.

New Java files:

- `src/main/java/com/worldeater/LaserAim.java`: long-range hit calculation and source target projection.
- `src/main/java/com/worldeater/SuperlaserTriggerItem.java`: hold-to-charge item and early cancellation.
- `src/main/java/com/worldeater/LaserControl.java`: authorization, targeting HUD/particles, firing and cosmetic beam/finale.
- `src/main/java/com/worldeater/ShockwaveCursor.java`: outward chunk plan and resumable sphere-clipped column traversal.
- `src/main/java/com/worldeater/LaserShot.java`: durable staged model/source destruction and progress.
- `src/main/java/com/worldeater/LaserJobs.java`: SavedData, global scheduling, shutdown cleanup and stop/retry commands.
- `src/main/java/com/worldeater/gametest/LaserGameTests.java`: charge/destruction/recovery/cleanup integration tests, excluded from release.

Updated Java files:

- `WorldEater.java`: DeferredRegister item and event listeners.
- `WorldEaterConfig.java`: translated laser controls and hard radius maximum.
- `DeathStarItem.java`: destructive warning and block re-entry during an existing shot.
- `OrbitSession.java`: provide trigger in slot 2 and look down on arrival/bounds recovery.
- `OrbitStations.java`: raised bridge placement.
- `PlanetProjection.java`: lower planet placement without changing dimension height.
- `OrbitCockpit.java`: clear previous cabin, widen glass floor and add underside emitter.
- `PlanetBuildJob.java`: reset the spent flag for a newly prepared globe.

Resources/tooling/docs:

- `scripts/generate-assets.ps1`: new reproducible 16-by-16 trigger texture/model.
- `src/main/resources/assets/worldeater/models/item/superlaser_trigger.json` and `textures/item/superlaser_trigger.png`: generated placeholder art.
- `src/main/resources/assets/worldeater/lang/en_us.json`: all new names, warnings, config labels, progress and command messages.
- `gradle.properties`: version 0.6.0.
- `README.md`, `docs/research.md`, `docs/phase-5-report.md`: current controls, API evidence and limits.
- `outputs/`: JAR, complete source archive, docs and successful logs; previous phase exports retained.

No unresolved guessed API remains in the compiled implementation. No mixins, custom shaders or unbounded vanilla explosions were added. Phase 6 audiovisual/model polish and recipes remain unfinished; the skipped Phase 2 weapon/armor set remains absent.
