# -*- coding: utf-8 -*-
"""
gen_escudos_championship.py

Gera 24 escudos placeholder (Android ShapeDrawable XML circular colorido)
para os clubes da EFL Championship — cor derivada do hash do nome do time
para garantir variedade sem duplicatas visuais obvias.

Depois atualiza seed_championship.json setando o campo escudo_res para o
nome do drawable de cada clube. Ao rodar merge_seeds.py na sequencia, o
campo se propaga para o seed_data.json consolidado.

Uso:
    python scripts/gen_escudos_championship.py
    python scripts/merge_seeds.py
"""
import json
import os
import hashlib

# --- Slugs oficiais (drawable filename, sem prefixo/extensao) --------------
SLUGS = {
    421: "wolverhamptonwanderers",
    422: "southampton_efl",
    423: "westhamunited",
    424: "burnley",
    425: "sheffieldunited",
    426: "middlesbrough",
    427: "norwichcity",
    428: "birminghamcity",
    429: "westbromwichalbion",
    430: "swanseacity",
    431: "watford",
    432: "stokecity",
    433: "derbycounty",
    434: "queensparkrangers",
    435: "cardiffcity",
    436: "bristolcity",
    437: "blackburnrovers",
    438: "prestonnorthend",
    439: "millwall",
    440: "charltonathletic",
    441: "portsmouth",
    442: "boltonwanderers",
    443: "wrexham_efl",
    444: "lincolncity",
}

DRAWABLE_DIR = os.path.join("app", "src", "main", "res", "drawable")
os.makedirs(DRAWABLE_DIR, exist_ok=True)

def cor_do_slug(slug):
    """Deriva 3 bytes de cor do hash SHA1 do slug, evitando cores muito escuras/claras."""
    h = hashlib.sha1(slug.encode("utf-8")).digest()
    # Usa 3 primeiros bytes, mas garante que cada canal fique em [80, 220]
    # para dar contraste bom com texto branco sem ser fluorescente.
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

# --- Atualiza seed_championship.json ---------------------------------------
SEED_PATH = os.path.join("app", "src", "main", "assets", "seed_championship.json")
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

print(f"seed_championship.json atualizado: {atualizados} times com escudo_res")
print("Rode agora: python scripts/merge_seeds.py")
