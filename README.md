# Landmass Lite

A Fabric mod for Minecraft 26.3 that gives worlds a realistic climate layout: temperature follows
latitude, with hot bands at the equator that get colder going north and south, until you reach the
frozen polar regions. Keep going past a pole and the bands come back in reverse, as if you'd walked over
the top of a globe. Humidity still varies as you travel east or west, and near the equator it decides
whether land is desert, savanna, forest or jungle.

Landmass Lite is the lighter, compatibility-first sister of the full Landmass mod (formerly Trini's Terrain): climate bands and continents, with
the terrain itself left to vanilla or your terrain mod.

## Installing

You need Minecraft 26.3 with Fabric Loader 0.20.1 or newer and Fabric API, on Java 25. Put
`landmass_lite-<version>.jar` in your `mods` folder (on servers too). In singleplayer, open
**Create New World > World**, set **World Type** to **Landmass Lite**, and optionally press **Customize**.
On a dedicated server, set `level-type=landmass_lite:landmass_lite` in `server.properties` before the world is first
created. Other world types generate normally, and only the overworld is changed.

## Customize

Pick **Landmass Lite** as the world type and press **Customize**. The sections are tabs:

- **Presets**: built-in world scales (Default to Realistic) and your own saved presets.
- **Climate**: pages **Zones** (zone widths, humidity zones, biome size, band waviness) and **Deviation**
  (climate deviation patches, desert share).
- **Landmass**: pages **Continents** (Custom Landmass Generation on/off, continent sizes, land share, island
  biome size), **Islands** (large islands, archipelagos,
  small islands) and **Coasts** (Coastline features on/off, then land bridges, sand bars, straits, erosion and
  shores).
- **Terrain**: switch off ravines, carver caves, lava lakes, aquifers or springs; Coast Size Override on/off with its
  coast size; Biome Size Scales Terrain on/off.
- **Structures**: switch off vanilla structure groups.

Each number setting has a slider and a box for an exact number. The slider covers the recommended range; typed
values past it are allowed up to a hard limit, with a red warning. **Preview map** draws the world from the
values as they stand, and right-click on it picks the spawn point. In game, **N** (or the pause menu button)
opens the world map.

The settings are saved into the world at creation, then copied to
`<world>/serverconfig/landmass_lite-climate.json` on first start; that file governs the world afterwards.
`config/landmass_lite-common.toml` holds the defaults for new worlds.

## Compatibility

Landmass Lite changes only the overworld (the `dimensions` list in the config), and only through the noise router's
temperature, humidity and continentalness, plus Biome Size's Large Biomes-style stretch. It never changes world height. Terrain mods and
datapacks such as Lithosphere or Streams Reflowing keep shaping the surface:

- With **Custom Landmass Generation** off, land and oceans are vanilla's or the terrain mod's own.
- Coast width is only changed while **Coast Size Override** is on.
- With **Biome Size Scales Terrain** off, Biome Size only resizes humidity and temperature patches and leaves the terrain
  noises alone (for mods that override continentalness/erosion or don't want mountains stretched).
- Shore shaping (steering erosion/weirdness near coasts) and gravel beaches only run with **Coastline
  features** on.
- Island biome size only resizes humidity and temperature patches, never erosion.
- TerraBlender is optional. Biome mods built on it (Biomes O' Plenty, Regions Unexplored) work as in a Default world.
  When it's installed, TerraBlender's biome regions (the patches where each mod's biomes are picked) also grow with
  **Biome Size**, so one big biome isn't split between mods. At 100% they keep TerraBlender's own size.

## Commands

`/latitude info`, `/latitude map climate|biomes` (saves a PNG) and `/latitude tp <x> <z>` (op level 2).

## Building

`./gradlew build` (Java 25). GitHub Actions builds every push and runs `runSelfTest`, a dedicated server
that checks the climate bands and prints `LANDMASS LITE SELF-TEST PASSED`.

## Known issues

- Landmass Lite is cut from Trini's Terrain (now Landmass) Fabric 0.21.1+26.3 and won't get its later terrain overhauls. 0.2.0
  brought back everything except world height, mountain ranges and compatibility mode (Landmass Lite is always
  in what was compatibility mode, with coast size and coastline features as their own switches).
- Presets keep the Coast Size Override, Custom Landmass Generation and Coastline features as they were;
  the full mod's presets set coast size, Landmass Lite presets don't (coast size belongs to the terrain mod).
- Continents use the Coast Size Override (or 100%) for the width of their own coast ramp, so huge presets get
  vanilla-width coasts unless the override is on.
- The Customize screen has a second row of page buttons, so rows sit 1px closer (20 instead of 21) on windows
  under 270 GUI pixels tall, and the summary line is left out where it would touch the bottom buttons.
- Coastline features off = CoastFeatures.NONE, CoastErosion.NONE, Shores.NONE at generation time; the page
  values are kept, so switching back on restores them.
- Settings files from the full Landmass mod are not read (different mod id and file name).
- 26.x text colours need alpha (0xFFxxxxxx); mouse buttons start at 1 (use InputConstants constants).
- Biome Size works like Large Biomes: it stretches the continentalness and erosion noises everywhere they're sampled
  (terrain and biome choice alike), plus local humidity/temperature. Stretching only the biome-picking erosion (0.3.0)
  looked like it did nothing in testing and made biomes stop matching the land, so it was reverted in 0.3.1. Big values
  (Realistic's 2500%) also stretch mountain ranges, including a terrain mod's if it samples vanilla's noises. The Coast
  Size Override multiplies the continentalness stretch. Biome Size Scales Terrain (`biome_terrain_scaling`, default on,
  kept by presets) turns the stretch off. In hot bands mountains show as badlands (vanilla's hot peaks).
- The L preset (0.4.0) sets its continent, island, archipelago and small island sizes by hand instead of scaling the
  default's (no single land scale gives 16k-72k continents with 10k islands and 3k small islands); its land scale 2.8
  still sizes coastal features. 0.4.0 also raised biome size, humidity zones and waviness for L to 3XL (old values in
  WorldPresets).
- Lithosphere support is on hold: the Lithosphere Compatibility switch (0.4.1-0.4.3) was taken out in 0.4.4 and kept on
  the `archive/lithosphere-compat` branch to pick up later. Landmass Lite makes no Lithosphere-specific changes now; with
  Lithosphere installed, its own land and oceans win over Custom Landmass Generation. 0.4.4 keeps the general speedup from
  0.4.3: Densities.rewriteAll/contains rewrite each density function once and keep shared functions shared.
- 0.5.0 renamed this mod from "Landmass" to "Landmass Lite" because the full mod (formerly Trini's Terrain) took the
  name Landmass. The mod id (`landmass` to `landmass_lite`), Java package (`io.github.trini3.landmasslite`), resource
  namespace, world type (`landmass_lite:landmass_lite`), lang keys and file names (`landmass_lite-common.toml`,
  `landmass_lite-climate.json`, `landmass_lite-maps/`, `landmass_lite-presets/`) all changed, so worlds from 0.4.4 or
  earlier don't open with 0.5.0; the author accepted that. The full mod now uses id `landmass` and package
  `io.github.trini3.landmass`. Never let the two share a mod id, Java package, namespace or config file name, or
  they can't be installed together. Class names (Landmass, LandmassGenerator) were kept; only packages differ.
- TerraBlender support (0.5.0, `compat/TerraBlenderCompat`, same code as the full Landmass mod) needs no TerraBlender at
  build time: at server start it reaches the `uniqueness` and `newUniqueness` fields TerraBlender's mixin adds to
  `Climate.ParameterList` by reflection and wraps the region map so it's sampled at quart coordinates divided by Biome
  Size, warped by smooth noise about one region cell in size so edges aren't blocky. `newUniqueness` must be wrapped
  too, because TerraBlender clones the biome source per chunk and calls `recreateUniqueness`. If the fields change in a
  later TerraBlender, it logs a warning and leaves the regions alone. Uses Biome Size even with Biome Size Scales Terrain
  off (regions are about biome choice, not terrain). Written against TerraBlender's 26.3 branch; not run in game yet.
