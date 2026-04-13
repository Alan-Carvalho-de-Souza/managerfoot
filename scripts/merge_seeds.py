"""
merge_seeds.py
Merge all seed JSON files into a single seed_data.json.
Usage: python scripts/merge_seeds.py
"""
import json
import os

ASSETS = os.path.join("app", "src", "main", "assets")
FILES = [
    "seed_brasileirao.json",
    "seed_argentina.json",
    "seed_argentina2.json",
]
OUT = os.path.join(ASSETS, "seed_data.json")

all_times: list = []
all_jogadores: list = []

for fname in FILES:
    path = os.path.join(ASSETS, fname)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    times = data.get("times", [])
    jogadores = data.get("jogadores", [])
    all_times.extend(times)
    all_jogadores.extend(jogadores)
    print(f"  {fname}: {len(times)} times, {len(jogadores)} jogadores")

merged = {"times": all_times, "jogadores": all_jogadores}

with open(OUT, "w", encoding="utf-8") as f:
    json.dump(merged, f, ensure_ascii=False, separators=(",", ":"))

size_kb = os.path.getsize(OUT) / 1024
print(f"\n✓ {OUT}")
print(f"  Times: {len(all_times)} | Jogadores: {len(all_jogadores)} | Tamanho: {size_kb:.1f} KB")
