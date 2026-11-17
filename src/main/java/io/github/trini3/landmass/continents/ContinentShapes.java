package io.github.trini3.landmass.continents;

/**
 * The shapes of a world's continents, archipelagos and islands, given as Minecraft's continentalness: the value its
 * terrain and its biome choice both read to tell deep ocean, ocean, coast and inland apart. Handing the land over
 * as that one value is what keeps everything else working: vanilla terrain, terrain packs that read
 * continentalness, and every biome mod placing biomes by it.
 *
 * <p>How it's built:
 * <ul>
 *     <li>Continents sit on a grid, at most one per cell. Each is a union of a few ellipses (a main body and lobes)
 *     inside a disk of its size, so continents come out blobby and varied. Cell size is worked out from the land
 *     share and the average continent area. The cell at the origin always has a continent, so the world spawns on land.
 *     <li>The whole map is bent by smooth noise (domain warping), which turns the ellipses into natural outlines,
 *     and coastline noise adds bays, peninsulas and the odd offshore island.
 *     <li>Large single islands are built like continents, on a grid of their own whose cell size comes from how
 *     common they should be.
 *     <li>Small islands sit on two finer grids of their own, one for the larger and one for the smaller half of the size
 *     range, so small islands outnumber large ones. A few appear anywhere in the oceans; inside archipelagos
 *     (stretched regions on a third grid) most cells get one.
 *     <li>Every shape gives a signed distance from its coast in blocks (positive on land). Distance becomes
 *     continentalness: the coast band at the shore, rising inland and dropping over a shelf to deep ocean.
 *     Inland, Minecraft's own continentalness noise ({@code vanilla}) decides between near, mid and far inland,
 *     so the interiors keep vanilla's variety of lowlands and highlands.
 * </ul>
 *
 * <p>Everything is a pure function of the seed and position, so it's thread-safe and the same on every run.
 * Coastline noise, shelf widths and the inland rise are kept as separate values so later coastline and
 * mountain settings can change them.
 */
public final class ContinentShapes {
    /** Continentalness at the shore: in Minecraft's coast band, where terrain meets sea level. */
    public static final double SHORE = -0.16;
    // Inland never goes below near-inland, so continents don't get stray inland seas.
    private static final double LOWEST_INTERIOR = -0.05;
    private static final double HIGHEST_INTERIOR = 1.0;

    // A continent built in a disk of radius r is about this many r wide, and covers about this many r^2 on average.
    // Measured from the shapes below (see the test harness in the build kit).
    static final double WIDTH_PER_RADIUS = 1.4;
    static final double AREA_PER_RADIUS_SQUARED = 1.15;
    // Roughly how much land the islands add, so the continents can leave room for it in the land share (fitted
    // from measurements with the test harness): scattered islands, archipelagos per unit of their amount, and large
    // islands per unit of their amount and of ocean.
    private static final double SCATTERED_ISLAND_LAND = 0.007;
    private static final double ARCHIPELAGO_LAND = 0.003;
    private static final double LARGE_ISLAND_LAND = 0.02;

    private static final double CONTINENT_CHANCE = 0.65;
    private static final double ARCHIPELAGO_CHANCE = 0.6;
    private static final double LARGE_ISLAND_CHANCE = 0.6;
    // Large islands take up about this share of the world at the default amount.
    private static final double LARGE_ISLAND_COVERAGE = 0.02;
    // Archipelagos take up about this share of the world at the default amount (most of it in the oceans).
    private static final double ARCHIPELAGO_COVERAGE = 0.06;
    private static final double ARCHIPELAGO_MEAN_ASPECT = 0.55;
    private static final double OPEN_OCEAN_ISLAND_CHANCE = 0.012;
    private static final double ARCHIPELAGO_ISLAND_CHANCE = 0.6;
    // At most this many cells out in each direction are checked.
    private static final int MAX_RANGE = 4;

    private static final long SALT_CONTINENT = 0x3C6EF372FE94F82BL;
    private static final long SALT_ARCHIPELAGO = 0xA54FF53A5F1D36F1L;
    private static final long SALT_BIGGER_ISLANDS = 0x510E527FADE682D1L;
    private static final long SALT_SMALLER_ISLANDS = 0x1F83D9ABFB41BD6BL;
    private static final long SALT_LARGE_ISLAND = 0x6A09E667F3BCC908L;
    private static final long SALT_NOISE = 0x9B05688C2B3E6C1FL;
    private static final long SALT_BRIDGE = 0xBB67AE8584CAA73BL;
    // Land bridges: chance per pair of neighbouring landmasses at the default amount, how far out cells are checked
    // (a bridge runs between neighbouring cells, so it stays within this many cells of where it starts), and the share
    // of beads left out of an island chain.
    private static final double BRIDGE_CHANCE = 0.2;
    private static final int BRIDGE_RANGE = 2;
    private static final int ISLAND_BRIDGE_RANGE = 2;
    private static final double MISSING_BEAD_CHANCE = 0.2;
    // Sand bars: chance a stretch of coast gets one at the default amount, how long a stretch is in bar lengths, how far out
    // (in bar widths) bars and their shoals reach, how wide shoals are, and the trail of islets past a spit's tip.
    private static final long SALT_BAR = 0x3F84D5B5B5470917L;
    private static final double BAR_CHANCE = 0.4;
    private static final double BAR_CELL = 2.5;
    private static final double BAR_REACH = 18.0;
    private static final double SHOAL_WIDTHS = 5.0;
    private static final double ISLET_STEP = 0.06;
    private static final int ISLETS_PAST_TIP = 5;
    private static final double ISLET_RUN = (ISLETS_PAST_TIP + 1) * ISLET_STEP;
    // Partial bridges form across gaps up to this many times the bridge length.
    private static final double PARTIAL_BRIDGE_GAP = 2.0;
    // Straits: chance per pair of landmasses that meet, how far apart (in radii of the smaller) they may still be, and how
    // deep the channel gets (continentalness: ocean, not deep ocean).
    private static final double STRAIT_CHANCE = 0.35;
    private static final double STRAIT_GAP = 0.1;
    private static final double STRAIT_BED = -0.4;

    private final long seed;
    /** How far inland, in blocks, continentalness takes to rise most of the way to the interior's. */
    private final double landRamp;

    private final GradientNoise warpX;
    private final GradientNoise warpZ;
    private final GradientNoise coast;
    private final GradientNoise islandWarpX;
    private final GradientNoise islandWarpZ;
    private final GradientNoise islandCoast;
    private final GradientNoise largeIslandCoast;

    private final double continentMinRadius;
    private final double continentMaxRadius;
    private final double continentCell;
    private final int continentRange;
    private final double continentWarp;
    private final double continentWarpWave;
    private final double continentCoast;
    // Octaves added to coastline and erosion noise on landmasses whose coast noise outgrew the old caps (0 below them).
    private final int continentDetail;
    private final int largeIslandDetail;
    private final double continentCoastWave;
    private final double continentShelf;
    private final double homeX;
    private final double homeZ;
    private final double homeRadius;

    private final boolean largeIslands;
    private final double largeIslandMinRadius;
    private final double largeIslandMaxRadius;
    private final double largeIslandCell;
    private final int largeIslandRange;
    private final double largeIslandCoastAmount;
    private final double largeIslandCoastWave;
    private final double largeIslandShelf;

    private final boolean archipelagos;
    private final double archipelagoMinRadius;
    private final double archipelagoMaxRadius;
    private final double archipelagoCell;
    private final int archipelagoRange;

    private final IslandGrid biggerIslands;
    private final IslandGrid smallerIslands;
    private final double islandMaxRadius;
    private final double islandWarp;
    private final double islandWarpWave;
    private final double islandCoastAmount;
    private final double islandCoastWave;

    private final CoastFeatures coastFeatures;
    private final boolean bridges;
    private final boolean bars;
    private final boolean straits;
    private final double bridgeChance;
    private final double barChance;
    private final double barCell;
    private final double straitChance;
    private final double straitReach;
    private final GradientNoise bridgeNoise;
    private final GradientNoise barNoise;

    private final Erosion continentErosion;
    private final Erosion largeIslandErosion;
    private final Erosion islandErosion;

    // Shores: which kind of shore each stretch of coast is (beach, grassy, rocky, cliffs, in that order along the shore noise).
    private final boolean shoreShaping;
    private final GradientNoise shoreNoise;
    private final double shoreWave;
    private final double[] shoreBounds;

