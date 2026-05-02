"""
Generates seed_uruguay.json with 16 Primera División Uruguaia + 12 Segunda División clubs,
22 players each.
"""
import json
import random
import os

# ─────────────────── Name pools ──────────────────────────────────────────────
PRIMEIROS = [
    "Carlos", "Diego", "Pablo", "Nicolás", "Matías", "Sebastián", "Lucas",
    "Federico", "Rodrigo", "Gastón", "Leandro", "Gonzalo", "Tomás", "Ramiro",
    "Emiliano", "Agustín", "Facundo", "Lautaro", "Nahuel", "Marcelo",
    "Esteban", "Hernán", "Sergio", "Martín", "Cristian", "Javier", "Daniel",
    "Fernando", "Santiago", "Iván", "Andrés", "Jorge", "Ricardo", "Oscar",
    "Gustavo", "Marco", "Alejandro", "Gerardo", "Claudio", "Raúl",
    "Nelson", "Walter", "Néstor", "Damián", "Leonardo", "Ignacio", "Maximiliano",
    "Ezequiel", "Franco", "Juan",
]
SOBRENOMES = [
    "González", "Rodríguez", "García", "López", "Martínez", "Pérez",
    "Fernández", "Romero", "Álvarez", "Díaz", "Sánchez", "Morales", "Castro",
    "Ramírez", "Torres", "Ruiz", "Rojas", "Herrera", "Medina", "Flores",
    "Da Silva", "Pereira", "Suárez", "Cavani", "Godín", "Bentancur",
    "Valverde", "Núñez", "Arrascaeta", "Torreira", "Fariña", "Laxalt",
    "Stuani", "Piquerez", "Cáceres", "Urruticoechea", "Cubilla", "Miguez",
    "Silveira", "Barreto", "Pandiani", "Forlan", "Recoba", "Ostolaza",
    "Saralegui", "Coelho", "Espino", "Aguirregaray", "Teixeira", "Nández",
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

assert len(POSICOES) == 22

# ─────────────────── Teams data ──────────────────────────────────────────────
# (id, nome, cidade, nivel, saldo, estadio_nome, cap, preco, tatica, estilo, rep, divisao_int)
# divisao_int: 9 = Primera División, 10 = Segunda División
TIMES = [
    # ── Primera División (16 clubs, divisao=9) ───────────────────────────
    (141, "Nacional",              "Montevideo",   9, 2_800_000_00, "Gran Parque Central",           25000, 4500, "4-3-3",   "OFENSIVO",      91, 9),
    (142, "Peñarol",               "Montevideo",   9, 3_000_000_00, "Estadio Campeón del Siglo",     40000, 5000, "4-4-2",   "EQUILIBRADO",   93, 9),
    (143, "Defensor Sporting",     "Montevideo",   7, 1_200_000_00, "Estadio Luis Franzini",         18000, 3000, "4-3-3",   "DEFENSIVO",     77, 9),
    (144, "Danubio",               "Montevideo",   6, 1_000_000_00, "Estadio Jardines del Hipódromo",12000, 2500, "4-4-2",   "EQUILIBRADO",   74, 9),
    (145, "Liverpool FC",          "Montevideo",   6,   950_000_00, "Estadio Belvedere",             12000, 2500, "4-4-2",   "DEFENSIVO",     72, 9),
    (148, "Cerro Largo",           "Melo",         5,   700_000_00, "Estadio Fútbol Club",           12000, 1800, "4-4-2",   "CONTRA_ATAQUE", 67, 9),
    (149, "Boston River",          "Montevideo",   5,   750_000_00, "Estadio Frazoni",                8000, 1800, "4-3-3",   "OFENSIVO",      68, 9),
    (150, "Montevideo Wanderers",  "Montevideo",   5,   700_000_00, "Estadio Vito Pereira",           8000, 1800, "4-4-2",   "EQUILIBRADO",   67, 9),
    (151, "Central Español",       "Montevideo",   5,   640_000_00, "Estadio Parque Tecnológico",     6000, 1500, "4-4-2",   "EQUILIBRADO",   65, 9),
    (153, "Racing",                "Montevideo",   5,   680_000_00, "Estadio Parque Uruguay",         8000, 1800, "4-4-2",   "EQUILIBRADO",   66, 9),
    (154, "Progreso",              "Montevideo",   5,   620_000_00, "Estadio José Nasazzi",           6000, 1500, "4-4-2",   "DEFENSIVO",     64, 9),
    (155, "Torque",                "Montevideo",   5,   650_000_00, "Estadio Parque Franzini",        7000, 1500, "4-3-3",   "CONTRA_ATAQUE", 65, 9),
    (156, "Cerro",                 "Montevideo",   5,   600_000_00, "Estadio Camino Carrasco",        8000, 1500, "4-4-2",   "DEFENSIVO",     64, 9),
    (164, "Juventud",              "Las Piedras",  5,   620_000_00, "Estadio Carlos Ceriani",         6000, 1500, "4-4-2",   "DEFENSIVO",     63, 9),
    (167, "Albion Football Club",  "Montevideo",   5,   580_000_00, "Estadio Parque Roosevelt",       3500, 1500, "4-4-2",   "DEFENSIVO",     62, 9),
    (168, "Deportivo Maldonado",   "Maldonado",    5,   620_000_00, "Estadio Domingo Burgueño",       8000, 1600, "4-4-2",   "EQUILIBRADO",   64, 9),

    # ── Segunda División (12 clubs, divisao=10) ───────────────────────────
    (146, "Fenix",              "Montevideo",   4,   400_000_00, "Estadio Parque Capurro",        10000, 1000, "4-3-3",   "EQUILIBRADO",   60, 10),
    (147, "Plaza Colonia",      "Colonia",      4,   380_000_00, "Estadio Plaza Colonia",         10000, 1000, "4-4-2",   "DEFENSIVO",     59, 10),
    (152, "River Plate UY",     "Montevideo",   4,   400_000_00, "Estadio Saroldi",                8000, 1000, "4-3-3",   "OFENSIVO",      59, 10),
    (157, "Miramar Misiones",   "Montevideo",   4,   400_000_00, "Estadio Parque Palermo",         6000, 1000, "4-4-2",   "DEFENSIVO",     58, 10),
    (158, "Rampla Juniors",     "Montevideo",   4,   420_000_00, "Estadio Parque Palermo 2",       5000, 1000, "4-4-2",   "EQUILIBRADO",   59, 10),
    (159, "Rentistas",          "Montevideo",   4,   450_000_00, "Estadio Saroldi 2",              5000, 1000, "4-3-3",   "OFENSIVO",      60, 10),
    (160, "Rocha FC",           "Rocha",        4,   380_000_00, "Estadio Parque Artigas",         5000, 1000, "4-4-2",   "DEFENSIVO",     57, 10),
    (161, "Sud América",        "Montevideo",   4,   390_000_00, "Estadio Parque Melilla",         4000, 1000, "4-4-2",   "EQUILIBRADO",   58, 10),
    (162, "Villa Española",     "Montevideo",   4,   370_000_00, "Estadio Luis Trochón",           5000,  900, "4-4-2",   "DEFENSIVO",     56, 10),
    (163, "Montevideo City",    "Montevideo",   4,   420_000_00, "Estadio Las Vegas",              5000, 1000, "4-3-3",   "OFENSIVO",      59, 10),
    (165, "Huracán Buceo",      "Montevideo",   4,   350_000_00, "Estadio Parque Ansina",          3000,  800, "4-4-2",   "DEFENSIVO",     55, 10),
    (166, "Fénix B",            "Montevideo",   4,   350_000_00, "Estadio Parque Capurro 2",       3000,  800, "4-4-2",   "EQUILIBRADO",   55, 10),
    (169, "Atenas FC",          "Nueva Helvecia",4,  340_000_00, "Estadio Parque Atenas",          3000,  800, "4-4-2",   "DEFENSIVO",     54, 10),
    (170, "Palermo FC",         "Montevideo",   4,   330_000_00, "Estadio Palermo",                3000,  800, "4-4-2",   "EQUILIBRADO",   53, 10),
]

assert len(TIMES) == 30, f"Expected 30 teams, got {len(TIMES)}"

# ─────────────────── Build seed ───────────────────────────────────────────────
data: dict = {"times": [], "jogadores": []}
jogador_id = 3165
jogador_idx = 0

for t in TIMES:
    tid, tnome, tcidade, tnivel, tsaldo, testadio_nome, testadio_cap, tpreco, ttatica, testilo, trep, tdivisao = t

    data["times"].append({
        "id": tid,
        "nome": tnome,
        "cidade": tcidade,
        "estado": "MVD",
        "divisao": tdivisao,
        "pais": "Uruguay",
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
        contrato = r.randint(1, 3) if tnivel <= 5 else r.randint(2, 5)
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
            "valor_mercado": val,
            "contrato": contrato,
            "nivel": tnivel,
        })
        jogador_id += 1
        jogador_idx += 1

# ─────────────────── Write output ─────────────────────────────────────────────
out_dir = os.path.dirname(os.path.abspath(__file__))
out_path = os.path.join(out_dir, "..", "app", "src", "main", "assets", "seed_uruguay.json")
os.makedirs(os.path.dirname(out_path), exist_ok=True)

with open(out_path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"Written {len(data['times'])} teams and {len(data['jogadores'])} players to {out_path}")
print(f"  Primera División: {sum(1 for t in TIMES if t[11] == 9)} clubs")
print(f"  Segunda División: {sum(1 for t in TIMES if t[11] == 10)} clubs")
