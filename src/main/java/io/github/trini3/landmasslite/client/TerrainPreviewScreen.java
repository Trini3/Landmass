package io.github.trini3.landmasslite.client;

import io.github.trini3.landmasslite.util.Climates;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.trini3.landmasslite.Landmass;
import io.github.trini3.landmasslite.climate.ClimateHooks;
import io.github.trini3.landmasslite.climate.LatitudeAxis;
import io.github.trini3.landmasslite.climate.LatitudeLayout;
import io.github.trini3.landmasslite.climate.ModNoises;
import io.github.trini3.landmasslite.climate.WorldClimateSettings;
import io.github.trini3.landmasslite.map.ClimateMapRenderer;
import io.github.trini3.landmasslite.world.LandmassGenerator;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * A map of the world being set up, drawn from the Customize screen's current values and the seed on the
 * World tab, the same way /latitude map draws a running world. Drag to move it and scroll (or use the
 * buttons) to zoom. Right-clicking picks the spawn point, which the Customize screen saves with the world.
 * Maps are drawn in the background, and a newer request replaces an unfinished one.
 */
public class TerrainPreviewScreen extends Screen {
    private static final Identifier TEXTURE = Landmass.id("customize_preview");
    private static final int MAX_BLOCKS_PER_PIXEL = 1 << 16;
    private static final double WORLD_LIMIT = 30_000_000;
    private static final int MAP_TOP = 38;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;

    private final Screen parent;
    private final WorldClimateSettings climate;
    private final WorldCreationContext context;
    private final Consumer<Optional<BlockPos>> onSpawnChanged;
    private final LongConsumer onSeedChanged;
    private volatile long seed;
    private final AtomicInteger generation = new AtomicInteger();
    private Optional<BlockPos> spawn;
    @Nullable
    private Button autoSpawn;
    private ClimateMapRenderer.Mode mode = ClimateMapRenderer.Mode.CLIMATE;
    // What the map should show. The centre is a double so that dragging can move it by less than a pixel's worth of blocks.
    private double centerX;
    private double centerZ;
    private int blocksPerPixel;
    // The picture on screen and what it shows, which differs from the view while a new one is being drawn.
    @Nullable
    private DynamicTexture texture;
    private double imageCenterX;
    private double imageCenterZ;
    private int imageBlocksPerPixel;
    private int imageSize;
    @Nullable
    private volatile RandomState randomState;
    private volatile long randomStateSeed;
    private boolean drawing;
    private boolean failed;
    private boolean dragging;
    private final BiomeHover hover = new BiomeHover();
    private int mapLeft;
    private int mapSize;

    public TerrainPreviewScreen(Screen parent, WorldClimateSettings climate, WorldCreationContext context,
                                Optional<BlockPos> spawn, Consumer<Optional<BlockPos>> onSpawnChanged, LongConsumer onSeedChanged) {
        super(Component.translatable("landmass_lite.preview.title"));
        this.parent = parent;
        this.climate = climate;
        this.context = context;
        this.spawn = spawn;
        this.onSpawnChanged = onSpawnChanged;
        this.onSeedChanged = onSeedChanged;
        this.seed = context.options().seed();
        // Centred on the equator, far enough out to show both poles, like /latitude map.
        LatitudeLayout layout = climate.latitude();
        if (layout.axis() == LatitudeAxis.Z) {
            this.centerZ = layout.equator();
        } else {
            this.centerX = layout.equator();
        }
    }

