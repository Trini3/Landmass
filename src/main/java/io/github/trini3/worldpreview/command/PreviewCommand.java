package io.github.trini3.worldpreview.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.trini3.worldpreview.ChosenSpawn;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

import java.util.Locale;
import java.util.Set;

/**
 * The world map's right-click actions, as commands so that they check permissions like /tp and /setworldspawn do:
 * /twp tp x z teleports to the surface there, and /twp setspawn x z puts the world spawn on dry ground near there.
 * Both generate the spot first, since the height of land nobody has visited isn't known yet.
 */
public final class PreviewCommand {
    public static final String NAME = "twp";
    private static final int LIMIT = 29_999_000;

    private PreviewCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(NAME)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("tp").then(xz((source, x, z) -> teleport(source, x, z))))
                .then(Commands.literal("setspawn").then(xz((source, x, z) -> setSpawn(source, x, z)))));
    }

    private interface XzAction {
        int run(CommandSourceStack source, int x, int z) throws CommandSyntaxException;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> xz(XzAction action) {
        return Commands.argument("x", IntegerArgumentType.integer(-LIMIT, LIMIT))
                .then(Commands.argument("z", IntegerArgumentType.integer(-LIMIT, LIMIT))
                        .executes(context -> action.run(context.getSource(), IntegerArgumentType.getInteger(context, "x"),
                                IntegerArgumentType.getInteger(context, "z"))));
    }

    private static int teleport(CommandSourceStack source, int x, int z) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int y = surface(level, x, z);
        player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Teleported to %d, %d, %d", x, y, z)), true);
        return 1;
    }

    private static int setSpawn(CommandSourceStack source, int x, int z) {
        ServerLevel level = source.getLevel();
        BlockPos spawn;
        if (level.dimensionType().hasCeiling()) {
            level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
            spawn = new BlockPos(x, surface(level, x, z), z);
        } else {
            spawn = ChosenSpawn.find(level, new BlockPos(x, 0, z));
        }
        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), spawn, 0.0F, 0.0F));
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "World spawn set to %d, %d, %d in %s",
                spawn.getX(), spawn.getY(), spawn.getZ(), level.dimension().identifier())), true);
        return 1;
    }

    /** The first free spot on the ground: the top block, or under a ceiling (like the Nether's) the highest two air blocks over something solid. */
    private static int surface(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (!level.dimensionType().hasCeiling()) {
            return top;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = level.getMinY() + level.dimensionType().logicalHeight() - 2; y > level.getMinY(); y--) {
            if (level.getBlockState(pos.setY(y)).isAir() && level.getBlockState(pos.setY(y + 1)).isAir()
                    && level.getBlockState(pos.setY(y - 1)).isSolid()) {
                return y;
            }
        }
        return top;
    }
}
