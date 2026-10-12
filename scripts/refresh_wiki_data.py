#!/usr/bin/env python3
"""Refresh numerical Wiki combat facts at an explicit revision, preserving local item metadata.

No calculator implementation is imported. Unknown gear is retained for data completeness
but excluded from search until its requirements and availability have been checked.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/com/bestdps/data"
DEFAULT_REVISION = "89c3e25b344aea90d0189746e4b5f73dde0f0383"


def read_resource(name):
    with gzip.open(RESOURCE / (name + ".json.gz"), "rt", encoding="utf-8") as source:
        return json.load(source)


def write_resource(name, rows):
    encoded = json.dumps(rows, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    destination = RESOURCE / (name + ".json.gz")
    destination.write_bytes(gzip.compress(encoded, compresslevel=9, mtime=0))
    return hashlib.sha256(encoded).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", default=DEFAULT_REVISION)
    args = parser.parse_args()
    if len(args.revision) != 40 or any(c not in "0123456789abcdef" for c in args.revision):
        parser.error("--revision must be a full lowercase Git commit hash")
    base = "https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/" + args.revision + "/cdn/json/"

    def fetch(name):
        request = Request(base + name + ".json", headers={"User-Agent": "BestDps data maintenance (github.com/guccifurs/best-dps)"})
        with urlopen(request, timeout=30) as response:
            return json.load(response)

    monsters = fetch("monsters")
    # Only game identity, numeric stats and mechanical flags; no descriptions or artwork.
    monster_keys = ("id", "name", "version", "level", "size", "skills", "offensive", "defensive", "attributes", "weakness", "is_slayer_monster")
    monsters = [{key: row[key] for key in monster_keys if key in row} for row in monsters]
    spells = [{key: row[key] for key in ("name", "max_hit", "spellbook", "element") if key in row} for row in fetch("spells")]
    old_items = {row["id"]: row for row in read_resource("gear_prices")}
    requirements = {row["id"]: row for row in read_resource("equipment_requirements")}
    overrides_path = ROOT / "scripts/gear_metadata_overrides.json"
    overrides = json.loads(overrides_path.read_text(encoding="utf-8")) if overrides_path.exists() else {}
    manifest_path = RESOURCE / "wiki-data-provenance.json"
    old_manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    unresolved_ids = {row["id"] for row in old_manifest.get("gearAwaitingMetadata", [])}
    equipment_keys = ("id", "name", "version", "slot", "speed", "category", "bonuses", "offensive", "defensive", "isTwoHanded")
    equipment = []
    unknown = []
    for row in fetch("equipment"):
        item = dict(old_items.get(row["id"], {}))
        new_item = not item
        if new_item:
            item.update(isStandardGear=False, tradeable=False, estimatedPrice=None)
        item.update({key: row[key] for key in equipment_keys if key in row})
        override = overrides.get(str(row["id"]))
        if override is not None:
            item.update({key: override[key] for key in ("tradeable", "isStandardGear") if key in override})
            if "skills" in override:
                requirements[row["id"]] = {"id": row["id"], "skills": override["skills"]}
        elif new_item or row["id"] in unresolved_ids:
            unknown.append({"id": row["id"], "name": row["name"], "version": row.get("version", "")})
        equipment.append(item)

    # Validate the downloaded format before replacing any bundled resource.
    for kind, rows in (("monsters", monsters), ("equipment", equipment), ("spells", spells)):
        if not rows or not all(isinstance(row.get("name"), str) for row in rows):
            raise ValueError("Invalid " + kind + " data")
    if len(monsters) < 2000 or len(equipment) < 4000:
        raise ValueError("Unexpected dataset truncation")

    manifest = {
        "source": "OSRS Wiki contributors, numerical combat data distributed by the Wiki DPS calculator",
        "repository": "https://github.com/weirdgloop/osrs-dps-calc",
        "revision": args.revision,
        "dataPages": "https://oldschool.runescape.wiki/",
        "attribution": "https://oldschool.runescape.wiki/w/RuneScape:Copyrights",
        "implementation": "No GPL calculator source is imported by this script.",
        "monsterVariants": len(monsters),
        "distinctNpcIds": len({row["id"] for row in monsters}),
        "gearVariants": len(equipment),
        "spells": len(spells),
        "gearAwaitingMetadata": unknown,
        "verifiedGearMetadataOverrides": len(overrides),
        "gearMetadataSources": "scripts/gear_metadata_overrides.json",
        "resourceSha256Uncompressed": {},
    }
    for name, rows in (("monsters", monsters), ("gear_prices", equipment), ("spells", spells),
                       ("equipment_requirements", sorted(requirements.values(), key=lambda row: row["id"]))):
        manifest["resourceSha256Uncompressed"][name] = write_resource(name, rows)
    (RESOURCE / "wiki-data-provenance.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print("Updated %d NPC variants / %d IDs, %d equipment variants, %d spells; %d new gear rows await metadata."
          % (len(monsters), manifest["distinctNpcIds"], len(equipment), len(spells), len(unknown)))


if __name__ == "__main__":
    main()
