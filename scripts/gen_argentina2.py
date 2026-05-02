"""
Generates seed_argentina2.json with 30 Argentine clubs (Segunda División / Primera Nacional)
and 22 players each. Teams are sourced from espn.com.br/futebol/times/_/liga/ARG.2,
excluding clubs already present in the Primera División seed (seed_argentina.json).
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
    # Use a different offset than gen_argentina.py to get distinct names
    p = (idx + 17) % len(PRIMEIROS)
    s = (idx * 11 + 29) % len(SOBRENOMES)
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
    7: (62, 76),  6: (55, 70), 5: (48, 63), 4: (40, 55), 3: (33, 48),
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
        3:  ((1_500_00,     5_000_00), (30_000_00,     250_000_00)),
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
# All teams have divisao="B" and pais="Argentina"
TIMES = [
    (111, "All Boys",            "Buenos Aires",           "BA",  5,  650_000_00, "Estadio Islas Malvinas",              25000, 2000, "4-4-2",   "EQUILIBRADO",  68),
    (112, "Chacarita Juniors",   "Buenos Aires",           "BA",  5,  700_000_00, "Estadio Roque Tueros",                28000, 2000, "4-3-3",   "OFENSIVO",     70),
    (113, "Ferro Carril Oeste",  "Buenos Aires",           "BA",  5,  600_000_00, "Estadio Arquitecto R. Etchart",       24000, 2000, "4-4-2",   "EQUILIBRADO",  68),
    (114, "Los Andes",           "Lomas de Zamora",        "BA",  5,  580_000_00, "Estadio Angel Gallardo",              18000, 1500, "4-4-2",   "DEFENSIVO",    65),
    (115, "Almagro",             "Buenos Aires",           "BA",  5,  550_000_00, "Estadio Tres de Febrero",             18000, 1500, "4-3-3",   "OFENSIVO",     65),
    (116, "San Martin SJ",       "San Juan",               "SJ",  5,  590_000_00, "Estadio Hilario Sanchez",             26000, 1500, "4-4-2",   "EQUILIBRADO",  67),
    (117, "San Martin Tuc",      "San Miguel de Tucuman",  "TUC", 5,  560_000_00, "Estadio San Martin",                  25000, 1500, "4-4-2",   "DEFENSIVO",    66),
    (118, "Almirante Brown",     "Glew",                   "BA",  5,  500_000_00, "Estadio Carlos Jara",                 16000, 1500, "4-4-2",   "DEFENSIVO",    64),
    (119, "Patronato",           "Parana",                 "ER",  5,  530_000_00, "Estadio Presbitero Bartolome",        22000, 1500, "4-4-2",   "EQUILIBRADO",  65),
    (120, "Nueva Chicago",       "Buenos Aires",           "BA",  4,  380_000_00, "Estadio Parque Donaldson",            21000, 1200, "4-4-2",   "EQUILIBRADO",  58),
    (121, "Atlanta",             "Buenos Aires",           "BA",  4,  350_000_00, "Estadio Vila Crespo",                 20000, 1200, "4-3-3",   "OFENSIVO",     55),
    (122, "Defensores Belgrano", "Buenos Aires",           "BA",  4,  320_000_00, "Estadio Lisandro de la Torre",        20000, 1000, "4-4-2",   "DEFENSIVO",    54),
    (123, "Deportivo Moron",     "Moron",                  "BA",  4,  300_000_00, "Estadio Nuevo Francisco Urbano",      22000, 1000, "4-4-2",   "EQUILIBRADO",  55),
    (124, "San Telmo",           "Buenos Aires",           "BA",  4,  280_000_00, "Estadio Manuel Neto Requejo",         16000, 1000, "4-4-2",   "DEFENSIVO",    52),
    (125, "Temperley",           "Temperley",              "BA",  4,  270_000_00, "Estadio Alfredo Schettini",           15000, 1000, "4-4-2",   "DEFENSIVO",    52),
    (126, "Deportivo Maipu",     "Mendoza",                "MZA", 4,  280_000_00, "Estadio Victor Legrotaglie",          15000, 1000, "4-4-2",   "EQUILIBRADO",  54),
    (127, "Atletico Rafaela",    "Rafaela",                "SF",  4,  260_000_00, "Estadio Neno Mattia",                 18000, 1000, "4-4-2",   "EQUILIBRADO",  53),
    (128, "Racing Cordoba",      "Cordoba",                "CBA", 4,  250_000_00, "Estadio Cordoba",                     12000, 1000, "4-4-2",   "EQUILIBRADO",  52),
    (129, "Estudiantes BA",      "Buenos Aires",           "BA",  4,  240_000_00, "Estadio Estudiantes",                 18000, 1000, "4-4-2",   "EQUILIBRADO",  50),
    (130, "Gimnasia Jujuy",      "San Salvador de Jujuy",  "JUJ", 4,  230_000_00, "Estadio 23 de Agosto",                20000, 1000, "4-4-2",   "EQUILIBRADO",  52),
    (131, "Agropecuario",        "Carlos Casares",         "BA",  4,  200_000_00, "Estadio Agropecuario",                10000,  800, "4-4-2",   "DEFENSIVO",    48),
    (132, "Deportivo Madryn",    "Puerto Madryn",          "CHU", 4,  200_000_00, "Estadio Mario Bocchio",                8000,  800, "4-4-2",   "DEFENSIVO",    48),
    (133, "Central Norte",       "Salta",                  "SAL", 4,  190_000_00, "Estadio Arturo Alvarez",              18000,  800, "4-4-2",   "EQUILIBRADO",  48),
    (134, "Gimnasia Tiro",       "Salta",                  "SAL", 4,  180_000_00, "Estadio El Gigante del Norte",        10000,  800, "4-4-2",   "EQUILIBRADO",  45),
    (135, "Acassuso",            "Buenos Aires",           "BA",  4,  150_000_00, "Estadio Acassuso",                     6000,  800, "4-4-2",   "DEFENSIVO",    42),
    (136, "Chaco For Ever",      "Resistencia",            "CHA", 4,  170_000_00, "Estadio Carlos A. Mercado",           20000,  800, "4-4-2",   "EQUILIBRADO",  48),
    (137, "Colegiales",          "Buenos Aires",           "BA",  4,  160_000_00, "Estadio de Colegiales",               10000,  800, "4-4-2",   "DEFENSIVO",    45),
    (138, "Ciudad de Bolivar",   "Bolivar",                "BA",  3,  120_000_00, "Estadio Ciudad de Bolivar",            8000,  500, "4-4-2",   "DEFENSIVO",    38),
    (139, "Guemes",              "Santiago del Estero",    "SGO", 3,  120_000_00, "Estadio Juan A. Garcia",              15000,  500, "4-4-2",   "DEFENSIVO",    38),
    (140, "Mitre Santiago",      "Santiago del Estero",    "SGO", 3,  110_000_00, "Estadio Mitre",                       12000,  500, "4-4-2",   "DEFENSIVO",    36),
]

assert len(TIMES) == 30, f"Expected 30 teams, got {len(TIMES)}"
assert len(POSICOES) == 22, f"Expected 22 positions, got {len(POSICOES)}"

# ─────────────────── Build Argentine B seed ───────────────────────────────────
data: dict = {"times": [], "jogadores": []}
jogador_id = 2390
jogador_idx = 0

for t in TIMES:
    tid, tnome, tcidade, testado, tnivel, tsaldo, testadio_nome, testadio_cap, tpreco, ttatica, testilo, trep = t

    data["times"].append({
        "id": tid,
        "nome": tnome,
        "cidade": tcidade,
        "estado": testado,
        "divisao": "B",
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
        idade = r.randint(19, 32)
        contrato = r.randint(1, 3)
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
assert data["jogadores"][0]["id"] == 2390
assert data["jogadores"][-1]["id"] == 3049

# ─────────────────── Write Argentine B seed ───────────────────────────────────
assets_dir = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
arg2_path = os.path.join(assets_dir, "seed_argentina2.json")
with open(arg2_path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"✓ Wrote {arg2_path}")
print(f"  Teams: {len(data['times'])}, Players: {len(data['jogadores'])}")
print(f"  Player IDs: {data['jogadores'][0]['id']} → {data['jogadores'][-1]['id']}")
