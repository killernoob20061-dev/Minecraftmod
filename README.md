# World Eater — Phase 5: Death Star superlaser (0.6.0)

Minecraft Java Edition **1.21.1**, NeoForge **21.1.252**, Java **21**. Mod ID `worldeater`; group `com.worldeater`.

The Singularity Cannon now fires a **persistent world-eating black hole**. It settles at block impact, then expands from that fixed point and consumes successive spherical shells. It has **no default expiry and no small-radius cap**. It loads terrain gradually, so it can keep working while you are elsewhere. Phase 2 was skipped at the user's request. Phases 3–4 were accepted in game. Phase 5 places the planet below the ship and adds targeting, charged firing, durable terrain destruction, the planet finale and automatic return.

## Launch and test

Close the previous development client. In PowerShell:

```powershell
cd "C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you"
./scripts/dev.ps1 runClient --no-daemon
```

Use a disposable Creative world; the effect now persists and continues destroying terrain. Confirm Mods shows **World Eater 0.6.0**, take **Singularity Cannon** from its creative tab, and right-click toward nearby dirt/stone. The small shot should settle where it hits, then become a growing dark sphere with a purple ring. If it hits nothing, it settles after five seconds by default. Observe it for longer than ten seconds; it should remain active and keep feeding. Growth pauses while the work budget catches up with a shell, then resumes. The Mass action-bar display increases with consumption and decays while online.

In that Minecraft world's chat, an operator (or a single-player user with cheats) can run `/worldeater stop` to remove all active black holes across loaded server dimensions and release their chunk tickets. The command does not restore consumed terrain.

Save/quit/rejoin to check that the anchored location and growth continue. Move away or change dimensions to check continued consumption. Dedicated-server login/restart behavior and client visuals still need manual acceptance checks; automated tests cover serialization and ticket behavior.

## Build and automated tests

```powershell
cd "C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you"
./scripts/dev.ps1 build --no-daemon
./scripts/dev.ps1 runGameTestServer --no-daemon
```

The helper selects the portable Java 21 JDK in `work/toolchains`, uses `work/gradle-home`, and sets a short process-local socket path under `Documents/Codex/work/worldeater-tmp` to avoid this host's Java socket issue. It restores the environment afterwards. A different machine needs its own Java 21 JDK. The normal Gradle wrappers are unchanged. Initial downloads require network access.

Artifact: `build/libs/worldeater-0.6.0.jar`. Install on client and server with Minecraft 1.21.1 and compatible NeoForge 21.1.252+. GameTests run in `work/run-gametest`, never the ordinary `run/saves` worlds. Development test classes/fixtures are excluded from the distributable JAR.

## Death Star: enter the cockpit

Restart the development client with the launch command above. In the **World Eater** creative tab, take **Death Star** (the gray sphere icon). Right-click once to read the warning, then again within ten seconds. After the second use you should stand inside a larger chamfered command bridge with a panoramic viewport, glass observation floor, recessed guide lights, cyan/green side consoles, a raised command dais and a rear wall display. The green arrival pad faces down toward the planet. The bridge is now at Y=224, above the entire globe; the old cabin at Y=96 is cleared on the next visit. The forward glass deck provides the main view.

Right-click the Death Star item again to return. In this project's launched world, the emergency chat command `/worldeater return` also works, including without operator permissions. The existing `/worldeater stop` still requires operator permissions. There are no recipes yet; obtain the item through Creative.

This is **Phase 5**: the interior cockpit, terrain-colored planet, aiming, charged superlaser and real source-world destruction are implemented. There is no exterior Death Star sphere. The Singularity Cannon retains its independent persistent behavior from Phase 1.

During a visit you temporarily enter Adventure mode with a Death Star return item in slot 1 and a Superlaser Trigger in slot 2; your original inventory, armor, offhand, selected slot, game mode, abilities, health, absorption, hunger, effects, air/fire status, XP, and Mass are saved in a serialized player attachment. The original inventory is stored outside the temporary visit inventory to avoid duplicating dropped items. Tossed visit items are returned to inventory. Death drops/XP are suppressed while the snapshot is active, and the snapshot copies on death. Return restores these fields once. Fall distance and velocity reset for a safe arrival; riding/sleeping/spectator entry is rejected.

Saved active visits recover within approximately one second after login or respawn. This covers logout and normal server shutdown while in orbit. Changing dimensions with commands also triggers recovery, and a player who enters orbit via commands without a saved visit is rescued to the Overworld spawn area. If the original dimension is unavailable, return falls back to the Overworld. If the original position lacks a safe floor or is obstructed, the mod creates a 5-by-5 bedrock rescue platform above local terrain. It does not repair any previously consumed terrain.

