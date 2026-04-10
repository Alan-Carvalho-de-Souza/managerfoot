"""
Generates seed_argentina.json with 30 Argentine clubs and 22 players each.
Also patches seed_brasileirao.json to add "pais": "Brasil" to all teams.
"""
import json
import random
import os

# ─────────────────── Name pools ──────────────────────────────────────────────
PRIMEIROS = [
    "Carlos", "Juan", "Pablo", "Diego", "Franco", "Nicolás", "Matías",
    "Ezequiel", "Rodrigo", "Federico", "Sebastián", "Agustín", "Lucas",
    "Maximiliano", "Emiliano", "Ignacio", "Facundo", "Lautaro", "Nahuel",
    "Leandro", "Gonzalo", "Tomás", "Ramiro", "Gastón", "Marcelo", "Leonardo",
    "Esteban", "Hernán", "Sergio", "Martín", "Cristian", "Javier", "Damián",
    "Walter", "Néstor", "Alejandro", "Gerardo", "Claudio", "Oscar", "Raúl",
    "Nelson", "Jorge", "Gustavo", "Ricardo", "Fernando", "Daniel", "Marco",
    "Andrés", "Santiago", "Iván",
]
SOBRENOMES = [
    "González", "Rodríguez", "García", "López", "Martínez", "Pérez",
    "Fernández", "Romero", "Álvarez", "Díaz", "Sánchez", "Morales", "Castro",
    "Ramírez", "Torres", "Ruiz", "Rojas", "Herrera", "Medina", "Flores",
    "Ibáñez", "Peralta", "Gutiérrez", "Vargas", "Ramos", "Alario", "Almada",
    "Barco", "Beltrán", "Domínguez", "Espósito", "Gaitán", "Heredia",
    "Insúa", "Lema", "Montoya", "Nieto", "Olmedo", "Quintero", "Ríos",
    "Suárez", "Tapia", "Urquiza", "Velázquez", "Zamora", "Arzani", "Bravo",
    "Cáceres", "Delgado", "Escobar",
]

used_names: set = set()


def gen_name(idx: int) -> str:
    p = idx % len(PRIMEIROS)
    s = (idx * 7 + 13) % len(SOBRENOMES)
    candidate = f"{PRIMEIROS[p]} {SOBRENOMES[s]}"
    offset = 0
    while candidate in used_names:
        offset += 1
        s2 = (s + offset) % len(SOBRENOMES)
        candidate = f"{PRIMEIROS[p]} {SOBRENOMES[s2]}"
    used_names.add(candidate)
    return candidate


def abrev(nome: str) -> str:
    parts = nome.split()
    if len(parts) >= 2:
        return f"{parts[0][0]}. {parts[-1]}"[:15]
    return nome[:15]


# ─────────────────── Attribute generation ────────────────────────────────────
BASE_RANGE = {
    10: (74, 91), 9: (70, 86), 8: (68, 82),
    7: (62, 76),  6: (55, 70), 5: (48, 63), 4: (40, 55),
}

BOOSTS: dict[str, dict[str, int]] = {
    "GOLEIRO":           {"defesa": 12, "velocidade": -8, "finalizacao": -15},
    "ZAGUEIRO":          {"defesa": 9, "fisico": 5, "finalizacao": -8},
    "LATERAL_DIREITO":   {"velocidade": 6, "defesa": 4},
    "LATERAL_ESQUERDO":  {"velocidade": 6, "defesa": 4},
    "VOLANTE":           {"passe": 6, "defesa": 5},
    "MEIA_CENTRAL":      {"tecnica": 8, "passe": 8},
    "PONTA_DIREITA":     {"velocidade": 9, "tecnica": 5, "defesa": -8},
    "PONTA_ESQUERDA":    {"velocidade": 9, "tecnica": 5, "defesa": -8},
    "CENTROAVANTE":      {"finalizacao": 9, "fisico": 6, "defesa": -9},
}