    /**
     * @param bandScale how much wider than vanilla's the coast and inland bands should be; the world's biome size
     *                  (1 = vanilla), of which the square root is used
     */
    public ContinentShapes(ContinentLayout layout, long seed, double bandScale) {
        this.seed = seed;
        double band = Math.sqrt(clamp(bandScale, 0.1, 1_000.0));
        this.landRamp = 500.0 * band;
        this.warpX = new GradientNoise(mix(seed, 1, SALT_NOISE));
        this.warpZ = new GradientNoise(mix(seed, 2, SALT_NOISE));
        this.coast = new GradientNoise(mix(seed, 3, SALT_NOISE));
        this.islandWarpX = new GradientNoise(mix(seed, 4, SALT_NOISE));
        this.islandWarpZ = new GradientNoise(mix(seed, 5, SALT_NOISE));
        this.islandCoast = new GradientNoise(mix(seed, 6, SALT_NOISE));
        this.largeIslandCoast = new GradientNoise(mix(seed, 7, SALT_NOISE));

        // Continents
        double minSize = layout.continentSize().min();
        double maxSize = layout.continentSize().max();
        double typical = Math.sqrt(minSize * maxSize);
        this.continentMinRadius = minSize / WIDTH_PER_RADIUS;
        this.continentMaxRadius = maxSize / WIDTH_PER_RADIUS;
        // Fractal noise mostly stays within +-0.3, so these move coasts by about a third of the amount given.
        this.continentWarp = 0.35 * typical;
        this.continentWarpWave = 0.8 * typical;
        // Before 0.17.0 (scaled_coasts missing) the coastline wobble stopped growing at 6,000 blocks, with bumps up to 12,000 blocks
        // wide, so coasts and their erosion looked smoother and smoother on huge continents. Now it grows with the land, and finer
        // octaves are added as it does, so coasts stay rugged down to the old detail size.
        boolean scaled = layout.scaledCoasts();
        this.continentCoast = scaled ? Math.max(60.0, 0.12 * typical) : clamp(0.12 * typical, 60.0, 6000.0);
        this.continentCoastWave = scaled ? Math.max(150.0, 0.25 * typical) : clamp(0.25 * typical, 150.0, 12000.0);
        this.continentDetail = extraOctaves(this.continentCoastWave, 12000.0);
        this.continentShelf = clamp(0.06 * typical, 300.0, 3000.0) * band;
        double reach = this.continentMaxRadius + this.continentCoast + this.continentShelf;
        this.continentCell = Math.max(gridCell(layout), reach / MAX_RANGE);
        this.continentRange = (int) Math.ceil(reach / this.continentCell);

        // The home continent, in the cell at the origin: the origin is a quarter of the way out from the middle of
        // its main body, which is kept fairly round. Placed after warping, so the origin really is inside.
        long home = cellHash(SALT_CONTINENT, 0, 0);
        this.homeRadius = Math.max(logLerp(this.continentMinRadius, this.continentMaxRadius, unit(home, 3)),
                Math.sqrt(this.continentMinRadius * this.continentMaxRadius));
        double homeMinor = this.homeRadius * mainMajor(home) * homeAspect(home);
        double direction = 2.0 * Math.PI * unit(home, 1);
        double limit = 0.499 * this.continentCell;
        this.homeX = clamp(this.warpedContinentX(0.0, 0.0) + 0.25 * homeMinor * Math.cos(direction), -limit, limit);
        this.homeZ = clamp(this.warpedContinentZ(0.0, 0.0) + 0.25 * homeMinor * Math.sin(direction), -limit, limit);

        // Large islands: like small continents, as many as their amount asks for.
        double largeMin = layout.largeIslandSize().min();
        double largeMax = layout.largeIslandSize().max();
        double largeTypical = Math.sqrt(largeMin * largeMax);
        this.largeIslands = layout.largeIslandAmount() > 0.0;
        this.largeIslandMinRadius = largeMin / WIDTH_PER_RADIUS;
        this.largeIslandMaxRadius = largeMax / WIDTH_PER_RADIUS;
        this.largeIslandCoastAmount = scaled ? Math.max(20.0, 0.12 * largeTypical) : clamp(0.12 * largeTypical, 20.0, 3000.0);
        this.largeIslandCoastWave = scaled ? Math.max(60.0, 0.25 * largeTypical) : clamp(0.25 * largeTypical, 60.0, 6000.0);
        this.largeIslandDetail = extraOctaves(this.largeIslandCoastWave, 6000.0);
        this.largeIslandShelf = clamp(0.06 * largeTypical, 150.0, 1500.0) * band;
        double largeReach = this.largeIslandMaxRadius + this.largeIslandCoastAmount + this.largeIslandShelf;
        double largeCoverage = clamp(LARGE_ISLAND_COVERAGE * layout.largeIslandAmount(), 1.0E-6, 0.5);
        double largeArea = AREA_PER_RADIUS_SQUARED * meanSquare(this.largeIslandMinRadius, this.largeIslandMaxRadius);
        this.largeIslandCell = Math.max(Math.sqrt(LARGE_ISLAND_CHANCE * largeArea / -Math.log(1.0 - largeCoverage)), largeReach / MAX_RANGE);
        this.largeIslandRange = (int) Math.ceil(largeReach / this.largeIslandCell);

        // Archipelagos
        this.archipelagos = layout.archipelagoAmount() > 0.0;
        double archipelagoCoverage = clamp(ARCHIPELAGO_COVERAGE * layout.archipelagoAmount(), 1.0E-6, 0.5);
        this.archipelagoMinRadius = layout.archipelagoSize().min() / 2.0;
        this.archipelagoMaxRadius = layout.archipelagoSize().max() / 2.0;
        double archipelagoArea = Math.PI * ARCHIPELAGO_MEAN_ASPECT * meanSquare(this.archipelagoMinRadius, this.archipelagoMaxRadius);
        this.archipelagoCell = Math.max(Math.sqrt(ARCHIPELAGO_CHANCE * archipelagoArea / -Math.log(1.0 - archipelagoCoverage)),
                this.archipelagoMaxRadius / MAX_RANGE);
        this.archipelagoRange = (int) Math.ceil(this.archipelagoMaxRadius / this.archipelagoCell);

        // Islands
        // Islands are built like small continents.
        double islandMinRadius = layout.islandSize().min() / WIDTH_PER_RADIUS;
        this.islandMaxRadius = layout.islandSize().max() / WIDTH_PER_RADIUS;
        double islandMidRadius = Math.sqrt(islandMinRadius * this.islandMaxRadius);
        this.islandWarp = 0.4 * islandMinRadius + 0.2 * this.islandMaxRadius;
        this.islandWarpWave = 2.0 * this.islandMaxRadius;
        this.islandCoastAmount = clamp(0.5 * islandMinRadius, 3.0, Math.max(3.0, 0.3 * this.islandMaxRadius));
        this.islandCoastWave = clamp(0.8 * islandMinRadius + 0.3 * this.islandMaxRadius, 16.0, 1500.0);
        this.biggerIslands = IslandGrid.of(SALT_BIGGER_ISLANDS, islandMidRadius, this.islandMaxRadius, this.islandCoastAmount, 600.0 * band);
        this.smallerIslands = IslandGrid.of(SALT_SMALLER_ISLANDS, islandMinRadius, islandMidRadius, this.islandCoastAmount, 600.0 * band);

        // Coastal features
        this.coastFeatures = layout.coastFeatures();
        this.bridges = this.coastFeatures.bridges();
        this.bars = this.coastFeatures.bars();
        this.straits = this.coastFeatures.straits();
        this.bridgeChance = Math.min(1.0, BRIDGE_CHANCE * this.coastFeatures.bridgeAmount());
        // Past the point where every stretch has a bar, more bars means shorter stretches.
        double barRolls = BAR_CHANCE * this.coastFeatures.barAmount();
        this.barChance = Math.min(1.0, barRolls);
        this.barCell = BAR_CELL * this.coastFeatures.barLength() / Math.max(1.0, barRolls);
        this.straitChance = Math.min(1.0, STRAIT_CHANCE * this.coastFeatures.straitAmount());
        // How far inland a strait can matter: its widest half plus the banks' rise.
        this.straitReach = this.straits ? 0.5 * this.coastFeatures.straitWidth().max() + 3.0 * this.landRamp : Double.NEGATIVE_INFINITY;
        this.bridgeNoise = new GradientNoise(mix(seed, 8, SALT_NOISE));
        this.barNoise = new GradientNoise(mix(seed, 9, SALT_NOISE));

        // Coastal erosion: sized from each kind of landmass's own coastline noise.
        CoastErosion erosion = layout.erosion();
        long erosionSeed = mix(seed, 10, SALT_NOISE);
        this.continentErosion = Erosion.of(erosionSeed, erosion.mainlandRate(), erosion.mainlandIntensity(), erosion.mainlandVariation(), this.continentCoast,
                this.continentCoastWave, this.continentDetail);
        this.largeIslandErosion = Erosion.of(erosionSeed + 1, erosion.islandRate(), erosion.islandIntensity(), erosion.islandVariation(), this.largeIslandCoastAmount,
                this.largeIslandCoastWave, this.largeIslandDetail);
        this.islandErosion = Erosion.of(erosionSeed + 2, erosion.islandRate(), erosion.islandIntensity(), erosion.islandVariation(), Math.max(this.islandCoastAmount, 8.0),
                this.islandCoastWave, 0);

        Shores shores = layout.shores();
        this.shoreShaping = shores.enabled();
        this.shoreNoise = new GradientNoise(mix(seed, 11, SALT_NOISE));
        this.shoreWave = 2.0 * shores.stretch();
        double total = Math.max(1.0E-9, shores.beaches() + shores.grassy() + shores.rocky() + shores.cliffs());
        double beach = shores.beaches() / total;
        double grassy = beach + shores.grassy() / total;
        double rocky = grassy + shores.rocky() / total;
        this.shoreBounds = new double[]{beach, grassy, rocky};
    }

