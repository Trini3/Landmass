package io.github.trini3.worldpreview;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import org.jetbrains.annotations.Nullable;

/**
 * Puts a new world's spawn where it was picked on the preview map. The Create World screen arms the picked point just before
 * it creates the world (singleplayer runs the server in the same game), and the new world's first spawn search uses it once.
 * Minecraft looks for solid, dry ground in the picked chunk, then in rings of chunks around it.
 */
public final class ChosenSpawn {
    private static final int SEARCH_RINGS = 3;
    @Nullable
    private static volatile BlockPos armed;

    private ChosenSpawn() {
    }

    /** Called by the Create World screen as it creates a world: the picked point, or null to leave the spawn to Minecraft. */
    public static void arm(@Nullable BlockPos picked) {
        armed = picked;
    }

    /** Sets the spawn of a new world whose spawn was picked on the preview map. Returns whether it did. */
    public static boolean apply(ServerLevel level, ServerLevelData levelData) {
        BlockPos picked = armed;
        armed = null;
        if (picked == null || level.dimension() != Level.OVERWORLD) {
            return false;
        }
        BlockPos spawn = find(level, picked);
        levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), spawn, 0.0F, 0.0F));
        WorldPreview.LOGGER.info("Spawn set to {}, {}, {}, near the point picked on the preview map ({}, {})",
                spawn.getX(), spawn.getY(), spawn.getZ(), picked.getX(), picked.getZ());
        return true;
    }

    /** Dry ground in or near the chunk of that point, or else the water's surface there. */
    public static BlockPos find(ServerLevel level, BlockPos picked) {
        ChunkPos center = ChunkPos.containing(picked);
        for (int ring = 0; ring <= SEARCH_RINGS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    @Nullable BlockPos found = PlayerSpawnFinder.getSpawnPosInChunk(level, new ChunkPos(center.x() + dx, center.z() + dz));
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        int y = level.getChunk(center.x(), center.z()).getHeight(Heightmap.Types.MOTION_BLOCKING, picked.getX() & 15, picked.getZ() & 15);
        return new BlockPos(picked.getX(), y, picked.getZ());
    }
}