STAT_KEYS = ["forca", "tecnica", "passe", "velocidade", "finalizacao", "defesa", "fisico"]


def gen_stats(nivel: int, pos: str, seed: int) -> dict[str, int]:
    r = random.Random(seed)
    lo, hi = BASE_RANGE[nivel]
    base = {k: r.randint(lo, hi) for k in STAT_KEYS}
    boosts = BOOSTS.get(pos, {})
    for stat, delta in boosts.items():
        if stat in base:
            base[stat] = max(1, min(99, base[stat] + delta))
    return base


def gen_salario_valor(nivel: int, seed: int) -> tuple[int, int]:
    r = random.Random(seed + 9999)
    ranges = {
        10: ((150_000_00, 420_000_00), (10_000_000_00, 50_000_000_00)),
        9:  ((90_000_00,  200_000_00), (6_000_000_00,  22_000_000_00)),
        8:  ((65_000_00,  160_000_00), (4_000_000_00,  16_000_000_00)),
        7:  ((38_000_00,  100_000_00), (2_000_000_00,  10_000_000_00)),
        6:  ((18_000_00,   58_000_00), (700_000_00,    5_500_000_00)),
        5:  ((7_000_00,    24_000_00), (200_000_00,    1_800_000_00)),
        4:  ((3_000_00,    10_000_00), (80_000_00,     700_000_00)),
    }
    sal_range, val_range = ranges[nivel]
    return r.randint(*sal_range), r.randint(*val_range)


# ─────────────────── Squads: 22 positions per team ───────────────────────────
POSICOES = [
    "GOLEIRO", "GOLEIRO", "GOLEIRO",
    "ZAGUEIRO", "ZAGUEIRO", "ZAGUEIRO", "ZAGUEIRO",
    "LATERAL_DIREITO", "LATERAL_DIREITO",
    "LATERAL_ESQUERDO", "LATERAL_ESQUERDO",
    "VOLANTE", "VOLANTE", "VOLANTE",
    "MEIA_CENTRAL", "MEIA_CENTRAL",
    "PONTA_DIREITA", "PONTA_DIREITA",
    "PONTA_ESQUERDA", "PONTA_ESQUERDA",
    "CENTROAVANTE", "CENTROAVANTE",
]  # 22 positions


