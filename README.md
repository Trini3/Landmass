# Even Larger Biomes

A small Fabric mod for Minecraft 26.3 that adds five world types with biomes bigger than vanilla
Large Biomes. Pick them with the **World Type** button on the Create World screen.

| World type | Biome size compared to Large Biomes | Compared to Default |
|---|---|---|
| Larger Biomes | 1.5x | 6x |
| XL Biomes | 2x | 8x |
| XXL Biomes | 3x | 12x |
| Huge Biomes | 4x | 16x |
| Giga Biomes | 8x | 32x |

Sizes are widths: a 2x biome is twice as wide and twice as long.

Needs Fabric Loader 0.20.1+, Fabric API and Java 25. There is no Java code; the mod is only data.

## How it works

Vanilla Large Biomes uses its own copies of the overworld density functions that sample the
`*_large` noises (two octaves lower than Default, so 4x wider). This mod copies those files and
lowers the horizontal scale (`xz_scale`) of the same four noises (continentalness, erosion,
temperature, vegetation) by a further 1.5x, 2x, 3x, 4x or 8x. Ridges (weirdness, which places rivers and
peaks) stay the same as in Large Biomes, again matching vanilla. The noises keep their vanilla
names, so a seed gives the same pattern as Large Biomes, only stretched.

The data files under `src/main/resources/data` are generated, do not edit them by hand. To change
them, edit `tools/generate.py` and run it on the vanilla data from the 26.3 server jar:

```
python3 tools/generate.py <folder containing minecraft/worldgen>
```

When updating to a new Minecraft version, run it again on that version's data.

## Builds

GitHub Actions builds every push. Download the `evenlargerbiomes-jar` artifact from the run page.
The build also starts a dedicated server on Giga Biomes to check that the presets load.

## Known issues

- The game has no maximum biome size, but at Huge and Giga sizes oceans grow too, so spawn can land
  in water more often, and biome edges look rounder because the finest noise detail stretches as well.

- Not tested in game yet.
- Mods that change the overworld noise settings directly (for example terrain datapacks that
  overwrite `minecraft:overworld` density functions) will not affect these world types, because
  they use their own copies, the same as vanilla Large Biomes.
- Because Large Biomes leaves the shift noise unscaled, the small edge wobble on biome borders
  becomes relatively smaller as biomes grow.
