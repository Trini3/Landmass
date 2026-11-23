package io.github.trini3.landmasslite.client;

import io.github.trini3.landmasslite.util.Climates;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.trini3.landmasslite.Landmass;
import io.github.trini3.landmasslite.climate.ClimateHooks;
import io.github.trini3.landmasslite.climate.LatitudeLayout;
import io.github.trini3.landmasslite.climate.WorldClimateSettings;
import io.github.trini3.landmasslite.map.ClimateMapRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The in-game world map, opened with its key (N by default) or the World map button in the pause menu.
 * It is drawn like /latitude map, straight from the world's climate sampler and biome source, so it can
 * show land nobody has visited yet. Drag to move it, scroll to zoom, right-click for a menu that teleports there (with
 * cheats on: it runs /latitude tp). It needs the world's own generator, so it only works in singleplayer
 * or for the player hosting a LAN world.
 */
public class WorldMapScreen extends Screen {
    private static final Identifier TEXTURE = Landmass.id("world_map");
    private static final int MAX_BLOCKS_PER_PIXEL = 1 << 16;
    private static final double WORLD_LIMIT = 29_999_000;
    private static final int MAP_TOP = 30;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    // Remembered between openings, so the map comes back the way it was left.
    private static int lastBlocksPerPixel = 32;
    private static ClimateMapRenderer.Mode lastMode = ClimateMapRenderer.Mode.BIOMES;

    @Nullable
    private final Screen parent;
    @Nullable
    private final ServerLevel level;
    private final AtomicInteger generation = new AtomicInteger();
    private ClimateMapRenderer.Mode mode = lastMode;
    private double centerX;
    private double centerZ;
    private int blocksPerPixel = lastBlocksPerPixel;
    @Nullable
    private DynamicTexture texture;
    private double imageCenterX;
    private double imageCenterZ;
    private int imageBlocksPerPixel;
    private int imageSize;
    private boolean drawing;
    private boolean failed;
    private boolean dragging;
    private final BiomeHover hover = new BiomeHover();
    private double dragDistance;
    @Nullable
    private Component status;
    private int mapLeft;
    private int mapSize;
    // The right-click menu: one "Teleport here" button, drawn over the map, or null when closed.
    @Nullable
    private Button teleportButton;

    public WorldMapScreen(@Nullable Screen parent) {
        super(Component.translatable("landmass_lite.world_map.title"));
        this.parent = parent;
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        this.level = server != null && minecraft.player != null ? server.getLevel(minecraft.player.level().dimension()) : null;
        if (minecraft.player != null) {
            this.centerX = minecraft.player.getX();
            this.centerZ = minecraft.player.getZ();
        }
    }

    @Override
    protected void init() {
        this.teleportButton = null;
        int bottom = this.height - 26;
        this.mapSize = Math.max(32, Math.min(this.width - 20, bottom - 4 - MAP_TOP));
        this.mapLeft = (this.width - this.mapSize) / 2;

        int left = this.width / 2 - 158;
        Button modeButton = this.addRenderableWidget(Button.builder(this.modeName(), button -> {
            this.mode = this.mode == ClimateMapRenderer.Mode.CLIMATE ? ClimateMapRenderer.Mode.BIOMES : ClimateMapRenderer.Mode.CLIMATE;
            lastMode = this.mode;
            button.setMessage(this.modeName());
            this.redraw();
        }).bounds(left, bottom, 100, 20).build());
        Button out = this.addRenderableWidget(Button.builder(Component.literal("-"), button -> this.zoom(2)).bounds(left + 104, bottom, 20, 20).build());
        Button in = this.addRenderableWidget(Button.builder(Component.literal("+"), button -> this.zoom(0.5)).bounds(left + 128, bottom, 20, 20).build());
        Button me = this.addRenderableWidget(Button.builder(Component.translatable("landmass_lite.world_map.center"), button -> this.centerOnPlayer())
                .bounds(left + 152, bottom, 80, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).bounds(left + 236, bottom, 80, 20).build());
        if (this.level == null) {
            modeButton.active = out.active = in.active = me.active = false;
        } else {
            this.redraw();
        }
    }

