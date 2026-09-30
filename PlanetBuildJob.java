package com.worldeater;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Cancellable scan -> old-shell cleanup -> surface build. All reads/writes run on the server thread. */
public final class PlanetBuildJob implements AutoCloseable {
    public enum Stage { SCAN, CLEAR, BUILD, DONE }
    private final ServerPlayer player;
    private final ServerLevel source, orbit;
    private final BlockPos center;
    private final int sourceX, sourceZ, scanRadius, grid, radius;
    private final int[] colors;
    private final BlockState[] palette;
    private final PlanetChunkLease lease = new PlanetChunkLease();
    private final CompoundTag data;
    private final ListTag shapes;
    private ExpandingShellCursor cursor;
    private Stage stage = Stage.SCAN;
    private int sampled, updated, lastUpdates;

    public PlanetBuildJob(ServerPlayer player, ServerLevel source, ServerLevel orbit, BlockPos station,
                          int sourceX, int sourceZ, int scanRadius, int grid, int radius) {
        this.player = player; this.source = source; this.orbit = orbit;
        this.sourceX = sourceX; this.sourceZ = sourceZ; this.scanRadius = scanRadius; this.grid = grid; this.radius = radius;
        center = PlanetProjection.center(station, radius);
        if (center.getY() - radius < orbit.getMinBuildHeight() || center.getY() + radius >= orbit.getMaxBuildHeight())
            throw new IllegalArgumentException("Planet does not fit dimension height");
        colors = new int[grid * grid]; palette = new BlockState[colors.length];
        data = OrbitStations.get(orbit).planet(player.getUUID());
        shapes = data.getList("Shapes", 10);
        data.putBoolean("Complete", false);
        data.putBoolean("Spent", false);
        persist();
    }
    public Stage stage() { return stage; }
    public int sampled() { return sampled; }
    public int lastUpdates() { return lastUpdates; }
    public boolean ticketHeld() { return lease.held(); }

    public void tick(int blockBudget, long deadline) {
        lastUpdates = 0;
        int steps = 0, samplesThisTick = 0;
        while (stage != Stage.DONE && steps++ < 8192 && lastUpdates < blockBudget && System.nanoTime() < deadline) {
            if (stage == Stage.SCAN) {
                if (samplesThisTick >= 8) break;
                int x = PlanetProjection.sampleCoordinate(sourceX, scanRadius, sampled % grid, grid);
                int z = PlanetProjection.sampleCoordinate(sourceZ, scanRadius, sampled / grid, grid);
                BlockPos column = new BlockPos(x, source.getMinBuildHeight(), z);
                // Sample locations outside the source border are marked as void, without generating them.
                if (!source.getWorldBorder().isWithinBounds(column)) {
                    colors[sampled] = MapColor.COLOR_BLACK.col;
                } else {
                    LevelChunk chunk = lease.request(source, new ChunkPos(column));
                    if (chunk == null) break;
                    colors[sampled] = sampleColor(source, chunk, x, z);
                }
                palette[sampled] = PlanetPalette.match(colors[sampled]);
                sampled++; samplesThisTick++;
                if (sampled == colors.length) {
                    lease.close();
                    data.putString("SourceDimension", source.dimension().location().toString());
                    data.putInt("SourceX", sourceX); data.putInt("SourceZ", sourceZ);
                    data.putInt("ScanRadius", scanRadius); data.putInt("Grid", grid);
                    data.putInt("ProjectionVersion", 1);
                    data.putLong("Center", center.asLong()); data.putInt("Radius", radius);
                    data.putIntArray("Colors", colors);
                    stage = Stage.CLEAR;
                    persist();
                }
            } else {
                if (cursor == null) {
                    if (stage == Stage.CLEAR) {
                        if (shapes.isEmpty()) {
                            CompoundTag shape = new CompoundTag();
                            shape.putLong("Center", center.asLong()); shape.putInt("Radius", radius);
                            shapes.add(shape); // Record shape BEFORE any new blocks; canceled jobs can be cleared next visit.
                            stage = Stage.BUILD; updated = 0;
                            cursor = PlanetProjection.shell(center, radius);
                            persist();
                        } else {
                            CompoundTag shape = shapes.getCompound(0);
                            cursor = PlanetProjection.shell(BlockPos.of(shape.getLong("Center")), Mth.clamp(shape.getInt("Radius"), 1, 100));
                        }
                    }
                }
                BlockPos pos = cursor.peek();
                if (pos != null) {
                    if (lease.request(orbit, new ChunkPos(pos)) == null) break;
                    BlockState state = stage == Stage.CLEAR ? Blocks.AIR.defaultBlockState()
                            : palette[PlanetProjection.sampleIndex(pos, center, radius, grid)];
                    // Normal light + client updates. Opaque inert palette needs no neighbor physics.
                    orbit.setBlock(pos, state, 2);
                    cursor.advance(); lastUpdates++; updated++;
                } else if (cursor.finished()) {
                    lease.close(); cursor = null;
                    if (stage == Stage.CLEAR) {
                        shapes.remove(0); persist();
                    } else {
                        stage = Stage.DONE;
                        data.putBoolean("Complete", true); persist();
                        player.sendSystemMessage(Component.translatable("message.worldeater.planet_ready", radius, scanRadius));
                    }
                }
            }
        }
    }
    public static int sampleColor(ServerLevel level, LevelChunk chunk, int x, int z) {
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            var state = chunk.getBlockState(pos);
            MapColor color = state.getMapColor(level, pos);
            if (!state.isAir() && color != MapColor.NONE) return color.col;
        }
        return MapColor.COLOR_BLACK.col;
    }
    public Component progress() {
        return switch (stage) {
            case SCAN -> Component.translatable("message.worldeater.planet_scan", sampled * 100 / colors.length, sampled, colors.length);
            case CLEAR -> Component.translatable("message.worldeater.planet_clear");
            case BUILD -> Component.translatable("message.worldeater.planet_build", Math.min(99, (int) (updated * 100.0 / (4 * Math.PI * radius * radius))));
            case DONE -> Component.translatable("message.worldeater.planet_complete");
        };
    }
    private void persist() { data.put("Shapes", shapes); OrbitStations.get(orbit).savePlanet(player.getUUID(), data); }
    @Override public void close() { lease.close(); }
}
