# Trini's World Preview

A Fabric mod for Minecraft 26.3 that draws a map of a world's climate zones and biomes, for any world type.
It only needs Fabric API.

- **Create New World > World tab > World Preview...**: a map of the world you're setting up, drawn from the
  selected world type, datapacks and biome mods with the current seed. Switch between climate zones and biomes,
  hover to see biome names, drag to move, scroll to zoom. **New seed** re-rolls the seed (it goes into the seed
  box), and right-clicking picks where the new world's spawn will be (**Auto spawn** gives it back to Minecraft).
- **In game**: press **N** (Options > Controls > Key Binds, "Trini's World Preview") or use the **World map**
  button in the pause menu. Right-click the map to teleport there or set the world spawn there (cheats needed,
  the same as /tp). Works in singleplayer and for the host of a LAN world.
- Commands (cheats needed): `/twp tp <x> <z>` and `/twp setspawn <x> <z>`.

## Alongside Landmass or Trini's Terrain

Both of those have their own world map and preview, so when either is installed this mod steps aside:
its world map key starts unbound (no clash over N), it adds no second pause menu button, and the World Preview
button is greyed out for their world types (use their Customize screen's preview). Everything else still works,
and the key can be bound to open this mod's map. Mod id, key category and commands are all its own.

## Building

`./gradlew build` (Java 25). GitHub Actions builds every push; the jar is in the run's `triniworldpreview-jar` artifact.

## Known issues

- The maps sample biomes at y=160 straight from the generator, so cave biomes don't show and structures aren't drawn.
- The in-game map needs the world's generator, which a client joined to a server doesn't have.
- For flat and other generators without noise settings, the climate view uses normal overworld noise; the biome
  view shows the generator's real biomes.
- A spawn picked on the preview is kept for that Create World screen only; it is handed to the world as it is
  created (singleplayer runs the server in the same game) and used once, for the overworld.
- The World Preview button is added as row 3 of the World tab's grid. If Minecraft changes that tab's layout,
  check the row.
