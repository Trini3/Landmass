package io.github.trini3.worldpreview.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.trini3.worldpreview.WorldPreview;
import io.github.trini3.worldpreview.map.MapRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What the Create World preview and the in-game world map share: a square map drawn in the background from a climate sampler
 * and biome source, dragged with the left mouse button, zoomed with the scroll wheel or the - and + buttons, switched between
 * climate zones and biomes, with the biome under the mouse named in the corner. A newer drawing replaces an unfinished one.
 */
abstract class MapScreen extends Screen {
    protected static final int MAX_BLOCKS_PER_PIXEL = 1 << 16;
    protected static final double WORLD_LIMIT = 29_999_000;
    protected static final int WHITE = 0xFFFFFFFF;
    protected static final int GREY = 0xFFA0A0A0;
    protected static final int RED = 0xFFFF5555;

    /** The world's climate sampler and biome source to draw from. */
    protected record Source(Climate.Sampler sampler, BiomeSource biomes) {
    }

    private final Identifier textureId;
    private final AtomicInteger generation = new AtomicInteger();
    protected MapRenderer.Mode mode;
    // What the map should show. The centre is a double so that dragging can move it by less than a pixel's worth of blocks.
    protected double centerX;
    protected double centerZ;
    protected int blocksPerPixel;
    // The picture on screen and what it shows, which differs from the view while a new one is being drawn.
    @Nullable
    private DynamicTexture texture;
    private double imageCenterX;
    private double imageCenterZ;
    private int imageBlocksPerPixel;
    private int imageSize;
    private boolean drawing;
    private boolean failed;
    private boolean dragging;
    private double dragDistance;
    private final BiomeHover hover = new BiomeHover();
    @Nullable
    protected Component status;
    protected int mapLeft;
    protected int mapTop;
    protected int mapSize;

    protected MapScreen(Component title, String textureName, MapRenderer.Mode mode, int blocksPerPixel) {
        super(title);
        this.textureId = WorldPreview.id(textureName);
        this.mode = mode;
        this.blocksPerPixel = blocksPerPixel;
    }

    /** Whether there is anything to draw at all; the map stays empty with {@link #unavailableMessage()} when not. */
    protected abstract boolean available();

    protected Component unavailableMessage() {
        return Component.translatable("triniworldpreview.map.unavailable");
    }

    /** Called off the render thread for every drawing. */
    protected abstract Source source();

    /** The view changed through the map's own controls (mode or zoom), for screens that remember it. */
    protected void viewChanged() {
    }

    /** A right-click on the map at that block. */
    protected abstract void rightClicked(double blockX, double blockZ, double mouseX, double mouseY);

    /** Draws markers (the player, the spawn) over the map, inside its clip. */
    protected abstract void drawMarkers(GuiGraphicsExtractor graphics);

    /** Lays out the map square between {@code top} and {@code bottom}, and the shared row of buttons at {@code buttonsY}. */
    protected void layoutMap(int top, int bottom, int buttonsY, int left) {
        this.mapTop = top;
        this.mapSize = Math.max(32, Math.min(this.width - 20, bottom - top));
        this.mapLeft = (this.width - this.mapSize) / 2;
        Button modeButton = this.addRenderableWidget(Button.builder(this.modeName(), button -> {
            this.mode = this.mode == MapRenderer.Mode.CLIMATE ? MapRenderer.Mode.BIOMES : MapRenderer.Mode.CLIMATE;
            button.setMessage(this.modeName());
            this.viewChanged();
            this.redraw();
        }).bounds(left, buttonsY, 100, 20).build());
        Button out = this.addRenderableWidget(Button.builder(Component.literal("-"), button -> this.zoom(2)).bounds(left + 104, buttonsY, 20, 20).build());
        Button in = this.addRenderableWidget(Button.builder(Component.literal("+"), button -> this.zoom(0.5)).bounds(left + 128, buttonsY, 20, 20).build());
        if (!this.available()) {
            modeButton.active = out.active = in.active = false;
        } else {
            this.redraw();
        }
    }

    private Component modeName() {
        return Component.translatable(this.mode == MapRenderer.Mode.CLIMATE ? "triniworldpreview.map.climate" : "triniworldpreview.map.biomes");
    }

    protected void zoom(double factor) {
        int zoomed = Math.clamp(Math.round(this.blocksPerPixel * factor), 1, MAX_BLOCKS_PER_PIXEL);
        if (zoomed != this.blocksPerPixel) {
            this.blocksPerPixel = zoomed;
            this.viewChanged();
            this.redraw();
        }
    }

    /** Starts drawing the map as it should now look, abandoning any map still being drawn. */
    protected void redraw() {
        if (!this.available()) {
            return;
        }
        // Kept within the world border, which also keeps block coordinates well inside int range.
        this.centerX = Math.clamp(this.centerX, -WORLD_LIMIT, WORLD_LIMIT);
        this.centerZ = Math.clamp(this.centerZ, -WORLD_LIMIT, WORLD_LIMIT);
        int request = this.generation.incrementAndGet();
        this.drawing = true;
        this.failed = false;
        MapRenderer.Request map = new MapRenderer.Request(this.mode, (int) Math.round(this.centerX), (int) Math.round(this.centerZ),
                this.mapSize, this.blocksPerPixel);
        Minecraft minecraft = this.minecraft;
        Holder<Biome>[] biomes = BiomeHover.grid(map);
        CompletableFuture
                .supplyAsync(() -> {
                    Source source = this.source();
                    return MapRenderer.draw(source.sampler(), source.biomes(), map, biomes, () -> this.generation.get() == request);
                })
                .whenComplete((image, error) -> minecraft.execute(() -> this.show(request, map, image, error, biomes)));
    }

