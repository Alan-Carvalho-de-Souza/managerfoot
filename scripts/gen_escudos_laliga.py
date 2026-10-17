# -*- coding: utf-8 -*-
"""
gen_escudos_laliga.py

Gera 20 escudos placeholder (VectorDrawable XML circular colorido) para os
clubes de La Liga. Cor derivada do hash do nome do time para garantir
variedade sem duplicatas visuais obvias. Formato compativel com o
painterResource() do Compose (VectorDrawable — nao ShapeDrawable).

Atualiza seed_laliga.json setando o campo escudo_res para o nome do
drawable de cada clube. Rode `merge_seeds.py` na sequencia para propagar
para seed_data.json.

Uso:
    python scripts/gen_escudos_laliga.py
    python scripts/merge_seeds.py
"""
import json
import os
import hashlib

# --- Slugs oficiais (drawable filename, sem prefixo/extensao) --------------
SLUGS = {
    501: "realmadrid",
    502: "barcelona",
    503: "atleticomadrid",
    504: "athleticbilbao",
    505: "realsociedad",
    506: "realbetis",
    507: "villarreal",
    508: "sevilla",
    509: "valencia_esp",
    510: "celtavigo",
    511: "osasuna",
    512: "getafe",
    513: "rayovallecano",
    514: "mallorca",
    515: "girona",
    516: "espanyol",
    517: "alaves",
    518: "elche",
    519: "levante",
    520: "realoviedo",
}

DRAWABLE_DIR = os.path.join("app", "src", "main", "res", "drawable")
os.makedirs(DRAWABLE_DIR, exist_ok=True)

def cor_do_slug(slug):
    """Deriva 3 bytes de cor do hash SHA1 do slug, evitando cores muito escuras/claras."""
    h = hashlib.sha1(slug.encode("utf-8")).digest()
    r = 80 + (h[0] % 141)
    g = 80 + (h[1] % 141)
    b = 80 + (h[2] % 141)
    return f"#{r:02X}{g:02X}{b:02X}"

### VectorDrawable circular — compativel com painterResource() do Compose.
### ShapeDrawable (<shape>) NAO funciona com painterResource — crasha ao carregar.
SHAPE_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="40dp"
    android:height="40dp"
    android:viewportWidth="40"
    android:viewportHeight="40">
    <path
        android:pathData="M20,20 m-19,0 a19,19 0 1,0 38,0 a19,19 0 1,0 -38,0"
        android:fillColor="{cor}" />
    <path
        android:pathData="M20,20 m-19,0 a19,19 0 1,0 38,0 a19,19 0 1,0 -38,0"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="1"
        android:fillColor="#00000000" />
</vector>
"""

# --- Gera os arquivos ------------------------------------------------------
gerados = 0
for tid, slug in SLUGS.items():
    cor = cor_do_slug(slug)
    fname = f"escudo_{slug}.xml"
    fpath = os.path.join(DRAWABLE_DIR, fname)
    with open(fpath, "w", encoding="utf-8") as f:
        f.write(SHAPE_TEMPLATE.format(cor=cor))
    gerados += 1

print(f"Escudos gerados: {gerados} em {DRAWABLE_DIR}")

# --- Atualiza seed_laliga.json ---------------------------------------------
SEED_PATH = os.path.join("app", "src", "main", "assets", "seed_laliga.json")
with open(SEED_PATH, encoding="utf-8") as f:
    data = json.load(f)

por_id = {t["id"]: t for t in data["times"]}
atualizados = 0
for tid, slug in SLUGS.items():
    if tid in por_id:
        por_id[tid]["escudo_res"] = f"escudo_{slug}"
        atualizados += 1

with open(SEED_PATH, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"seed_laliga.json atualizado: {atualizados} times com escudo_res")
print("Rode agora: python scripts/merge_seeds.py")
