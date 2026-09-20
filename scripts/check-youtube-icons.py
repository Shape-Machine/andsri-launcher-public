#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1] / "app/src/main/assets/icons"
packs = ("lawnicons", "arcticons", "appstract", "cuscon", "delta", "dollphone", "snow")
for pack in packs:
    folder = root / pack
    pairs = [line.split("\t") for line in (folder / "mapping.tsv").read_text().splitlines() if "\t" in line]
    mapping = dict(pairs)
    assert len(mapping) == len(pairs), f"Duplicate package in {pack}"
    asset = mapping["xyz.shapemachine.andsri.youtube"]
    assert asset == mapping["com.google.android.youtube"], pack
    assert (folder / asset).is_file(), f"Missing {pack}/{asset}"
    print(f"YouTube mapping verified: {pack}")