`orbit_operator_only = true` permits single-player use and permission-level-2 operators on multiplayer servers. Setting it false permits other players. Each player receives a persistent separate station, spaced 1024 blocks apart (up to 4096 station assignments per world). The cannon cannot be fired in orbit. Normal player chunk loading supplies the cockpit; planet jobs temporarily request one additional chunk each and release it when moving on or stopping. These work tickets do not persist across server restarts.

Automated tests cover real cross-dimension travel, confirmation expiry, inventory/state round trips, player-save serialization, death attachment copying, drop suppression, login/respawn recovery handlers, dimension-command escape, emergency command permissions, separate cockpit allocation, viewport/void blocks, and rescue platforms. The isolated GameTest world uses a generated test-only flat preset containing the actual orbit dimension definition: vanilla GameTestServer otherwise drops custom dimensions. This fixture is not included in the mod JAR.

Manual acceptance still needed: sky/cockpit appearance, a real disconnect/rejoin, full death/respawn, graceful server restart while in orbit, and simultaneous clients. Other mods' custom player capabilities are not snapshotted; this is not a complete rollback of arbitrary modded player state. Existing black holes can continue feeding while you visit orbit.

## Terrain scan and planet preview

After entering orbit, an action-bar message shows **Terrain scan**, **Preparing the orbital display**, then **Building planet**. Keep the game unpaused. The default scan samples a 33-by-33 grid across the square extending 1500 blocks in each horizontal direction from your launch point. Its 1089 samples can take several minutes on unexplored terrain because Minecraft must generate the sampled chunks and their dependencies. The scan reads terrain without consuming it, but generation creates normal world data. Right-click to return at any time; leaving cancels the current job.

The completed planet is a hollow, **80-block-radius** sphere outside the forward viewport. It uses a fixed concrete/wool/stone/terracotta palette matched against the top blocks' map colors, including water and foliage. North is at the top and east at the right. A square-to-disk projection includes the scan's corners on the front hemisphere; the back repeats the projection for a complete colored shell. This is a coarse color preview, not a scaled replica of mountains/buildings. Each new visit rescans the launch region and clears the previous planet before building the new one.

For the whole planet, use **16-chunk render distance**; on a dedicated server, its view distance must also be sufficient (16 recommended). Stand at the forward glass deck and look down. The entire planet is below the ship. Soft ambient lighting helps the surface colors remain visible in the midnight sky. Planet construction is real block placement, so you may see the shell assemble in sections.

| Server config | Default | Meaning |
|---|---:|---|
| `planet_scan_radius` | 1500 | Half-width of the sampled square; range 16–3000. |
| `planet_sample_grid` | 33 | Samples per side; range 9–65. Higher values increase work. |
| `planet_radius` | 80 | Globe radius; range 60–100. |
| `planet_blocks_per_tick` | 512 | Global maximum shell placements/removals per server tick. |
| `planet_time_budget_ms` | 3 | Cooperative time budget for scan/build processing. |

Only one visitor's job advances each server tick, in rotation. Each job keeps at most one direct, non-ticking work ticket; Minecraft loads additional dependencies. Chunk readiness is polled without a blocking generation call. Up to eight samples and 8192 cursor steps are processed per selected tick, subject to the elapsed-time budget. Engine generation and lighting still add work outside that budget; this is not a hard limit on total server tick time.

Sample colors, source coordinates/dimension, projection version and planet geometry are saved per station. Incomplete builds are canceled on exit, logout, death, command dimension changes or server shutdown. Their shell geometry stays indexed for bounded cleanup on the next visit, including after a radius change. Active visits still return home on reconnect. Unfinished scans/builds are restarted on a new visit rather than resumed.

Six automated GameTests pass, including a full-size 80-block planet from known red/blue/green/sand terrain, north-up and corner mapping, water/transparent-top sampling, per-tick update limits, interrupted-build replacement, and the existing orbit/black-hole regression tests. Full 1500-radius generation performance on an ordinary unexplored world, client appearance and simultaneous multiplayer visits remain manual checks.

## Superlaser: aim, charge and fire

**This version permanently removes source-world terrain.** The entry warning reports the configured blast radius and requires the existing second use within ten seconds. Use a backed-up or disposable world. The required default radius remains **1000 blocks**; that can involve hundreds of millions of blocks and take a long time. For a quick first test, set `superlaser_radius` to **32 or 64** in the server config before launching the world. The development config is `run/config/worldeater-server.toml`; a per-world serverconfig override can take precedence. Stop the world before editing TOML manually.

