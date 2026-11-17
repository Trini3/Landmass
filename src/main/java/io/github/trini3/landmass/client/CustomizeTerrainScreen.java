package io.github.trini3.landmass.client;

import io.github.trini3.landmass.climate.ClimateDeviation;
import io.github.trini3.landmass.climate.LatitudeLayout;
import io.github.trini3.landmass.climate.WorldClimateSettings;
import io.github.trini3.landmass.climate.WorldPresets;
import io.github.trini3.landmass.continents.CoastErosion;
import io.github.trini3.landmass.continents.CoastFeatures;
import io.github.trini3.landmass.continents.ContinentLayout;
import io.github.trini3.landmass.continents.ContinentShapes;
import io.github.trini3.landmass.continents.Shores;
import io.github.trini3.landmass.world.LandmassGenerator;
import io.github.trini3.landmass.world.WorldFeatures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The Customize screen of the Landmass Terrain world type, opened from the Create World screen. Its sections are tabs:
 * Presets, Climate (Zones and Deviation pages), Landmass (Continents, Islands and Coasts pages), Terrain (features) and
 * Structures. Each number setting is a row with a slider for quick changes and a number box for exact values; hovering
 * over one shows what it does. Preview map shows the world with the values as they stand, before they're applied, and
 * lets you pick the spawn point.
 *
 * <p>World height and mountain ranges are not settings here: Landmass leaves the landscape to vanilla or the installed
 * terrain mod, and only overrides coast size when the Coast Size Override is switched on.
 */
public class CustomizeTerrainScreen extends Screen {
    private enum Tab {
        PRESETS,
        CLIMATE,
        LANDMASS,
        TERRAIN,
        STRUCTURES
    }

    /** The pages of the tabs that have them, shown in a second row of buttons under the tabs. */
    private enum Page {
        ZONES(Tab.CLIMATE),
        DEVIATION(Tab.CLIMATE),
        CONTINENTS(Tab.LANDMASS),
        ISLANDS(Tab.LANDMASS),
        COASTS(Tab.LANDMASS);

        private final Tab tab;

        Page(Tab tab) {
            this.tab = tab;
        }

        static List<Page> of(Tab tab) {
            return Stream.of(values()).filter(page -> page.tab == tab).toList();
        }
    }

    private enum CoastPage {
        BRIDGES,
        BARS,
        STRAITS,
        EROSION,
        SHORES
    }

    private static final String KEY = "landmass.customize.";
    private static final String[] ZONES = {"hot", "warm", "temperate", "cold", "frozen"};
    // Four zones of the smallest width make the smallest polar distance, and four of the largest the largest.
    // Each setting has a recommended range, which its slider covers, and wider limits for typed values (with a warning).
    private static final int MIN_ZONE_WIDTH = LatitudeLayout.MIN_POLAR_DISTANCE / 4;
    private static final int MAX_ZONE_WIDTH = 5_000_000;
    private static final int LIMIT_ZONE_WIDTH = LatitudeLayout.MAX_POLAR_DISTANCE / 4;
    private static final int MIN_HUMIDITY_ZONE = (int) Math.round(WorldClimateSettings.VANILLA_HUMIDITY_ZONE * WorldClimateSettings.MIN_HUMIDITY_SCALE);
    private static final int MAX_HUMIDITY_ZONE = 1_000_000;
    private static final int LIMIT_HUMIDITY_ZONE = (int) Math.round(WorldClimateSettings.VANILLA_HUMIDITY_ZONE * WorldClimateSettings.MAX_HUMIDITY_SCALE);
    private static final int MIN_SIZE_PERCENT = 25;
    private static final int MAX_SIZE_PERCENT = 2_500;
    private static final int LIMIT_MIN_SIZE_PERCENT = (int) Math.round(100 * WorldClimateSettings.MIN_SIZE_SCALE);
    private static final int LIMIT_MAX_SIZE_PERCENT = (int) Math.round(100 * WorldClimateSettings.MAX_SIZE_SCALE);
    private static final int MIN_LAND_PERCENT = (int) Math.round(100 * ContinentLayout.MIN_LAND_SHARE);
    private static final int MAX_LAND_PERCENT = (int) Math.round(100 * ContinentLayout.MAX_LAND_SHARE);
    private static final int MAX_AMOUNT_PERCENT = (int) Math.round(100 * ContinentLayout.MAX_AMOUNT);
    private static final int MIN_ISLAND_BIOME_PERCENT = (int) Math.round(100 * ContinentLayout.MIN_ISLAND_BIOME_SCALE);
    private static final int MAX_ISLAND_BIOME_PERCENT = (int) Math.round(100 * ContinentLayout.MAX_ISLAND_BIOME_SCALE);
    private static final int GOLD = 0xFFAA00;
    private static final int TAB_TOP = 4;
    private static final int PAGE_TOP = 26;
    // Rows hold up to eight lines of widgets: settings, toggles and page buttons.
    private static final int ROWS = 8;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;

