# -*- coding: utf-8 -*-
"""
gen_championship.py
Gera seed_championship.json com os 24 clubes da EFL Championship 2026/27
(divisao=12, pais="Inglaterra") e elencos SINTETICOS calibrados por nivel.

Diferente do gen_premier.py (que consome elencos reais pesquisados), aqui
os jogadores sao gerados proceduralmente porque nao ha lista real facilmente
disponivel dos ~600 jogadores da segunda divisao inglesa. A forca de cada
jogador segue o nivel do time (dispersa ~+-6 pontos) e as posicoes preenchem
uma formacao 4-4-2 titular + banco.

Uso: python scripts/gen_championship.py
"""
import json
import os
import random

# --- Metadados dos 24 clubes (ids 421-444) --------------------------------
# (id, nome, cidade, estado, nivel, saldo(centavos), estadio, cap, preco, tatica, estilo, reputacao)
TIMES = [
    (421, "Wolverhampton Wanderers", "Wolverhampton",    "ENG", 8,  35_000_000_00, "Molineux Stadium",                31750, 5500, "3-4-3",   "OFENSIVO",     73),
    (422, "Southampton",             "Southampton",      "ENG", 8,  32_000_000_00, "St Mary's Stadium",               32384, 5500, "4-2-3-1", "EQUILIBRADO",  72),
    (423, "West Ham United",         "Londres",          "ENG", 8,  30_000_000_00, "London Stadium",                  62500, 6000, "4-2-3-1", "EQUILIBRADO",  74),
    (424, "Burnley",                 "Burnley",          "ENG", 7,  22_000_000_00, "Turf Moor",                       21944, 4500, "4-4-2",   "DEFENSIVO",    68),
    (425, "Sheffield United",        "Sheffield",        "ENG", 7,  20_000_000_00, "Bramall Lane",                    32050, 4500, "4-2-3-1", "EQUILIBRADO",  67),
    (426, "Middlesbrough",           "Middlesbrough",    "ENG", 7,  18_000_000_00, "Riverside Stadium",               34742, 4200, "4-3-3",   "EQUILIBRADO",  66),
    (427, "Norwich City",            "Norwich",          "ENG", 7,  17_000_000_00, "Carrow Road",                     27359, 4200, "4-3-3",   "OFENSIVO",     66),
    (428, "Birmingham City",         "Birmingham",       "ENG", 6,  16_000_000_00, "St Andrew's",                     29409, 4000, "4-4-2",   "EQUILIBRADO",  63),
    (429, "West Bromwich Albion",    "West Bromwich",    "ENG", 6,  15_000_000_00, "The Hawthorns",                   26688, 4000, "4-4-2",   "EQUILIBRADO",  62),
    (430, "Swansea City",            "Swansea",          "ENG", 6,  14_000_000_00, "Swansea.com Stadium",             21088, 4000, "4-3-3",   "OFENSIVO",     62),
    (431, "Watford",                 "Watford",          "ENG", 6,  14_000_000_00, "Vicarage Road",                   22200, 4000, "4-4-2",   "EQUILIBRADO",  62),
    (432, "Stoke City",              "Stoke-on-Trent",   "ENG", 6,  14_000_000_00, "bet365 Stadium",                  30089, 3800, "4-4-2",   "DEFENSIVO",    61),
    (433, "Derby County",            "Derby",            "ENG", 6,  13_000_000_00, "Pride Park Stadium",              32956, 3800, "4-4-2",   "EQUILIBRADO",  61),
    (434, "Queens Park Rangers",     "Londres",          "ENG", 6,  12_000_000_00, "Loftus Road",                     18439, 3800, "4-3-3",   "EQUILIBRADO",  60),
    (435, "Cardiff City",            "Cardiff",          "ENG", 6,  12_000_000_00, "Cardiff City Stadium",            33280, 3800, "4-4-2",   "DEFENSIVO",    60),
    (436, "Bristol City",            "Bristol",          "ENG", 6,  11_000_000_00, "Ashton Gate",                     27000, 3600, "4-4-2",   "EQUILIBRADO",  58),
    (437, "Blackburn Rovers",        "Blackburn",        "ENG", 6,  10_000_000_00, "Ewood Park",                      31367, 3500, "4-4-2",   "EQUILIBRADO",  58),
    (438, "Preston North End",       "Preston",          "ENG", 6,  10_000_000_00, "Deepdale",                        23404, 3500, "4-4-2",   "DEFENSIVO",    58),
    (439, "Millwall",                "Londres",          "ENG", 6,   9_000_000_00, "The Den",                         20146, 3500, "4-4-2",   "DEFENSIVO",    57),
    (440, "Charlton Athletic",       "Londres",          "ENG", 5,   8_000_000_00, "The Valley",                      27111, 3200, "4-4-2",   "EQUILIBRADO",  54),
    (441, "Portsmouth",              "Portsmouth",       "ENG", 5,   7_500_000_00, "Fratton Park",                    20899, 3200, "4-4-2",   "EQUILIBRADO",  54),
    (442, "Bolton Wanderers",        "Bolton",           "ENG", 5,   7_000_000_00, "Toughsheet Community Stadium",    28723, 3000, "4-4-2",   "DEFENSIVO",    53),
    (443, "Wrexham",                 "Wrexham",          "ENG", 5,   7_000_000_00, "SToK Cae Ras",                    13341, 3000, "4-3-3",   "OFENSIVO",     52),
    (444, "Lincoln City",            "Lincoln",          "ENG", 5,   6_000_000_00, "Sincil Bank",                     10120, 2800, "4-4-2",   "DEFENSIVO",    50),
]

