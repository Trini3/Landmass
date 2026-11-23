package io.github.trini3.landmasslite.map;

import java.awt.image.BufferedImage;

/**
 * Coordinates drawn onto a map so distances can be read straight off it: ticks along every edge (every
 * 10,000 blocks on the default maps), x and z labels along the top and left edges, a scale bar in the
 * bottom-left corner and an arrow pointing north in the top-right corner.
 */
final class MapMarkers {
    private static final int LIGHT = 0xFFFFFF;
    private static final int DARK = 0x000000;
    /** How dark the boxes behind labels are, from 0 (clear) to 1 (black). */
    private static final double BOX_SHADE = 0.6;
    /** Fewest pixels between ticks at scale 1. On the default maps this puts a tick every 10,000 blocks. */
    private static final int MIN_TICK_SPACING = 12;
    /** Smaller maps only get ticks; labels would cover them. */
    private static final int MIN_LABELLED_SIZE = 128;
    private static final int[] LABEL_EVERY = {1, 2, 5, 10, 20, 50, 100};

    private MapMarkers() {
    }

    static void draw(BufferedImage image, ClimateMapRenderer.Request request) {
        int size = request.size();
        int step = request.blocksPerPixel();
        // Bigger maps get bigger markings, so they stay readable when the picture is shrunk to fit a screen.
        int scale = Math.max(1, size / 512);
        long interval = niceAtLeast((long) MIN_TICK_SPACING * scale * step);
        Axis across = new Axis(request.centerX(), size, step);
        Axis down = new Axis(request.centerZ(), size, step);

        int minorTick = 4 * scale;
        int majorTick = 7 * scale;
        int pad = scale;
        int gap = 3 * scale;
        int textHeight = PixelFont.HEIGHT * scale;
        int boxHeight = textHeight + 2 * pad;

        if (size < MIN_LABELLED_SIZE) {
            drawTicks(image, across, down, interval, Long.MAX_VALUE, Long.MAX_VALUE, minorTick, majorTick, scale);
            return;
        }

        // Label every tick, or every 2nd, 5th, 10th... tick, whichever first leaves room between the labels.
        long downLabels = interval * labelEvery(interval, step, 3 * boxHeight);
        int widestLeft = widestLabel("z ", down, downLabels, scale);
        long acrossLabels = interval * labelEvery(interval, step, widestLabel("x ", across, interval, scale) + 2 * pad + 2 * gap);
        drawTicks(image, across, down, interval, acrossLabels, downLabels, minorTick, majorTick, scale);

        int inset = majorTick + scale;

        // North arrow, top right: an up arrow over an N.
        int northWidth = PixelFont.WIDTH * scale + 2 * pad;
        int northHeight = 2 * textHeight + 2 * scale + 2 * pad;
        int northLeft = size - inset - northWidth;
        shade(image, northLeft, inset, northWidth, northHeight);
        PixelFont.draw(image, "^", northLeft + pad, inset + pad, scale, LIGHT);
        PixelFont.draw(image, "N", northLeft + pad, inset + pad + textHeight + 2 * scale, scale, LIGHT);

        // Scale bar, bottom left: the longest round distance that fits in a fifth of the map.
        long barBlocks = niceAtMost((long) size / 5 * step);
        int barLength = (int) Math.round(barBlocks / (double) step);
        String barText = format(barBlocks) + " blocks";
        int barBoxWidth = Math.max(PixelFont.width(barText, scale), barLength) + 2 * pad;
        int barBoxHeight = textHeight + 2 * scale + 3 * scale + 2 * pad;
        int barTop = size - inset - barBoxHeight;
        shade(image, inset, barTop, barBoxWidth, barBoxHeight);
        PixelFont.draw(image, barText, inset + pad, barTop + pad, scale, LIGHT);
        int barY = barTop + pad + textHeight + 2 * scale;
        fill(image, inset + pad, barY + scale, barLength, scale, LIGHT);
        fill(image, inset + pad, barY, scale, 3 * scale, LIGHT);
        fill(image, inset + pad + barLength - scale, barY, scale, 3 * scale, LIGHT);

        // Labels stay clear of each other in the top-left corner and of the boxes in the other corners.
        int leftLabelsRight = inset + widestLeft + 2 * pad + gap;
        int topLabelsBottom = inset + boxHeight + gap;
        for (long value = across.first(interval); value <= across.max(); value += interval) {
            if (value % acrossLabels != 0) {
                continue;
            }
            String text = "x " + format(value);
            int width = PixelFont.width(text, scale) + 2 * pad;
            int left = across.pixel(value) - width / 2;
            if (left >= leftLabelsRight && left + width <= northLeft - gap) {
                label(image, text, left, inset, scale);
            }
        }
        for (long value = down.first(interval); value <= down.max(); value += interval) {
            if (value % downLabels != 0) {
                continue;
            }
            int top = down.pixel(value) - boxHeight / 2;
            if (top >= topLabelsBottom && top + boxHeight <= barTop - gap) {
                label(image, "z " + format(value), inset, top, scale);
            }
        }
    }

