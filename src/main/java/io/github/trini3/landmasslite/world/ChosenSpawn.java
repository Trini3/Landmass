package io.github.trini3.landmasslite.world;

import io.github.trini3.landmasslite.Landmass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Puts a new world's spawn where it was picked on the Customize screen's preview map. Minecraft looks for
 * solid, dry ground in the picked chunk, then in rings of chunks around it, as it does around its own pick.
 * Bonus chests aren't placed for a picked spawn.
 */
public final class ChosenSpawn {
    /** How many rings of chunks around the picked one to search for dry ground before settling for the water's surface. */
    private static final int SEARCH_RINGS = 3;

    private ChosenSpawn() {
    }

    /** Sets the spawn of a new Landmass world whose spawn was picked on the preview map. Returns whether it did. */
    public static boolean apply(ServerLevel level, ServerLevelData levelData) {
        if (level.dimension() != Level.OVERWORLD || !(level.getChunkSource().getGenerator() instanceof LandmassGenerator generator)) {
            return false;
        }
        Optional<BlockPos> picked = generator.spawn();
        if (picked.isEmpty()) {
            return false;
        }
        BlockPos spawn = find(level, picked.get());
        levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), spawn, 0.0F, 0.0F));
        Landmass.LOGGER.info("Spawn set to {}, {}, {}, near the point picked on the preview map ({}, {})",
                spawn.getX(), spawn.getY(), spawn.getZ(), picked.get().getX(), picked.get().getZ());
        return true;
    }

    private static BlockPos find(ServerLevel level, BlockPos picked) {
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
        // Open ocean all around: the surface of the water at the picked point.
        int y = level.getChunk(center.x(), center.z()).getHeight(Heightmap.Types.MOTION_BLOCKING, picked.getX() & 15, picked.getZ() & 15);
        return new BlockPos(picked.getX(), y, picked.getZ());
    }
}