# ─────────────────── Teams data ──────────────────────────────────────────────
# (id, nome, cidade, estado, nivel, saldo, estadio_nome, cap, preco, tatica, estilo, rep)
TIMES = [
    (81,  "River Plate",       "Buenos Aires",        "BA",  10, 7_000_000_00, "Monumental",                      84567, 8500, "4-3-3",   "OFENSIVO",      97),
    (82,  "Boca Juniors",      "Buenos Aires",        "BA",  10, 6_500_000_00, "La Bombonera",                    54000, 8500, "4-4-2",   "EQUILIBRADO",   96),
    (83,  "Racing Club",       "Avellaneda",          "BA",   8, 3_500_000_00, "El Cilindro",                     51389, 6000, "4-3-3",   "OFENSIVO",      88),
    (84,  "Independiente",     "Avellaneda",          "BA",   7, 2_500_000_00, "Estadio Libertadores",            52000, 5000, "4-4-2",   "DEFENSIVO",     84),
    (85,  "San Lorenzo",       "Buenos Aires",        "BA",   7, 2_200_000_00, "Estadio Pedro Bidegain",          47964, 5000, "4-2-3-1", "EQUILIBRADO",   83),
    (86,  "Estudiantes LP",    "La Plata",            "BA",   7, 2_000_000_00, "Estadio UNO",                     30000, 4500, "4-4-2",   "DEFENSIVO",     82),
    (87,  "Velez Sarsfield",   "Buenos Aires",        "BA",   7, 2_100_000_00, "Jose Amalfitani",                 49540, 4500, "4-3-3",   "OFENSIVO",      81),
    (88,  "Newells Old Boys",  "Rosario",             "SF",   7, 1_900_000_00, "Marcelo Bielsa",                  42000, 4500, "4-3-3",   "OFENSIVO",      80),
    (89,  "Rosario Central",   "Rosario",             "SF",   7, 1_900_000_00, "Gigante de Arroyito",             41654, 4500, "4-4-2",   "EQUILIBRADO",   80),
    (90,  "Talleres Cordoba",  "Córdoba",             "CBA",  6, 1_600_000_00, "Mario Alberto Kempes",            57000, 4000, "4-3-3",   "OFENSIVO",      76),
    (91,  "Belgrano",          "Córdoba",             "CBA",  6, 1_400_000_00, "Estadio Julio Cornet",            40000, 3500, "4-4-2",   "DEFENSIVO",     74),
    (92,  "Defensa y Justicia","Florencio Varela",    "BA",   6, 1_500_000_00, "Estadio Norberto Tito",           15000, 3500, "4-3-3",   "CONTRA_ATAQUE", 74),
    (93,  "Atletico Tucuman",  "Tucumán",             "TUC",  6, 1_300_000_00, "Estadio Monumental",              35000, 3500, "4-4-2",   "EQUILIBRADO",   73),
    (94,  "Lanus",             "Lanús",               "BA",   6, 1_400_000_00, "Estadio La Fortaleza",            47027, 4000, "4-2-3-1", "EQUILIBRADO",   76),
    (95,  "Argentinos Jrs",   "Buenos Aires",         "BA",   6, 1_200_000_00, "Diego Armando Maradona",          26000, 3000, "4-3-3",   "OFENSIVO",      74),
    (96,  "Huracan",           "Buenos Aires",        "BA",   6, 1_100_000_00, "Estadio Tomas Duco",              48314, 3500, "4-4-2",   "EQUILIBRADO",   75),
    (97,  "Banfield",          "Banfield",            "BA",   6, 1_000_000_00, "Estadio Florencio Sola",          20000, 3000, "4-4-2",   "DEFENSIVO",     72),
    (98,  "Godoy Cruz",        "Mendoza",             "MZA",  6, 1_100_000_00, "Estadio Feliciano Gambarte",      36000, 3000, "4-4-2",   "EQUILIBRADO",   72),
    (99,  "Gimnasia LP",       "La Plata",            "BA",   6, 1_000_000_00, "Estadio Juan Carmelo Zerillo",    35000, 3000, "4-3-3",   "DEFENSIVO",     73),
    (100, "Union Santa Fe",    "Santa Fe",            "SF",   5,   800_000_00, "Estadio 15 de Abril",             24000, 2500, "4-4-2",   "EQUILIBRADO",   68),
    (101, "Tigre",             "Tigre",               "BA",   5,   750_000_00, "Estadio Jose Dellagiovanna",      27000, 2500, "4-4-2",   "CONTRA_ATAQUE", 68),
    (102, "Platense",          "Buenos Aires",        "BA",   5,   700_000_00, "Est. Ciudad de Vicente Lopez",    15000, 2000, "4-3-3",   "DEFENSIVO",     65),
    (103, "Colon Santa Fe",    "Santa Fe",            "SF",   5,   750_000_00, "Estadio Brigadier Lopez",         40000, 2500, "4-4-2",   "EQUILIBRADO",   67),
    (104, "Barracas Central",  "Buenos Aires",        "BA",   5,   650_000_00, "Estadio Fragata Sarmiento",       20000, 2000, "4-4-2",   "DEFENSIVO",     62),
    (105, "Central Cordoba",   "Santiago del Estero", "SGO",  5,   700_000_00, "Estadio Madre de Ciudades",       30000, 2000, "4-4-2",   "DEFENSIVO",     67),
    (106, "Sarmiento",         "Junín",               "BA",   5,   600_000_00, "Estadio de Junin",                15000, 2000, "4-4-2",   "DEFENSIVO",     64),
    (107, "Instituto",         "Córdoba",             "CBA",  5,   650_000_00, "Estadio Juan Domingo Peron",      22000, 2000, "4-4-2",   "EQUILIBRADO",   66),
    (108, "Dep. Riestra",      "Buenos Aires",        "BA",   4,   500_000_00, "Estadio Jose Fierro",              8000, 1500, "4-4-2",   "DEFENSIVO",     58),
    (109, "Quilmes",           "Quilmes",             "BA",   4,   500_000_00, "Estadio Centenario",              30000, 1500, "4-4-2",   "DEFENSIVO",     60),
    (110, "Aldosivi",          "Mar del Plata",       "BA",   4,   450_000_00, "Estadio Jose Maria Minella",      35000, 1500, "4-4-2",   "DEFENSIVO",     58),
]