# Formacao padrao do elenco titular (11 jogadores)
POSICOES_TITULARES = [
    "GOLEIRO",
    "LATERAL_DIREITO", "ZAGUEIRO", "ZAGUEIRO", "LATERAL_ESQUERDO",
    "VOLANTE", "VOLANTE", "MEIA_ATACANTE", "MEIA_ATACANTE",
    "CENTROAVANTE", "CENTROAVANTE",
]
# Banco de 11 reservas (composicao balanceada)
POSICOES_RESERVAS = [
    "GOLEIRO",
    "ZAGUEIRO", "LATERAL_DIREITO", "LATERAL_ESQUERDO",
    "VOLANTE", "MEIA_CENTRAL", "MEIA_ATACANTE",
    "PONTA_DIREITA", "PONTA_ESQUERDA",
    "CENTROAVANTE", "SEGUNDA_ATACANTE",
]

# Delta absoluto por posicao aplicado ao overall (mesmo padrao do gen_premier.py)
DELTAS = {
    "GOLEIRO":          {"defesa": 6,  "fisico": 2,  "passe": -6,  "velocidade": -12, "finalizacao": -32, "tecnica": -10},
    "ZAGUEIRO":         {"defesa": 6,  "fisico": 5,  "forca": 4,   "finalizacao": -16, "tecnica": -4,  "velocidade": -2},
    "LATERAL_DIREITO":  {"defesa": 2,  "velocidade": 6, "fisico": 2, "finalizacao": -8, "tecnica": -1},
    "LATERAL_ESQUERDO": {"defesa": 2,  "velocidade": 6, "fisico": 2, "finalizacao": -8, "tecnica": -1},
    "VOLANTE":          {"defesa": 5,  "passe": 3,   "fisico": 3,  "finalizacao": -8, "velocidade": -2},
    "MEIA_CENTRAL":     {"passe": 5,   "tecnica": 4, "finalizacao": -3, "defesa": -2},
    "MEIA_ATACANTE":    {"tecnica": 6, "passe": 4,   "finalizacao": 2,  "defesa": -8,  "fisico": -2},
    "PONTA_DIREITA":    {"velocidade": 7, "tecnica": 4, "finalizacao": 2, "defesa": -12, "fisico": -2},
    "PONTA_ESQUERDA":   {"velocidade": 7, "tecnica": 4, "finalizacao": 2, "defesa": -12, "fisico": -2},
    "CENTROAVANTE":     {"finalizacao": 8, "fisico": 4, "forca": 3, "defesa": -16, "passe": -4},
    "SEGUNDA_ATACANTE": {"finalizacao": 5, "tecnica": 4, "velocidade": 3, "defesa": -12, "passe": -1},
}

STAT_KEYS = ["forca", "tecnica", "passe", "velocidade", "finalizacao", "defesa", "fisico"]

# Nomes/sobrenomes ingleses para gerar jogadores fake (~1000 combinacoes)
NOMES = [
    "James","Harry","Jack","Thomas","Oliver","Jamie","Callum","George","Charlie","Alex",
    "Daniel","Tom","Ben","Kyle","Marcus","Luke","Reece","Aaron","Kieran","Mason",
    "William","Ethan","Jacob","Ryan","Adam","Josh","Lewis","Jordan","Sam","Nathan",
    "Connor","Tyler","Dylan","Owen","Louis","Isaac","Freddie","Archie","Alfie","Leo",
    "Max","Henry","Toby","Josh","Cameron","Elliot","Finn","Rory","Sean","Kai",
]
SOBRENOMES = [
    "Smith","Jones","Taylor","Brown","Williams","Wilson","Johnson","Davies","Robinson","Wright",
    "Walker","Roberts","Green","Hall","Wood","Harris","Clark","Baker","Cooper","Bennett",
    "Turner","Hill","Ward","Martin","Evans","White","Thompson","King","Lee","Allen",
    "Young","Scott","Adams","Mitchell","Carter","Phillips","Watson","Morris","Parker","Stone",
    "Marshall","Hunter","Fletcher","Grant","Powell","Cole","Hughes","Wells","Chapman","Palmer",
]

def gen_nome(rng):
    n = rng.choice(NOMES)
    s = rng.choice(SOBRENOMES)
    return f"{n} {s}", f"{n[0]}. {s}"[:15]

