# Phase 1 — Singularity Cannon and Mass

## Result

Implemented Phase 1 as World Eater **0.2.0** for Minecraft **1.21.1**, NeoForge **21.1.252**. Phase 0 remains available in its archived source ZIP/JAR. Stopped before Phase 2.

Right-clicking the cannon spawns a server-controlled slow singularity. It pulls nearby living entities and dropped items, consumes allowed blocks, damages mobs in its core, grows with consumption and expires. The firing player is excluded. Other players are affected only when survival/adventure and PvP/team rules allow it. Dropped items are pulled but remain recoverable and award no Mass. Mob kills award Mass once; living mobs can survive initial hits. Phase 1 uses vanilla indirect-magic damage; custom damage and armor protection remain Phase 2 work.

Mass is a serialized player data attachment, capped and preserved across save/load and death. A server-sent translated action-bar meter appears once per second while holding the cannon or while Mass is nonzero. Mass decays while online; the cannon has no Mass/ammunition cost in this phase.

## Files created or changed

Paths below are relative to the editable project directory.

| File | Change |
| --- | --- |
| `src/main/java/com/worldeater/WorldEater.java` | Registers cannon, entity, attachments and server config; adds cannon to the creative tab |
| `src/main/java/com/worldeater/WorldEaterConfig.java` | Validated server settings with translated labels |
| `src/main/java/com/worldeater/Mass.java` | Persistent attachment, clamp, gain, decay and basic meter |
| `src/main/java/com/worldeater/SingularityCannonItem.java` | Server-owned firing, cooldown and active-entity limits |
| `src/main/java/com/worldeater/SingularityEntity.java` | Pull, damage, bounded block consumption, growth, persistence, expiry and client interpolation |
| `src/main/java/com/worldeater/client/WorldEaterClient.java` | Client-only renderer registration and config screen |
| `src/main/java/com/worldeater/client/SingularityRenderer.java` | Dark sphere and segmented rotating ring with vanilla rendering |
| `src/main/java/com/worldeater/gametest/PhaseOneGameTests.java` | Development-only server integration suite |
| `src/main/resources/data/worldeater/structure/test_empty.nbt` | Generated isolated test fixture |
| `src/main/resources/assets/worldeater/lang/en_us.json` | Cannon, entity, messages and config labels |
| `src/main/resources/assets/worldeater/models/item/singularity_cannon.json` | Replaceable handheld model |
| `src/main/resources/assets/worldeater/textures/item/singularity_cannon.png` | Scripted 16x16 placeholder |
| `src/main/resources/assets/worldeater/textures/entity/singularity.png` | Scripted 16x16 tintable entity texture |
| `scripts/generate-assets.ps1` | Generates Phase 0 and Phase 1 art |
| `scripts/GenerateTestStructure.java` | Reproducible compressed NBT fixture generator |
| `build.gradle` | Isolated GameTest working directory; excludes test class/fixture from release JAR |
| `gradle.properties`, `src/main/templates/META-INF/neoforge.mods.toml` | Version 0.2.0 and Phase 1 description |
| `README.md`, `docs/research.md`, `docs/phase-1-report.md` | Updated instructions, verified APIs and this report |

The original test-item assets were regenerated without a design change. Gradle wrapper/version pins are unchanged. No mixins are used. All registry entries use DeferredRegister; client rendering registration uses NeoForge's dedicated renderer event.

## Validation

`./scripts/dev.ps1 runGameTestServer --no-daemon` passed: **all 1 required tests passed**, exit code 0. This is one integration suite with multiple assertions, not a claim of multiple independently scheduled GameTests. It checked:

- Mass maximum/minimum, once-per-second decay and actual player NBT attachment save/load.
- Operator versus non-operator block-destruction permission.
- Consumption really occurs and awards the expected Mass; per-tick removal and scan caps hold; radius grows but stays capped.
- Bedrock, default-protected chest and a block protected by a canceled BreakEvent remain intact.
- Core mob death awards Mass once, dropped items pull inward and remain alive, and disabling block destruction stops removals.
- Entity radius save/load, finite lifetime, cannon projectile creation and cooldown rejection.

The test runs in `work/run-gametest`, with its own world/config. It does not load the user's `run/saves` world. Test classes and fixture are excluded from the distributable JAR. The first test-server launch logged a missing initial `server.properties` message, then generated its environment and passed. Compiler/upstream Gradle deprecation notices do not prevent this pinned build.

Final Gradle build and packaged-asset checks are recorded in `outputs/build-phase1.log`. The client renderer and multiplayer connections have not been visually/runtime tested here.

## What to test in game

Close any earlier development client, then from the project directory run:

```powershell
./scripts/dev.ps1 runClient --no-daemon
```

1. Use a disposable creative test world: this phase now removes terrain. Confirm Mods shows **World Eater 0.2.0**.
2. Take **Singularity Cannon** from the World Eater tab, or run `/give @s worldeater:singularity_cannon`.
3. Aim across nearby dirt/stone and right-click. Expect a slowly moving dark sphere, visibly rotating purple ring and particles. It should carve nearby blocks and disappear after about 10 seconds.
4. Hold the cannon and watch the **Mass** action-bar meter rise as blocks/mobs are consumed. After it ends, Mass should decrease by 1 per second.
5. Try nearby chickens and dropped dirt. Mobs should pull inward and take damage; items should pull inward and remain recoverable. The shooter should not be pulled.
6. Bedrock and chests should remain. There is a 2-second firing cooldown, at most 2 loaded singularities per shooter and 8 per dimension by default.
7. Save/quit and rejoin: Mass should persist. Existing singularities retain their radius and expiry; they do not force-load chunks.
8. Optional server checks: non-operators cannot consume blocks by default; other players respect PvP/team rules. Toggle drop/destruction options and confirm behavior. These multiplayer checks remain unverified by the isolated suite.

## Defaults, limitations and remaining uncertainty

Defaults: maximum consumption radius 6 (absolute configurable ceiling 16), 16 directly consumed blocks/tick, 512 candidate positions/tick, 2 ms cooperative work budget, 200-tick lifetime, speed 0.18 blocks/tick, 40-tick cooldown, no consumed-block drops, operator-only multiplayer block destruction, preserve unbreakable blocks and block entities. Mass: +1/block, +25/mob kill, maximum 10,000, decay 1/second. Config is `worldeater-server.toml`; see README for locations.

Time budgets are cooperative: a vanilla block update, entity query or protection event may itself exceed the deadline. They are not a guarantee of a fixed server tick duration. Removal counts limit direct singularity operations; vanilla neighbor physics (falling blocks, unsupported plants, etc.) can produce additional changes. No lighting bypasses are used. Liquids/waterlogged blocks are skipped. Protection mods can cancel standard BreakEvents; compatibility with every protection mod is not established.

Singularities do not load chunks, and discard when moving into an unloaded chunk, out of world bounds or beyond the world border. They discard when their owner is unavailable, dead, spectating or in a different dimension. Lifetime uses world time plus loaded age, so leaving/reloading does not reset it. Entity processing is capped at 64 nearby candidates per tick; dense piles may not all be processed. Active caps apply to loaded cannon-created entities, not administrative summons or all unloaded entities.

The meter temporarily shares Minecraft's action-bar area with other messages. Textures/models are placeholders and the ring/sphere still need the user visual acceptance check. No recipe, survival balance pass, armor, other weapons or Death Star/orbit content is included yet. No major design changes were made.
