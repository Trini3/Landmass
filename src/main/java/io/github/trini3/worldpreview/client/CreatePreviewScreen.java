package io.github.trini3.worldpreview.client;

import io.github.trini3.worldpreview.map.MapRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A map of the world being set up on the Create World screen, opened from the World tab's World Preview button. It is drawn from
 * the selected world type's own overworld generator and the seed, so it shows whatever world type, datapacks and biome mods are
 * chosen. Right-clicking picks the spawn point, and New seed re-rolls the seed (it goes into the World tab's seed box).
 */
public class CreatePreviewScreen extends MapScreen {
    private static final int MAP_TOP = 38;
    // Remembered while the game runs, so the preview comes back the way it was left.
    private static int lastBlocksPerPixel = 32;
    private static MapRenderer.Mode lastMode = MapRenderer.Mode.BIOMES;

    private final CreateWorldScreen parent;
    @Nullable
    private Button autoSpawn;
    // The seed and dimensions the cached noise was made for; it is the slow part to set up.
    @Nullable
    private volatile RandomState randomState;
    @Nullable
    private volatile WorldCreationContext randomStateFor;

    public CreatePreviewScreen(CreateWorldScreen parent) {
        super(Component.translatable("triniworldpreview.preview.title"), "create_preview", lastMode, lastBlocksPerPixel);
        this.parent = parent;
    }

    private WorldCreationContext context() {
        return this.parent.getUiState().getSettings();
    }

    private long seed() {
        return this.context().options().seed();
    }

    private Optional<BlockPos> spawn() {
        return PendingSpawn.get(this.parent);
    }

    @Override
    protected boolean available() {
        return true;
    }

    @Override
    protected void init() {
        int bottom = this.height - 28;
        int left = this.width / 2 - 158;
        this.layoutMap(MAP_TOP, bottom - 6, bottom, left);
        this.autoSpawn = this.addRenderableWidget(Button.builder(Component.translatable("triniworldpreview.preview.auto_spawn"),
                button -> this.setSpawn(Optional.empty())).bounds(left + 152, bottom, 80, 20).build());
        this.autoSpawn.active = this.spawn().isPresent();
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).bounds(left + 236, bottom, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("triniworldpreview.preview.new_seed"), button -> this.newSeed())
                .bounds(this.width - 84, 4, 80, 20).build())
                .setTooltip(Tooltip.create(Component.translatable("triniworldpreview.preview.new_seed.hint")));
    }

    @Override
    protected void viewChanged() {
        lastMode = this.mode;
        lastBlocksPerPixel = this.blocksPerPixel;
    }

    /** Re-rolls the seed: the World tab's seed box gets it, so the new world is made with it, and the map is redrawn. */
    private void newSeed() {
        this.parent.getUiState().setSeed(Long.toString(ThreadLocalRandom.current().nextLong()));
        this.redraw();
    }

    @Override
    protected Source source() {
        WorldCreationContext context = this.context();
        ChunkGenerator generator = context.selectedDimensions().overworld();
        return new Source(MapRenderer.sampler(this.randomState(context, generator)), generator.getBiomeSource());
    }

    /**
     * The world's noise, made the way the world will make it. Any mod that changes worldgen through RandomState (TerraBlender
     * for Biomes O' Plenty and Regions Unexplored, for example) applies here the same way.
     */
    private RandomState randomState(WorldCreationContext context, ChunkGenerator generator) {
        RandomState state = this.randomState;
        // The context changes with the seed, the world type and datapacks, and a map still drawing may have cached an older one.
        if (state == null || this.randomStateFor != context) {
            HolderGetter<NormalNoise> noises = context.worldgenLoadContext().lookupOrThrow(Registries.NOISE);
            // Flat and other generators without noise settings still have a biome source; normal overworld noise drives it.
            NoiseGeneratorSettings settings = generator instanceof NoiseBasedChunkGenerator noise ? noise.generatorSettings().value()
                    : context.worldgenLoadContext().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
            state = RandomState.create(noises, context.options().seed(), settings);
            this.randomState = state;
            this.randomStateFor = context;
        }
        return state;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        Component title = this.overMap(mouseX, mouseY)
                ? Component.translatable("triniworldpreview.preview.title_at", Long.toString(this.seed()),
                        format(Math.round(this.blockX(mouseX))), format(Math.round(this.blockZ(mouseY))))
                : Component.translatable("triniworldpreview.preview.title_seed", Long.toString(this.seed()));
        graphics.centeredText(this.font, title, this.width / 2, 5, WHITE);
        graphics.centeredText(this.font, Component.translatable("triniworldpreview.preview.hint", format(this.blocksPerPixel)),
                this.width / 2, 16, GREY);
        graphics.centeredText(this.font, this.spawn()
                .map(pos -> Component.translatable("triniworldpreview.preview.spawn", format(pos.getX()), format(pos.getZ())))
                .orElseGet(() -> Component.translatable("triniworldpreview.preview.spawn_auto")), this.width / 2, 27, GREY);
        this.drawMap(graphics, mouseX, mouseY, true);
    }

    @Override
    protected void drawMarkers(GuiGraphicsExtractor graphics) {
        this.spawn().ifPresent(pos -> this.marker(graphics, pos.getX(), pos.getZ(), 0xFFFFC20E));
    }

    @Override
    protected void rightClicked(double blockX, double blockZ, double mouseX, double mouseY) {
        this.setSpawn(Optional.of(new BlockPos((int) Math.round(blockX), 0, (int) Math.round(blockZ))));
    }

    /** Picks the spawn point, or leaves it to Minecraft when empty. */
    private void setSpawn(Optional<BlockPos> spawn) {
        PendingSpawn.set(this.parent, spawn);
        if (this.autoSpawn != null) {
            this.autoSpawn.active = spawn.isPresent();
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
