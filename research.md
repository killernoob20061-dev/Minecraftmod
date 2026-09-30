# World Eater API research — through Phase 5

Target: Minecraft **1.21.1**, NeoForge **21.1.252**, Mojmap/Parchment. Primary evidence is the exact generated source archive `build/moddev/artifacts/neoforge-21.1.252-sources.jar`, plus versioned official documentation. Phases 3–4 were accepted in game; Phase 2 was skipped by request.

## Platform and registration

The [official 1.21.1 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/tree/7819b902a351b03fe71db00754d103b5a31c4ebf) was verified at commit `7819b902a351b03fe71db00754d103b5a31c4ebf`: NeoForge 21.1.252, ModDevGradle 2.0.148, Gradle 9.2.1, Java 21, Parchment 2024.11.17. These are template pins, not claims about other releases. Items, creative tab, entity type and attachments use DeferredRegister. Datapack dimension entries are data-driven. Ticket controllers and client effects use their dedicated registration events; vanilla TicketType has a factory rather than a registry. No mixins or copied third-party mod implementations are used.

## Entities, visuals and item use

[Entity networking docs](https://docs.neoforged.net/docs/1.21.1/networking/entities/) and exact sources confirm SynchedEntityData/Builder, EntityRenderersEvent and the pre-render-state EntityRenderer API. The singularity renders its own dark sphere and ring; logic runs on the server. Exact Item/LivingEntity sources confirm `use`, `getUseDuration(ItemStack, LivingEntity)`, `onUseTick(Level, LivingEntity, ItemStack, int)`, `startUsingItem` and `stopUsingItem`. Superlaser charge uses these hooks; remaining time counts downward. Release cancels without firing.

Phase 5 uses server-side quadratic ray/sphere intersection beyond block reach. The painted square/disk projection is also used for source coordinates. Exact `ServerLevel#sendParticles(ServerPlayer, ParticleOptions, boolean, ...)` supports targeted long-distance dust markers/beam and explosion particles. No actual vanilla explosion bypasses the work budget. New client visuals remain manually unverified.

## Player state, configuration and recovery

[Attachment docs](https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/) and exact sources support codec attachments, getData/setData and copyOnDeath. Mass uses an integer attachment. Orbit stores a CompoundTag snapshot with explicit Inventory, Abilities, FoodData, effect, health, XP and Mass serialization. It does not wholesale-load player NBT on return. Lifecycle events and PlayerTickEvent.Post recover visits after login, respawn and dimension escape; drop/toss events prevent duplication. PlayerList.save is protected; public saveAll flushes player snapshots.

[Config docs](https://docs.neoforged.net/docs/1.21.1/misc/config/) confirm ModConfigSpec and server-owned synchronized values. RegisterCommandsEvent supplies return, stop, stoplaser and resumelaser. Destructive command permissions are on individual subcommands. Phase 5 fire authorization and target data are checked again on the server after charging.

## Orbit and planet generation

Exact LevelStem, DimensionType, FlatLevelGeneratorSettings and biome codecs define the void/midnight dimension. RegisterDimensionSpecialEffectsEvent retains the vanilla star renderer, with black fog/sky and no clouds. The existing 0–256 height range is unchanged; the new bridge at Y=224 sits above every supported 60–100-radius globe.

MapItem, MapColor, BlockBehaviour and ChunkAccess verify WORLD_SURFACE height lookup, block map colors and the RGB field `MapColor.col`. Sampling skips map-transparent top blocks, including glass; water/foliage retain their colors. The fixed palette is matched by these map colors. An inverse elliptical-grid mapping fits the entire sampled square to the front hemisphere. SavedData stores source, colors, projection version and geometry. The chunk-ordered shell cursor avoids scanning the globe volume.

GameTestServer explicitly bakes only its flat preset against an empty LevelStem registry. A test-only Gradle task adds the production orbit JSON to that preset inside `work/run-gametest/world`. It does not modify ordinary worlds or the release JAR.

## Chunk loading and durable destruction

Exact NeoForge TicketController sources show that `forceChunk` synchronously obtains a chunk. Persistent singularities use that controller with throttled requests, a saved anchor index and core/frontier tickets. Exact `ServerChunkCache#getChunkFuture` also managed-blocks when called on the server thread, so preview/laser jobs instead use `addRegionTicket`, poll `getChunkNow` on later ticks, and `removeRegionTicket`. One transient distance-0 FULL ticket supplies a non-ticking work chunk; dependencies may load additional chunks. Shutdown releases leases. Durable laser SavedData stores target, radius, stage, wave and cursor; restart reacquires the current chunk. No persistent orphan work ticket is needed.

The laser traverses outward-sorted chunks and heightmap-bounded columns clipped to a 3D sphere. A server tick advances one job, with global removal/scan/time caps. FakePlayerFactory supplies the shooter identity in the source dimension. mayInteract, adventure restrictions, unbreakable/container options and cancellable BreakEvent are checked before mutation. Block.UPDATE_CLIENTS, UPDATE_KNOWN_SHAPE and UPDATE_SUPPRESS_DROPS retain normal heightmap/lighting bookkeeping while suppressing neighbor cascades and ordinary drops. No direct chunk-section or speculative lighting bypass is used, so no separate deferred relight pass is required. Individual engine calls, chunk generation, plan creation and callbacks may exceed a cooperative deadline.

## Verification limits

Nine integration tests pass, including protected removal, charge cancellation, sphere coverage/checkpoint recovery, a saved shot resumed mid-wave, lifecycle cleanup, operator stop and final return. No unresolved guessed API remains in compiled Phase 5 code. Tests use small destructive radii; full 1000-radius performance, new client visuals, actual disconnect/process-restart cycles and protection-mod compatibility remain unverified. Normal saves are not a cross-file crash-atomic transaction. Phase 6 polish has not begun.
