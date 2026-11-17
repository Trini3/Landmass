package io.github.trini3.worldpreview.client;

import io.github.trini3.worldpreview.command.PreviewCommand;
import io.github.trini3.worldpreview.map.MapRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game world map, opened with its key or the World map button in the pause menu. It is drawn straight from the world's
 * own climate sampler and biome source, so it shows land nobody has visited yet, in any world type. Drag to move it, scroll to
 * zoom, right-click for a menu that teleports there or sets the world spawn there (with cheats on). It needs the world's own
 * generator, so it only works in singleplayer or for the player hosting a LAN world.
 */
public class WorldMapScreen extends MapScreen {
    private static final int MAP_TOP = 30;
    // Remembered between openings, so the map comes back the way it was left.
    private static int lastBlocksPerPixel = 32;
    private static MapRenderer.Mode lastMode = MapRenderer.Mode.BIOMES;

    @Nullable
    private final Screen parent;
    @Nullable
    private final ServerLevel level;
    // The right-click menu, drawn over the map, or empty when closed.
    private final List<Button> menu = new ArrayList<>();

    public WorldMapScreen(@Nullable Screen parent) {
        super(Component.translatable("triniworldpreview.world_map.title"), "world_map", lastMode, lastBlocksPerPixel);
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
    protected boolean available() {
        return this.level != null;
    }

    @Override
    protected Source source() {
        ServerLevel level = this.level;
        // The world's own sampler and biome source, which are safe to use off the server thread.
        return new Source(MapRenderer.sampler(level.getChunkSource().randomState()), level.getChunkSource().getGenerator().getBiomeSource());
    }

    @Override
    protected void init() {
        this.menu.clear();
        int bottom = this.height - 26;
        int left = this.width / 2 - 158;
        this.layoutMap(MAP_TOP, bottom - 4, bottom, left);
        Button me = this.addRenderableWidget(Button.builder(Component.translatable("triniworldpreview.world_map.center"), button -> this.centerOnPlayer())
                .bounds(left + 152, bottom, 80, 20).build());
        me.active = this.available();
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).bounds(left + 236, bottom, 80, 20).build());
    }

    @Override
    protected void viewChanged() {
        this.closeMenu();
        lastMode = this.mode;
        lastBlocksPerPixel = this.blocksPerPixel;
    }

    private void centerOnPlayer() {
        this.closeMenu();
        if (this.minecraft.player != null) {
            this.centerX = this.minecraft.player.getX();
            this.centerZ = this.minecraft.player.getZ();
            this.redraw();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        Component title = this.available() && this.overMap(mouseX, mouseY)
                ? Component.translatable("triniworldpreview.world_map.title_at", format(Math.round(this.blockX(mouseX))), format(Math.round(this.blockZ(mouseY))))
                : this.title;
        graphics.centeredText(this.font, title, this.width / 2, 5, WHITE);
        graphics.centeredText(this.font, Component.translatable("triniworldpreview.world_map.hint", format(this.blocksPerPixel)), this.width / 2, 17, GREY);
        this.drawMap(graphics, mouseX, mouseY, this.menu.isEmpty());
        // Not renderable widgets, so that they are drawn here on top of the map instead of under it.
        for (Button button : this.menu) {
            button.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void drawMarkers(GuiGraphicsExtractor graphics) {
        if (this.minecraft.player != null) {
            this.marker(graphics, this.minecraft.player.getX(), this.minecraft.player.getZ(), 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // A click anywhere but on the menu closes it; a click on one of its buttons runs that button first.
        if (this.menu.stream().noneMatch(button -> button.isMouseOver(event.x(), event.y()))) {
            this.closeMenu();
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Opens the right-click menu at the cursor: teleport there, or set the world spawn there. */
    @Override
    protected void rightClicked(double blockX, double blockZ, double mouseX, double mouseY) {
        this.closeMenu();
        int x = (int) Math.floor(blockX);
        int z = (int) Math.floor(blockZ);
        Component teleport = Component.translatable("triniworldpreview.world_map.teleport_here", format(x), format(z));
        Component spawn = Component.translatable("triniworldpreview.world_map.spawn_here");
        int buttonWidth = Math.max(this.font.width(teleport), this.font.width(spawn)) + 12;
        // Kept inside the map so that it never covers the buttons below it.
        int left = Math.max(this.mapLeft, (int) Math.min(mouseX, this.mapLeft + this.mapSize - buttonWidth));
        int top = Math.max(this.mapTop, (int) Math.min(mouseY, this.mapTop + this.mapSize - 40));
        this.menu.add(this.addWidget(Button.builder(teleport, b -> this.runCommand("tp", x, z)).bounds(left, top, buttonWidth, 20).build()));
        this.menu.add(this.addWidget(Button.builder(spawn, b -> this.runCommand("setspawn", x, z)).bounds(left, top + 20, buttonWidth, 20).build()));
    }

    private void closeMenu() {
        for (Button button : this.menu) {
            this.removeWidget(button);
        }
        this.menu.clear();
    }

    /** Runs /twp tp or /twp setspawn, which check the player may use commands like /tp, then closes the map. */
    private void runCommand(String action, int x, int z) {
        if (this.minecraft.player == null) {
            return;
        }
        if (!this.minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            this.closeMenu();
            this.status = Component.translatable("triniworldpreview.world_map.no_permission");
            return;
        }
        this.minecraft.player.connection.sendCommand(PreviewCommand.NAME + " " + action + " " + x + " " + z);
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // The same key closes the map again.
        if (!WorldPreviewClient.WORLD_MAP_KEY.isUnbound() && WorldPreviewClient.WORLD_MAP_KEY.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