    private void show(int request, MapRenderer.Request map, @Nullable BufferedImage image, @Nullable Throwable error,
                      @Nullable Holder<Biome>[] biomes) {
        if (request != this.generation.get()) {
            return;
        }
        this.drawing = false;
        if (error != null) {
            WorldPreview.LOGGER.error("Couldn't draw the map", error);
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
        DynamicTexture texture = new DynamicTexture(this.textureId::toString, pixels);
        this.minecraft.getTextureManager().register(this.textureId, texture);
        this.texture = texture;
        this.imageCenterX = map.centerX();
        this.imageCenterZ = map.centerZ();
        this.imageBlocksPerPixel = map.blocksPerPixel();
        this.imageSize = size;
        this.hover.set(map, biomes);
    }

    /** Draws the map square, the biome name under the mouse and the drawing or error notes. */
    protected void drawMap(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean showHover) {
        int top = this.mapTop;
        int right = this.mapLeft + this.mapSize;
        int bottom = top + this.mapSize;
        graphics.fill(this.mapLeft - 1, top - 1, right + 1, bottom + 1, 0xFF000000);
        if (!this.available()) {
            graphics.centeredText(this.font, this.unavailableMessage(), this.width / 2, top + this.mapSize / 2 - 4, RED);
            return;
        }
        graphics.enableScissor(this.mapLeft, top, right, bottom);
        if (this.texture != null) {
            // The last finished picture, moved and scaled to match the view until the new one is ready.
            double scale = this.imageBlocksPerPixel / (double) this.blocksPerPixel;
            int drawnSize = (int) Math.round(this.imageSize * scale);
            int x = (int) Math.round(this.mapLeft + this.mapSize / 2.0 + (this.imageCenterX - this.centerX) / this.blocksPerPixel - drawnSize / 2.0);
            int y = (int) Math.round(top + this.mapSize / 2.0 + (this.imageCenterZ - this.centerZ) / this.blocksPerPixel - drawnSize / 2.0);
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.textureId, x, y, 0.0F, 0.0F, drawnSize, drawnSize, this.imageSize, this.imageSize, this.imageSize, this.imageSize);
        }
        this.drawMarkers(graphics);
        graphics.disableScissor();
        if (showHover && this.texture != null && this.overMap(mouseX, mouseY)) {
            Component biome = this.hover.at(this.blockX(mouseX), this.blockZ(mouseY));
            if (biome != null) {
                BiomeHover.draw(graphics, this.font, biome, this.mapLeft, top);
            }
        }
        if (this.failed) {
            graphics.centeredText(this.font, Component.translatable("triniworldpreview.map.failed"), this.width / 2, top + this.mapSize / 2 - 4, RED);
        } else if (this.status != null) {
            graphics.centeredText(this.font, this.status, this.width / 2, bottom - 12, RED);
        } else if (this.drawing) {
            graphics.centeredText(this.font, Component.translatable("triniworldpreview.map.drawing"), this.width / 2, bottom - 12, WHITE);
        }
    }

    /** A small square marker at a block, with a black edge so it shows up on every colour of the map. */
    protected void marker(GuiGraphicsExtractor graphics, double blockX, double blockZ, int color) {
        int x = (int) Math.round(this.mapLeft + this.mapSize / 2.0 + (blockX - this.centerX) / this.blocksPerPixel);
        int y = (int) Math.round(this.mapTop + this.mapSize / 2.0 + (blockZ - this.centerZ) / this.blocksPerPixel);
        graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
    }

    protected double blockX(double mouseX) {
        return this.centerX + (mouseX - (this.mapLeft + this.mapSize / 2.0)) * this.blocksPerPixel;
    }

    protected double blockZ(double mouseY) {
        return this.centerZ + (mouseY - (this.mapTop + this.mapSize / 2.0)) * this.blocksPerPixel;
    }

    protected boolean overMap(double mouseX, double mouseY) {
        return mouseX >= this.mapLeft && mouseX < this.mapLeft + this.mapSize && mouseY >= this.mapTop && mouseY < this.mapTop + this.mapSize;
    }

    protected static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        if (!this.available() || !this.overMap(mouseX, mouseY)) {
            return false;
        }
        // 26.x numbers mouse buttons from 1 (left 1, right 3), so use the named constants.
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragging = true;
            this.dragDistance = 0;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.rightClicked(Math.clamp(this.blockX(mouseX), -WORLD_LIMIT, WORLD_LIMIT), Math.clamp(this.blockZ(mouseY), -WORLD_LIMIT, WORLD_LIMIT),
                    mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging) {
            this.centerX -= dragX * this.blocksPerPixel;
            this.centerZ -= dragY * this.blocksPerPixel;
            this.dragDistance += Math.abs(dragX) + Math.abs(dragY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragging = false;
            if (this.dragDistance > 0) {
                this.redraw();
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && this.available() && this.overMap(mouseX, mouseY)) {
            this.zoom(scrollY > 0 ? 0.5 : 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        // Abandons any map still being drawn and frees the picture. Closing the texture itself, rather than
        // releasing its name, leaves nothing to free twice when the next map registers the same name.
        this.generation.incrementAndGet();
        if (this.texture != null) {
            this.texture.close();
            this.texture = null;
        }
    }
}
