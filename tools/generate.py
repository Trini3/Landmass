"""Generates the mod's world presets from vanilla's Large Biomes data.

Vanilla Large Biomes makes biomes 4x wider than Default by sampling the
"*_large" noises (two octaves lower) through the overworld_large_biomes
density functions. This script copies those files and lowers the xz_scale of
the four biome noises (continentalness, erosion, temperature, vegetation)
by a further factor, exactly the way Large Biomes stretches them. Ridges
(weirdness) stay as they are, just like in Large Biomes.

Usage: python3 tools/generate.py <extracted vanilla data folder>
The folder must contain minecraft/worldgen/... from the 26.3 server jar.
"""
import json
import os
import shutil
import sys

MOD = "evenlargerbiomes"
# preset id -> size relative to vanilla Large Biomes
PRESETS = {"larger_biomes": 1.5, "xl_biomes": 2.0, "xxl_biomes": 3.0, "huge_biomes": 4.0, "giga_biomes": 8.0}
SCALED = ("continents", "erosion", "temperature", "vegetation")
LARGE_XZ_SCALE = 0.25

src = os.path.join(sys.argv[1], "minecraft", "worldgen")
out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", MOD, "worldgen")
shutil.rmtree(out, ignore_errors=True)


def retarget(text, preset):
    return text.replace("minecraft:overworld_large_biomes/", f"{MOD}:{preset}/")


def scale_noise(node, factor):
    if isinstance(node, dict):
        if node.get("type") == "minecraft:noise" and "xz_scale" in node:
            assert node["xz_scale"] == LARGE_XZ_SCALE, node
            node["xz_scale"] = LARGE_XZ_SCALE / factor
        for v in node.values():
            scale_noise(v, factor)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


df_dir = os.path.join(src, "density_function", "overworld_large_biomes")
for preset, factor in PRESETS.items():
    for name in sorted(os.listdir(df_dir)):
        data = json.loads(retarget(open(os.path.join(df_dir, name)).read(), preset))
        if name[:-5] in SCALED:
            scale_noise(data, factor)
        write(os.path.join(out, "density_function", preset, name), data)

    settings = json.loads(retarget(open(os.path.join(src, "noise_settings", "large_biomes.json")).read(), preset))
    write(os.path.join(out, "noise_settings", preset + ".json"), settings)

    world = json.load(open(os.path.join(src, "world_preset", "large_biomes.json")))
    world["dimensions"]["minecraft:overworld"]["generator"]["settings"] = f"{MOD}:{preset}"
    write(os.path.join(out, "world_preset", preset + ".json"), world)

# Adds the presets to the World Type button on the Create World screen.
tag = {"replace": False, "values": [f"{MOD}:{p}" for p in PRESETS]}
write(os.path.join(out, "..", "..", "minecraft", "tags", "worldgen", "world_preset", "normal.json"), tag)
print("Generated", len(PRESETS), "presets in", os.path.normpath(out))