    @Override
    protected void init() {
        int bottom = this.height - 28;
        this.mapSize = Math.max(32, Math.min(this.width - 20, bottom - 6 - MAP_TOP));
        this.mapLeft = (this.width - this.mapSize) / 2;
        if (this.blocksPerPixel == 0) {
            this.blocksPerPixel = Math.clamp((int) Math.ceil(this.climate.latitude().mapSpan() / this.mapSize), 1, MAX_BLOCKS_PER_PIXEL);
        }

        int left = this.width / 2 - 158;
        this.addRenderableWidget(Button.builder(this.modeName(), button -> {
            this.mode = this.mode == ClimateMapRenderer.Mode.CLIMATE ? ClimateMapRenderer.Mode.BIOMES : ClimateMapRenderer.Mode.CLIMATE;
            button.setMessage(this.modeName());
            this.redraw();
        }).bounds(left, bottom, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("-"), button -> this.zoom(2)).bounds(left + 104, bottom, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> this.zoom(0.5)).bounds(left + 128, bottom, 20, 20).build());
        this.autoSpawn = this.addRenderableWidget(Button.builder(Component.translatable("landmass_lite.preview.auto_spawn"),
                button -> this.setSpawn(Optional.empty())).bounds(left + 152, bottom, 80, 20).build());
        this.autoSpawn.active = this.spawn.isPresent();
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).bounds(left + 236, bottom, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("landmass_lite.preview.new_seed"), button -> this.newSeed())
                .bounds(this.width - 84, 4, 80, 20).build())
                .setTooltip(Tooltip.create(Component.translatable("landmass_lite.preview.new_seed.hint")));
        this.redraw();
    }

    /** Re-rolls the seed: the map is redrawn with it, and the new world gets it (it goes into the World tab's seed box). */
    private void newSeed() {
        this.seed = ThreadLocalRandom.current().nextLong();
        this.onSeedChanged.accept(this.seed);
        this.randomState = null;
        this.redraw();
    }

    private Component modeName() {
        return Component.translatable(this.mode == ClimateMapRenderer.Mode.CLIMATE ? "landmass_lite.preview.climate" : "landmass_lite.preview.biomes");
    }

    private void zoom(double factor) {
        int zoomed = Math.clamp(Math.round(this.blocksPerPixel * factor), 1, MAX_BLOCKS_PER_PIXEL);
        if (zoomed != this.blocksPerPixel) {
            this.blocksPerPixel = zoomed;
            this.redraw();
        }
    }

    /** Starts drawing the map as it should now look, abandoning any map still being drawn. */
    private void redraw() {
        // Kept within the world border, which also keeps block coordinates well inside int range.
        this.centerX = Math.clamp(this.centerX, -WORLD_LIMIT, WORLD_LIMIT);
        this.centerZ = Math.clamp(this.centerZ, -WORLD_LIMIT, WORLD_LIMIT);
        int request = this.generation.incrementAndGet();
        this.drawing = true;
        this.failed = false;
        ClimateMapRenderer.Request map = new ClimateMapRenderer.Request(this.mode, (int) Math.round(this.centerX), (int) Math.round(this.centerZ),
                this.mapSize, this.blocksPerPixel);
        Minecraft minecraft = this.minecraft;
        Holder<Biome>[] biomes = BiomeHover.grid(map);
        CompletableFuture
                .supplyAsync(() -> {
                    Optional<LandmassGenerator> generator = LandmassGenerator.of(this.context.selectedDimensions());
                    if (generator.isEmpty()) {
                        throw new IllegalStateException("The world type isn't Landmass Lite");
                    }
                    return ClimateMapRenderer.draw(Climates.sampler(this.randomState(generator.get())), generator.get().getBiomeSource(),
                            this.climate.latitude(), map, null, biomes, () -> this.generation.get() == request);
                })
                .whenComplete((image, error) -> minecraft.execute(() -> this.show(request, map, image, error, biomes)));
    }

    /** The world's noise with the Customize screen's climate, made once, since it's the slow part to set up. */
    private RandomState randomState(LandmassGenerator generator) {
        RandomState state = this.randomState;
        long seed = this.seed;
        // A map still drawing with the old seed may have cached its state after a re-roll, so the seed is checked too.
        if (state == null || this.randomStateSeed != seed) {
            HolderGetter<NormalNoise> noises = this.context.worldgenLoadContext().lookupOrThrow(Registries.NOISE);
            NoiseGeneratorSettings settings = generator.generatorSettings().value();
            Optional<Holder.Reference<NormalNoise>> wander = noises.get(ModNoises.BORDER_WANDER);
            if (wander.isPresent() && this.climate.appliesTo(Level.OVERWORLD)) {
                settings = ClimateHooks.withClimate(settings, wander.get(), this.climate, seed, null);
            }
            state = RandomState.create(noises, seed, settings);
            this.randomState = state;
            this.randomStateSeed = seed;
        }
        return state;
    }

    private void show(int request, ClimateMapRenderer.Request map, @Nullable BufferedImage image, @Nullable Throwable error,
                      @Nullable Holder<Biome>[] biomes) {
        if (request != this.generation.get()) {
            return;
        }
        this.drawing = false;
        if (error != null) {
            Landmass.LOGGER.error("Couldn't draw the world preview", error);
            this.failed = true;
            return;
        }
        if (image == null) {
            return;
        }
        int size = image.getWidth();
        NativeImage pixels = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = image.getRGB(x, y);
                // NativeImage wants alpha, blue, green, red.
                pixels.setPixelABGR(x, y, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 0xFF);
            }
        }
        // Registering under the same name frees the previous picture.
        DynamicTexture texture = new DynamicTexture(TEXTURE::toString, pixels);
        this.minecraft.getTextureManager().register(TEXTURE, texture);
        this.texture = texture;
        this.imageCenterX = map.centerX();
        this.imageCenterZ = map.centerZ();
        this.imageBlocksPerPixel = map.blocksPerPixel();
        this.imageSize = size;
        this.hover.set(map, biomes);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, Component.translatable("landmass_lite.preview.title_seed", Long.toString(this.seed)), this.width / 2, 5, WHITE);
        graphics.centeredText(this.font, Component.translatable("landmass_lite.preview.hint", format(this.blocksPerPixel)),
                this.width / 2, 16, GREY);
        graphics.centeredText(this.font, this.spawn
                .map(pos -> Component.translatable("landmass_lite.preview.spawn", format(pos.getX()), format(pos.getZ())))
                .orElseGet(() -> Component.translatable("landmass_lite.preview.spawn_auto")), this.width / 2, 27, GREY);

        int top = MAP_TOP;
        int right = this.mapLeft + this.mapSize;
        int bottom = top + this.mapSize;
        graphics.fill(this.mapLeft - 1, top - 1, right + 1, bottom + 1, 0xFF000000);
        if (this.texture != null) {
            // The last finished picture, moved and scaled to match the view until the new one is ready.
            double scale = this.imageBlocksPerPixel / (double) this.blocksPerPixel;
            int drawnSize = (int) Math.round(this.imageSize * scale);
            int x = (int) Math.round(this.mapLeft + this.mapSize / 2.0 + (this.imageCenterX - this.centerX) / this.blocksPerPixel - drawnSize / 2.0);
            int y = (int) Math.round(top + this.mapSize / 2.0 + (this.imageCenterZ - this.centerZ) / this.blocksPerPixel - drawnSize / 2.0);
            graphics.enableScissor(this.mapLeft, top, right, bottom);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, drawnSize, drawnSize, this.imageSize, this.imageSize, this.imageSize, this.imageSize);
            graphics.disableScissor();
        }
        if (this.spawn.isPresent()) {
            // A gold square with a black edge, which shows up on every colour of the map.
            int x = (int) Math.round(this.mapLeft + this.mapSize / 2.0 + (this.spawn.get().getX() - this.centerX) / this.blocksPerPixel);
            int y = (int) Math.round(top + this.mapSize / 2.0 + (this.spawn.get().getZ() - this.centerZ) / this.blocksPerPixel);
            graphics.enableScissor(this.mapLeft, top, right, bottom);
            graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFC20E);
            graphics.disableScissor();
        }
        if (this.texture != null && this.overMap(mouseX, mouseY)) {
            Component biome = this.hover.at(this.centerX + (mouseX - (this.mapLeft + this.mapSize / 2.0)) * this.blocksPerPixel,
                    this.centerZ + (mouseY - (MAP_TOP + this.mapSize / 2.0)) * this.blocksPerPixel);
            if (biome != null) {
                BiomeHover.draw(graphics, this.font, biome, this.mapLeft, top);
            }
        }
        if (this.failed) {
            graphics.centeredText(this.font, Component.translatable("landmass_lite.preview.failed"), this.width / 2, top + this.mapSize / 2 - 4, RED);
        } else if (this.drawing) {
            graphics.centeredText(this.font, Component.translatable("landmass_lite.preview.drawing"), this.width / 2, bottom - 12, WHITE);
        }
    }

    private boolean overMap(double mouseX, double mouseY) {
        return mouseX >= this.mapLeft && mouseX < this.mapLeft + this.mapSize && mouseY >= MAP_TOP && mouseY < MAP_TOP + this.mapSize;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && this.overMap(mouseX, mouseY)) {
            this.dragging = true;
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT && this.overMap(mouseX, mouseY)) {
            double x = this.centerX + (mouseX - (this.mapLeft + this.mapSize / 2.0)) * this.blocksPerPixel;
            double z = this.centerZ + (mouseY - (MAP_TOP + this.mapSize / 2.0)) * this.blocksPerPixel;
            this.setSpawn(Optional.of(new BlockPos((int) Math.round(Math.clamp(x, -WORLD_LIMIT, WORLD_LIMIT)), 0,
                    (int) Math.round(Math.clamp(z, -WORLD_LIMIT, WORLD_LIMIT)))));
            return true;
        }
        return false;
    }

    /** Picks the spawn point (or leaves it to Minecraft, when empty), here and on the Customize screen. */
    private void setSpawn(Optional<BlockPos> spawn) {
        this.spawn = spawn;
        this.onSpawnChanged.accept(spawn);
        if (this.autoSpawn != null) {
            this.autoSpawn.active = spawn.isPresent();
        }
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging) {
            this.centerX -= dragX * this.blocksPerPixel;
            this.centerZ -= dragY * this.blocksPerPixel;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragging = false;
            this.redraw();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && this.overMap(mouseX, mouseY)) {
            this.zoom(scrollY > 0 ? 0.5 : 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        // Abandons any map still being drawn and frees the picture. Closing the texture itself, rather than
        // releasing its name, leaves nothing to free twice when the next preview registers the same name.
        this.generation.incrementAndGet();
        if (this.texture != null) {
            this.texture.close();
            this.texture = null;
        }
    }
}
