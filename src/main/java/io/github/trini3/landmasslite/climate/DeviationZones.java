package io.github.trini3.landmasslite.climate;

/**
 * Where the climate deviation patches are, and how far each one shifts the climate (see {@link ClimateDeviation}).
 *
 * <p>The world is split into square cells. Each cell may hold one patch: a rough blob around a point somewhere in the middle
 * of the cell, with its own temperature and humidity jump. Bigger jumps are rarer (one zone is four times as common as two).
 * Along a patch's rim the jump fades in over the outer 15% of its radius, so a cold patch in a desert is ringed by a thin
 * band of the climates in between rather than cut off sharply. Pure Java and seeded from the world seed, so the temperature
 * and the humidity, which are separate density functions, always agree on where the patches are.
 */
public final class DeviationZones {
    // A patch's radius is between 0.35 and 0.65 times the size; E[r^2] = 0.2575 size^2, so a patch covers about 0.809 size^2.
    private static final double PATCH_AREA = Math.PI * 0.2575;
    private static final double SPARSE_CELL = 2.0;
    private static final double RIM = 0.15;
    // Rough rims: the radius varies by up to this share with a value noise about 0.35 patch sizes across.
    private static final double ROUGHNESS = 0.3;
    private static final double ROUGH_WAVE = 0.35;

    private final ClimateDeviation settings;
    private final long seed;
    private final long worldSeed;
    private final double cell;
    private final double chance;
    private final int reach;
    private final double maxRadius;

    public DeviationZones(ClimateDeviation settings, long seed) {
        this.settings = settings;
        this.worldSeed = seed;
        this.seed = seed ^ 0x5DEECE66DL * 31 + 0x1F3D_5B79_A3C1L;
        double size = settings.size();
        double sparseShare = PATCH_AREA / (SPARSE_CELL * SPARSE_CELL);
        if (settings.frequency() <= sparseShare) {
            this.cell = SPARSE_CELL * size;
            this.chance = settings.frequency() / sparseShare;
        } else {
            // Every cell has a patch, packed closer: they overlap a little, which makes bigger, odd-shaped regions.
            this.cell = size * Math.sqrt(PATCH_AREA / settings.frequency());
            this.chance = 1.0;
        }
        this.maxRadius = 0.65 * size * (1 + ROUGHNESS);
        // Patch centres sit within the middle half of their cell.
        this.reach = (int) Math.ceil((0.25 * this.cell + this.maxRadius) / this.cell);
    }

    /** The seed the patches were placed from (the world seed). */
    public long seed() {
        return this.worldSeed;
    }

    public ClimateDeviation settings() {
        return this.settings;
    }

    /** How many temperature zones the climate here jumps: positive is colder (further from the equator), with fractions on rims. */
    public double temperatureShift(int x, int z) {
        return this.shift(x, z, false);
    }

    /** How many humidity bands the climate here jumps: positive is wetter. */
    public double humidityShift(int x, int z) {
        return this.shift(x, z, true);
    }