    private final CreateWorldScreen parent;
    private final WorldClimateSettings initial;
    // Kept exactly as they are unless their setting is changed, so Done without changes changes nothing.
    private final int[] zoneWidths;
    private boolean zonesChanged;
    private double humidityScale;
    private double biomeScale;
    private double waviness;
    private double desertShare;
    private ClimateDeviation deviation;
    private ContinentLayout continents;
    private boolean coastline;
    private boolean coastOverride;
    private boolean biomeTerrain;
    private double coastScale;
    private WorldFeatures features;
    private Optional<BlockPos> spawn;
    private Tab tab = Tab.CLIMATE;
    private Page climatePage = Page.ZONES;
    private Page landmassPage = Page.CONTINENTS;
    private CoastPage coastPage = CoastPage.BRIDGES;
    // Presets tab: the name typed for saving, which page of saved presets is shown, a preset waiting for a second click
    // to delete it, and the line shown under the buttons.
    private String presetName = "";
    private int presetPage;
    private String pendingDelete;
    private Component presetStatus = Component.translatable(KEY + "presets.summary");

    private final List<BlockSetting> zoneSettings;
    private final List<BlockSetting> deviationSettings;
    private final BlockSetting humidityRow;
    private final BlockSetting coastSizeRow;
    private final List<BlockSetting> continentSettings;
    private final List<BlockSetting> islandSettings;
    private final List<BlockSetting> bridgeSettings;
    private final List<BlockSetting> barSettings;
    private final List<BlockSetting> straitSettings;
    private final List<BlockSetting> erosionSettings;
    private final List<BlockSetting> shoreSettings;
    // Set in init(): where rows start on the open tab, and how far apart they are (a little closer on short windows).
    private int firstRow;
    private int rowHeight;

    public CustomizeTerrainScreen(CreateWorldScreen parent, WorldCreationContext context) {
        this(parent, LandmassGenerator.of(context.selectedDimensions()).map(LandmassGenerator::climateOrDefaults)
                        .orElseGet(WorldClimateSettings::fromConfig),
                LandmassGenerator.of(context.selectedDimensions()).flatMap(LandmassGenerator::spawn));
    }

    /** The screen showing these settings, not yet applied: how a preset is loaded (Cancel still leaves the world as it was). */
    private CustomizeTerrainScreen(CreateWorldScreen parent, WorldClimateSettings initial, Optional<BlockPos> spawn) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.initial = initial;
        this.spawn = spawn;
        this.zoneWidths = initial.latitude().zoneWidths();
        this.humidityScale = initial.humidityScale();
        this.biomeScale = initial.biomeScale();
        this.waviness = initial.latitude().waviness();
        this.desertShare = initial.latitude().desertShare();
        this.deviation = initial.deviation();
        this.continents = initial.continents();
        this.coastline = initial.coastline();
        this.coastOverride = initial.coast().isPresent();
        this.biomeTerrain = initial.biomeTerrain();
        this.coastScale = initial.coastScale();
        this.features = initial.features();

