package io.github.trini3.landmasslite.compat;

import io.github.trini3.landmasslite.Landmass;
import io.github.trini3.landmasslite.climate.WorldClimateSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Optional TerraBlender support. TerraBlender isn't needed, and nothing here runs or loads TerraBlender classes
 * unless it's installed.
 *
 * <p>TerraBlender biome mods (Biomes O' Plenty, Regions Unexplored and others) already work in Landmass Lite worlds without
 * this: the world type uses Minecraft's own overworld biome source, so TerraBlender sets up its regions there as it would
 * in a Default world, and their biomes follow the climate bands because they're picked from the same temperature.
 *
 * <p>What this adds: TerraBlender splits the world into regions (vanilla's, Biomes O' Plenty's and so on), each a few
 * hundred blocks across, and picks biomes from that region's mod. When Biome size makes biomes several times bigger, the
 * regions stay small, so one huge forest gets chopped into vanilla and modded pieces. This stretches the regions by Biome
 * size too, with wavy edges so they don't show as blocky steps.
 *
 * <p>TerraBlender keeps its regions in fields that its mixin adds to Climate.ParameterList ({@code uniqueness}, an Area,
 * and {@code newUniqueness}, which makes a fresh Area for each chunk's copy of the biome source). Those are reached by
 * reflection, so the mod doesn't need TerraBlender to build. If TerraBlender changes them, this logs a warning and the
 * regions keep TerraBlender's own size; nothing else is affected.
 */
public final class TerraBlenderCompat {
    private static final String MOD_ID = "terrablender";
    private static final String AREA = "terrablender.worldgen.noise.Area";
    private static final String PIXEL_TRANSFORMER = "terrablender.worldgen.noise.PixelTransformer";
    private static final int CACHE_SIZE = 1024;
    // Parameter lists already resized, so a list shared between worlds or dimensions is never stretched twice.
    private static final Set<Object> SCALED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private TerraBlenderCompat() {
    }

    public static boolean installed() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /**
     * Called when a Landmass Lite world starts, after TerraBlender has set up its regions (it does that while the server is
     * constructed) and before any chunk is generated.
     */
    public static void onServerStarting(MinecraftServer server, WorldClimateSettings settings) {
        if (!installed()) {
            return;
        }
        double scale = settings.biomeScale();
        if (scale == 1.0) {
            Landmass.LOGGER.info("TerraBlender found: its biome regions keep their own size (Biome size is 100%)");
            return;
        }
        try {
            BiomeSource source = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getOptional(LevelStem.OVERWORLD)
                    .map(stem -> stem.generator().getBiomeSource())
                    .orElse(null);
            if (!(source instanceof MultiNoiseBiomeSource multiNoise)) {
                Landmass.LOGGER.info("TerraBlender found, but the overworld doesn't use a multi-noise biome source, so its regions are left alone");
                return;
            }
            Method parametersMethod = MultiNoiseBiomeSource.class.getDeclaredMethod("parameters");
            parametersMethod.setAccessible(true);
            Object parameters = parametersMethod.invoke(multiNoise);
            if (scaleRegions(parameters, scale, server.getWorldGenSettings().options().seed())) {
                Landmass.LOGGER.info("TerraBlender found: its biome regions are {} times their usual size, to match Biome size", scale);
            } else {
                Landmass.LOGGER.info("TerraBlender found, but it hasn't set up regions for the overworld, so they're left alone");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Landmass.LOGGER.warn("Couldn't resize TerraBlender's biome regions (this TerraBlender version may not be supported); "
                    + "they keep their usual size", e);
        }
    }

    /** Wraps the parameter list's region map so it's sampled at scaled-down coordinates. Returns false if TerraBlender hasn't set it up. */
    private static boolean scaleRegions(Object parameters, double scale, long seed) throws ReflectiveOperationException {
        Field uniquenessField = field(parameters.getClass(), "uniqueness");
        Field supplierField = field(parameters.getClass(), "newUniqueness");
        Object current = uniquenessField.get(parameters);
        @SuppressWarnings("unchecked")
        Supplier<Object> original = (Supplier<Object>) supplierField.get(parameters);
        if (current == null || original == null) {
            return false;
        }
        if (!SCALED.add(parameters)) {
            return true;
        }
        Class<?> areaClass = Class.forName(AREA, false, parameters.getClass().getClassLoader());
        Class<?> transformerClass = Class.forName(PIXEL_TRANSFORMER, false, parameters.getClass().getClassLoader());
        Constructor<?> areaConstructor = areaClass.getConstructor(transformerClass, int.class);
        Method get = areaClass.getMethod("get", int.class, int.class);
        RegionWarp warp = new RegionWarp(scale, seed);

        Supplier<Object> scaled = () -> {
            Object inner = original.get();
            Object transformer = Proxy.newProxyInstance(transformerClass.getClassLoader(), new Class<?>[]{transformerClass}, (proxy, method, args) -> {
                if (method.getName().equals("apply") && args != null && args.length == 2) {
                    int x = (Integer) args[0];
                    int z = (Integer) args[1];
                    return get.invoke(inner, warp.x(x, z), warp.z(x, z));
                }
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Landmass Lite scaled TerraBlender regions";
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
            try {
                return areaConstructor.newInstance(transformer, CACHE_SIZE);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Couldn't make a scaled TerraBlender region map", e);
            }
        };
        // Make one first so a problem shows up now, while the old fields are still in place.
        Object first = scaled.get();
        get.invoke(first, 0, 0);
        supplierField.set(parameters, scaled);
        uniquenessField.set(parameters, first);
        return true;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Try the superclass.
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    /**
     * Maps quart coordinates (4 blocks) to the smaller coordinates the region map is sampled at. Dividing alone would make
     * region edges steps {@code scale} quarts wide, so the position is first pushed around by smooth noise about one
     * region cell in size, which turns the steps into wavy edges.
     */
    static final class RegionWarp {
        private final double scale;
        private final double amplitude;
        private final double wavelength;
        private final long seedX;
        private final long seedZ;

        RegionWarp(double scale, long seed) {
            this.scale = scale;
            this.amplitude = scale;
            this.wavelength = Math.max(4.0, scale * 4.0);
            this.seedX = mix(seed ^ 0x6C616E646D617373L);
            this.seedZ = mix(seed ^ 0x74657272616C6231L);
        }

        int x(int x, int z) {
            return (int) Math.floor((x + this.amplitude * valueNoise(this.seedX, x / this.wavelength, z / this.wavelength)) / this.scale);
        }

        int z(int x, int z) {
            return (int) Math.floor((z + this.amplitude * valueNoise(this.seedZ, x / this.wavelength, z / this.wavelength)) / this.scale);
        }

        /** Smooth value noise in -1..1. */
        private static double valueNoise(long seed, double x, double z) {
            long x0 = (long) Math.floor(x);
            long z0 = (long) Math.floor(z);
            double fx = smooth(x - x0);
            double fz = smooth(z - z0);
            double a = lattice(seed, x0, z0);
            double b = lattice(seed, x0 + 1, z0);
            double c = lattice(seed, x0, z0 + 1);
            double d = lattice(seed, x0 + 1, z0 + 1);
            double top = a + (b - a) * fx;
            double bottom = c + (d - c) * fx;
            return top + (bottom - top) * fz;
        }

        private static double smooth(double t) {
            return t * t * (3.0 - 2.0 * t);
        }

        private static double lattice(long seed, long x, long z) {
            long h = mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL));
            return (h >>> 11) * 0x1.0p-53 * 2.0 - 1.0;
        }

        private static long mix(long h) {
            h ^= h >>> 33;
            h *= 0xFF51AFD7ED558CCDL;
            h ^= h >>> 33;
            h *= 0xC4CEB9FE1A85EC53L;
            h ^= h >>> 33;
            return h;
        }
    }
}
