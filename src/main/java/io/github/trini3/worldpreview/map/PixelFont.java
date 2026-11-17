package io.github.trini3.worldpreview.map;

import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * A small 5x7 pixel font for the labels on maps. The map draws its own letters because java.awt's fonts
 * aren't always installed on servers.
 */
final class PixelFont {
    static final int WIDTH = 5;
    static final int HEIGHT = 7;
    private static final int ADVANCE = WIDTH + 1;

    // Seven rows of five pixels each; '#' is drawn. '^' is an arrow pointing up.
    private static final Map<Character, String[]> GLYPHS = Map.ofEntries(
            glyph('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
            glyph('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
            glyph('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
            glyph('3', "#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
            glyph('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
            glyph('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
            glyph('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
            glyph('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
            glyph('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
            glyph('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
            glyph('-', ".....", ".....", ".....", ".###.", ".....", ".....", "....."),
            glyph('k', "#....", "#....", "#..#.", "#.#..", "##...", "#.#..", "#..#."),
            glyph('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
            glyph('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"),
            glyph('x', ".....", ".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#"),
            glyph('z', ".....", ".....", "#####", "...#.", "..#..", ".#...", "#####"),
            glyph('b', "#....", "#....", "#.##.", "##..#", "#...#", "#...#", "####."),
            glyph('l', ".##..", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
            glyph('o', ".....", ".....", ".###.", "#...#", "#...#", "#...#", ".###."),
            glyph('c', ".....", ".....", ".###.", "#....", "#....", "#...#", ".###."),
            glyph('s', ".....", ".....", ".####", "#....", ".###.", "....#", "####."),
            glyph('^', "..#..", ".###.", "#.#.#", "..#..", "..#..", "..#..", "..#.."),
            glyph(' ', ".....", ".....", ".....", ".....", ".....", ".....", "....."));

    private PixelFont() {
    }

    private static Map.Entry<Character, String[]> glyph(char character, String... rows) {
        return Map.entry(character, rows);
    }

    /** Width in pixels of the text drawn at the given scale. */
    static int width(String text, int scale) {
        return text.isEmpty() ? 0 : (text.length() * ADVANCE - 1) * scale;
    }

    /** Draws text with its top-left corner at (x, y); each font pixel becomes a scale x scale square. Characters it doesn't know are left blank. */
    static void draw(BufferedImage image, String text, int x, int y, int scale, int rgb) {
        int left = x;
        for (int i = 0; i < text.length(); i++) {
            String[] rows = GLYPHS.get(text.charAt(i));
            if (rows != null) {
                for (int row = 0; row < HEIGHT; row++) {
                    for (int column = 0; column < WIDTH; column++) {
                        if (rows[row].charAt(column) == '#') {
                            MapMarkers.fill(image, left + column * scale, y + row * scale, scale, scale, rgb);
                        }
                    }
                }
            }
            left += ADVANCE * scale;
        }
    }
}