After the globe is ready, select **Superlaser Trigger in hotbar slot 2**. Look down through the forward glass floor. A green ring marks the mathematical ray/sphere aim point, and the action bar shows the source X/Z and destruction radius. This works beyond vanilla reach; cockpit glass does not intercept targeting. The color preview and aim conversion use the same square/disk mapping.

Hold right-click for **four seconds** to fire. Releasing early, looking away from the globe, losing permission or leaving orbit cancels charging. On firing, the target and radius are fixed for that shot. A green particle beam strikes the globe and opens a surface crater. The source-world blast then processes chunks outward from the target in bounded batches, clipping every block to the configured 3D sphere. Its center Y is the current top surface at the target X/Z. The wave's visible/work front is chunk-granular rather than a perfectly smooth sphere.

The wave continues if you return, disconnect or restart the server normally. Its saved cursor resumes without needing the shooter online. You may still use **Death Star in slot 1** to return. A player cannot start another orbit visit while their previous shot is active, and each prepared planet can fire once. Once terrain work finishes, the block globe disappears with explosion particles and any active orbit visitor automatically returns with their saved state. If the return location was destroyed, the existing rescue-platform logic applies.

In the world launched from this project directory (`C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you`), these are **Minecraft chat commands**, not terminal commands:

- `/worldeater stoplaser` — operator-only; stops all active/paused superlaser jobs and releases tickets. It does not restore removed blocks.
- `/worldeater resumelaser` — operator-only; retries jobs paused after a processing error.
- `/worldeater return` — returns your active orbit visit; it does not cancel an already fired shot.
- `/worldeater stop` — existing operator command for black holes only.

| Server config | Default | Meaning |
|---|---:|---|
| `superlaser_radius` | 1000 | Source-world sphere radius, hard range 4–2000; captured on firing. |
| `superlaser_charge_ticks` | 80 | Hold duration (20 ticks ≈ 1 second). |
| `superlaser_speed` | 32 | Maximum wave expansion in blocks/second; work/loading can be slower. |
| `superlaser_blocks_per_tick` | 4096 | Global maximum source/model blocks directly removed per tick. |
| `superlaser_scans_per_tick` | 32768 | Global candidate/cursor check limit per tick. |
| `superlaser_time_budget_ms` | 4 | Cooperative processing budget; not a hard whole-server tick limit. |

A shot uses one transient non-ticking work ticket at a time, plus Minecraft's generation dependencies; the persistent job reacquires its current chunk after restart. Source chunks are visited once in outward order, then scanned top-down below the heightmap and within the sphere. Source removal honors the world border, `skip_unbreakable`, `skip_block_entities`, spawn/adventure restrictions and cancellable break events. With block-entity preservation disabled, container contents are also erased. Ordinary block drops are suppressed. Protection-mod compatibility still needs testing.

Gameplay and aiming are server-authoritative. Multiplayer firing requires operator permission when either orbit access or operator-only destruction is enabled. Vanilla heightmap/lighting bookkeeping stays enabled; neighbor cascades are suppressed. There is no speculative lighting bypass or unbounded vanilla explosion. A failing job pauses, releases its ticket and remains saved for the operator retry/stop commands. Normal Minecraft saves provide persistence; crash-time atomicity across job/player/chunk files is not guaranteed.

Nine automated GameTests pass. Added tests cover long-range hit/miss math, exact bounded-sphere cursor coverage after reload, charging cancellation/full charge, one-shot enforcement, source destruction and preserved protected blocks, fluid removal, a mid-wave save/reload, departure/shutdown behavior, stop permissions and final return. Full 1000-radius timing, final client visuals and actual network/process-restart cycles remain manual acceptance checks.

## Persistent behavior

- Impact anchors the hole to a block-center position. It is self-sustaining after that: owner death, logout or dimension travel do not automatically remove it.
- A serialized entity retains its radius, exact shell scan position, owner identity and pending Mass. A per-dimension SavedData index retains anchor locations and the current work ticket.
- One direct core ticket keeps the hole active. At most one additional direct work ticket is retained per hole. NeoForge can also load neighboring chunks to fulfill ticket dependencies; this is not a claim that only two chunks reside in memory.
- Work chunk requests are throttled across the dimension. Old work tickets are released on replacement, shell completion and removal. Startup validation removes transient/stale tickets; orphan anchor cleanup has a ten-second entity-loading grace period.
- Offline work uses the shooter's saved UUID for protection hooks and current operator checks. Offline-earned Mass is buffered up to the Mass cap, then delivered when that owner is available. Protection mods may treat this fake-player context differently from a real connected player.
- Empty air does not permanently starve the hole: baseline growth continues between shells. Radius does not race ahead of terrain processing. A static world would be scanned out toward the current dimension's world border; a full Minecraft world is enormous and will not finish in practical playtime.
- Unbreakable blocks, block entities, fluids and protected blocks remain according to the existing rules. Completed inner shells are not repeatedly rescanned, so newly placed blocks behind the expanding front are not guaranteed to be consumed.
- Entity effects use bounded core/frontier windows once the radius becomes large, rather than querying every entity inside a world-sized sphere. The shooter is excluded. Dropped items are pulled and remain recoverable. Other players respect PvP/team rules.