    private double shift(int x, int z, boolean humidity) {
        int max = humidity ? this.settings.humidity() : this.settings.temperature();
        if (max <= 0 || this.chance <= 0) {
            return 0.0;
        }
        long cx0 = (long) Math.floor(x / this.cell);
        long cz0 = (long) Math.floor(z / this.cell);
        double best = 0.0;
        long bestHash = 0;
        double roughness = Double.NaN;
        for (long cx = cx0 - this.reach; cx <= cx0 + this.reach; cx++) {
            for (long cz = cz0 - this.reach; cz <= cz0 + this.reach; cz++) {
                long h = mix(this.seed + cx * 0x9E3779B97F4A7C15L + cz * 0xC2B2AE3D27D4EB4FL);
                if (unit(h) >= this.chance) {
                    continue;
                }
                long h2 = mix(h + 1);
                double centreX = (cx + 0.25 + 0.5 * unit(h2)) * this.cell;
                double centreZ = (cz + 0.25 + 0.5 * unit(mix(h + 2))) * this.cell;
                double dx = x - centreX;
                double dz = z - centreZ;
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq >= this.maxRadius * this.maxRadius) {
                    continue;
                }
                double radius = this.settings.size() * (0.35 + 0.3 * unit(mix(h + 3)));
                if (Double.isNaN(roughness)) {
                    roughness = this.valueNoise(x / (ROUGH_WAVE * this.settings.size()), z / (ROUGH_WAVE * this.settings.size()));
                }
                double d = Math.sqrt(distanceSq) / (radius * (1 + ROUGHNESS * roughness));
                if (d >= 1.0) {
                    continue;
                }
                double t = Math.min(1.0, (1.0 - d) / RIM);
                double weight = t * t * (3 - 2 * t);
                if (weight > best) {
                    best = weight;
                    bestHash = h;
                }
            }
        }
        if (best <= 0.0) {
            return 0.0;
        }
        return best * this.jump(bestHash, humidity);
    }

    /** The whole-number jump of the patch with this hash, for temperature or humidity. */
    private int jump(long h, boolean humidity) {
        int temperature = pick(mix(h + 11), this.settings.temperature(), this.settings.humidity() > 0 ? 0.3 : 0.0);
        int wet = pick(mix(h + 12), this.settings.humidity(), this.settings.temperature() > 0 ? 0.4 : 0.0);
        if (temperature == 0 && wet == 0) {
            // Every patch changes something.
            if (this.settings.temperature() > 0) {
                temperature = pick(mix(h + 13), this.settings.temperature(), 0.0);
            } else {
                wet = pick(mix(h + 13), this.settings.humidity(), 0.0);
            }
        }
        return humidity ? wet : temperature;
    }

    /** A jump of up to max either way: none with chance zeroChance, otherwise a size with weight 1/size^2 and a random sign. */
    private static int pick(long h, int max, double zeroChance) {
        if (max <= 0) {
            return 0;
        }
        double u = unit(h);
        if (u < zeroChance) {
            return 0;
        }
        double total = 0;
        for (int m = 1; m <= max; m++) {
            total += 1.0 / (m * m);
        }
        double target = unit(mix(h + 1)) * total;
        int size = max;
        for (int m = 1; m <= max; m++) {
            target -= 1.0 / (m * m);
            if (target < 0) {
                size = m;
                break;
            }
        }
        return (mix(h + 2) & 1) == 0 ? size : -size;
    }

    /** Smooth value noise from -1 to 1 with features about one unit across. */
    private double valueNoise(double x, double z) {
        long ix = (long) Math.floor(x);
        long iz = (long) Math.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = this.corner(ix, iz);
        double b = this.corner(ix + 1, iz);
        double c = this.corner(ix, iz + 1);
        double d = this.corner(ix + 1, iz + 1);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    private double corner(long x, long z) {
        return 2 * unit(mix(this.seed ^ (x * 0x632BE59BD9B4E019L + z * 0x85157AF5L + 0x7F4A7C15L))) - 1;
    }

    /** Shifts a distance from the equator by some temperature zones, given the profile's zone edges (distances[i] = start of zone i). */
    public static double shiftDistance(double distance, double zones, double[] distances) {
        int last = distances.length - 1;
        double u = last;
        for (int i = 1; i <= last; i++) {
            if (distance < distances[i]) {
                double span = distances[i] - distances[i - 1];
                u = i - 1 + (span > 0 ? Math.max(0.0, distance - distances[i - 1]) / span : 0.0);
                break;
            }
        }
        double shifted = Math.clamp(u + zones, 0.0, last);
        int i = Math.min((int) Math.floor(shifted), last - 1);
        return distances[i] + (distances[i + 1] - distances[i]) * (shifted - i);
    }

    // Minecraft's humidity band edges, driest to wettest: the same five bands vanilla, Biomes O' Plenty and Regions Unexplored use.
    private static final double[] HUMIDITY_EDGES = {-1.0, -0.35, -0.1, 0.1, 0.3, 1.0};

    /** Shifts a humidity value by some humidity bands (positive is wetter), keeping where it sits within its band. */
    public static double shiftHumidity(double humidity, double bands) {
        double h = Math.clamp(humidity, -1.0, 1.0);
        double[] e = HUMIDITY_EDGES;
        double u = e.length - 1;
        for (int i = 1; i < e.length; i++) {
            if (h <= e[i]) {
                u = i - 1 + (h - e[i - 1]) / (e[i] - e[i - 1]);
                break;
            }
        }
        double shifted = Math.clamp(u + bands, 0.0, e.length - 1);
        int i = Math.min((int) Math.floor(shifted), e.length - 2);
        return e[i] + (e[i + 1] - e[i]) * (shifted - i);
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