    /**
     * Continentalness at a block position.
     *
     * @param vanilla the continentalness the world would have had, used for variety inland and far out at sea
     *                (including the rare mushroom fields)
     */
    public double continentalness(double x, double z, double vanilla) {
        double interior = clamp(0.2 + 0.5 * vanilla, LOWEST_INTERIOR, HIGHEST_INTERIOR);
        double ocean = Math.min(-0.6 + 0.1 * clamp(vanilla, -1.0, 1.0), vanilla);

        double cx = this.warpedContinentX(x, z);
        double cz = this.warpedContinentZ(x, z);
        double land = this.continentLand(cx, cz);
        // Cliffs rise right from the water, beaches slope gently: the kind of shore sets how fast the land climbs inland.
        double ramp = this.shoreShaping ? this.shoreRamp(x, z) : this.landRamp;
        double value = land > Double.NEGATIVE_INFINITY ? this.toContinentalness(land, this.continentShelf, interior, ocean, ramp) : ocean;
        if (this.bars && this.nearBarBand(land)) {
            Body body = this.nearestBody(SALT_CONTINENT, CONTINENT_CHANCE, this.continentMinRadius, this.continentMaxRadius, this.continentCell,
                    this.continentRange, true, null, cx, cz);
            if (body != null) {
                value = Math.max(value, this.bars(body, land, cx, cz, interior, ocean, SALT_CONTINENT));
            }
        }
        if (this.bridges && land < 0.5 * this.coastFeatures.bridgeWidth().max()) {
            value = Math.max(value, this.pairs(SALT_CONTINENT, CONTINENT_CHANCE, this.continentMinRadius, this.continentMaxRadius, this.continentCell,
                    true, null, BRIDGE_RANGE, true, false, cx, cz, interior, ocean)[0]);
            if (this.largeIslands) {
                value = Math.max(value, this.pairs(SALT_LARGE_ISLAND, LARGE_ISLAND_CHANCE, this.largeIslandMinRadius, this.largeIslandMaxRadius,
                        this.largeIslandCell, false, null, BRIDGE_RANGE, true, false, cx, cz, interior, ocean)[0]);
            }
        }
        // Each kind of island is skipped where the continent is already higher than that kind of island gets.
        if (this.largeIslands && land < this.largeIslandMaxRadius + this.largeIslandCoastAmount) {
            double large = this.landDistance(SALT_LARGE_ISLAND, LARGE_ISLAND_CHANCE, this.largeIslandMinRadius, this.largeIslandMaxRadius,
                    this.largeIslandCell, this.largeIslandRange, this.largeIslandCoastAmount + this.largeIslandShelf + this.largeIslandErosion.outward, false, cx, cz);
            if (large > Double.NEGATIVE_INFINITY) {
                large += this.largeIslandCoastAmount * this.largeIslandCoast.fractal(cx, cz, this.largeIslandCoastWave, 4 + this.largeIslandDetail);
                large = this.largeIslandErosion.apply(large, cx, cz);
                value = Math.max(value, this.toContinentalness(large, this.largeIslandShelf, interior, ocean, ramp));
                if (this.bars && this.nearBarBand(large)) {
                    Body body = this.nearestBody(SALT_LARGE_ISLAND, LARGE_ISLAND_CHANCE, this.largeIslandMinRadius, this.largeIslandMaxRadius,
                            this.largeIslandCell, this.largeIslandRange, false, null, cx, cz);
                    if (body != null) {
                        value = Math.max(value, this.bars(body, large, cx, cz, interior, ocean, SALT_LARGE_ISLAND));
                    }
                }
            }
        }
        // Straits cut through everything so far; islands come after, so a strait can have islets in it. They only ever lower
        // land and shallows, so open sea skips them.
        if (this.straits && value > STRAIT_BED && land < this.straitReach) {
            value = Math.min(value, this.pairs(SALT_CONTINENT, CONTINENT_CHANCE, this.continentMinRadius, this.continentMaxRadius, this.continentCell,
                    true, null, BRIDGE_RANGE, false, true, cx, cz, interior, ocean)[1]);
            if (this.largeIslands) {
                value = Math.min(value, this.pairs(SALT_LARGE_ISLAND, LARGE_ISLAND_CHANCE, this.largeIslandMinRadius, this.largeIslandMaxRadius,
                        this.largeIslandCell, false, null, BRIDGE_RANGE, false, true, cx, cz, interior, ocean)[1]);
            }
        }
        if (land < this.islandMaxRadius + this.islandCoastAmount) {
            double ix = x + this.islandWarp * this.islandWarpX.fractal(x, z, this.islandWarpWave, 2);
            double iz = z + this.islandWarp * this.islandWarpZ.fractal(x, z, this.islandWarpWave, 2);
            double bigger = this.islandDistance(this.biggerIslands, ix, iz);
            double smaller = this.islandDistance(this.smallerIslands, ix, iz);
            if (bigger > Double.NEGATIVE_INFINITY || smaller > Double.NEGATIVE_INFINITY) {
                double coastline = this.islandCoastAmount * this.islandCoast.fractal(ix, iz, this.islandCoastWave, 3);
                if (this.islandErosion.on) {
                    bigger = bigger > Double.NEGATIVE_INFINITY ? this.islandErosion.apply(bigger + coastline, ix, iz) - coastline : bigger;
                    smaller = smaller > Double.NEGATIVE_INFINITY ? this.islandErosion.apply(smaller + coastline, ix, iz) - coastline : smaller;
                }
                if (bigger > Double.NEGATIVE_INFINITY) {
                    value = Math.max(value, this.toContinentalness(bigger + coastline, this.biggerIslands.shelf, interior, ocean, ramp));
                    if (this.bars && this.nearBarBand(bigger + coastline)) {
                        Body body = this.nearestBody(SALT_BIGGER_ISLANDS, ARCHIPELAGO_ISLAND_CHANCE, this.biggerIslands.minRadius, this.biggerIslands.maxRadius,
                                this.biggerIslands.cell, 1, false, this.biggerIslands, ix, iz);
                        if (body != null) {
                            value = Math.max(value, this.bars(body, bigger + coastline, ix, iz, interior, ocean, SALT_BIGGER_ISLANDS));
                        }
                    }
                }
                if (smaller > Double.NEGATIVE_INFINITY) {
                    value = Math.max(value, this.toContinentalness(smaller + coastline, this.smallerIslands.shelf, interior, ocean, ramp));
                }
            }
            // Bridges between small islands only run inside archipelagos (both ends are in one, and archipelagos are convex),
            // so the rest of the ocean can skip them, apart from a margin for their meanders, islets and shoals.
            if (this.bridges && this.nearArchipelago(ix, iz, 3.0 * this.biggerIslands.maxRadius)) {
                value = Math.max(value, this.pairs(SALT_BIGGER_ISLANDS, ARCHIPELAGO_ISLAND_CHANCE, this.biggerIslands.minRadius,
                        this.biggerIslands.maxRadius, this.biggerIslands.cell, false, this.biggerIslands, ISLAND_BRIDGE_RANGE, true, false, ix, iz, interior, ocean)[0]);
                value = Math.max(value, this.pairs(SALT_SMALLER_ISLANDS, ARCHIPELAGO_ISLAND_CHANCE, this.smallerIslands.minRadius,
                        this.smallerIslands.maxRadius, this.smallerIslands.cell, false, this.smallerIslands, ISLAND_BRIDGE_RANGE, true, false, ix, iz, interior, ocean)[0]);
            }
        }
        return value;
    }

    // ---- Sand bars --------------------------------------------------------------------------------------------------

    /** Whether a point this far from a coast (negative at sea) can be on a sand bar or its shoal. */
    private boolean nearBarBand(double land) {
        double width = this.coastFeatures.barWidth();
        return land > -BAR_REACH * width && land < width;
    }

    /**
     * Continentalness of the sand bars along one landmass's coast, or deep ocean if none is here. {@code land} is the
     * signed distance to that coast (negative at sea). Bars are placed around the landmass by angle: its coast is split
     * into stretches, and some stretches get a spit (joined to the shore at its root, running along the coast and curling
     * back toward land at its tip, with sandbank islets beyond) or a barrier (joined at both ends, closing off a shallow
     * lagoon, often with an inlet). Both sit on wide shallow shoals.
     */
    private double bars(Body body, double land, double x, double z, double interior, double ocean, long layer) {
        double radius = Math.max(body.radius, 1.0);
        int cells = Math.max(1, (int) Math.round(2.0 * Math.PI * radius / this.barCell));
        double cellAngle = 2.0 * Math.PI / cells;
        double theta = Math.atan2(z - body.z, x - body.x) + Math.PI;
        int cell = Math.min(cells - 1, (int) (theta / cellAngle));
        double value = ocean;
        // A bar starts in its own stretch and can run on into the next, so this stretch and the one before (either way round).
        for (int i = -1; i <= 1 && i < cells; i++) {
            value = Math.max(value, this.bar(body, Math.floorMod(cell + i, cells), cells, cellAngle, theta, -land, x, z, interior, ocean, layer));
        }
        return value;
    }

    private double bar(Body body, int cell, int cells, double cellAngle, double theta, double out, double x, double z, double interior,
                       double ocean, long layer) {
        long hash = mix(body.hash ^ SALT_BAR ^ layer, cell, cells);
        if (unit(hash, 0) >= this.barChance) {
            return ocean;
        }
        double width = this.coastFeatures.barWidth();
        double radius = Math.max(body.radius, 1.0);
        double length = Math.min(this.coastFeatures.barLength() * (0.6 + 0.9 * unit(hash, 2)), 0.45 * 2.0 * Math.PI * radius);
        double span = length / radius;
        double start = (cell + 0.9 * unit(hash, 1)) * cellAngle;
        double turn = theta - start;
        turn -= 2.0 * Math.PI * Math.floor((turn + Math.PI) / (2.0 * Math.PI));
        if (unit(hash, 3) < 0.5) {
            turn = -turn;
        }
        double t = turn / span;
        boolean lagoon = unit(hash, 4) < this.coastFeatures.lagoonShare();
        double reach = lagoon ? width * (3.0 + 7.0 * unit(hash, 5)) : width * (2.5 + 4.0 * unit(hash, 5));
        if (t < 0.0 || t > (lagoon ? 1.0 : 1.0 + ISLET_RUN)) {
            return ocean;
        }
        double value = ocean;
        if (t <= 1.0) {
            double offset = barOffset(t, lagoon, reach);
            double slope = (barOffset(Math.min(1.0, t + 0.01), lagoon, reach) - barOffset(Math.max(0.0, t - 0.01), lagoon, reach)) / (0.02 * length);
            double cross = Math.abs(out - offset) / Math.sqrt(1.0 + slope * slope);
            double half = lagoon ? 0.5 * width * (0.8 + 0.4 * Math.sin(Math.PI * t)) : 0.5 * width * (1.3 - 0.9 * t);
            if (cross > half + SHOAL_WIDTHS * width) {
                return this.barIslets(hash, t, length, out, lagoon, reach, width, interior, ocean);
            }
            double distance = half - cross + 0.25 * half * this.barNoise.fractal(x, z, 3.0 * width, 2);
            if (lagoon && unit(hash, 6) < 0.6) {
                // An inlet through the barrier, where the lagoon meets the sea.
                double gap = width * (0.6 + unit(hash, 8));
                double fromInlet = Math.abs(t - (0.3 + 0.4 * unit(hash, 7))) * length;
                distance = Math.min(distance, fromInlet - gap);
            }
            value = Math.max(this.toContinentalness(Math.min(distance, half), width, interior, ocean), shoal(cross - half, width, ocean));
        }
        return Math.max(value, this.barIslets(hash, t, length, out, lagoon, reach, width, interior, ocean));
    }