        BlockSetting[] rows = new BlockSetting[ZONES.length + 3];
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
                LIMIT_HUMIDITY_ZONE, (int) Math.round(this.humidityScale * WorldClimateSettings.VANILLA_HUMIDITY_ZONE),
                blocks -> this.humidityScale = blocks / (double) WorldClimateSettings.VANILLA_HUMIDITY_ZONE);
        rows[ZONES.length] = this.humidityRow;
        rows[ZONES.length + 1] = this.sizePercent("biome_size", this.biomeScale, scale -> this.biomeScale = scale);
        rows[ZONES.length + 2] = linear(new BlockSetting(KEY + "waviness", "percent", 0, (int) Math.round(100 * LatitudeLayout.MAX_WAVINESS), 0,
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

        this.coastSizeRow = this.sizePercent("coast_size", this.coastScale, scale -> this.coastScale = scale);
        BlockSetting[] continentSizes = this.sizePair("continent", ContinentLayout.MIN_CONTINENT, ContinentLayout.MAX_CONTINENT,
                () -> this.continents.continentSize(), range -> this.continents = this.continents.withContinentSize(range));
        BlockSetting landShare = linear(new BlockSetting(KEY + "land_share", "percent", MIN_LAND_PERCENT, MAX_LAND_PERCENT,
                (int) Math.round(100 * ContinentLayout.LIMIT_MIN_LAND_SHARE), (int) Math.round(100 * ContinentLayout.LIMIT_MAX_LAND_SHARE),
                (int) Math.round(this.continents.landShare() * 100), percent -> this.continents = this.continents.withLandShare(percent / 100.0)));
        BlockSetting islandBiomes = new BlockSetting(KEY + "island_biomes", "percent", MIN_ISLAND_BIOME_PERCENT,
                MAX_ISLAND_BIOME_PERCENT, (int) Math.round(100 * ContinentLayout.LIMIT_MIN_ISLAND_BIOME_SCALE),
                (int) Math.round(100 * ContinentLayout.LIMIT_MAX_ISLAND_BIOME_SCALE), (int) Math.round(this.continents.islandBiomeScale() * 100),
                percent -> this.continents = this.continents.withIslandBiomeScale(percent / 100.0));
        this.continentSettings = List.of(continentSizes[0], continentSizes[1], landShare, islandBiomes);

        BlockSetting[] largeIslandSizes = this.sizePair("large_island", ContinentLayout.MIN_LARGE_ISLAND, ContinentLayout.MAX_LARGE_ISLAND,
                () -> this.continents.largeIslandSize(), range -> this.continents = this.continents.withLargeIslandSize(range));
        BlockSetting largeIslandAmount = this.amount("large_islands", this.continents.largeIslandAmount(),
                amount -> this.continents = this.continents.withLargeIslandAmount(amount));
        BlockSetting[] archipelagoSizes = this.sizePair("archipelago", ContinentLayout.MIN_ARCHIPELAGO, ContinentLayout.MAX_ARCHIPELAGO,
                () -> this.continents.archipelagoSize(), range -> this.continents = this.continents.withArchipelagoSize(range));
        BlockSetting archipelagoAmount = this.amount("archipelagos", this.continents.archipelagoAmount(),
                amount -> this.continents = this.continents.withArchipelagoAmount(amount));
        BlockSetting[] islandSizes = this.sizePair("island", ContinentLayout.MIN_ISLAND, ContinentLayout.MAX_ISLAND,
                () -> this.continents.islandSize(), range -> this.continents = this.continents.withIslandSize(range));
        this.islandSettings = List.of(largeIslandSizes[0], largeIslandSizes[1], largeIslandAmount, archipelagoSizes[0], archipelagoSizes[1],
                archipelagoAmount, islandSizes[0], islandSizes[1]);

        BlockSetting bridgeAmount = this.amount("bridges", this.coast().bridgeAmount(), amount -> this.setCoast(this.coast().withBridgeAmount(amount)));
        BlockSetting[] bridgeWidths = this.sizePair("bridge_width", CoastFeatures.MIN_BRIDGE_WIDTH, CoastFeatures.MAX_BRIDGE_WIDTH,
                () -> this.coast().bridgeWidth(), range -> this.setCoast(this.coast().withBridgeWidth(range)));
        BlockSetting bridgeLength = new BlockSetting(KEY + "bridge_length", "blocks", CoastFeatures.MIN_BRIDGE_LENGTH,
                CoastFeatures.MAX_BRIDGE_LENGTH, CoastFeatures.LIMIT_MIN_LENGTH, CoastFeatures.LIMIT_MAX_LENGTH, this.coast().bridgeLength(),
                blocks -> this.setCoast(this.coast().withBridgeLength(blocks)));
        BlockSetting chains = linear(new BlockSetting(KEY + "chains", "percent", 0, 100, 0, 100,
                (int) Math.round(this.coast().chainShare() * 100), percent -> this.setCoast(this.coast().withChainShare(percent / 100.0))));
        BlockSetting barAmount = this.amount("bars", this.coast().barAmount(), amount -> this.setCoast(this.coast().withBarAmount(amount)));
        BlockSetting barWidth = new BlockSetting(KEY + "bar_width", "blocks", CoastFeatures.MIN_BAR_WIDTH, CoastFeatures.MAX_BAR_WIDTH,
                CoastFeatures.LIMIT_MIN_BAR_WIDTH, CoastFeatures.LIMIT_MAX_BAR_WIDTH, this.coast().barWidth(),
                blocks -> this.setCoast(this.coast().withBarWidth(blocks)));
        BlockSetting barLength = new BlockSetting(KEY + "bar_length", "blocks", CoastFeatures.MIN_BAR_LENGTH, CoastFeatures.MAX_BAR_LENGTH,
                CoastFeatures.LIMIT_MIN_LENGTH, CoastFeatures.LIMIT_MAX_LENGTH, this.coast().barLength(),
                blocks -> this.setCoast(this.coast().withBarLength(blocks)));
        BlockSetting bridgeIslets = this.amount("bridge_islets", this.coast().bridgeIslets(),
                amount -> this.setCoast(this.coast().withBridgeIslets(amount)));
        BlockSetting lagoons = linear(new BlockSetting(KEY + "lagoons", "percent", 0, 100, 0, 100,
                (int) Math.round(this.coast().lagoonShare() * 100), percent -> this.setCoast(this.coast().withLagoonShare(percent / 100.0))));
        BlockSetting barIslets = this.amount("bar_islets", this.coast().barIslets(), amount -> this.setCoast(this.coast().withBarIslets(amount)));
        BlockSetting straitAmount = this.amount("straits", this.coast().straitAmount(), amount -> this.setCoast(this.coast().withStraitAmount(amount)));
        BlockSetting[] straitWidths = this.sizePair("strait_width", CoastFeatures.MIN_STRAIT_WIDTH, CoastFeatures.MAX_STRAIT_WIDTH,
                () -> this.coast().straitWidth(), range -> this.setCoast(this.coast().withStraitWidth(range)));
        this.bridgeSettings = List.of(bridgeAmount, bridgeWidths[0], bridgeWidths[1], bridgeLength, chains, bridgeIslets);
        this.barSettings = List.of(barAmount, barWidth, barLength, lagoons, barIslets);
        this.straitSettings = List.of(straitAmount, straitWidths[0], straitWidths[1]);
        this.erosionSettings = List.of(
                this.erosionRate("mainland_erosion_rate", this.erosion().mainlandRate(), v -> this.setErosion(this.erosion().withMainlandRate(v))),
                this.erosionIntensity("mainland_erosion_intensity", this.erosion().mainlandIntensity(),
                        v -> this.setErosion(this.erosion().withMainlandIntensity(v))),
                this.erosionRate("mainland_erosion_variation", this.erosion().mainlandVariation(), v -> this.setErosion(this.erosion().withMainlandVariation(v))),
                this.erosionRate("island_erosion_rate", this.erosion().islandRate(), v -> this.setErosion(this.erosion().withIslandRate(v))),
                this.erosionIntensity("island_erosion_intensity", this.erosion().islandIntensity(),
                        v -> this.setErosion(this.erosion().withIslandIntensity(v))),
                this.erosionRate("island_erosion_variation", this.erosion().islandVariation(), v -> this.setErosion(this.erosion().withIslandVariation(v))));
        this.shoreSettings = List.of(
                this.shoreShare("shore_beaches", this.shores().beaches(), v -> this.setShores(this.shores().withBeaches(v))),
                this.erosionRate("shore_gravel", this.shores().gravelShare(), v -> this.setShores(this.shores().withGravelShare(v))),
                this.shoreShare("shore_grassy", this.shores().grassy(), v -> this.setShores(this.shores().withGrassy(v))),
                this.shoreShare("shore_rocky", this.shores().rocky(), v -> this.setShores(this.shores().withRocky(v))),
                this.shoreShare("shore_cliffs", this.shores().cliffs(), v -> this.setShores(this.shores().withCliffs(v))),
                new BlockSetting(KEY + "shore_stretch", "blocks", Shores.MIN_STRETCH, Shores.MAX_STRETCH, Shores.LIMIT_MIN_STRETCH,
                        Shores.LIMIT_MAX_STRETCH, this.shores().stretch(), blocks -> this.setShores(this.shores().withStretch(blocks))));
    }

    private static BlockSetting linear(BlockSetting setting) {
        setting.linear = true;
        return setting;
    }

    private CoastFeatures coast() {
        return this.continents.coastFeatures();
    }

    private void setCoast(CoastFeatures coast) {
        this.continents = this.continents.withCoastFeatures(coast);
    }

    private Shores shores() {
        return this.continents.shores();
    }

    private void setShores(Shores shores) {
        this.continents = this.continents.withShores(shores);
    }

    private CoastErosion erosion() {
        return this.continents.erosion();
    }

    private void setErosion(CoastErosion erosion) {
        this.continents = this.continents.withErosion(erosion);
    }

    /** A row for how much of the coast is one kind of shore, in percent of the usual share. */
    private BlockSetting shoreShare(String name, double share, DoubleConsumer set) {
        return linear(new BlockSetting(KEY + name, "percent", 0, (int) Math.round(100 * Shores.MAX_SHARE), 0,
                (int) Math.round(100 * Shores.LIMIT_MAX_SHARE), (int) Math.round(share * 100), percent -> set.accept(percent / 100.0)));
    }

    /** A row for the part of a coastline that is eroded, 0 to 100%. */
    private BlockSetting erosionRate(String name, double rate, DoubleConsumer set) {
        return linear(new BlockSetting(KEY + name, "percent", 0, 100, 0, 100, (int) Math.round(rate * 100), percent -> set.accept(percent / 100.0)));
    }

    /** A row for how strong erosion is, in percent of the default. */
    private BlockSetting erosionIntensity(String name, double intensity, DoubleConsumer set) {
        return linear(new BlockSetting(KEY + name, "percent", 0, (int) Math.round(100 * CoastErosion.MAX_INTENSITY), 0,
                (int) Math.round(100 * CoastErosion.LIMIT_MAX_INTENSITY), (int) Math.round(intensity * 100), percent -> set.accept(percent / 100.0)));
    }

    /** A row for a size compared with vanilla's, in percent. */
    private BlockSetting sizePercent(String name, double scale, DoubleConsumer set) {
        return new BlockSetting(KEY + name, "percent", MIN_SIZE_PERCENT, MAX_SIZE_PERCENT, LIMIT_MIN_SIZE_PERCENT,
                LIMIT_MAX_SIZE_PERCENT, (int) Math.round(scale * 100), percent -> set.accept(percent / 100.0));
    }

    /** A row for how common something is, in percent of the usual amount (0 for none). */
    private BlockSetting amount(String name, double amount, DoubleConsumer set) {
        return linear(new BlockSetting(KEY + name, "percent", 0, MAX_AMOUNT_PERCENT, 0,
                (int) Math.round(100 * ContinentLayout.LIMIT_MAX_AMOUNT), (int) Math.round(amount * 100), percent -> set.accept(percent / 100.0)));
    }

    /** Rows for the smallest and largest of a size. Moving one past the other takes the other along. */
    private BlockSetting[] sizePair(String name, int min, int max, Supplier<ContinentLayout.SizeRange> get, Consumer<ContinentLayout.SizeRange> set) {
        BlockSetting[] pair = new BlockSetting[2];
        String key = KEY + name;
        int lowest = ContinentLayout.LIMIT_MIN_SIZE;
        int highest = ContinentLayout.LIMIT_MAX_SIZE;
        pair[0] = new BlockSetting(key + "_min", "blocks", min, max, lowest, highest, get.get().min(), blocks -> {
            set.accept(get.get().withMin(blocks));
            pair[1].show(get.get().max());
        });
        pair[1] = new BlockSetting(key + "_max", "blocks", min, max, lowest, highest, get.get().max(), blocks -> {
            set.accept(get.get().withMax(blocks));
            pair[0].show(get.get().min());
        });
        return pair;
    }

    /** The open page of the open tab, if it has pages. */
    private Optional<Page> page() {
        return switch (this.tab) {
            case CLIMATE -> Optional.of(this.climatePage);
            case LANDMASS -> Optional.of(this.landmassPage);
            default -> Optional.empty();
        };
    }

    private List<BlockSetting> tabSettings() {
        if (this.tab == Tab.TERRAIN) {
            return List.of(this.coastSizeRow);
        }
        return this.page().map(page -> switch (page) {
            case ZONES -> this.zoneSettings;
            case DEVIATION -> this.deviationSettings;
            case CONTINENTS -> this.continentSettings;
            case ISLANDS -> this.islandSettings;
            case COASTS -> switch (this.coastPage) {
                case BRIDGES -> this.bridgeSettings;
                case BARS -> this.barSettings;
                case STRAITS -> this.straitSettings;
                case EROSION -> this.erosionSettings;
                case SHORES -> this.shoreSettings;
            };
        }).orElse(List.of());
    }

    /**
     * The row the first number setting of the open page is on. Terrain has three rows of feature toggles and the Coast Size
     * Override switch above its coast size row, Continents starts with the Custom Landmass Generation switch, Coasts with its
     * page buttons and the coastline features switch.
     */
    private int firstSettingRow() {
        if (this.tab == Tab.TERRAIN) {
            return 4;
        }
        return this.page().map(page -> page == Page.COASTS ? 2 : page == Page.CONTINENTS ? 1 : 0).orElse(0);
    }

    private int rowTop(int row) {
        return this.firstRow + row * this.rowHeight;
    }

    /** An on/off button for a terrain feature or structure group, filling one of two columns. */
    private void addToggle(int left, int row, int column, String key, boolean on, Consumer<Boolean> set) {
        boolean[] state = {on};
        Button button = Button.builder(this.toggleMessage(key, on), pressed -> {
            state[0] = !state[0];
            set.accept(state[0]);
            pressed.setMessage(this.toggleMessage(key, state[0]));
        }).bounds(left + column * 157, this.rowTop(row), 153, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable(key + ".hint")));
        this.addRenderableWidget(button);
    }

    private Component toggleMessage(String key, boolean on) {
        return Component.translatable(on ? KEY + "toggle.on" : KEY + "toggle.off", Component.translatable(key));
    }

    /** An on/off button as wide as a setting row, saying what it switches ("Coastline features: ON"). */
    private Button addSwitch(int left, int row, String key, boolean on, Runnable flip) {
        Button button = Button.builder(this.toggleMessage(key, on), pressed -> {
            flip.run();
            this.rebuildWidgets();
        }).bounds(left, this.rowTop(row), 310, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable(key + ".hint")));
        return this.addRenderableWidget(button);
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155;
        // A little closer together on short windows, so eight rows and the line below them stay clear of the bottom buttons.
        this.rowHeight = this.height >= 270 ? 21 : 20;
        this.firstRow = this.page().isPresent() ? PAGE_TOP + 24 : PAGE_TOP + 2;

        // The tabs: as wide as the window allows, up to 62 each, centred over the settings.
        int tabWidth = Math.clamp((this.width - 10) / Tab.values().length - 2, 48, 62);
        int tabsLeft = this.width / 2 - (Tab.values().length * (tabWidth + 2) - 2) / 2;
        for (Tab each : Tab.values()) {
            Button tabButton = Button.builder(Component.translatable(KEY + "tab." + each.name().toLowerCase(Locale.ROOT)), button -> {
                this.tab = each;
                this.rebuildWidgets();
            }).bounds(tabsLeft + each.ordinal() * (tabWidth + 2), TAB_TOP, tabWidth, 20).build();
            // The open tab's button is greyed out, which shows where you are.
            tabButton.active = each != this.tab;
            this.addRenderableWidget(tabButton);
        }
        // The open tab's pages, in a second row.
        List<Page> pages = Page.of(this.tab);
        for (int i = 0; i < pages.size(); i++) {
            Page each = pages.get(i);
            String key = KEY + "page." + each.name().toLowerCase(Locale.ROOT);
            int width = (310 - 4 * (pages.size() - 1)) / pages.size();
            Button pageButton = Button.builder(Component.translatable(key), button -> {
                if (each.tab == Tab.CLIMATE) {
                    this.climatePage = each;
                } else {
                    this.landmassPage = each;
                }
                this.rebuildWidgets();
            }).bounds(left + i * (width + 4), PAGE_TOP, width, 20).build();
            pageButton.active = this.page().orElse(null) != each;
            pageButton.setTooltip(Tooltip.create(Component.translatable(key + ".hint")));
            this.addRenderableWidget(pageButton);
        }

        List<BlockSetting> rows = this.tabSettings();
        for (int i = 0; i < rows.size(); i++) {
            BlockSetting setting = rows.get(i);
            Tooltip tooltip = Tooltip.create(setting == this.humidityRow
                    ? Component.translatable(setting.key + ".hint", format(WorldClimateSettings.VANILLA_HUMIDITY_ZONE))
                    : Component.translatable(setting.key + ".hint"));
            int top = this.rowTop(this.firstSettingRow() + i);
            AbstractWidget slider = this.addRenderableWidget(setting.createSlider(left + 100, top, 140));
            AbstractWidget box = this.addRenderableWidget(setting.createBox(this.font, left + 244, top, 66));
            slider.setTooltip(tooltip);
            box.setTooltip(tooltip);
            if (setting == this.coastSizeRow) {
                // Only adjustable while the override is on; otherwise vanilla or the terrain mod decides coast size.
                slider.active = this.coastOverride;
                box.active = this.coastOverride;
            }
        }

        switch (this.tab) {
            case PRESETS -> this.addPresetWidgets(left);
            case TERRAIN -> {
                String feature = KEY + "feature.";
                this.addToggle(left, 0, 0, feature + "ravines", this.features.ravines(), on -> this.features = this.features.withRavines(on));
                this.addToggle(left, 0, 1, feature + "cave_tunnels", this.features.caveTunnels(), on -> this.features = this.features.withCaveTunnels(on));
                this.addToggle(left, 1, 0, feature + "lava_lakes", this.features.lavaLakes(), on -> this.features = this.features.withLavaLakes(on));
                this.addToggle(left, 1, 1, feature + "aquifers", this.features.aquifers(), on -> this.features = this.features.withAquifers(on));
                this.addToggle(left, 2, 0, feature + "springs", this.features.springs(), on -> this.features = this.features.withSprings(on));
                this.addSwitch(left, 3, KEY + "coast_override", this.coastOverride, () -> this.coastOverride = !this.coastOverride);
                this.addSwitch(left, 5, KEY + "biome_terrain", this.biomeTerrain, () -> this.biomeTerrain = !this.biomeTerrain);
            }
            case STRUCTURES -> {
                WorldFeatures.StructureGroup[] groups = WorldFeatures.StructureGroup.values();
                for (int i = 0; i < groups.length; i++) {
                    WorldFeatures.StructureGroup group = groups[i];
                    this.addToggle(left, i / 2, i % 2, KEY + "structure." + group.id(), this.features.structureEnabled(group),
                            on -> this.features = this.features.withStructure(group, on));
                }
            }
            case LANDMASS -> {
                if (this.landmassPage == Page.CONTINENTS) {
                    this.addSwitch(left, 0, KEY + "landmass_generation", this.continents.enabled(),
                            () -> this.continents = this.continents.withEnabled(!this.continents.enabled()));
                } else if (this.landmassPage == Page.COASTS) {
                    for (CoastPage each : CoastPage.values()) {
                        String key = KEY + "coasts.page." + each.name().toLowerCase(Locale.ROOT);
                        Button pageButton = Button.builder(Component.translatable(key), button -> {
                            this.coastPage = each;
                            this.rebuildWidgets();
                        }).bounds(left + each.ordinal() * 63, this.rowTop(0), 58, 20).build();
                        pageButton.active = each != this.coastPage;
                        pageButton.setTooltip(Tooltip.create(Component.translatable(key + ".hint")));
                        this.addRenderableWidget(pageButton);
                    }
                    this.addSwitch(left, 1, KEY + "coastline", this.coastline, () -> this.coastline = !this.coastline);
                }
            }
            default -> {
            }
        }
        int bottom = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.translatable(KEY + "preview"),
                        button -> this.minecraft.gui.setScreen(new TerrainPreviewScreen(this, this.chosen(), this.parent.getUiState().getSettings(),
                                this.spawn, spawn -> this.spawn = spawn, seed -> this.parent.getUiState().setSeed(Long.toString(seed)))))
                .bounds(left, bottom, 102, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.applyAndClose())
                .bounds(left + 104, bottom, 102, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(left + 208, bottom, 102, 20).build());
    }

    /**
     * The Presets tab: the built-in presets in three columns on the first rows, a name box and Save below them, then the
     * saved presets, eight to a page, each with a button that deletes it after a second click.
     */
    private void addPresetWidgets(int left) {
        String key = KEY + "preset.";
        WorldPresets[] builtIn = WorldPresets.values();
        for (int i = 0; i < builtIn.length; i++) {
            WorldPresets preset = builtIn[i];
            Component name = Component.translatable(key + preset.id());
            this.addRenderableWidget(Button.builder(name, button -> this.load(preset.apply(this.chosen()), name))
                            .bounds(left + (i % 3) * 104, this.rowTop(i / 3), 102, 20).build())
                    .setTooltip(Tooltip.create(Component.translatable(key + preset.id() + ".hint")));
        }

        List<UserPresets.Preset> saved = UserPresets.list();
        int pages = Math.max(1, (saved.size() + 7) / 8);
        this.presetPage = Math.clamp(this.presetPage, 0, pages - 1);
        int row = this.rowTop((builtIn.length + 2) / 3);
        int boxWidth = pages > 1 ? 174 : 246;
        EditBox name = new EditBox(this.font, left + 1, row, boxWidth - 2, 20, Component.translatable(KEY + "presets.name"));
        name.setMaxLength(UserPresets.MAX_NAME);
        name.setHint(Component.translatable(KEY + "presets.name").withColor(GREY & 0xFFFFFF));
        name.setValue(this.presetName);
        name.setResponder(text -> this.presetName = text);
        this.addRenderableWidget(name);
        this.addRenderableWidget(Button.builder(Component.translatable(KEY + "presets.save"), button -> {
            String typed = this.presetName.trim();
            if (typed.isEmpty()) {
                this.presetStatus = Component.translatable(KEY + "presets.need_name");
            } else if (UserPresets.save(typed, this.chosen())) {
                this.presetStatus = Component.translatable(KEY + "presets.saved", typed);
                this.presetName = "";
            } else {
                this.presetStatus = Component.translatable(KEY + "presets.save_failed");
            }
            this.rebuildWidgets();
        }).bounds(left + boxWidth, row, 64, 20).build()).setTooltip(Tooltip.create(Component.translatable(KEY + "presets.save.hint")));
        if (pages > 1) {
            Button back = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                this.presetPage--;
                this.rebuildWidgets();
            }).bounds(left + 242, row, 33, 20).build());
            back.active = this.presetPage > 0;
            Button next = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                this.presetPage++;
                this.rebuildWidgets();
            }).bounds(left + 277, row, 33, 20).build());
            next.active = this.presetPage < pages - 1;
        }

        for (int i = 0; i < 8 && this.presetPage * 8 + i < saved.size(); i++) {
            UserPresets.Preset preset = saved.get(this.presetPage * 8 + i);
            int x = left + (i % 2) * 157;
            int y = row + (1 + i / 2) * this.rowHeight;
            Component label = Component.literal(preset.name());
            this.addRenderableWidget(Button.builder(label, button -> this.load(preset.climate(), label))
                    .bounds(x, y, 129, 20).build()).setTooltip(Tooltip.create(Component.translatable(KEY + "presets.load.hint")));
            boolean confirming = preset.file().toString().equals(this.pendingDelete);
            this.addRenderableWidget(Button.builder(Component.literal(confirming ? "?" : "x").withColor((confirming ? RED : WHITE) & 0xFFFFFF), button -> {
                if (confirming) {
                    UserPresets.delete(preset);
                    this.pendingDelete = null;
                    this.presetStatus = Component.translatable(KEY + "presets.deleted", preset.name());
                } else {
                    this.pendingDelete = preset.file().toString();
                    this.presetStatus = Component.translatable(KEY + "presets.confirm_delete", preset.name());
                }
                this.rebuildWidgets();
            }).bounds(x + 131, y, 22, 20).build()).setTooltip(Tooltip.create(Component.translatable(KEY + "presets.delete.hint")));
        }
    }

    /** Shows these settings on a fresh copy of this screen, still on the Presets tab and not yet applied. */
    private void load(WorldClimateSettings climate, Component name) {
        CustomizeTerrainScreen loaded = new CustomizeTerrainScreen(this.parent, climate, this.spawn);
        loaded.tab = Tab.PRESETS;
        loaded.presetStatus = Component.translatable(KEY + "presets.loaded", name);
        this.minecraft.gui.setScreen(loaded);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = this.width / 2 - 155;
        List<BlockSetting> rows = this.tabSettings();
        for (int i = 0; i < rows.size(); i++) {
            BlockSetting setting = rows.get(i);
            // The coast size label is greyed out while the Coast Size Override is off, like its slider.
            boolean dim = setting == this.coastSizeRow && !this.coastOverride;
            graphics.text(this.font, Component.translatable(setting.key), left, this.rowTop(this.firstSettingRow() + i) + 6, dim ? GREY : WHITE);
        }

        int below = this.rowTop(ROWS) + 4;
        if (below + this.font.lineHeight > this.height - 30) {
            // No room for the line on the shortest windows.
            return;
        }
        Optional<BlockSetting> invalid = rows.stream().filter(setting -> !setting.valid).findFirst();
        Optional<BlockSetting> beyond = rows.stream().filter(BlockSetting::beyondRecommended).findFirst();
        boolean beyondElsewhere = beyond.isEmpty() && this.allSettings().anyMatch(BlockSetting::beyondRecommended);
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
        } else if (this.tab == Tab.PRESETS) {
            line = this.presetStatus;
        } else if (this.tab == Tab.TERRAIN || this.tab == Tab.STRUCTURES) {
            line = Component.translatable(KEY + this.tab.name().toLowerCase(Locale.ROOT) + ".summary");
        } else if (this.tab == Tab.LANDMASS && !this.continents.enabled() && this.landmassPage != Page.CONTINENTS) {
            line = Component.translatable(KEY + "landmass.off_summary");
        } else if (this.tab == Tab.LANDMASS && this.landmassPage == Page.COASTS) {
            line = Component.translatable(this.coastline ? KEY + "coasts.summary" : KEY + "coastline.off_summary");
        } else if (this.tab == Tab.LANDMASS && this.landmassPage == Page.ISLANDS) {
            line = Component.translatable(KEY + "islands.summary");
        } else if (this.tab == Tab.LANDMASS) {
            line = this.continents.enabled()
                    ? Component.translatable(KEY + "continents.summary", format(Math.round(ContinentShapes.continentSpacing(this.continents))))
                    : Component.translatable(KEY + "continents.off_summary");
        } else if (this.climatePage == Page.DEVIATION) {
            line = Component.translatable(KEY + "deviation.summary");
        } else if (!this.initial.latitude().customProfile().isEmpty()) {
            line = Component.translatable(KEY + "custom_profile");
        } else {
            LatitudeLayout layout = this.chosen().latitude();
            line = Component.translatable(KEY + "zones_summary", format(layout.polarDistance()), format(Math.round(layout.poleDistance())));
        }
        graphics.text(this.font, line, left, below, color);
        if (color == GREY && below + 12 + this.font.lineHeight <= this.height - 30) {
            this.spawn.ifPresent(pos -> graphics.text(this.font, Component.translatable(KEY + "spawn",
                    format(pos.getX()), format(pos.getZ())), left, below + 12, GREY));
        }
    }

    private Stream<BlockSetting> allSettings() {
        return Stream.of(this.zoneSettings, this.deviationSettings, List.of(this.coastSizeRow), this.continentSettings, this.islandSettings,
                this.bridgeSettings, this.barSettings, this.straitSettings, this.erosionSettings, this.shoreSettings).flatMap(List::stream);
    }

    /** The settings as they stand on this screen, applied or not. */
    private WorldClimateSettings chosen() {
        WorldClimateSettings chosen = this.initial;
        if (this.zonesChanged) {
            int[] w = this.zoneWidths;
            chosen = chosen.withZoneWidths(w[0], w[1], w[2], w[3], w[4]);
        }
        if (this.waviness != this.initial.latitude().waviness()) {
            chosen = chosen.withWaviness(this.waviness);
        }
        // Only when moved, so an untouched share (e.g. 20 shown for 19.6) stays exactly as it was.
        if (this.desertShare != this.initial.latitude().desertShare()) {
            chosen = chosen.withDesertShare(this.desertShare);
        }
        return chosen.withHumidityScale(this.humidityScale).withBiomeScale(this.biomeScale).withDeviation(this.deviation)
                .withContinents(this.continents).withCoastline(this.coastline)
                .withCoast(this.coastOverride ? Optional.of(this.coastScale) : Optional.empty()).withBiomeTerrain(this.biomeTerrain).withFeatures(this.features);
    }

    private void applyAndClose() {
        WorldClimateSettings chosen = this.chosen();
        WorldCreationContext context = this.parent.getUiState().getSettings();
        WorldDimensions dimensions = context.selectedDimensions();
        LandmassGenerator.of(dimensions).ifPresent(generator -> {
            LandmassGenerator updated = generator.withClimate(chosen).withSpawn(this.spawn);
            Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>(dimensions.dimensions());
            stems.put(LevelStem.OVERWORLD, new LevelStem(stems.get(LevelStem.OVERWORLD).type(), updated));
            this.parent.getUiState().setSettings(context.withSettings(context.options(), new WorldDimensions(stems)));
        });
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
            this.slider = new BlockSlider(x, y, width, this.min, this.max, this.value, this.linear, "landmass.customize." + this.unit, this::slid);
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
