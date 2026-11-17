package io.github.trini3.acclimatization.client;

import io.github.trini3.acclimatization.climate.ClimateDeviation;
import io.github.trini3.acclimatization.climate.ClimateSettings;
import io.github.trini3.acclimatization.climate.LatitudeLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

/**
 * The Climate settings of Custom Climate, opened from the Create World screen's World tab: the same Climate section as the
 * Landmass mod, with a Zones page and a Deviation page. Each number setting is a row with a slider for quick changes and a
 * number box for exact values; hovering over one shows what it does. The grey line under the rows sums up the zones.
 */
public class ClimateScreen extends Screen {
    private enum Page {
        ZONES,
        DEVIATION
    }

    private static final String KEY = "acclimatization.climate.";
    private static final String[] ZONES = {"hot", "warm", "temperate", "cold", "frozen"};
    // Four zones of the smallest width make the smallest polar distance, and four of the largest the largest.
    // Each setting has a recommended range, which its slider covers, and wider limits for typed values (with a warning).
    private static final int MIN_ZONE_WIDTH = LatitudeLayout.MIN_POLAR_DISTANCE / 4;
    private static final int MAX_ZONE_WIDTH = 5_000_000;
    private static final int LIMIT_ZONE_WIDTH = LatitudeLayout.MAX_POLAR_DISTANCE / 4;
    private static final int MIN_HUMIDITY_ZONE = (int) Math.round(ClimateSettings.VANILLA_HUMIDITY_ZONE * ClimateSettings.MIN_HUMIDITY_SCALE);
    private static final int MAX_HUMIDITY_ZONE = 1_000_000;
    private static final int LIMIT_HUMIDITY_ZONE = (int) Math.round(ClimateSettings.VANILLA_HUMIDITY_ZONE * ClimateSettings.MAX_HUMIDITY_SCALE);
    private static final int GOLD = 0xFFAA00;
    private static final int TITLE_TOP = 8;
    private static final int PAGE_TOP = 22;
    // Rows hold up to eight lines of settings.
    private static final int ROWS = 8;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;

    private final CreateWorldScreen parent;
    private final CustomClimateState state;
    private final ClimateSettings initial;
    // Kept exactly as they are unless their setting is changed, so Done without changes changes nothing.
    private final int[] zoneWidths;
    private boolean zonesChanged;
    private double humidityScale;
    private double waviness;
    private double desertShare;
    private ClimateDeviation deviation;
    private Page page = Page.ZONES;

    private final List<BlockSetting> zoneSettings;
    private final List<BlockSetting> deviationSettings;
    private final BlockSetting humidityRow;
    private int firstRow;
    private int rowHeight;

    public ClimateScreen(CreateWorldScreen parent, CustomClimateState state) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.state = state;
        this.initial = state.settings();
        this.zoneWidths = this.initial.latitude().zoneWidths();
        this.humidityScale = this.initial.humidityScale();
        this.waviness = this.initial.latitude().waviness();
        this.desertShare = this.initial.latitude().desertShare();
        this.deviation = this.initial.deviation();

        BlockSetting[] rows = new BlockSetting[ZONES.length + 2];
        for (int i = 0; i < ZONES.length; i++) {
            int zone = i;
            rows[i] = new BlockSetting(KEY + "zone." + ZONES[i], "blocks", MIN_ZONE_WIDTH, MAX_ZONE_WIDTH, MIN_ZONE_WIDTH,
                    LIMIT_ZONE_WIDTH, this.zoneWidths[i],
                    blocks -> {
                        this.zoneWidths[zone] = blocks;
                        this.zonesChanged = true;
                    });
        }
        this.humidityRow = new BlockSetting(KEY + "humidity", "blocks", MIN_HUMIDITY_ZONE, MAX_HUMIDITY_ZONE, MIN_HUMIDITY_ZONE,
                LIMIT_HUMIDITY_ZONE, (int) Math.round(this.humidityScale * ClimateSettings.VANILLA_HUMIDITY_ZONE),
                blocks -> this.humidityScale = blocks / (double) ClimateSettings.VANILLA_HUMIDITY_ZONE);
        rows[ZONES.length] = this.humidityRow;
        rows[ZONES.length + 1] = linear(new BlockSetting(KEY + "waviness", "percent", 0, (int) Math.round(100 * LatitudeLayout.MAX_WAVINESS), 0,
                (int) Math.round(100 * LatitudeLayout.LIMIT_MAX_WAVINESS), (int) Math.round(this.waviness * 100), percent -> this.waviness = percent / 100.0));
        this.zoneSettings = List.of(rows);

