# Acclimatization

A Fabric mod for Minecraft 26.3 that makes the overworld's climate follow latitude: hot at the equator (z = 0), getting
colder going north and south, up to frozen poles. Humidity still varies, so deserts and jungles share the same latitudes.

It works with any world type (vanilla, Large Biomes, Amplified, datapack or mod world types) and only changes which biomes
go where. Terrain is left to the world type or your terrain mod, and other dimensions are never touched.

Needs Fabric Loader 0.20.1+, Fabric API and Java 25.

## Use

On the Create World screen's **World** tab, switch **Custom Climate** on (it is off by default), then press
**Climate Settings...** to change:

- **Zones:** the width of the hot, warm, temperate, cold and frozen zones, humidity zone size, biome size and band
  waviness. The grey line under the rows shows where the frozen zone starts and where the pole is.
- **Deviation:** patches where the climate jumps warmer, colder, drier or wetter, and how much of the hot zone is desert.

The settings are saved in the world folder at `serverconfig/acclimatization.json`. Edit that file and restart the world to
change them (only new chunks change). On a dedicated server, put a file there before the world is first created; `{}`
gives the defaults. Deleting the file turns Custom Climate off for that world.

## Compatibility

- Biome mods (Biomes O' Plenty, Regions Unexplored, Terralith) and terrain mods (Lithosphere, Tectonic) keep working:
  the mod only wraps the overworld's final temperature and humidity.
- Safe alongside Landmass and Trini's Terrain: worlds made with their world types keep their own climate settings, and
  the Custom Climate switch is greyed out for them.
- World preview mods that build their own preview (like Trini's World Preview) show the climate zones too.

## Known issues

- Client screens are untested in game; CI only builds the mod and starts a dedicated server with the climate on.
- Biome size only resizes the humidity and temperature patches. Stretching terrain like Large Biomes would need the
  world's generator settings changed, which would break terrain mods, so it is left out.
- The Create World screen hands the settings to the new world by its folder name, at the moment Create is pressed.
