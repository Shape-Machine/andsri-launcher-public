#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[2]
relative = Path("app/src/main/java/xyz/shapemachine/andsri/settings/SettingsControls.kt")
canonical = (root / "andsri-launcher-private" / relative).read_bytes()
for name in ("andsri-tasks", "andsri-podcast-player", "andsri-audiobook-player", "andsri-youtube-player"):
    repo = root / name
    if repo.exists():
        assert (repo / relative).read_bytes() == canonical, f"Shared settings drift: {name}"
        print(f"Settings controls match: {name}")