## Configuration and migration

NeoForge creates `config/worldeater-server.toml` for the running game/server (normally `run/config/worldeater-server.toml` in this development client). A world override may be placed in `<world>/serverconfig/worldeater-server.toml`. Server values control gameplay. Stop the world/server before manually editing its TOML.

The new `world_eating_radius_limit` and `persistent_lifetime_ticks` keys deliberately replace the old radius/lifetime keys. Old six-block/200-tick values do not carry over to the revised behavior. Other existing settings are retained by NeoForge.

| Setting | Default | Effect |
| --- | --- | --- |
| `world_eating_radius_limit` | 0 | Expand toward the world border. Positive values impose an optional whole-block radius cap (rounded down, minimum 1). |
| `persistent_lifetime_ticks` | 0 | Never expires. A positive value gives an optional lifetime measured from impact. |
| `passive_growth_per_second` | 0.25 | Baseline radius growth between completed shells. |
| `growth_per_mass` | 0.015 | Extra growth from feeding, bounded by the next shell. |
| `flight_ticks` | 100 | Settle after this many ticks if no block is hit. |
| `chunk_load_interval_ticks` | 20 | Minimum interval between new work-chunk requests in a dimension. |
| `blocks_per_tick` | 16 | Maximum directly consumed blocks per hole/tick (hard range 1–256). |
| `scans_per_tick` | 512 | Maximum scan steps per hole/tick (hard range 1–4096). Includes skipped columns/chunks. |
| `tick_budget_ms` | 2 | Cooperative time budget for normal entity/terrain work. |
| `projectile_speed` | 0.18 | Flight speed in blocks/tick. |
| `cannon_cooldown_ticks` | 40 | Two-second firing cooldown. |
| `max_active_per_level` | 8 | Maximum active cannon-created holes per dimension. Also at most two loaded holes per shooter. |
| `destroy_blocks` | true | Enables terrain consumption and expansion. Entity effects can remain active when disabled. |
| `consumed_blocks_drop_items` | false | Whether consumption produces vanilla block drops. |
| `operator_only_destruction` | true | Multiplayer terrain consumption requires permission level 2. |
| `skip_unbreakable` | true | Preserve bedrock and other negative-hardness blocks. |
| `skip_block_entities` | true | Preserve chests and other block entities. |
| `max_mass` | 10000 | Player Mass cap. |
| `mass_per_block` | 1 | Mass per consumed block. |
| `mass_per_mob` | 25 | Mass per mob killed by the core. |
| `mass_decay_per_second` | 1 | Online decay; 0 disables decay. |

Time budgets are cooperative: a vanilla update, protection callback or synchronous chunk-generation operation can exceed the deadline. Chunk requests are throttled, but no fixed maximum server tick time is promised. Vanilla lighting/neighbor updates remain enabled; their secondary effects may change additional blocks beyond the direct removal count. Restart restoration may load core chunks while restoring persistent tickets.

## Assets, scope and reports

Textures/models remain replaceable placeholders under `src/main/resources/assets/worldeater`. All game names/messages/config labels live in `lang/en_us.json`. The entity mesh is in `client/SingularityRenderer.java`. The visible mesh has constant complexity; ordinary entity tracking/view distance still limits visibility far from the center. Distant horizon rendering and high-load behavior remain unverified.

To regenerate placeholder art (overwrites generated assets):

```powershell
cd "C:\Users\MrSchneider\Documents\Codex\2026-09-30\files-pasted-by-the-user-you"
./scripts/generate-assets.ps1
```

No recipes, blade, armor or legendary weapon are implemented yet. Phase 2 was skipped; Phases 3–4 were accepted; Phase 5 is complete pending manual acceptance. Phase 6 polish has not begun. No mixins/custom shaders are used. The original inert test item is retained. The MDK version pins and template license remain unchanged; mod license remains All Rights Reserved. See `docs/research.md` and `docs/phase-5-report.md`. The older phase reports describe their archived builds, not this revision.