    /**
     * How far out from the shore a bar runs at {@code t} (0 at its root, 1 at its tip). A spit leaves the shore, runs
     * along the coast and curls back toward land at its tip; a barrier bows out from the shore and back.
     */
    private static double barOffset(double t, boolean lagoon, double reach) {
        if (lagoon) {
            return reach * Math.sqrt(Math.sin(Math.PI * t));
        }
        double rise = smoothStep(t / 0.3);
        double hook = t > 0.75 ? (t - 0.75) / 0.25 : 0.0;
        return reach * (rise - 0.6 * hook * hook);
    }

    /** Sandbank islets off a bar: a trail beyond a spit's tip, and a few scattered on the seaward side of any bar. */
    private double barIslets(long hash, double t, double length, double out, boolean lagoon, double reach, double width, double interior,
                             double ocean) {
        double amount = this.coastFeatures.barIslets();
        if (amount <= 0.0) {
            return ocean;
        }
        double best = Double.NEGATIVE_INFINITY;
        double size = 0.0;
        if (!lagoon && t > 0.85) {
            double tip = barOffset(1.0, false, reach);
            int nearest = (int) Math.round((t - 1.0) / ISLET_STEP);
            for (int k = Math.max(1, nearest - 1); k <= Math.min(ISLETS_PAST_TIP, nearest + 1); k++) {
                long islet = mix(hash, k, 11);
                if (unit(islet, 0) >= 0.55 * amount) {
                    continue;
                }
                double r = width * (0.35 + 0.5 * unit(islet, 1)) * (1.0 - 0.12 * k);
                double dx = (t - (1.0 + k * ISLET_STEP)) * length;
                double dz = out - (tip + 2.0 * width * (unit(islet, 2) - 0.5));
                double d = r - Math.sqrt(dx * dx + dz * dz);
                if (d > best) {
                    best = d;
                    size = r;
                }
            }
        }
        if (t <= 1.0) {
            double spacing = 8.0 * width;
            int nearest = (int) Math.round(t * length / spacing);
            for (int k = nearest - 1; k <= nearest + 1; k++) {
                long islet = mix(hash, k, 13);
                double tk = k * spacing / length;
                if (tk < 0.1 || tk > 0.95 || unit(islet, 0) >= 0.25 * amount) {
                    continue;
                }
                double r = width * (0.3 + 0.45 * unit(islet, 1));
                double dx = (t - tk) * length;
                double dz = out - (barOffset(tk, lagoon, reach) + width * (2.0 + 2.0 * unit(islet, 2)));
                double d = r - Math.sqrt(dx * dx + dz * dz);
                if (d > best) {
                    best = d;
                    size = r;
                }
            }
        }
        if (best == Double.NEGATIVE_INFINITY) {
            return ocean;
        }
        return Math.max(this.toContinentalness(Math.min(best, size), width, interior, ocean), shoal(-best, width, ocean));
    }

    /**
     * Shallow water around a sand bar, bridge or islet: continentalness just below the coast band next to it, getting
     * deeper over {@link #SHOAL_WIDTHS} widths. {@code outside} is how far out from the landform's edge the point is.
     */
    private static double shoal(double outside, double width, double ocean) {
        double q = Math.max(0.0, outside) / (SHOAL_WIDTHS * width);
        if (q >= 1.0) {
            return ocean;
        }
        // Eases into the sea floor around it, so the shoal has no edge.
        double top = SHORE - 0.035;
        return Math.max(ocean, top + (ocean - top) * smoothStep(q));
    }

    // ---- Shores -------------------------------------------------------------------------------------------------------

    // Spread of the three-octave shore noise (measured with the test harness) and how gradually one kind of shore turns
    // into the next, as a part of all shores.
    private static final double SHORE_SPREAD = 0.2;
    private static final double SHORE_BLEND = 0.04;
    // Per kind of shore (beach, grassy, rocky, cliffs): how fast the land rises compared with usual, the erosion Minecraft's
    // biome and terrain choice should see at the shore, and the weirdness (as a size; its sign is kept). Beaches: low weirdness
    // slice with erosion 4 (beach biomes, flat land). Grassy: high slice, erosion 3 (the inland biome runs to the water).
    // Rocky: low slice, erosion 1 (stony shore). Cliffs: middle slice, erosion 1 (stony shore, high rugged land behind it).
    private static final double[] SHORE_RAMP = {1.6, 0.7, 0.8, 0.06};
    private static final double[] SHORE_EROSION = {0.25, 0.0, -0.55, -0.55};
    private static final double[] SHORE_WEIRDNESS = {0.16, 0.48, 0.16, 0.33};

    /** Whether the coasts are steered into stretches of one kind of shore. */
    public boolean shapesShores() {
        return this.shoreShaping;
    }

    /** How much each kind of shore (beach, grassy, rocky, cliffs) applies at a position; they add up to 1. */
    private void shoreWeights(double x, double z, double[] weights) {
        double u = normalCdf(this.shoreNoise.fractal(x, z, this.shoreWave, 3) / SHORE_SPREAD);
        double previous = 1.0;
        for (int i = 0; i < 3; i++) {
            double above = smoothStep((u - this.shoreBounds[i]) / SHORE_BLEND + 0.5);
            weights[i] = previous - above;
            previous = above;
        }
        weights[3] = previous;
    }

    private static final ThreadLocal<double[]> SHORE_WEIGHTS = ThreadLocal.withInitial(() -> new double[4]);

    private double shoreRamp(double x, double z) {
        double[] w = SHORE_WEIGHTS.get();
        this.shoreWeights(x, z, w);
        double ramp = 0.0;
        for (int i = 0; i < 4; i++) {
            ramp += w[i] * SHORE_RAMP[i];
        }
        return ramp * this.landRamp;
    }

    /** The erosion, or with {@code ridges} the weirdness, that the kind of shore at a position asks for, given the value there. */
    public double shoreTarget(boolean ridges, double x, double z, double value) {
        double[] w = SHORE_WEIGHTS.get();
        this.shoreWeights(x, z, w);
        double[] targets = ridges ? SHORE_WEIRDNESS : SHORE_EROSION;
        double target = 0.0;
        for (int i = 0; i < 4; i++) {
            target += w[i] * targets[i];
        }
        return ridges ? Math.copySign(target, value) : target;
    }