        this.deviationSettings = List.of(
                linear(new BlockSetting(KEY + "deviation_temperature", "zones", 0, ClimateDeviation.MAX_TEMPERATURE, 0, ClimateDeviation.MAX_TEMPERATURE,
                        this.deviation.temperature(), zones -> this.deviation = this.deviation.withTemperature(zones))),
                linear(new BlockSetting(KEY + "deviation_humidity", "zones", 0, 2, 0, ClimateDeviation.MAX_HUMIDITY, this.deviation.humidity(),
                        zones -> this.deviation = this.deviation.withHumidity(zones))),
                new BlockSetting(KEY + "deviation_size", "blocks", 200, 200_000, (int) ClimateDeviation.MIN_SIZE, (int) ClimateDeviation.MAX_SIZE,
                        (int) Math.round(this.deviation.size()), blocks -> this.deviation = this.deviation.withSize(blocks)),
                linear(new BlockSetting(KEY + "deviation_frequency", "percent", 0, 50, 0, 100, (int) Math.round(this.deviation.frequency() * 100),
                        percent -> this.deviation = this.deviation.withFrequency(percent / 100.0))),
                linear(new BlockSetting(KEY + "desert_share", "percent", 0, 80, 0, (int) Math.round(100 * LatitudeLayout.MAX_DESERT_SHARE),
                        (int) Math.round(this.desertShare * 100), percent -> this.desertShare = percent / 100.0)));
    }

    private static BlockSetting linear(BlockSetting setting) {
        setting.linear = true;
        return setting;
    }

    private List<BlockSetting> pageSettings() {
        return this.page == Page.ZONES ? this.zoneSettings : this.deviationSettings;
    }

    private int rowTop(int row) {
        return this.firstRow + row * this.rowHeight;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155;
        // A little closer together on short windows, so eight rows and the line below them stay clear of the bottom buttons.
        this.rowHeight = this.height >= 270 ? 21 : 20;
        this.firstRow = PAGE_TOP + 24;

        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            Page each = pages[i];
            String key = KEY + "page." + each.name().toLowerCase(Locale.ROOT);
            int width = (310 - 4 * (pages.length - 1)) / pages.length;
            Button pageButton = Button.builder(Component.translatable(key), button -> {
                this.page = each;
                this.rebuildWidgets();
            }).bounds(left + i * (width + 4), PAGE_TOP, width, 20).build();
            // The open page's button is greyed out, which shows where you are.
            pageButton.active = this.page != each;
            pageButton.setTooltip(Tooltip.create(Component.translatable(key + ".hint")));
            this.addRenderableWidget(pageButton);
        }

        List<BlockSetting> rows = this.pageSettings();
        for (int i = 0; i < rows.size(); i++) {
            BlockSetting setting = rows.get(i);
            Tooltip tooltip = Tooltip.create(setting == this.humidityRow
                    ? Component.translatable(setting.key + ".hint", format(ClimateSettings.VANILLA_HUMIDITY_ZONE))
                    : Component.translatable(setting.key + ".hint"));
            int top = this.rowTop(i);
            AbstractWidget slider = this.addRenderableWidget(setting.createSlider(left + 100, top, 140));
            AbstractWidget box = this.addRenderableWidget(setting.createBox(this.font, left + 244, top, 66));
            slider.setTooltip(tooltip);
            box.setTooltip(tooltip);
        }

        int bottom = this.height - 28;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.applyAndClose())
                .bounds(left + 53, bottom, 100, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(left + 157, bottom, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = this.width / 2 - 155;
        graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, TITLE_TOP, WHITE);
        List<BlockSetting> rows = this.pageSettings();
        for (int i = 0; i < rows.size(); i++) {
            graphics.text(this.font, Component.translatable(rows.get(i).key), left, this.rowTop(i) + 6, WHITE);
        }

        int below = this.rowTop(ROWS) + 4;
        if (below + this.font.lineHeight > this.height - 30) {
            // No room for the line on the shortest windows.
            return;
        }
        Optional<BlockSetting> invalid = rows.stream().filter(setting -> !setting.valid).findFirst();
        Optional<BlockSetting> beyond = rows.stream().filter(BlockSetting::beyondRecommended).findFirst();
        boolean beyondElsewhere = beyond.isEmpty()
                && Stream.of(this.zoneSettings, this.deviationSettings).flatMap(List::stream).anyMatch(BlockSetting::beyondRecommended);
        Component line;
        int color = GREY;
        if (invalid.isPresent()) {
            BlockSetting setting = invalid.get();
            line = Component.translatable(KEY + "range." + setting.unit, Component.translatable(setting.key), format(setting.limitMin), format(setting.limitMax));
            color = RED;
        } else if (beyond.isPresent() || beyondElsewhere) {
            // Typed past the slider's range: allowed, but players should know it's untested ground.
            line = beyond.map(setting -> Component.translatable(KEY + "beyond." + setting.unit,
                            Component.translatable(setting.key), format(setting.min), format(setting.max)))
                    .orElseGet(() -> Component.translatable(KEY + "beyond_elsewhere"));
            color = RED;
            if (below + 12 + this.font.lineHeight <= this.height - 30) {
                graphics.text(this.font, Component.translatable(KEY + "unsupported"), left, below + 12, RED);
            }
        } else if (this.page == Page.DEVIATION) {
            line = Component.translatable(KEY + "deviation.summary");
        } else {
            LatitudeLayout layout = this.chosen().latitude();
            line = Component.translatable(KEY + "zones_summary", format(layout.polarDistance()), format(Math.round(layout.poleDistance())));
        }
        graphics.text(this.font, line, left, below, color);
    }

    /** The settings as they stand on this screen, applied or not. */
    private ClimateSettings chosen() {
        LatitudeLayout latitude = this.initial.latitude();
        if (this.zonesChanged) {
            int[] w = this.zoneWidths;
            latitude = latitude.withZoneWidths(w[0], w[1], w[2], w[3], w[4]);
        }
        if (this.waviness != this.initial.latitude().waviness()) {
            latitude = latitude.withWaviness(this.waviness);
        }
        // Only when moved, so an untouched share (e.g. 20 shown for 19.6) stays exactly as it was.
        if (this.desertShare != this.initial.latitude().desertShare()) {
            latitude = latitude.withDesertShare(this.desertShare);
        }
        return this.initial.withLatitude(latitude).withHumidityScale(this.humidityScale).withDeviation(this.deviation);
    }

    private void applyAndClose() {
        this.state.setSettings(this.chosen());
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    private static String format(long blocks) {
        return String.format(Locale.ROOT, "%,d", blocks);
    }

    /** One setting, a whole number of blocks or percent, shown on a slider and in a number box. */
    private static final class BlockSetting {
        private final String key;
        // "blocks" or "percent": picks the text for values and for the allowed range.
        private final String unit;
        // The recommended range, which the slider covers.
        private final int min;
        private final int max;
        // What can be typed in: past the recommended range is allowed, with a warning.
        private final int limitMin;
        private final int limitMax;
        private final IntConsumer onChange;
        private int value;
        private boolean valid = true;
        // Logarithmic unless set; percentages of a whole read better on an even scale.
        private boolean linear;
        private BlockSlider slider;
        private EditBox box;

        BlockSetting(String key, String unit, int min, int max, int limitMin, int limitMax, int value, IntConsumer onChange) {
            this.key = key;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.limitMin = limitMin;
            this.limitMax = limitMax;
            this.onChange = onChange;
            this.value = Math.clamp(value, limitMin, limitMax);
        }

        /** Whether the value, though allowed, is outside the recommended range. */
        boolean beyondRecommended() {
            return this.valid && (this.value < this.min || this.value > this.max);
        }

        private int textColor() {
            return !this.valid ? 0xFF000000 | RED : this.beyondRecommended() ? 0xFF000000 | GOLD : EditBox.DEFAULT_TEXT_COLOR;
        }

        BlockSlider createSlider(int x, int y, int width) {
            this.slider = new BlockSlider(x, y, width, this.min, this.max, this.value, this.linear, "acclimatization.climate." + this.unit, this::slid);
            return this.slider;
        }

        EditBox createBox(Font font, int x, int y, int width) {
            this.box = new EditBox(font, x, y, width, 20, Component.translatable(this.key));
            this.box.setMaxLength(9);
            this.box.setValue(Integer.toString(this.value));
            this.box.setResponder(text -> {
                // Digits only: anything else typed is taken out again.
                String digits = text.replaceAll("[^0-9]", "");
                if (!digits.equals(text)) {
                    this.box.setValue(digits);
                } else {
                    this.typed(text);
                }
            });
            this.valid = true;
            this.box.setTextColor(this.textColor());
            return this.box;
        }

        /** Shows a value set from elsewhere (another row), without reporting it back. */
        void show(int blocks) {
            this.value = Math.clamp(blocks, this.limitMin, this.limitMax);
            if (this.slider != null) {
                this.slider.show(this.value);
                // The box then reports the same number back through typed(), which leaves it alone.
                this.box.setValue(Integer.toString(this.value));
            }
        }

        private void slid(int blocks) {
            this.value = blocks;
            this.onChange.accept(blocks);
            // The box then reports the same number back through typed(), which leaves it alone.
            this.box.setValue(Integer.toString(blocks));
        }

        private void typed(String text) {
            int blocks = text.isEmpty() ? -1 : Integer.parseInt(text);
            this.valid = blocks >= this.limitMin && blocks <= this.limitMax;
            if (this.valid && blocks != this.value) {
                this.value = blocks;
                this.onChange.accept(blocks);
                this.slider.show(blocks);
            }
            this.box.setTextColor(this.textColor());
        }
    }

    /**
     * A slider over a range of blocks or percent. It's logarithmic, so short and long distances get the same
     * room, and rounds to two significant figures so dragging gives tidy numbers. A linear one moves in whole steps.
     */
    private static final class BlockSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final boolean linear;
        private final String valueKey;
        private final IntConsumer onSlide;
        private int blocks;

        BlockSlider(int x, int y, int width, int min, int max, int blocks, boolean linear, String valueKey, IntConsumer onSlide) {
            super(x, y, width, 20, Component.empty(), 0.0);
            this.min = min;
            this.max = max;
            this.linear = linear;
            this.valueKey = valueKey;
            this.onSlide = onSlide;
            this.show(blocks);
        }

        /** Moves the handle to a number typed in elsewhere, without reporting it back. */
        void show(int blocks) {
            this.blocks = blocks;
            double position = this.linear ? (blocks - this.min) / (double) (this.max - this.min)
                    : Math.log(blocks / (double) this.min) / Math.log(this.max / (double) this.min);
            this.value = Math.clamp(position, 0.0, 1.0);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(this.valueKey, format(this.blocks)));
        }

        @Override
        protected void applyValue() {
            long picked = this.linear ? Math.round(this.min + this.value * (this.max - this.min))
                    : twoFigures(this.min * Math.pow(this.max / (double) this.min, this.value));
            this.blocks = Math.clamp(picked, this.min, this.max);
            this.onSlide.accept(this.blocks);
        }

        private static long twoFigures(double blocks) {
            if (blocks < 100) {
                return Math.round(blocks);
            }
            double unit = Math.pow(10, Math.floor(Math.log10(blocks)) - 1);
            return Math.round(Math.round(blocks / unit) * unit);
        }
    }
}
