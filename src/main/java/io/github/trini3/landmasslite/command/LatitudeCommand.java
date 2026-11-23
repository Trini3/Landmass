package io.github.trini3.landmasslite.command;

import io.github.trini3.landmasslite.util.Climates;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.trini3.landmasslite.Landmass;
import io.github.trini3.landmasslite.climate.ClimateZone;
import io.github.trini3.landmasslite.climate.LatitudeAxis;
import io.github.trini3.landmasslite.climate.LatitudeLayout;
import io.github.trini3.landmasslite.climate.LatitudeTemperature;
import io.github.trini3.landmasslite.climate.WorldClimateSettings;
import io.github.trini3.landmasslite.climate.ClimateHooks;
import io.github.trini3.landmasslite.map.ClimateMapRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.storage.LevelResource;
import com.mojang.brigadier.CommandDispatcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * /latitude info: the active settings and the climate where you stand.
 * /latitude map climate|biomes [size] [blocksPerPixel]: saves a PNG map to the world folder.
 * /latitude tp x z: teleports to the surface there, generating the spot first (the world map's right-click uses it).
 */
public final class LatitudeCommand {
    public static final String MAP_FOLDER = Landmass.MODID + "-maps";
    private static final int DEFAULT_SIZE = 512;

    private LatitudeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("latitude")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("info").executes(context -> info(context.getSource())))
                .then(Commands.literal("map")
                        .then(mapMode("climate", ClimateMapRenderer.Mode.CLIMATE))
                        .then(mapMode("biomes", ClimateMapRenderer.Mode.BIOMES)))
                .then(Commands.literal("tp")
                        .then(Commands.argument("x", IntegerArgumentType.integer(-29_999_000, 29_999_000))
                                .then(Commands.argument("z", IntegerArgumentType.integer(-29_999_000, 29_999_000))
                                        .executes(context -> teleport(context.getSource(), IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "z")))))));
    }

    private static int teleport(CommandSourceStack source, int x, int z) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        // Loads (or generates) the chunk first, since the surface height of a chunk that isn't loaded is unknown.
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int y = surface(level, x, z);
        player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Teleported to %d, %d, %d", x, y, z)), true);
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

    private static LiteralArgumentBuilder<CommandSourceStack> mapMode(String name, ClimateMapRenderer.Mode mode) {
        return Commands.literal(name)
                .executes(context -> map(context.getSource(), mode, DEFAULT_SIZE, 0))
                .then(Commands.argument("size", IntegerArgumentType.integer(64, 2048))
                        .executes(context -> map(context.getSource(), mode, IntegerArgumentType.getInteger(context, "size"), 0))
                        .then(Commands.argument("blocksPerPixel", IntegerArgumentType.integer(1, 100_000))
                                .executes(context -> map(context.getSource(), mode, IntegerArgumentType.getInteger(context, "size"),
                                        IntegerArgumentType.getInteger(context, "blocksPerPixel")))));
    }

    private static int info(CommandSourceStack source) {
        WorldClimateSettings settings = ClimateHooks.active();
        if (settings == null) {
            source.sendFailure(Component.literal("This world wasn't made with the Landmass Lite world type, so it has no climate bands."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        LatitudeLayout layout = settings.latitude();
        boolean active = Climates.router(level.getChunkSource().randomState()).temperature() instanceof LatitudeTemperature;
        source.sendSuccess(() -> Component.literal(active
                ? "Climate bands are active in " + level.dimension().identifier() + "."
                : "Climate bands are not active in " + level.dimension().identifier() + " (they apply to " + settings.dimensionNames() + ").")
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        for (String line : layout.describe()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }

        BlockPos pos = BlockPos.containing(source.getPosition());
        Climate.TargetPoint point = Climates.sampler(level.getChunkSource().randomState())
                .sample(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
        double temperature = Climate.unquantizeCoord(point.temperature());
        int along = layout.axis().along(pos.getX(), pos.getZ());
        String side = along == layout.equator() ? "on the equator"
                : layout.axis() == LatitudeAxis.Z ? (along < layout.equator() ? "north of the equator" : "south of the equator")
                : (along < layout.equator() ? "west of the equator" : "east of the equator");
        int distance = Math.abs(along - layout.equator());
        String here = String.format(Locale.ROOT, "You are %,d blocks %s. Temperature here is %.2f (%s).",
                distance, side, temperature, ClimateZone.of(temperature).displayName());
        source.sendSuccess(() -> Component.literal(here), false);
        if (layout.repeats() && distance > layout.poleDistance()) {
            String past = String.format(Locale.ROOT, "You're past the pole, so the climate here is like %,d blocks from an equator.",
                    Math.round(layout.foldAtPoles(distance)));
            source.sendSuccess(() -> Component.literal(past), false);
        }
        source.sendSuccess(() -> Component.literal("This world's settings file: " + WorldClimateSettings.file(source.getServer())), false);
        return 1;
    }

    private static int map(CommandSourceStack source, ClimateMapRenderer.Mode mode, int size, int blocksPerPixel) {
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        WorldClimateSettings settings = ClimateHooks.active();
        LatitudeLayout layout = settings != null && settings.appliesTo(level.dimension()) ? settings.latitude() : null;

        BlockPos pos = BlockPos.containing(source.getPosition());
        int centerX = pos.getX();
        int centerZ = pos.getZ();
        int step = blocksPerPixel;
        if (layout != null) {
            // Centre on the equator and fit both poles (and where the zones repeat, some of the way back), unless a scale was given.
            if (layout.axis() == LatitudeAxis.Z) {
                centerZ = layout.equator();
            } else {
                centerX = layout.equator();
            }
            if (step <= 0) {
                step = (int) Math.ceil(layout.mapSpan() / size);
            }
        }
        if (step <= 0) {
            step = 16;
        }

        ClimateMapRenderer.Request request = new ClimateMapRenderer.Request(mode, centerX, centerZ, size, step);
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve(MAP_FOLDER).normalize();
        int finalStep = step;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Drawing a %dx%d map at %,d blocks per pixel (%,d blocks across). This can take a little while...", size, size, finalStep, size * finalStep)), false);

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return ClimateMapRenderer.render(level, layout, request, folder);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null) {
                        Landmass.LOGGER.error("Couldn't draw the climate map", error);
                        source.sendFailure(Component.literal("Couldn't draw the map: " + error.getMessage()));
                        return;
                    }
                    String path = result.file().toAbsolutePath().toString();
                    // A link that opens the file only works for the player at the computer that saved it, and Minecraft
                    // rejects such links coming from other servers, so on dedicated and LAN servers it copies the path instead.
                    boolean local = !server.isDedicatedServer() && !server.isPublished();
                    ClickEvent click = local ? new ClickEvent.OpenFile(path) : new ClickEvent.CopyToClipboard(path);
                    source.sendSuccess(() -> Component.literal(local ? "Saved map: " : "Saved map (click to copy the path): ")
                            .append(Component.literal(path).withStyle(style -> style
                                    .withUnderlined(true)
                                    .withClickEvent(click))), false);
                }));
        return 1;
    }
}