def gen_stats(overall, pos, seed):
    rng = random.Random(seed)
    deltas = DELTAS.get(pos, {})
    return {k: max(1, min(99, overall + deltas.get(k, 0) + rng.randint(-2, 2))) for k in STAT_KEYS}

def gen_salario_valor(overall, seed):
    rng = random.Random(seed + 7777)
    if overall >= 80:
        sal = (180_000_00, 350_000_00); val = (35_000_000_00, 80_000_000_00)
    elif overall >= 75:
        sal = (100_000_00, 200_000_00); val = (15_000_000_00, 40_000_000_00)
    elif overall >= 70:
        sal = (55_000_00, 130_000_00);  val = (7_000_000_00, 20_000_000_00)
    elif overall >= 65:
        sal = (30_000_00, 80_000_00);   val = (3_000_000_00, 12_000_000_00)
    elif overall >= 60:
        sal = (18_000_00, 45_000_00);   val = (1_200_000_00, 5_000_000_00)
    else:
        sal = (10_000_00, 28_000_00);   val = (500_000_00, 2_500_000_00)
    return rng.randint(*sal), rng.randint(*val)

# --- Monta seed ------------------------------------------------------------
data = {"times": [], "jogadores": []}
jogador_id = 9000  # começa em 9000 (Premier usa 8000+)

for (tid, tnome, tcidade, testado, tnivel, tsaldo, testadio, tcap, tpreco, ttatica, testilo, trep) in TIMES:
    data["times"].append({
        "id": tid, "nome": tnome, "cidade": tcidade, "estado": testado,
        "divisao": 12, "pais": "Inglaterra", "nivel": tnivel, "saldo": tsaldo,
        "estadio_nome": testadio, "estadio_capacidade": tcap, "preco_ingresso": tpreco,
        "tatica": ttatica, "estilo": testilo, "reputacao": trep, "escudo_res": "",
    })

    # Overall base do time deriva do nivel (nivel 8 -> ~72; nivel 5 -> ~55).
    # Cada jogador desvia ate +-6 do overall base, com titulares mais fortes que reservas.
    base_overall = 48 + tnivel * 3  # nivel 5 -> 63 | 6 -> 66 | 7 -> 69 | 8 -> 72
    rng_time = random.Random(tid)

    for i, pos in enumerate(POSICOES_TITULARES):
        # Titulares desviam +/- 4 do overall base
        overall = max(45, min(85, base_overall + rng_time.randint(-4, 4)))
        idade = rng_time.randint(19, 32)
        nome, nome_abrev = gen_nome(rng_time)
        attrs = gen_stats(overall, pos, jogador_id)
        sal, val = gen_salario_valor(overall, jogador_id)
        contrato = rng_time.randint(2, 4)
        data["jogadores"].append({
            "id": jogador_id, "time_id": tid, "nome": nome,
            "nome_abrev": nome_abrev, "idade": idade, "posicao": pos,
            "nacionalidade": "Ingles",
            "forca": attrs["forca"], "tecnica": attrs["tecnica"], "passe": attrs["passe"],
            "velocidade": attrs["velocidade"], "finalizacao": attrs["finalizacao"],
            "defesa": attrs["defesa"], "fisico": attrs["fisico"],
            "salario": sal, "valor_mercado": val, "contrato": contrato, "nivel": tnivel,
        })
        jogador_id += 1

    for pos in POSICOES_RESERVAS:
        # Reservas desviam -6 a +2 do overall base (piores que os titulares)
        overall = max(42, min(80, base_overall + rng_time.randint(-6, 2)))
        idade = rng_time.randint(18, 33)
        nome, nome_abrev = gen_nome(rng_time)
        attrs = gen_stats(overall, pos, jogador_id)
        sal, val = gen_salario_valor(overall, jogador_id)
        contrato = rng_time.randint(1, 4)
        data["jogadores"].append({
            "id": jogador_id, "time_id": tid, "nome": nome,
            "nome_abrev": nome_abrev, "idade": idade, "posicao": pos,
            "nacionalidade": "Ingles",
            "forca": attrs["forca"], "tecnica": attrs["tecnica"], "passe": attrs["passe"],
            "velocidade": attrs["velocidade"], "finalizacao": attrs["finalizacao"],
            "defesa": attrs["defesa"], "fisico": attrs["fisico"],
            "salario": sal, "valor_mercado": val, "contrato": contrato, "nivel": tnivel,
        })
        jogador_id += 1

# --- Escreve arquivo -------------------------------------------------------
out_path = os.path.join("app", "src", "main", "assets", "seed_championship.json")
os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

por_time = {}
for j in data["jogadores"]:
    por_time[j["time_id"]] = por_time.get(j["time_id"], 0) + 1
print(f"Escrito {len(data['times'])} clubes e {len(data['jogadores'])} jogadores em {out_path}")
if any(n < 16 for n in por_time.values()):
    print(f"  AVISO: times com <16 jogadores: {[t for t,n in por_time.items() if n<16]}")