assert len(TIMES) == 30, f"Expected 30 teams, got {len(TIMES)}"
assert len(POSICOES) == 22, f"Expected 22 positions, got {len(POSICOES)}"

# ─────────────────── Build Argentine seed ─────────────────────────────────────
data: dict = {"times": [], "jogadores": []}
jogador_id = 1730
jogador_idx = 0

for t in TIMES:
    tid, tnome, tcidade, testado, tnivel, tsaldo, testadio_nome, testadio_cap, tpreco, ttatica, testilo, trep = t

    data["times"].append({
        "id": tid,
        "nome": tnome,
        "cidade": tcidade,
        "estado": testado,
        "divisao": "A",
        "pais": "Argentina",
        "nivel": tnivel,
        "saldo": tsaldo,
        "estadio_nome": testadio_nome,
        "estadio_capacidade": testadio_cap,
        "preco_ingresso": tpreco,
        "tatica": ttatica,
        "estilo": testilo,
        "reputacao": trep,
        "escudo_res": "",
    })

    for pos in POSICOES:
        nome = gen_name(jogador_idx)
        r = random.Random(jogador_id)
        idade = r.randint(19, 33)
        contrato = r.randint(1, 3) if tnivel <= 6 else r.randint(2, 5)
        attrs = gen_stats(tnivel, pos, jogador_id)
        sal, val = gen_salario_valor(tnivel, jogador_id)

        data["jogadores"].append({
            "id": jogador_id,
            "time_id": tid,
            "nome": nome,
            "nome_abrev": abrev(nome),
            "idade": idade,
            "posicao": pos,
            "forca":       attrs["forca"],
            "tecnica":     attrs["tecnica"],
            "passe":       attrs["passe"],
            "velocidade":  attrs["velocidade"],
            "finalizacao": attrs["finalizacao"],
            "defesa":      attrs["defesa"],
            "fisico":      attrs["fisico"],
            "salario":     sal,
            "contrato":    contrato,
            "valor_mercado": val,
        })
        jogador_id += 1
        jogador_idx += 1

assert len(data["times"]) == 30
assert len(data["jogadores"]) == 660
assert data["jogadores"][0]["id"] == 1730
assert data["jogadores"][-1]["id"] == 2389

# ─────────────────── Write Argentine seed ─────────────────────────────────────
assets_dir = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
arg_path = os.path.join(assets_dir, "seed_argentina.json")
with open(arg_path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"✓ Wrote {arg_path}")
print(f"  Teams: {len(data['times'])}, Players: {len(data['jogadores'])}")
print(f"  Player IDs: {data['jogadores'][0]['id']} → {data['jogadores'][-1]['id']}")

# ─────────────────── Patch Brazilian seed ─────────────────────────────────────
br_path = os.path.join(assets_dir, "seed_brasileirao.json")
with open(br_path, encoding="utf-8") as f:
    br_data = json.load(f)

patched = 0
for team in br_data["times"]:
    if "pais" not in team:
        team["pais"] = "Brasil"
        patched += 1

with open(br_path, "w", encoding="utf-8") as f:
    json.dump(br_data, f, ensure_ascii=False, indent=2)
print(f"✓ Patched {br_path}: added pais=Brasil to {patched} teams")
