# Landmass

A Fabric mod for Minecraft 26.3 that gives worlds a realistic climate layout: temperature follows
latitude, with hot bands at the equator that get colder going north and south, until you reach the
frozen polar regions. Keep going past a pole and the bands come back in reverse, as if you'd walked over
the top of a globe. Humidity still varies as you travel east or west, and near the equator it decides
whether land is desert, savanna, forest or jungle.

Landmass is the lighter, compatibility-first sister of Trini's Terrain Mod: climate bands and continents, with
the terrain itself left to vanilla or your terrain mod.

## Installing

You need Minecraft 26.3 with Fabric Loader 0.20.1 or newer and Fabric API, on Java 25. Put
`landmass-<version>.jar` in your `mods` folder (on servers too). In singleplayer, open
**Create New World > World**, set **World Type** to **Landmass Terrain**, and optionally press **Customize**.
On a dedicated server, set `level-type=landmass:landmass` in `server.properties` before the world is first
created. Other world types generate normally, and only the overworld is changed.

## Customize

Pick **Landmass Terrain** as the world type and press **Customize**. The sections are tabs:

- **Presets**: built-in world scales (Default to Realistic) and your own saved presets.
- **Climate**: pages **Zones** (zone widths, humidity zones, biome size, band waviness) and **Deviation**
  (climate deviation patches, desert share).
- **Landmass**: pages **Continents** (Custom Landmass Generation on/off, Coast Size Override on/off with
  its coast size, continent sizes, land share, island biome size), **Islands** (large islands, archipelagos,
  small islands) and **Coasts** (Coastline features on/off, then land bridges, sand bars, straits, erosion and
  shores).
- **Terrain**: switch off ravines, carver caves, lava lakes, aquifers or springs.
- **Structures**: switch off vanilla structure groups.

Each number setting has a slider and a box for an exact number. The slider covers the recommended range; typed
values past it are allowed up to a hard limit, with a red warning. **Preview map** draws the world from the
values as they stand, and right-click on it picks the spawn point. In game, **N** (or the pause menu button)
opens the world map.

The settings are saved into the world at creation, then copied to
`<world>/serverconfig/landmass-climate.json` on first start; that file governs the world afterwards.
`config/landmass-common.toml` holds the defaults for new worlds.

## Compatibility

Landmass changes only the overworld (the `dimensions` list in the config), and only through the noise router's
temperature, humidity and continentalness. It never changes world height or mountain ranges. Terrain mods and
datapacks such as Lithosphere or Streams Reflowing keep shaping the surface:

- With **Custom Landmass Generation** off, land and oceans are vanilla's or the terrain mod's own.
- Coast width is only changed while **Coast Size Override** is on.
- Shore shaping (steering erosion/weirdness near coasts) and gravel beaches only run with **Coastline
  features** on.
- Island biome size never resizes erosion (Trini's Terrain did this outside compatibility mode).

## Commands

`/latitude info`, `/latitude map climate|biomes` (saves a PNG) and `/latitude tp <x> <z>` (op level 2).

## Building

`./gradlew build` (Java 25). GitHub Actions builds every push and runs `runSelfTest`, a dedicated server
that checks the climate bands and prints `LANDMASS SELF-TEST PASSED`.

## Known issues

- Landmass is cut from Trini's Terrain Fabric 0.21.1+26.3 and won't get its later terrain overhauls. 0.2.0
  brought back everything except world height, mountain ranges and compatibility mode (Landmass is always
  in what was compatibility mode, with coast size and coastline features as their own switches).
- Presets keep the Coast Size Override, Custom Landmass Generation and Coastline features as they were;
  Trini's Terrain presets set coast size, Landmass presets don't (coast size belongs to the terrain mod).
- Continents use the Coast Size Override (or 100%) for the width of their own coast ramp, so huge presets get
  vanilla-width coasts unless the override is on.
- The Customize screen has a second row of page buttons, so rows sit 1px closer (20 instead of 21) on windows
  under 270 GUI pixels tall, and the summary line is left out where it would touch the bottom buttons.
- Coastline features off = CoastFeatures.NONE, CoastErosion.NONE, Shores.NONE at generation time; the page
  values are kept, so switching back on restores them.
- Settings files from Trini's Terrain are not read (different mod id and file name).
- 26.x text colours need alpha (0xFFxxxxxx); mouse buttons start at 1 (use InputConstants constants).
- Biome Size only resizes the humidity and temperature patches. Many biome borders (desert/badlands, plains/windswept,
  mountain biomes) follow vanilla's erosion and weirdness, which shape the terrain too, so they keep vanilla's size; in
  hot bands mountains show as badlands, which is vanilla's choice for hot peaks.
