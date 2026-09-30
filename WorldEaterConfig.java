package com.worldeater;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned limits. Hard upper bounds apply even to edited config files. */
public final class WorldEaterConfig {
    // Larger than the diagonal across Minecraft's default world border; not a small gameplay cap.
    public static final double HARD_MAX_RADIUS = 90000000.0;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ORBIT_OP_ONLY;
    public static final ModConfigSpec.IntValue PLANET_SCAN_RADIUS, PLANET_GRID, PLANET_RADIUS, PLANET_BLOCK_BUDGET;
    public static final ModConfigSpec.DoubleValue PLANET_TIME_BUDGET;
    public static final int LASER_HARD_MAX_RADIUS = 2000;
    public static final ModConfigSpec.IntValue LASER_RADIUS, LASER_BLOCKS, LASER_SCANS, LASER_CHARGE_TICKS;
    public static final ModConfigSpec.DoubleValue LASER_SPEED, LASER_TIME_BUDGET;
    public static final ModConfigSpec.DoubleValue MAX_RADIUS, TICK_BUDGET_MS, SPEED, GROWTH, PASSIVE_GROWTH;
    public static final ModConfigSpec.IntValue CHUNK_LOAD_INTERVAL, FLIGHT_TICKS;
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK, SCANS_PER_TICK, LIFETIME, COOLDOWN;
    public static final ModConfigSpec.IntValue MAX_ACTIVE, MAX_MASS, BLOCK_MASS, MOB_MASS, DECAY;
    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS, DROP_BLOCKS, OP_ONLY, SKIP_UNBREAKABLE, SKIP_BLOCK_ENTITIES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        LASER_RADIUS = b.translation("config.worldeater.laser_radius").comment("Destructive sphere radius in the source world. Large blasts can take a long time; use 32-64 for quick tests.")
                .defineInRange("superlaser_radius", 1000, 4, LASER_HARD_MAX_RADIUS);
        LASER_BLOCKS = b.translation("config.worldeater.laser_blocks").defineInRange("superlaser_blocks_per_tick", 4096, 16, 16384);
        LASER_SCANS = b.translation("config.worldeater.laser_scans").defineInRange("superlaser_scans_per_tick", 32768, 256, 131072);
        LASER_CHARGE_TICKS = b.translation("config.worldeater.laser_charge").defineInRange("superlaser_charge_ticks", 80, 20, 400);
        LASER_SPEED = b.translation("config.worldeater.laser_speed").comment("Maximum wave expansion in blocks per second. Removal/loading budgets can make actual progress slower.")
                .defineInRange("superlaser_speed", 32.0, 1.0, 256.0);
        LASER_TIME_BUDGET = b.translation("config.worldeater.laser_budget").defineInRange("superlaser_time_budget_ms", 4.0, 0.25, 10.0);
        PLANET_SCAN_RADIUS = b.translation("config.worldeater.planet_scan_radius")
                .comment("Half-width of the square sampled around the launch position. Samples may generate previously unexplored chunks.")
                .defineInRange("planet_scan_radius", 1500, 16, 3000);
        PLANET_GRID = b.translation("config.worldeater.planet_sample_grid")
                .comment("Samples per side of the color grid. 33 gives 1089 terrain samples; higher values take longer.")
                .defineInRange("planet_sample_grid", 33, 9, 65);
        PLANET_RADIUS = b.translation("config.worldeater.planet_radius").defineInRange("planet_radius", 80, 60, 100);
        PLANET_BLOCK_BUDGET = b.translation("config.worldeater.planet_blocks_per_tick")
                .comment("Global maximum planet surface placements/removals per server tick across all visitors.")
                .defineInRange("planet_blocks_per_tick", 512, 32, 2048);
        PLANET_TIME_BUDGET = b.translation("config.worldeater.planet_time_budget_ms")
                .comment("Global cooperative elapsed-time budget for scan/build work; engine chunk generation runs separately.")
                .defineInRange("planet_time_budget_ms", 3.0, 0.25, 10.0);
        ORBIT_OP_ONLY = b.translation("config.worldeater.orbit_operator_only")
                .comment("Death Star access is single-player or permission-level-2 operators by default. Each player has a separate orbit cockpit.")
                .define("orbit_operator_only", true);
        MAX_RADIUS = b.translation("config.worldeater.max_radius").comment("0 expands toward the world border indefinitely. A positive value imposes an optional radius limit. This new key replaces the old 6-block cap.")
                .defineInRange("world_eating_radius_limit", 0.0, 0.0, HARD_MAX_RADIUS);
        BLOCKS_PER_TICK = b.translation("config.worldeater.blocks_per_tick").comment("Maximum directly consumed blocks per singularity per tick; neighbor physics may break additional blocks.")
                .defineInRange("blocks_per_tick", 16, 1, 256);
        SCANS_PER_TICK = b.translation("config.worldeater.scans_per_tick").comment("Maximum candidate block positions checked per singularity per tick, including air.")
                .defineInRange("scans_per_tick", 512, 1, 4096);
        TICK_BUDGET_MS = b.translation("config.worldeater.tick_budget_ms").comment("Soft elapsed-time budget shared by pull/damage and block scanning. A vanilla operation cannot be interrupted mid-call.")
                .defineInRange("tick_budget_ms", 2.0, 0.1, 10.0);
        LIFETIME = b.translation("config.worldeater.lifetime_ticks").comment("0 never expires. Optional positive lifetime after impact, in ticks. Replaces the old 200-tick default.")
                .defineInRange("persistent_lifetime_ticks", 0, 0, 72000000);
        PASSIVE_GROWTH = b.translation("config.worldeater.passive_growth_per_second").comment("Radius growth per second even through air. Growth waits for the current shell to be consumed.")
                .defineInRange("passive_growth_per_second", 0.25, 0.01, 4.0);
        CHUNK_LOAD_INTERVAL = b.translation("config.worldeater.chunk_load_interval_ticks").comment("Minimum ticks between work-chunk requests across the dimension. Chunk generation itself is synchronous and may exceed the time budget.")
                .defineInRange("chunk_load_interval_ticks", 20, 1, 1200);
        FLIGHT_TICKS = b.translation("config.worldeater.flight_ticks").comment("Settle automatically after this many ticks if the projectile hits nothing.")
                .defineInRange("flight_ticks", 100, 1, 1200);
        SPEED = b.translation("config.worldeater.projectile_speed").defineInRange("projectile_speed", 0.18, 0.02, 0.5);
        GROWTH = b.translation("config.worldeater.growth_per_mass").defineInRange("growth_per_mass", 0.015, 0.0, 0.1);
        COOLDOWN = b.translation("config.worldeater.cannon_cooldown_ticks").defineInRange("cannon_cooldown_ticks", 40, 5, 1200);
        MAX_ACTIVE = b.translation("config.worldeater.max_active_per_level").comment("Maximum cannon-created singularities in loaded chunks of one dimension.")
                .defineInRange("max_active_per_level", 8, 1, 32);
        DESTROY_BLOCKS = b.translation("config.worldeater.destroy_blocks").define("destroy_blocks", true);
        DROP_BLOCKS = b.translation("config.worldeater.consumed_blocks_drop_items").define("consumed_blocks_drop_items", false);
        OP_ONLY = b.translation("config.worldeater.operator_only_destruction").comment("On multiplayer servers, only permission-level-2 operators may consume blocks. Entity effects remain enabled.")
                .define("operator_only_destruction", true);
        SKIP_UNBREAKABLE = b.translation("config.worldeater.skip_unbreakable").define("skip_unbreakable", true);
        SKIP_BLOCK_ENTITIES = b.translation("config.worldeater.skip_block_entities").comment("Preserve chests and other block entities by default.")
                .define("skip_block_entities", true);
        MAX_MASS = b.translation("config.worldeater.max_mass").defineInRange("max_mass", 10000, 1, 1000000);
        BLOCK_MASS = b.translation("config.worldeater.mass_per_block").defineInRange("mass_per_block", 1, 0, 1000);
        MOB_MASS = b.translation("config.worldeater.mass_per_mob").defineInRange("mass_per_mob", 25, 0, 10000);
        DECAY = b.translation("config.worldeater.mass_decay_per_second").defineInRange("mass_decay_per_second", 1, 0, 10000);
        SPEC = b.build();
    }

    private WorldEaterConfig() {}
}