    /** The standard normal distribution function (Zelen and Severo 26.2.17, accurate to about 1e-7). */
    private static double normalCdf(double x) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(x));
        double d = 0.3989422804014327 * Math.exp(-0.5 * x * x);
        double p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        return x >= 0.0 ? 1.0 - p : p;
    }

    // ---- Coastal erosion -------------------------------------------------------------------------------------------

    /**
     * Wears stretches of coast into jagged headlands, coves and bays with narrow inlets cutting inland. Works on a signed
     * distance to the coast (positive on land), only near the coast: far inland and far out at sea it's left as it was,
     * so no lakes or islands appear away from the shore. Which stretches are eroded comes from a broad mask noise.
     */
    static final class Erosion {
        // Spread of the two-octave mask noise (measured with the test harness), and how gradually eroded stretches start.
        private static final double MASK_SPREAD = 0.2;
        private static final double MASK_SOFTNESS = 0.08;
        private static final Erosion OFF = new Erosion(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null);

        final boolean on;
        private final double threshold;
        private final double maskWave;
        private final double roughAmount;
        private final double roughWave;
        private final double inletDepth;
        private final double inletWave;
        private final double inward;
        final double outward;
        private final GradientNoise mask;
        private final GradientNoise rough;
        private final GradientNoise inlets;
        private final double variation;
        // Extra octaves for the rough and inlet noise, matching the coastline's.
        private final int detail;
        private final GradientNoise strengthNoise;

        private Erosion(boolean on, double threshold, double maskWave, double roughAmount, double roughWave, double inletDepth, double inletWave,
                        double inward, double outward, double variation, int detail, GradientNoise mask, GradientNoise rough, GradientNoise inlets,
                        GradientNoise strengthNoise) {
            this.variation = variation;
            this.detail = detail;
            this.strengthNoise = strengthNoise;
            this.on = on;
            this.threshold = threshold;
            this.maskWave = maskWave;
            this.roughAmount = roughAmount;
            this.roughWave = roughWave;
            this.inletDepth = inletDepth;
            this.inletWave = inletWave;
            this.inward = inward;
            this.outward = outward;
            this.mask = mask;
            this.rough = rough;
            this.inlets = inlets;
        }

        /**
         * @param rate      the part of the coast eroded, 0 to 1
         * @param intensity how strong, 1 = default
         * @param variation how much the intensity swings up and down along the coast, 0 to 1
         * @param coast     the landmass's coastline noise amount in blocks, which sizes everything
         * @param coastWave the landmass's coastline noise wavelength in blocks
         * @param detail    octaves added to the rough and inlet noise on very large landmasses
         */
        static Erosion of(long seed, double rate, double intensity, double variation, double coast, double coastWave, int detail) {
            if (rate <= 0.0 || intensity <= 0.0) {
                return OFF;
            }
            // Above the threshold counts as eroded; rate 1 erodes everything.
            double threshold = rate >= 1.0 ? Double.NEGATIVE_INFINITY : MASK_SPREAD * probit(1.0 - rate);
            double roughAmount = 0.9 * intensity * coast;
            double inletDepth = 0.8 * intensity * coast;
            // Fractal noise stays within about +-0.7: the furthest the coast can move out, and the band inland it can affect.
            // With variation, the strongest stretches reach (1 + variation) times as far.
            double peak = 1.0 + clamp(variation, 0.0, 1.0);
            double outward = 0.7 * roughAmount * peak;
            double inward = 2.0 * (outward + inletDepth * peak);
            return new Erosion(true, threshold, 2.5 * coastWave, roughAmount, 0.5 * coastWave, inletDepth, 0.6 * coastWave, inward, 2.0 * outward,
                    clamp(variation, 0.0, 1.0), detail, new GradientNoise(mix(seed, 1, SALT_NOISE)), new GradientNoise(mix(seed, 2, SALT_NOISE)),
                    new GradientNoise(mix(seed, 3, SALT_NOISE)), new GradientNoise(mix(seed, 4, SALT_NOISE)));
        }

        /** The signed distance to the coast after erosion. */
        double apply(double distance, double x, double z) {
            if (!this.on || distance >= this.inward || distance <= -this.outward) {
                return distance;
            }
            double strength = this.threshold == Double.NEGATIVE_INFINITY ? 1.0
                    : smoothStep((this.mask.fractal(x, z, this.maskWave, 2) - this.threshold) / MASK_SOFTNESS + 0.5);
            if (strength <= 0.0) {
                return distance;
            }
            // Fades out toward the edges of the band, so the distance stays continuous where erosion stops.
            double fade = distance > 0.0 ? 1.0 - smoothStep((distance - 0.5 * this.inward) / (0.5 * this.inward))
                    : 1.0 - smoothStep((-distance - 0.5 * this.outward) / (0.5 * this.outward));
            if (fade <= 0.0) {
                return distance;
            }
            // Rugged headlands and coves at several scales.
            double change = this.roughAmount * this.rough.fractal(x, z, this.roughWave, 4 + this.detail);
            // Inlets: narrow channels along the noise's zero lines, deepest at the coast and closing up inland.
            double ridge = 1.0 - 4.0 * Math.abs(this.inlets.fractal(x, z, this.inletWave, 3 + this.detail));
            if (ridge > 0.0) {
                double reach = distance > 0.0 ? Math.max(0.0, 1.0 - distance / (1.5 * this.inletDepth)) : smoothStep(1.0 + distance / (0.3 * this.outward));
                change -= this.inletDepth * ridge * ridge * reach;
            }
            if (this.variation > 0.0) {
                // Intensity drifts up and down along the coast, between (1 - variation) and (1 + variation) times the setting.
                change *= 1.0 + this.variation * clamp(1.6 * this.strengthNoise.fractal(x, z, 1.5 * this.maskWave, 2), -1.0, 1.0);
            }
            return distance + strength * fade * change;
        }

        /** The standard normal quantile (Abramowitz and Stegun 26.2.23, accurate to about 0.0005). */
        private static double probit(double p) {
            if (p <= 0.0) {
                return Double.NEGATIVE_INFINITY;
            }
            if (p >= 1.0) {
                return Double.POSITIVE_INFINITY;
            }
            double q = p < 0.5 ? p : 1.0 - p;
            double t = Math.sqrt(-2.0 * Math.log(q));
            double x = t - (2.515517 + 0.802853 * t + 0.010328 * t * t) / (1.0 + 1.432788 * t + 0.189269 * t * t + 0.001308 * t * t * t);
            return p < 0.5 ? -x : x;
        }
    }

    private static double smoothStep(double t) {
        double c = clamp(t, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    // ---- Landmasses by cell -----------------------------------------------------------------------------------------

    /**
     * A landmass on one grid: its middle, the radius of its disk and its cell's hash. For a small island that only exists
     * inside an archipelago, that check is slow, so it's left until it's needed ({@link #exists}).
     */
    private static final class Body {
        final double x;
        final double z;
        final double radius;
        final long hash;
        final boolean home;
        // Null until checked, for islands that need an archipelago here; true for everything else.
        Boolean exists;
        // How strong the archipelago must be here for the island to exist.
        final double archipelagoNeeded;

        Body(double x, double z, double radius, long hash, boolean home, Boolean exists, double archipelagoNeeded) {
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.hash = hash;
            this.home = home;
            this.exists = exists;
            this.archipelagoNeeded = archipelagoNeeded;
        }
    }

    private boolean exists(Body body) {
        if (body.exists == null) {
            body.exists = this.archipelago(body.x, body.z) > body.archipelagoNeeded;
        }
        return body.exists;
    }

    /** The landmass in one cell of a grid, or null if the cell has none (the same choice {@link #landDistance} and islandDistance make). */
    private Body body(long salt, double chance, double minRadius, double maxRadius, double cell, boolean home, IslandGrid islands, int cx, int cz) {
        long hash = cellHash(salt, cx, cz);
        if (home && cx == 0 && cz == 0) {
            return new Body(this.homeX, this.homeZ, this.homeRadius, hash, true, true, 0.0);
        }
        double roll = unit(hash, 0);
        if (roll >= chance) {
            return null;
        }
        double centerX = (cx - 0.5 + unit(hash, 1)) * cell;
        double centerZ = (cz - 0.5 + unit(hash, 2)) * cell;
        double radius = logLerp(minRadius, maxRadius, unit(hash, 3));
        if (islands != null && roll >= OPEN_OCEAN_ISLAND_CHANCE) {
            // Exists where the archipelago is stronger than this (the same test islandDistance makes), checked later.
            double needed = (roll - OPEN_OCEAN_ISLAND_CHANCE) / (ARCHIPELAGO_ISLAND_CHANCE - OPEN_OCEAN_ISLAND_CHANCE);
            return this.archipelagos ? new Body(centerX, centerZ, radius, hash, false, null, needed) : null;
        }
        return new Body(centerX, centerZ, radius, hash, false, true, 0.0);
    }

    /** The landmass of one grid whose coast is nearest (the one landDistance or islandDistance measures to), or null. */
    private Body nearestBody(long salt, double chance, double minRadius, double maxRadius, double cell, int range, boolean home, IslandGrid islands,
                             double x, double z) {
        int cellX = (int) Math.floor(x / cell + 0.5);
        int cellZ = (int) Math.floor(z / cell + 0.5);
        Body nearest = null;
        double best = Double.NEGATIVE_INFINITY;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                Body body = this.body(salt, chance, minRadius, maxRadius, cell, home, islands, cellX + dx, cellZ + dz);
                if (body == null) {
                    continue;
                }
                double ox = x - body.x;
                double oz = z - body.z;
                double disk = body.radius - Math.sqrt(ox * ox + oz * oz);
                if (disk <= best || !this.exists(body)) {
                    continue;
                }
                double distance = Math.min(disk, continentShape(body.hash, body.radius, ox, oz, body.home));
                if (distance > best) {
                    best = distance;
                    nearest = body;
                }
            }
        }
        return nearest;
    }

    // ---- Land bridges and straits -----------------------------------------------------------------------------------

    // Neighbouring cells a bridge can run to; each pair of cells is looked at once, from the cell before it.
    private static final int[][] BRIDGE_NEIGHBOURS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    /**
     * The land bridges and straits between neighbouring landmasses of one grid near a point: the highest continentalness
     * the bridges give here (deep ocean if none) and the lowest the straits allow (+infinity if none).
     */
    private double[] pairs(long salt, double chance, double minRadius, double maxRadius, double cell, boolean home, IslandGrid islands, int range,
                           boolean bridges, boolean straits, double x, double z, double interior, double ocean) {
        int cellX = (int) Math.floor(x / cell + 0.5);
        int cellZ = (int) Math.floor(z / cell + 0.5);
        PairList list = this.pairList(salt, chance, minRadius, maxRadius, cell, home, islands, range, cellX, cellZ);
        double[] found = {ocean, Double.POSITIVE_INFINITY};
        for (int i = 0; i < list.count; i++) {
            if (bridges && list.bridge[i]) {
                found[0] = Math.max(found[0], this.bridge(list.hash[i], list.a[i], list.b[i], x, z, interior, ocean));
            }
            if (straits && list.strait[i]) {
                found[1] = Math.min(found[1], this.strait(list.hash[i], list.a[i], list.b[i], x, z, interior, ocean));
            }
        }
        return found;
    }

    /**
     * The pairs of landmasses whose bridge or strait could reach into one cell of a grid. Neighbouring samples are nearly
     * always in the same cell, so each thread keeps the last list for each grid instead of looking at every pair again.
     */
    private static final class PairList {
        long salt;
        int cellX;
        int cellZ;
        int count;
        Body[] a = new Body[8];
        Body[] b = new Body[8];
        long[] hash = new long[8];
        boolean[] bridge = new boolean[8];
        boolean[] strait = new boolean[8];

        void add(Body first, Body second, long pairHash, boolean isBridge, boolean isStrait) {
            if (this.count == this.a.length) {
                int grown = 2 * this.count;
                this.a = java.util.Arrays.copyOf(this.a, grown);
                this.b = java.util.Arrays.copyOf(this.b, grown);
                this.hash = java.util.Arrays.copyOf(this.hash, grown);
                this.bridge = java.util.Arrays.copyOf(this.bridge, grown);
                this.strait = java.util.Arrays.copyOf(this.strait, grown);
            }
            this.a[this.count] = first;
            this.b[this.count] = second;
            this.hash[this.count] = pairHash;
            this.bridge[this.count] = isBridge;
            this.strait[this.count] = isStrait;
            this.count++;
        }
    }

    // One slot per grid that has pairs: continents, large islands, bigger and smaller islands.
    private final ThreadLocal<PairList[]> pairLists = ThreadLocal.withInitial(() -> new PairList[4]);

    private PairList pairList(long salt, double chance, double minRadius, double maxRadius, double cell, boolean home, IslandGrid islands, int range,
                              int cellX, int cellZ) {
        PairList[] lists = this.pairLists.get();
        int slot = salt == SALT_CONTINENT ? 0 : salt == SALT_LARGE_ISLAND ? 1 : salt == SALT_BIGGER_ISLANDS ? 2 : 3;
        PairList list = lists[slot];
        if (list != null && list.salt == salt && list.cellX == cellX && list.cellZ == cellZ) {
            return list;
        }
        if (list == null) {
            list = new PairList();
            lists[slot] = list;
        }
        list.salt = salt;
        list.cellX = cellX;
        list.cellZ = cellZ;
        list.count = 0;
        // The cell's square, which a pair's reach must overlap.
        double minX = (cellX - 0.5) * cell;
        double maxX = (cellX + 0.5) * cell;
        double minZ = (cellZ - 0.5) * cell;
        double maxZ = (cellZ + 0.5) * cell;
        int size = 2 * range + 3;
        Body[] bodies = new Body[size * size];
        boolean[] known = new boolean[size * size];
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                Body a = this.cachedBody(bodies, known, size, range, dx, dz, salt, chance, minRadius, maxRadius, cell, home, islands, cellX, cellZ);
                if (a == null) {
                    continue;
                }
                for (int[] step : BRIDGE_NEIGHBOURS) {
                    Body b = this.cachedBody(bodies, known, size, range, dx + step[0], dz + step[1], salt, chance, minRadius, maxRadius, cell, home,
                            islands, cellX, cellZ);
                    if (b == null) {
                        continue;
                    }
                    // Bridges stay within 3 times the smaller radius of the box around both middles, straits within 1.1 times the larger.
                    double margin = Math.max(3.0 * Math.min(a.radius, b.radius), 1.2 * Math.max(a.radius, b.radius));
                    if (Math.max(a.x, b.x) + margin < minX || Math.min(a.x, b.x) - margin > maxX
                            || Math.max(a.z, b.z) + margin < minZ || Math.min(a.z, b.z) - margin > maxZ) {
                        continue;
                    }
                    long pair = mix(this.seed ^ SALT_BRIDGE ^ salt, mix(cellX + dx, cellZ + dz, 1), step[0] * 3L + step[1]);
                    double chanceHere = a.exists == null || b.exists == null ? 0.5 * this.bridgeChance : this.bridgeChance;
                    boolean isBridge = this.bridges && unit(pair, 0) < chanceHere;
                    boolean isStrait = this.straits && unit(pair, 10) < this.straitChance;
                    if (isBridge || isStrait) {
                        list.add(a, b, pair, isBridge, isStrait);
                    }
                }
            }
        }
        return list;
    }

    private Body cachedBody(Body[] bodies, boolean[] known, int size, int range, int dx, int dz, long salt, double chance, double minRadius,
                            double maxRadius, double cell, boolean home, IslandGrid islands, int cellX, int cellZ) {
        int index = (dx + range) * size + (dz + range + 1);
        if (!known[index]) {
            known[index] = true;
            bodies[index] = this.body(salt, chance, minRadius, maxRadius, cell, home, islands, cellX + dx, cellZ + dz);
        }
        return bodies[index];
    }

    /**
     * Continentalness of the bridge between two landmasses, if they get one. It runs between their middles, so the part over
     * water crosses the gap between them; it meanders a little, is narrowest halfway and sits on a shallow shoal with
     * sandbank islets along its sides. Some come as chains of islands. Where the gap is a bit too wide for a full bridge,
     * a spit reaches out from each side and tapers into the water, trailing islets.
     */
    private double bridge(long pair, Body a, Body b, double x, double z, double interior, double ocean) {
        // Quick test first: a bridge stays within this far of the box around its two ends (width, meander, shoal, islets).
        double margin = 3.0 * Math.min(a.radius, b.radius);
        if (x < Math.min(a.x, b.x) - margin || x > Math.max(a.x, b.x) + margin || z < Math.min(a.z, b.z) - margin || z > Math.max(a.z, b.z) + margin) {
            return ocean;
        }
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        // The main bodies of the shapes reach roughly this far from their middles, which leaves this much water between them.
        double reachA = 0.65 * a.radius;
        double reachB = 0.65 * b.radius;
        double gap = length - reachA - reachB;
        double maxGap = this.coastFeatures.bridgeLength();
        boolean partial = gap > maxGap;
        // Fewer between small islands, which would otherwise end up in stars of bridges.
        double chance = a.exists == null || b.exists == null ? 0.5 * this.bridgeChance : this.bridgeChance;
        if (gap > PARTIAL_BRIDGE_GAP * maxGap || length < 1.0 || unit(pair, 0) >= chance) {
            return ocean;
        }
        ContinentLayout.SizeRange widths = this.coastFeatures.bridgeWidth();
        // Never more than a third as wide as the smaller landmass, so small islands get small bridges.
        double half = 0.5 * Math.min(logLerp(widths.min(), widths.max(), unit(pair, 1)), 0.35 * Math.min(a.radius, b.radius));
        double ux = dx / length;
        double uz = dz / length;
        double px = x - a.x;
        double pz = z - a.z;
        double along = px * ux + pz * uz;
        double t = along / length;
        // Past either end, measure from that end, so the shoal rounds off there instead of stopping in a straight line.
        double over = t < 0.0 ? -along : t > 1.0 ? along - length : 0.0;
        t = clamp(t, 0.0, 1.0);
        double across = pz * ux - px * uz;
        double meander = Math.min(0.08 * length, 8.0 * half);
        double wave = Math.max(length / 3.0, 8.0 * half);
        double noiseRow = unit(pair, 2) * 1.0E5;
        double lateral = meander * Math.sin(Math.PI * t) * this.bridgeNoise.fractal(along, noiseRow, wave, 2);
        double off = Math.sqrt((across - lateral) * (across - lateral) + over * over);
        double shoalWidth = 0.6 * SHOAL_WIDTHS * Math.max(half, 8.0);
        if (off > 4.0 * half + shoalWidth || !this.exists(a) || !this.exists(b)) {
            return ocean;
        }
        double distance;
        double spitTip = 0.0;
        if (partial) {
            // Each side reaches part way across, more of it the closer the gap is to what a bridge can span.
            double share = clamp(maxGap / gap, 0.0, 1.0) * (0.35 + 0.15 * unit(pair, 4));
            double tipA = (reachA + share * gap) / length;
            double tipB = 1.0 - (reachB + share * gap) / length;
            double taper;
            double tip;
            if (t < 0.5) {
                taper = t < reachA / length ? 1.0 : (tipA - t) / (tipA - reachA / length);
                tip = tipA;
            } else {
                taper = t > 1.0 - reachB / length ? 1.0 : (t - tipB) / (1.0 - reachB / length - tipB);
                tip = tipB;
            }
            distance = taper > 0.0 ? half * Math.pow(Math.min(taper, 1.0), 0.6) - off : -off - Math.abs(t - tip) * length;
            spitTip = tip;
        } else if (unit(pair, 3) < this.coastFeatures.chainShare()) {
            distance = this.chain(pair, t, length, along, across, meander, wave, noiseRow, half);
        } else {
            double middle = 2.0 * t - 1.0;
            distance = half * (0.55 + 0.45 * middle * middle) - off;
        }
        // A ragged edge, on the scale of the bridge itself.
        distance += 0.3 * half * this.bridgeNoise.fractal(x, z, 4.0 * half, 2);
        // The shoal reaches about three bridge widths out (a five-width shoal on a 0.6 scale).
        double shoalScale = 0.6 * Math.max(half, 8.0);
        double shoal = distance == Double.NEGATIVE_INFINITY ? shoal(off - half, shoalScale, ocean) : shoal(-distance, shoalScale, ocean);
        // Islets get their own small shoals, not the bridge's wide one.
        double islets = this.flankIslets(pair, t, length, across, lateral, half);
        if (partial) {
            islets = Math.max(islets, this.trailIslets(pair, t, spitTip, t < 0.5 ? 1 : -1, length, across, lateral, half));
        }
        double land = Math.max(distance, islets);
        if (land == Double.NEGATIVE_INFINITY) {
            return shoal;
        }
        return Math.max(Math.max(this.toContinentalness(land, 2.0 * half, interior, ocean), shoal), shoal(-islets, 0.5 * shoalScale, ocean));
    }

    /** An island chain along a bridge's line: beads, some left out, each its own size. */
    private double chain(long pair, double t, double length, double along, double across, double meander, double wave, double noiseRow, double half) {
        int beads = (int) clamp(Math.round(length / (5.0 * half)), 2, 64);
        int nearest = (int) Math.round(t * beads);
        double distance = Double.NEGATIVE_INFINITY;
        for (int k = Math.max(1, nearest - 1); k <= Math.min(beads - 1, nearest + 1); k++) {
            long bead = mix(pair, k, 7);
            if (unit(bead, 0) < MISSING_BEAD_CHANCE) {
                continue;
            }
            double tk = (double) k / beads;
            double lateralK = meander * Math.sin(Math.PI * tk) * this.bridgeNoise.fractal(tk * length, noiseRow, wave, 2);
            double radius = half * (0.7 + 1.5 * unit(bead, 1));
            double bx = along - tk * length;
            double bz = across - lateralK;
            distance = Math.max(distance, radius - Math.sqrt(bx * bx + bz * bz));
        }
        return distance;
    }

    /** Sandbank islets in the shoal along a bridge's sides. */
    private double flankIslets(long pair, double t, double length, double across, double lateral, double half) {
        double amount = this.coastFeatures.bridgeIslets();
        if (amount <= 0.0) {
            return Double.NEGATIVE_INFINITY;
        }
        double spacing = 4.0 * half;
        int nearest = (int) Math.round(t * length / spacing);
        double best = Double.NEGATIVE_INFINITY;
        for (int k = nearest - 1; k <= nearest + 1; k++) {
            long islet = mix(pair, k, 17);
            double tk = k * spacing / length;
            if (tk < 0.05 || tk > 0.95 || unit(islet, 0) >= 0.35 * amount) {
                continue;
            }
            double side = unit(islet, 3) < 0.5 ? -1.0 : 1.0;
            double r = half * (0.25 + 0.35 * unit(islet, 1));
            double dx = (t - tk) * length;
            double dz = across - (lateral + side * half * (1.8 + 1.7 * unit(islet, 2)));
            best = Math.max(best, r - Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    /** Islets trailing on from the tip of a spit that reaches part way across a gap, in direction {@code direction} along t. */
    private double trailIslets(long pair, double t, double tip, int direction, double length, double across, double lateral, double half) {
        double amount = this.coastFeatures.bridgeIslets();
        if (amount <= 0.0) {
            return Double.NEGATIVE_INFINITY;
        }
        double spacing = 3.0 * half;
        double past = (t - tip) * direction * length;
        int nearest = (int) Math.round(past / spacing);
        double best = Double.NEGATIVE_INFINITY;
        for (int k = Math.max(1, nearest - 1); k <= Math.min(5, nearest + 1); k++) {
            long islet = mix(pair, k * direction, 19);
            if (unit(islet, 0) >= 0.6 * amount) {
                continue;
            }
            double r = half * (0.3 + 0.4 * unit(islet, 1)) * (1.0 - 0.12 * k);
            double dx = past - k * spacing;
            double dz = across - lateral - half * (unit(islet, 2) - 0.5);
            best = Math.max(best, r - Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    /**
     * The highest continentalness a strait between two landmasses allows here, or +infinity if they don't get one. Straits
     * form where neighbouring landmasses meet: a channel crossing the join, right through both shapes so it opens into the
     * sea at each end, narrowest halfway and meandering, with beaches along its banks.
     */
    private double strait(long pair, Body a, Body b, double x, double z, double interior, double ocean) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0 || length - 0.65 * (a.radius + b.radius) > STRAIT_GAP * Math.min(a.radius, b.radius)
                || unit(pair, 10) >= this.straitChance) {
            return Double.POSITIVE_INFINITY;
        }
        double ux = dx / length;
        double uz = dz / length;
        // Where the two meet along the line between their middles.
        double share = a.radius / (a.radius + b.radius);
        double mx = a.x + ux * length * share;
        double mz = a.z + uz * length * share;
        double px = x - mx;
        double pz = z - mz;
        double along = pz * ux - px * uz;
        double halfLength = 1.1 * Math.max(a.radius, b.radius);
        if (Math.abs(along) > halfLength) {
            return Double.POSITIVE_INFINITY;
        }
        ContinentLayout.SizeRange widths = this.coastFeatures.straitWidth();
        double half = 0.5 * Math.min(logLerp(widths.min(), widths.max(), unit(pair, 11)), 0.4 * Math.min(a.radius, b.radius));
        double s = along / halfLength;
        double halfHere = half * (0.6 + 0.4 * s * s);
        double meander = Math.min(0.1 * halfLength, 6.0 * half);
        double lateral = meander * Math.cos(0.5 * Math.PI * s) * this.bridgeNoise.fractal(along, unit(pair, 12) * 1.0E5, Math.max(halfLength / 2.0, 8.0 * half), 2);
        double off = Math.abs(px * ux + pz * uz - lateral);
        if (off > halfHere + 3.0 * this.landRamp) {
            return Double.POSITIVE_INFINITY;
        }
        double inside = halfHere - off + 0.2 * half * this.bridgeNoise.fractal(x, z, 3.0 * half, 2);
        if (inside <= 0.0) {
            // The banks: land rising from the shore, as on any coast.
            return this.toContinentalness(-inside, 1.0, interior, ocean);
        }
        double bed = Math.max(ocean, STRAIT_BED);
        return SHORE + (bed - SHORE) * smoothStep(inside / (0.6 * halfHere));
    }

    /**
     * How much a position counts as island rather than continent, from 0 to 1: 0 on continents, rising across their
     * coastal shelf to 1 out at sea, where all the islands are. Used to give islands their own biome size.
     */
    public double islandness(double x, double z) {
        double land = this.continentLand(this.warpedContinentX(x, z), this.warpedContinentZ(x, z));
        return land == Double.NEGATIVE_INFINITY ? 1.0 : clamp(-land / this.continentShelf, 0.0, 1.0);
    }

    /** Signed distance in blocks to the nearest continent's coast, coastline noise included, at a warped position. */
    private double continentLand(double cx, double cz) {
        double land = this.landDistance(SALT_CONTINENT, CONTINENT_CHANCE, this.continentMinRadius, this.continentMaxRadius,
                this.continentCell, this.continentRange, this.continentCoast + this.continentShelf + this.continentErosion.outward, true, cx, cz);
        if (land > Double.NEGATIVE_INFINITY) {
            land += this.continentCoast * this.coast.fractal(cx, cz, this.continentCoastWave, 5 + this.continentDetail);
            land = this.continentErosion.apply(land, cx, cz);
        }
        return land;
    }

    /** Typical distance between the middles of neighbouring continents, in blocks. */
    /** Octaves to add so noise of this wavelength still has detail down to that of the old cap: one per doubling, up to 7. */
    private static int extraOctaves(double wave, double oldCap) {
        return wave <= oldCap ? 0 : Math.min(7, (int) Math.ceil(Math.log(wave / oldCap) / Math.log(2.0)));
    }

    public double continentSpacing() {
        return this.continentCell / Math.sqrt(CONTINENT_CHANCE);
    }

    /** Roughly the typical distance between the middles of neighbouring continents, without building the shapes. */
    public static double continentSpacing(ContinentLayout layout) {
        return gridCell(layout) / Math.sqrt(CONTINENT_CHANCE);
    }

    /** Roughly the share of the world the islands cover. */
    private static double islandLand(ContinentLayout layout) {
        return SCATTERED_ISLAND_LAND + ARCHIPELAGO_LAND * layout.archipelagoAmount()
                + LARGE_ISLAND_LAND * layout.largeIslandAmount() * (1.0 - layout.landShare());
    }

    /** The size of the continent grid's cells that gives the layout's land share. */
    private static double gridCell(ContinentLayout layout) {
        // Shapes dropped at random cover 1 - e^-(number per area * mean area) of the plane; solve that for the land share.
        // The grid spreads continents more evenly than chance, so they overlap less and cover more the more land
        // there is; the last factor corrects for that (fitted from measurements with the test harness).
        double share = clamp(layout.landShare() - islandLand(layout), 0.01, 0.95);
        double density = -Math.log(1.0 - share) / (0.85 + 0.42 * share);
        double meanArea = AREA_PER_RADIUS_SQUARED * meanSquare(layout.continentSize().min() / WIDTH_PER_RADIUS, layout.continentSize().max() / WIDTH_PER_RADIUS);
        return Math.sqrt(CONTINENT_CHANCE * meanArea / density);
    }

    private double toContinentalness(double distance, double shelf, double interior, double ocean) {
        return this.toContinentalness(distance, shelf, interior, ocean, this.landRamp);
    }

    private double toContinentalness(double distance, double shelf, double interior, double ocean, double ramp) {
        if (distance >= 0.0) {
            return SHORE + (interior - SHORE) * (1.0 - Math.exp(-distance / ramp));
        }
        // Drops quickly past the shore, then levels off over the shelf into deep ocean.
        double q = Math.min(1.0, -distance / shelf);
        return SHORE + (ocean - SHORE) * (1.0 - (1.0 - q) * (1.0 - q));
    }

    private double warpedContinentX(double x, double z) {
        return x + this.continentWarp * this.warpX.fractal(x, z, this.continentWarpWave, 3);
    }

    private double warpedContinentZ(double x, double z) {
        return z + this.continentWarp * this.warpZ.fractal(x, z, this.continentWarpWave, 3);
    }

    /**
     * Signed distance in blocks to the nearest coast (before coastline noise) of the continents, or large islands, on one
     * grid; -infinity if none is near. Each cell has a landmass with the chance given, sized between the two radii.
     * With {@code home}, the cell at the origin always has one: the home continent.
     */
    private double landDistance(long salt, double chance, double minRadius, double maxRadius, double cell, int range, double reach,
                                boolean home, double x, double z) {
        int cellX = (int) Math.floor(x / cell + 0.5);
        int cellZ = (int) Math.floor(z / cell + 0.5);
        double best = Double.NEGATIVE_INFINITY;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long hash = cellHash(salt, cx, cz);
                boolean isHome = home && cx == 0 && cz == 0;
                double centerX;
                double centerZ;
                double radius;
                if (isHome) {
                    centerX = this.homeX;
                    centerZ = this.homeZ;
                    radius = this.homeRadius;
                } else {
                    if (unit(hash, 0) >= chance) {
                        continue;
                    }
                    centerX = (cx - 0.5 + unit(hash, 1)) * cell;
                    centerZ = (cz - 0.5 + unit(hash, 2)) * cell;
                    radius = logLerp(minRadius, maxRadius, unit(hash, 3));
                }
                double ox = x - centerX;
                double oz = z - centerZ;
                double distanceSq = ox * ox + oz * oz;
                double limit = radius + reach;
                if (distanceSq >= limit * limit) {
                    continue;
                }
                // The shape lies inside its disk, so it can't be nearer than the disk; skip it if that can't beat the best.
                double disk = radius - Math.sqrt(distanceSq);
                if (disk <= best) {
                    continue;
                }
                best = Math.max(best, Math.min(disk, continentShape(hash, radius, ox, oz, isHome)));
            }
        }
        return best;
    }

    /** A main ellipse with two to four smaller lobes around it, all inside the continent's disk. */
    static double continentShape(long hash, double radius, double ox, double oz, boolean home) {
        double major = radius * mainMajor(hash);
        double minor = major * (home ? homeAspect(hash) : 0.4 + 0.5 * unit(hash, 5));
        double angle = 2.0 * Math.PI * unit(hash, 6);
        double best = ellipse(ox, oz, major, minor, Math.cos(angle), Math.sin(angle));
        int lobes = 2 + (int) (unit(hash, 7) * 3.0);
        for (int lobe = 0; lobe < lobes; lobe++) {
            int base = 8 + lobe * 5;
            double lobeMajor = radius * (0.2 + 0.25 * unit(hash, base));
            double lobeMinor = lobeMajor * (0.45 + 0.5 * unit(hash, base + 1));
            double distance = (radius - lobeMajor) * (0.3 + 0.7 * unit(hash, base + 2));
            double direction = 2.0 * Math.PI * unit(hash, base + 3);
            double lobeAngle = 2.0 * Math.PI * unit(hash, base + 4);
            double lx = ox - distance * Math.cos(direction);
            double lz = oz - distance * Math.sin(direction);
            best = smoothMax(best, ellipse(lx, lz, lobeMajor, lobeMinor, Math.cos(lobeAngle), Math.sin(lobeAngle)), 0.15 * radius);
        }
        return best;
    }

    /** The larger of two values, with the corner between them rounded over a width of {@code k} (fills in sharp bays). */
    private static double smoothMax(double a, double b, double k) {
        double h = clamp(0.5 + 0.5 * (a - b) / k, 0.0, 1.0);
        return b + (a - b) * h + k * h * (1.0 - h);
    }

    private static double mainMajor(long hash) {
        return 0.5 + 0.3 * unit(hash, 4);
    }

    private static double homeAspect(long hash) {
        return 0.7 + 0.2 * unit(hash, 5);
    }

    /** Signed distance in blocks to the nearest island's coast (before coastline noise), or -infinity if none is near. */
    private double islandDistance(IslandGrid grid, double x, double z) {
        int cellX = (int) Math.floor(x / grid.cell + 0.5);
        int cellZ = (int) Math.floor(z / grid.cell + 0.5);
        double reach = this.islandCoastAmount + grid.shelf;
        double best = Double.NEGATIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long hash = cellHash(grid.salt, cx, cz);
                double roll = unit(hash, 0);
                if (roll >= ARCHIPELAGO_ISLAND_CHANCE) {
                    continue;
                }
                double centerX = (cx - 0.5 + unit(hash, 1)) * grid.cell;
                double centerZ = (cz - 0.5 + unit(hash, 2)) * grid.cell;
                double radius = logLerp(grid.minRadius, grid.maxRadius, unit(hash, 3));
                double ox = x - centerX;
                double oz = z - centerZ;
                double distanceSq = ox * ox + oz * oz;
                double limit = radius + reach;
                if (distanceSq >= limit * limit) {
                    continue;
                }
                double disk = radius - Math.sqrt(distanceSq);
                if (disk <= best) {
                    continue;
                }
                if (roll >= OPEN_OCEAN_ISLAND_CHANCE
                        && roll >= OPEN_OCEAN_ISLAND_CHANCE + (ARCHIPELAGO_ISLAND_CHANCE - OPEN_OCEAN_ISLAND_CHANCE) * this.archipelago(centerX, centerZ)) {
                    continue;
                }
                best = Math.max(best, Math.min(disk, continentShape(hash, radius, ox, oz, false)));
            }
        }
        return best;
    }

    /**
     * One grid of islands with radii from {@code minRadius} to {@code maxRadius}. Everything an island affects, out to the
     * end of its shelf, stays within one cell of its centre, so only the nearest 3x3 cells need checking.
     */
    private record IslandGrid(long salt, double minRadius, double maxRadius, double cell, double shelf) {
        static IslandGrid of(long salt, double minRadius, double maxRadius, double coastline, double widestShelf) {
            return new IslandGrid(salt, minRadius, maxRadius, 3.2 * maxRadius, Math.min(widestShelf, 2.2 * maxRadius - coastline));
        }
    }

    /** How strongly a point is inside an archipelago: 1 in the middle half, fading to 0 at the edge. */
    private double archipelago(double x, double z) {
        if (!this.archipelagos) {
            return 0.0;
        }
        int cellX = (int) Math.floor(x / this.archipelagoCell + 0.5);
        int cellZ = (int) Math.floor(z / this.archipelagoCell + 0.5);
        double strength = 0.0;
        for (int dx = -this.archipelagoRange; dx <= this.archipelagoRange; dx++) {
            for (int dz = -this.archipelagoRange; dz <= this.archipelagoRange; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long hash = cellHash(SALT_ARCHIPELAGO, cx, cz);
                if (unit(hash, 0) >= ARCHIPELAGO_CHANCE) {
                    continue;
                }
                double radius = logLerp(this.archipelagoMinRadius, this.archipelagoMaxRadius, unit(hash, 3));
                double ox = x - (cx - 0.5 + unit(hash, 1)) * this.archipelagoCell;
                double oz = z - (cz - 0.5 + unit(hash, 2)) * this.archipelagoCell;
                if (ox * ox + oz * oz >= radius * radius) {
                    continue;
                }
                // Stretched, so archipelagos often come as island chains.
                double minor = radius * (0.3 + 0.5 * unit(hash, 4));
                double angle = 2.0 * Math.PI * unit(hash, 5);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double along = (ox * cos + oz * sin) / radius;
                double across = (oz * cos - ox * sin) / minor;
                double k = Math.sqrt(along * along + across * across);
                if (k < 1.0) {
                    double t = clamp((k - 0.5) / 0.5, 0.0, 1.0);
                    strength = Math.max(strength, 1.0 - t * t * (3.0 - 2.0 * t));
                }
            }
        }
        return strength;
    }

    /** Whether a point is inside an archipelago or within {@code pad} blocks of one. */
    private boolean nearArchipelago(double x, double z, double pad) {
        if (!this.archipelagos) {
            return false;
        }
        int cellX = (int) Math.floor(x / this.archipelagoCell + 0.5);
        int cellZ = (int) Math.floor(z / this.archipelagoCell + 0.5);
        for (int dx = -this.archipelagoRange; dx <= this.archipelagoRange; dx++) {
            for (int dz = -this.archipelagoRange; dz <= this.archipelagoRange; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long hash = cellHash(SALT_ARCHIPELAGO, cx, cz);
                if (unit(hash, 0) >= ARCHIPELAGO_CHANCE) {
                    continue;
                }
                double radius = logLerp(this.archipelagoMinRadius, this.archipelagoMaxRadius, unit(hash, 3));
                double ox = x - (cx - 0.5 + unit(hash, 1)) * this.archipelagoCell;
                double oz = z - (cz - 0.5 + unit(hash, 2)) * this.archipelagoCell;
                double outer = radius + pad;
                if (ox * ox + oz * oz >= outer * outer) {
                    continue;
                }
                double minor = radius * (0.3 + 0.5 * unit(hash, 4)) + pad;
                double angle = 2.0 * Math.PI * unit(hash, 5);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double along = (ox * cos + oz * sin) / outer;
                double across = (oz * cos - ox * sin) / minor;
                if (along * along + across * across < 1.0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Approximate signed distance to an ellipse (positive inside), exact along its axes. {@code cos} and {@code sin}
     * give the direction of the major axis.
     */
    private static double ellipse(double dx, double dz, double major, double minor, double cos, double sin) {
        double u = (dx * cos + dz * sin) / major;
        double v = (dz * cos - dx * sin) / minor;
        double k = Math.sqrt(u * u + v * v);
        if (k < 1.0E-9) {
            return minor;
        }
        double gradient = Math.sqrt(u * u / (major * major) + v * v / (minor * minor)) / k;
        return (1.0 - k) / gradient;
    }

    private long cellHash(long salt, int cx, int cz) {
        return mix(this.seed ^ salt, cx, cz);
    }

    /** Hashes a seed and two numbers into a well-mixed 64-bit value. */
    static long mix(long seed, long a, long b) {
        return splitMix(seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL));
    }

    /** The {@code index}th random number from 0 (inclusive) to 1 (exclusive) for a hash. */
    private static double unit(long hash, int index) {
        return (splitMix(hash + index * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    private static long splitMix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** From low to high, evenly on a log scale, so small and large sizes are equally likely. */
    private static double logLerp(double low, double high, double t) {
        return low * Math.pow(high / low, t);
    }

    /** The mean of r^2 for r spread evenly on a log scale from low to high. */
    private static double meanSquare(double low, double high) {
        if (high <= low * 1.0001) {
            return low * low;
        }
        return (high * high - low * low) / (2.0 * Math.log(high / low));
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
