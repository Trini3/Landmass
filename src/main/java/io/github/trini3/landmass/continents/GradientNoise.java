package io.github.trini3.landmass.continents;

/**
 * Seeded 2D gradient (Perlin) noise for the continent shapes. Gradients come from hashing the lattice point,
 * so the noise never repeats, needs no tables, and is safe to use from any number of threads.
 */
final class GradientNoise {
    private static final double DIAGONAL = Math.sqrt(0.5);
    private static final double[] GRAD_X = {1, -1, 0, 0, DIAGONAL, -DIAGONAL, DIAGONAL, -DIAGONAL};
    private static final double[] GRAD_Z = {0, 0, 1, -1, DIAGONAL, DIAGONAL, -DIAGONAL, -DIAGONAL};
    // Perlin noise with unit gradients stays within about +-0.71; this brings it to about +-1.
    private static final double SCALE = Math.sqrt(2.0);

    private final long seed;

    GradientNoise(long seed) {
        this.seed = seed;
    }

    /** One octave, about -1 to 1, with features about 1 unit across. */
    double sample(double x, double z) {
        return this.sample(this.seed, x, z);
    }

    /**
     * Fractal noise: {@code octaves} layers, each twice as fine and half as strong as the last. About -1 to 1 (it rarely
     * gets past +-0.7), with the largest features about {@code wavelength} across.
     */
    double fractal(double x, double z, double wavelength, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double total = 0.0;
        double frequency = 1.0 / wavelength;
        for (int octave = 0; octave < octaves; octave++) {
            // Offsetting each octave keeps their lattices from lining up, which would leave a grid of calm points.
            long octaveSeed = ContinentShapes.mix(this.seed, octave, 0x5EED);
            sum += amplitude * this.sample(octaveSeed, x * frequency + octave * 0.371, z * frequency - octave * 0.613);
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / total;
    }

    private double sample(long seed, double x, double z) {
        double floorX = Math.floor(x);
        double floorZ = Math.floor(z);
        long x0 = (long) floorX;
        long z0 = (long) floorZ;
        double fx = x - floorX;
        double fz = z - floorZ;
        double n00 = corner(seed, x0, z0, fx, fz);
        double n10 = corner(seed, x0 + 1, z0, fx - 1.0, fz);
        double n01 = corner(seed, x0, z0 + 1, fx, fz - 1.0);
        double n11 = corner(seed, x0 + 1, z0 + 1, fx - 1.0, fz - 1.0);
        double u = fade(fx);
        double w = fade(fz);
        double south = n00 + u * (n10 - n00);
        double north = n01 + u * (n11 - n01);
        return (south + w * (north - south)) * SCALE;
    }

    private static double corner(long seed, long x, long z, double dx, double dz) {
        int gradient = (int) (ContinentShapes.mix(seed, x, z) >>> 61);
        return GRAD_X[gradient] * dx + GRAD_Z[gradient] * dz;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }
}
