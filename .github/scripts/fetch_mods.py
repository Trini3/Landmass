#!/usr/bin/env python3
"""Download the newest Fabric 26.3 build of Modrinth projects, plus their required dependencies.

Used by the GitHub Actions self-test to check the mod alongside other worldgen mods.

    python3 fetch_mods.py <game dir> <modrinth slug> [<modrinth slug> ...]

Mods go in <game dir>/mods. Projects that only ship as datapacks go in <game dir>/world/datapacks,
so they're active when the test world is created.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
GAME_VERSION = "26.3"
HEADERS = {"User-Agent": "Trini3/landmass_lite-ci (https://github.com/Trini3/landmass)"}


def get(url):
    request = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def newest_version(project, loader):
    query = urllib.parse.urlencode({
        "loaders": json.dumps([loader]),
        "game_versions": json.dumps([GAME_VERSION]),
    })
    versions = get(f"{API}/project/{project}/version?{query}")
    return versions[0] if versions else None


def download(version, folder):
    files = version["files"]
    file = next((f for f in files if f.get("primary")), files[0])
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, file["filename"])
    request = urllib.request.Request(file["url"], headers=HEADERS)
    with urllib.request.urlopen(request, timeout=300) as response, open(path, "wb") as out:
        out.write(response.read())
    print(f"  {file['filename']} -> {folder}")


def fetch(project, game_dir, seen, required_by=None):
    if project in seen:
        return True
    seen.add(project)
    version = newest_version(project, "fabric")
    folder = os.path.join(game_dir, "mods")
    if version is None:
        version = newest_version(project, "datapack")
        folder = os.path.join(game_dir, "world", "datapacks")
    if version is None:
        where = f" (needed by {required_by})" if required_by else ""
        print(f"No Fabric or datapack build of '{project}' for {GAME_VERSION}{where}", file=sys.stderr)
        return False
    print(f"{project} {version['version_number']}")
    download(version, folder)
    ok = True
    for dependency in version.get("dependencies", []):
        if dependency.get("dependency_type") != "required":
            continue
        if dependency.get("project_id"):
            ok = fetch(dependency["project_id"], game_dir, seen, project) and ok
        elif dependency.get("version_id"):
            pinned = get(f"{API}/version/{dependency['version_id']}")
            if pinned["project_id"] not in seen:
                seen.add(pinned["project_id"])
                print(f"{pinned['project_id']} {pinned['version_number']} (pinned by {project})")
                download(pinned, os.path.join(game_dir, "mods"))
    return ok


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    game_dir = sys.argv[1]
    seen = set()
    ok = True
    for project in sys.argv[2:]:
        ok = fetch(project, game_dir, seen) and ok
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
