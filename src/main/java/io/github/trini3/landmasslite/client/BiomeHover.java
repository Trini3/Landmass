package io.github.trini3.landmasslite.client;

import io.github.trini3.landmasslite.map.ClimateMapRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers which biome each pixel of the last finished biome map shows, so the preview and world map can name the biome under
 * the mouse without sampling anything again.
 */
final class BiomeHover {
    @Nullable
    private Holder<Biome>[] biomes;
    private int size;
    private int centerX;
    private int centerZ;
    private int blocksPerPixel;

    /** An empty grid for a map about to be drawn, or null when it isn't a biome map. */
    @SuppressWarnings("unchecked")
    @Nullable
    static Holder<Biome>[] grid(ClimateMapRenderer.Request map) {
        return map.mode() == ClimateMapRenderer.Mode.BIOMES ? (Holder<Biome>[]) new Holder[map.size() * map.size()] : null;
    }

    /** Called with the finished map that's now on screen. */
    void set(ClimateMapRenderer.Request map, @Nullable Holder<Biome>[] grid) {
        this.biomes = grid;
        this.size = map.size();
        this.centerX = map.centerX();
        this.centerZ = map.centerZ();
        this.blocksPerPixel = map.blocksPerPixel();
    }

    /** The name of the biome shown at this block, or null if it isn't on the biome map. */
    @Nullable
    Component at(double blockX, double blockZ) {
        Holder<Biome>[] biomes = this.biomes;
        if (biomes == null) {
            return null;
        }
        // The renderer samples pixel p at center + (p - size / 2) * blocksPerPixel.
        long px = this.size / 2 + (long) Math.floor((blockX - this.centerX) / this.blocksPerPixel + 0.5);
        long py = this.size / 2 + (long) Math.floor((blockZ - this.centerZ) / this.blocksPerPixel + 0.5);
        if (px < 0 || py < 0 || px >= this.size || py >= this.size) {
            return null;
        }
        Holder<Biome> biome = biomes[(int) (py * this.size + px)];
        if (biome == null) {
            return null;
        }
        return biome.unwrapKey().map(key -> {
            Identifier id = key.identifier();
            // Biome mods name their biomes the same way; the id is the fallback for any without a name.
            return (Component) Component.translatableWithFallback(id.toLanguageKey("biome"), id.toString());
        }).orElse(null);
    }

    /** Draws the name in a dark box at the map's top-left corner. */
    static void draw(GuiGraphicsExtractor graphics, Font font, Component name, int mapLeft, int mapTop) {
        int width = font.width(name);
        graphics.fill(mapLeft + 2, mapTop + 2, mapLeft + width + 8, mapTop + font.lineHeight + 6, 0xB0000000);
        graphics.text(font, name, mapLeft + 5, mapTop + 5, 0xFFFFFFFF);
    }
}