    private static void drawTicks(BufferedImage image, Axis across, Axis down, long interval, long acrossLabels, long downLabels,
                                  int minorTick, int majorTick, int scale) {
        int size = image.getWidth();
        for (long value = across.first(interval); value <= across.max(); value += interval) {
            int x = across.pixel(value);
            int length = value % acrossLabels == 0 ? majorTick : minorTick;
            tick(image, x, 0, scale, length, scale);
            tick(image, x, size - length, scale, length, scale);
        }
        for (long value = down.first(interval); value <= down.max(); value += interval) {
            int y = down.pixel(value);
            int length = value % downLabels == 0 ? majorTick : minorTick;
            tick(image, 0, y, length, scale, scale);
            tick(image, size - length, y, length, scale, scale);
        }
    }

    /** A white mark with a dark outline, so it shows on snow and on ocean alike. */
    private static void tick(BufferedImage image, int x, int y, int width, int height, int scale) {
        fill(image, x - scale, y - scale, width + 2 * scale, height + 2 * scale, DARK);
        fill(image, x, y, width, height, LIGHT);
    }

    private static void label(BufferedImage image, String text, int left, int top, int scale) {
        shade(image, left, top, PixelFont.width(text, scale) + 2 * scale, PixelFont.HEIGHT * scale + 2 * scale);
        PixelFont.draw(image, text, left + scale, top + scale, scale, LIGHT);
    }

    private static int widestLabel(String prefix, Axis axis, long every, int scale) {
        int widest = 0;
        for (long value = axis.first(every); value <= axis.max(); value += every) {
            widest = Math.max(widest, PixelFont.width(prefix + format(value), scale));
        }
        return widest;
    }

    private static int labelEvery(long interval, int step, int minPixels) {
        for (int every : LABEL_EVERY) {
            if (interval * every / (double) step >= minPixels) {
                return every;
            }
        }
        return LABEL_EVERY[LABEL_EVERY.length - 1];
    }

    /** "0", "250", "50k", "-120k", "2M". */
    static String format(long blocks) {
        if (blocks != 0 && blocks % 1_000_000 == 0) {
            return blocks / 1_000_000 + "M";
        }
        if (blocks != 0 && blocks % 1_000 == 0) {
            return blocks / 1_000 + "k";
        }
        return Long.toString(blocks);
    }

    /** The smallest of 1, 2, 5, 10, 20, 50... that is at least the given number. */
    static long niceAtLeast(long minimum) {
        long nice = 1;
        while (true) {
            for (long factor : new long[]{1, 2, 5}) {
                if (nice * factor >= minimum) {
                    return nice * factor;
                }
            }
            nice *= 10;
        }
    }

    /** The largest of 1, 2, 5, 10, 20, 50... that is at most the given number, and at least 1. */
    static long niceAtMost(long maximum) {
        long best = 1;
        for (long nice = 1; nice <= maximum; nice *= 10) {
            for (long factor : new long[]{1, 2, 5}) {
                if (nice * factor <= maximum) {
                    best = nice * factor;
                }
            }
        }
        return best;
    }

    private static void shade(BufferedImage image, int left, int top, int width, int height) {
        for (int y = Math.max(0, top); y < Math.min(image.getHeight(), top + height); y++) {
            for (int x = Math.max(0, left); x < Math.min(image.getWidth(), left + width); x++) {
                int rgb = image.getRGB(x, y);
                int r = (int) Math.round(((rgb >> 16) & 0xFF) * (1 - BOX_SHADE));
                int g = (int) Math.round(((rgb >> 8) & 0xFF) * (1 - BOX_SHADE));
                int b = (int) Math.round((rgb & 0xFF) * (1 - BOX_SHADE));
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
    }

    /** Fills a rectangle, skipping whatever falls outside the image. */
    static void fill(BufferedImage image, int left, int top, int width, int height, int rgb) {
        for (int y = Math.max(0, top); y < Math.min(image.getHeight(), top + height); y++) {
            for (int x = Math.max(0, left); x < Math.min(image.getWidth(), left + width); x++) {
                image.setRGB(x, y, rgb);
            }
        }
    }

    /** One direction of the map: pixel i shows the block at center + (i - size / 2) * step. */
    private record Axis(int center, int size, int step) {
        long min() {
            return this.center - (long) (this.size / 2) * this.step;
        }

        long max() {
            return this.center + (long) (this.size - 1 - this.size / 2) * this.step;
        }

        /** The first multiple of {@code every} on the map. */
        long first(long every) {
            return Math.ceilDiv(this.min(), every) * every;
        }

        int pixel(long block) {
            return (int) (this.size / 2 + Math.floorDiv(block - this.center, this.step));
        }
    }
}
