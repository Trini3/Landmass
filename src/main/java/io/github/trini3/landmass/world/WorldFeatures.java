package io.github.trini3.landmass.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmass.util.CodecUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Minecraft's "gamey" terrain features and vanilla structures that a Landmass world can leave out, picked on the
 * Customize screen's Terrain and Structures tabs. Only the overworld's generator applies them, so other dimensions keep
 * everything. Features and structures from other mods are never removed.
 *
 * @param ravines        canyon carvers
 * @param caveTunnels    carver caves: the older winding tunnels, which often open as holes in the ground
 * @param lavaLakes      lava lakes on the surface and underground
 * @param aquifers       aquifer lakes: water at its own level in dips and caves (random lakes on hillsides), flooded caves and
 *                       the lava at the bottom of the world. Off, caves are dry and only oceans and rivers hold water.
 * @param springs        single water and lava springs pouring out of cliffs and cave walls
 * @param disabledStructures names of the {@link StructureGroup}s left out
 */
public record WorldFeatures(boolean ravines, boolean caveTunnels, boolean lavaLakes, boolean aquifers, boolean springs,
                            List<String> disabledStructures) {
    public static final WorldFeatures DEFAULT = new WorldFeatures(true, true, true, true, true, List.of());

    public static final Codec<WorldFeatures> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtil.field(Codec.BOOL, "ravines", true).forGetter(WorldFeatures::ravines),
            CodecUtil.field(Codec.BOOL, "cave_tunnels", true).forGetter(WorldFeatures::caveTunnels),
            CodecUtil.field(Codec.BOOL, "lava_lakes", true).forGetter(WorldFeatures::lavaLakes),
            CodecUtil.field(Codec.BOOL, "aquifers", true).forGetter(WorldFeatures::aquifers),
            CodecUtil.field(Codec.BOOL, "springs", true).forGetter(WorldFeatures::springs),
            CodecUtil.field(Codec.STRING.listOf(), "disabled_structures", List.of()).forGetter(WorldFeatures::disabledStructures)
    ).apply(instance, WorldFeatures::new));

    private static final Set<Identifier> CAVE_CARVERS = Set.of(mc("cave"), mc("cave_extra_underground"));
    private static final Set<Identifier> RAVINE_CARVERS = Set.of(mc("canyon"));
    private static final Set<Identifier> LAVA_LAKES = Set.of(mc("lake_lava_underground"), mc("lake_lava_surface"));
    private static final Set<Identifier> SPRINGS = Set.of(mc("spring_water"), mc("spring_lava"), mc("spring_lava_frozen"));

    /** Groups of vanilla structure sets, each switched on or off as one. */
    public enum StructureGroup {
        VILLAGES("villages"),
        PILLAGER_OUTPOSTS("pillager_outposts"),
        TEMPLES("desert_pyramids", "jungle_temples", "swamp_huts", "igloos"),
        WOODLAND_MANSIONS("woodland_mansions"),
        OCEAN_STRUCTURES("ocean_monuments", "ocean_ruins", "shipwrecks", "buried_treasures"),
        MINESHAFTS("mineshafts"),
        STRONGHOLDS("strongholds"),
        RUINED_PORTALS("ruined_portals"),
        ANCIENT_CITIES("ancient_cities"),
        TRAIL_RUINS("trail_ruins"),
        TRIAL_CHAMBERS("trial_chambers");

        private final List<ResourceKey<StructureSet>> sets;

        StructureGroup(String... sets) {
            this.sets = Stream.of(sets).map(id -> ResourceKey.create(Registries.STRUCTURE_SET, mc(id))).toList();
        }

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    public boolean structureEnabled(StructureGroup group) {
        return !this.disabledStructures.contains(group.id());
    }

    public WorldFeatures withStructure(StructureGroup group, boolean enabled) {
        Set<String> disabled = new LinkedHashSet<>(this.disabledStructures);
        if (enabled) {
            disabled.remove(group.id());
        } else {
            disabled.add(group.id());
        }
        return new WorldFeatures(this.ravines, this.caveTunnels, this.lavaLakes, this.aquifers, this.springs, List.copyOf(disabled));
    }

    public WorldFeatures withRavines(boolean on) {
        return new WorldFeatures(on, this.caveTunnels, this.lavaLakes, this.aquifers, this.springs, this.disabledStructures);
    }

    public WorldFeatures withCaveTunnels(boolean on) {
        return new WorldFeatures(this.ravines, on, this.lavaLakes, this.aquifers, this.springs, this.disabledStructures);
    }

    public WorldFeatures withLavaLakes(boolean on) {
        return new WorldFeatures(this.ravines, this.caveTunnels, on, this.aquifers, this.springs, this.disabledStructures);
    }

    public WorldFeatures withAquifers(boolean on) {
        return new WorldFeatures(this.ravines, this.caveTunnels, this.lavaLakes, on, this.springs, this.disabledStructures);
    }

    public WorldFeatures withSprings(boolean on) {
        return new WorldFeatures(this.ravines, this.caveTunnels, this.lavaLakes, this.aquifers, on, this.disabledStructures);
    }

    /** Whether biome decoration (carvers and placed features) needs filtering at all. */
    public boolean filtersBiomes() {
        return !(this.ravines && this.caveTunnels && this.lavaLakes && this.springs);
    }

    public boolean filtersStructures() {
        return !this.disabledStructures.isEmpty();
    }

    /** A biome's carvers and features without the ones turned off. */
    public BiomeGenerationSettings filter(BiomeGenerationSettings settings) {
        Set<Identifier> carvers = new java.util.HashSet<>();
        if (!this.caveTunnels) {
            carvers.addAll(CAVE_CARVERS);
        }
        if (!this.ravines) {
            carvers.addAll(RAVINE_CARVERS);
        }
        Set<Identifier> features = new java.util.HashSet<>();
        if (!this.lavaLakes) {
            features.addAll(LAVA_LAKES);
        }
        if (!this.springs) {
            features.addAll(SPRINGS);
        }
        BiomeGenerationSettings.PlainBuilder builder = new BiomeGenerationSettings.PlainBuilder();
        for (Holder<WorldCarver> carver : settings.getCarvers()) {
            if (!isOneOf(carver, carvers)) {
                builder.addCarver(carver);
            }
        }
        List<HolderSet<PlacedFeature>> steps = settings.features();
        for (int i = 0; i < steps.size(); i++) {
            for (Holder<PlacedFeature> feature : steps.get(i)) {
                if (!isOneOf(feature, features)) {
                    builder.addFeature(i, feature);
                }
            }
        }
        return builder.build();
    }

    private static boolean isOneOf(Holder<?> holder, Set<Identifier> ids) {
        Optional<? extends ResourceKey<?>> key = holder.unwrapKey();
        return key.isPresent() && ids.contains(key.get().identifier());
    }

    /** The structure sets a world may place: all of them but the vanilla groups turned off. */
    public HolderLookup<StructureSet> filter(HolderLookup<StructureSet> lookup) {
        Set<ResourceKey<StructureSet>> removed = new java.util.HashSet<>();
        for (StructureGroup group : StructureGroup.values()) {
            if (!this.structureEnabled(group)) {
                removed.addAll(group.sets);
            }
        }
        return new HolderLookup<>() {
            @Override
            public Stream<Holder.Reference<StructureSet>> listElements() {
                return lookup.listElements().filter(set -> !removed.contains(set.key()));
            }

            @Override
            public Stream<HolderSet.Named<StructureSet>> listTags() {
                return lookup.listTags();
            }

            @Override
            public Optional<Holder.Reference<StructureSet>> get(ResourceKey<StructureSet> key) {
                return removed.contains(key) ? Optional.empty() : lookup.get(key);
            }

            @Override
            public Optional<HolderSet.Named<StructureSet>> get(TagKey<StructureSet> tag) {
                return lookup.get(tag);
            }
        };
    }

    /** For the log. */
    public List<String> describe() {
        List<String> off = new ArrayList<>();
        if (!this.ravines) off.add("ravines");
        if (!this.caveTunnels) off.add("cave tunnels");
        if (!this.lavaLakes) off.add("lava lakes");
        if (!this.aquifers) off.add("aquifer lakes and flooded caves");
        if (!this.springs) off.add("springs");
        off.addAll(this.disabledStructures.stream().map(id -> id.replace('_', ' ')).toList());
        return off.isEmpty() ? List.of() : List.of("Left out of the overworld: " + String.join(", ", off));
    }
}