    private Component modeName() {
        return Component.translatable(this.mode == ClimateMapRenderer.Mode.CLIMATE ? "landmass_lite.preview.climate" : "landmass_lite.preview.biomes");
    }

    private void centerOnPlayer() {
        this.closeMenu();
        if (this.minecraft.player != null) {
            this.centerX = this.minecraft.player.getX();
            this.centerZ = this.minecraft.player.getZ();
            this.redraw();
        }
    }

    private void zoom(double factor) {
        this.closeMenu();
        int zoomed = Math.clamp(Math.round(this.blocksPerPixel * factor), 1, MAX_BLOCKS_PER_PIXEL);
        if (zoomed != this.blocksPerPixel) {
            this.blocksPerPixel = zoomed;
            lastBlocksPerPixel = zoomed;
            this.redraw();
        }
    }

    /** Starts drawing the map as it should now look, abandoning any map still being drawn. */
    private void redraw() {
        ServerLevel level = this.level;
        if (level == null) {
            return;
        }
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
                    // The sampler and biome source are the ones the world generates with, which are safe to use off the server thread.
                    Climate.Sampler sampler = Climates.sampler(level.getChunkSource().randomState());
                    BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
                    WorldClimateSettings settings = ClimateHooks.active();
                    LatitudeLayout layout = settings != null && settings.appliesTo(level.dimension()) ? settings.latitude() : null;
                    return ClimateMapRenderer.draw(sampler, biomeSource, layout, map, null, biomes, () -> this.generation.get() == request);
                })
                .whenComplete((image, error) -> minecraft.execute(() -> this.show(request, map, image, error, biomes)));
    }

    private void show(int request, ClimateMapRenderer.Request map, @Nullable BufferedImage image, @Nullable Throwable error,
                      @Nullable Holder<Biome>[] biomes) {
        if (request != this.generation.get()) {
            return;
        }
        this.drawing = false;
        if (error != null) {
            Landmass.LOGGER.error("Couldn't draw the world map", error);
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
        Component title = this.overMap(mouseX, mouseY)
                ? Component.translatable("landmass_lite.world_map.title_at", format(Math.round(this.blockX(mouseX))), format(Math.round(this.blockZ(mouseY))))
                : this.title;
        graphics.centeredText(this.font, title, this.width / 2, 5, WHITE);
        graphics.centeredText(this.font, Component.translatable("landmass_lite.world_map.hint", format(this.blocksPerPixel)), this.width / 2, 17, GREY);

        int top = MAP_TOP;
        int right = this.mapLeft + this.mapSize;
        int bottom = top + this.mapSize;
        graphics.fill(this.mapLeft - 1, top - 1, right + 1, bottom + 1, 0xFF000000);
        if (this.level == null) {
            graphics.centeredText(this.font, Component.translatable("landmass_lite.world_map.unavailable"), this.width / 2, top + this.mapSize / 2 - 4, RED);
            return;
        }
        graphics.enableScissor(this.mapLeft, top, right, bottom);
        if (this.texture != null) {
            // The last finished picture, moved and scaled to match the view until the new one is ready.
            double scale = this.imageBlocksPerPixel / (double) this.blocksPerPixel;
            int drawnSize = (int) Math.round(this.imageSize * scale);
            int x = (int) Math.round(this.mapLeft + this.mapSize / 2.0 + (this.imageCenterX - this.centerX) / this.blocksPerPixel - drawnSize / 2.0);
            int y = (int) Math.round(top + this.mapSize / 2.0 + (this.imageCenterZ - this.centerZ) / this.blocksPerPixel - drawnSize / 2.0);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, drawnSize, drawnSize, this.imageSize, this.imageSize, this.imageSize, this.imageSize);
        }
        if (this.minecraft.player != null) {
            // You are here: a white square with a black edge.
            int x = (int) Math.round(this.mapLeft + this.mapSize / 2.0 + (this.minecraft.player.getX() - this.centerX) / this.blocksPerPixel);
            int y = (int) Math.round(top + this.mapSize / 2.0 + (this.minecraft.player.getZ() - this.centerZ) / this.blocksPerPixel);
            graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
        }
        graphics.disableScissor();
        if (this.texture != null && this.teleportButton == null && this.overMap(mouseX, mouseY)) {
            Component biome = this.hover.at(this.blockX(mouseX), this.blockZ(mouseY));
            if (biome != null) {
                BiomeHover.draw(graphics, this.font, biome, this.mapLeft, top);
            }
        }
        if (this.failed) {
            graphics.centeredText(this.font, Component.translatable("landmass_lite.preview.failed"), this.width / 2, top + this.mapSize / 2 - 4, RED);
        } else if (this.status != null) {
            graphics.centeredText(this.font, this.status, this.width / 2, bottom - 12, RED);
        } else if (this.drawing) {
            graphics.centeredText(this.font, Component.translatable("landmass_lite.preview.drawing"), this.width / 2, bottom - 12, WHITE);
        }
        if (this.teleportButton != null) {
            // Not a renderable widget, so that it is drawn here on top of the map instead of under it.
            this.teleportButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private double blockX(double mouseX) {
        return this.centerX + (mouseX - (this.mapLeft + this.mapSize / 2.0)) * this.blocksPerPixel;
    }

    private double blockZ(double mouseY) {
        return this.centerZ + (mouseY - (MAP_TOP + this.mapSize / 2.0)) * this.blocksPerPixel;
    }

    private boolean overMap(double mouseX, double mouseY) {
        return mouseX >= this.mapLeft && mouseX < this.mapLeft + this.mapSize && mouseY >= MAP_TOP && mouseY < MAP_TOP + this.mapSize;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        // 26.x numbers mouse buttons from 1 (left 1, right 3), so use the named constants.
        boolean handled = super.mouseClicked(event, doubleClick);
        // Any click closes the menu; a click on its button has already teleported above.
        this.closeMenu();
        if (handled) {
            return true;
        }
        if (this.level == null || !this.overMap(mouseX, mouseY)) {
            return false;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragging = true;
            this.dragDistance = 0;
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.openMenu(mouseX, mouseY);
            return true;
        }
        return false;
    }

    /** Opens the right-click menu at the cursor, with a button that teleports to the clicked spot. */
    private void openMenu(double mouseX, double mouseY) {
        long x = (long) Math.floor(Math.clamp(this.blockX(mouseX), -WORLD_LIMIT, WORLD_LIMIT));
        long z = (long) Math.floor(Math.clamp(this.blockZ(mouseY), -WORLD_LIMIT, WORLD_LIMIT));
        Component label = Component.translatable("landmass_lite.world_map.teleport_here", format(x), format(z));
        int buttonWidth = this.font.width(label) + 12;
        // Kept inside the map so that it never covers the buttons below it.
        int left = (int) Math.min(mouseX, this.mapLeft + this.mapSize - buttonWidth);
        int top = (int) Math.min(mouseY, MAP_TOP + this.mapSize - 20);
        this.teleportButton = this.addWidget(Button.builder(label, b -> this.teleport(x, z))
                .bounds(Math.max(this.mapLeft, left), Math.max(MAP_TOP, top), buttonWidth, 20).build());
    }

    private void closeMenu() {
        if (this.teleportButton != null) {
            this.removeWidget(this.teleportButton);
            this.teleportButton = null;
        }
    }

    /** Teleports to the surface at that spot through /latitude tp, which checks the player may use commands like /tp. */
    private void teleport(double x, double z) {
        if (this.minecraft.player == null) {
            return;
        }
        if (!this.minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            this.status = Component.translatable("landmass_lite.world_map.no_permission");
            return;
        }
        int blockX = (int) Math.floor(Math.clamp(x, -WORLD_LIMIT, WORLD_LIMIT));
        int blockZ = (int) Math.floor(Math.clamp(z, -WORLD_LIMIT, WORLD_LIMIT));
        this.minecraft.player.connection.sendCommand("latitude tp " + blockX + " " + blockZ);
        this.minecraft.gui.setScreen(null);
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
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
        if (scrollY != 0 && this.level != null && this.overMap(mouseX, mouseY)) {
            this.zoom(scrollY > 0 ? 0.5 : 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // The same key closes the map again.
        if (LandmassClient.WORLD_MAP_KEY.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        this.generation.incrementAndGet();
        if (this.texture != null) {
            this.texture.close();
            this.texture = null;
        }
    }
}
