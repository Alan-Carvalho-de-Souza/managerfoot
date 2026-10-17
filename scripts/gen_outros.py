# -*- coding: utf-8 -*-
"""
gen_outros.py
Gera seed_outros.json com clubes do pool "Outros times" — clubes que NÃO
disputam uma liga nacional modelada no jogo, mas existem com sua nacionalidade
e reputação (para, futuramente, disputar torneios intercontinentais).

Códigos de divisão reservados para o pool (fora de todas as ligas nacionais,
que usam 1..14):
    90 = Outros — América do Sul (CONMEBOL)
    91 = Outros — Europa (UEFA)
    92 = Outros — resto do mundo (CONCACAF / Ásia / África / Oceania)

Mesmo pipeline dos demais gen_*: converte o "overall" nos 7 atributos do jogo.
Faixas de id reservadas: times 601+, jogadores 13000+.

Uso: python scripts/gen_outros.py
"""
import json
import os
import random

# ─────────────────── Metadados dos clubes ─────────────────────────────────────
# (id, nome, cidade, estado, divisao, pais, nivel, saldo(centavos), estadio, cap,
#  preco(centavos), tatica, estilo, reputacao)
TIMES = [
    (601, "Inter Miami", "Miami", "FL", 92, "Estados Unidos", 7, 40_000_000_00,
     "Chase Stadium", 21550, 5000, "4-3-3", "OFENSIVO", 72),
]

# ─────────────────── Derivação dos 7 atributos a partir do overall ────────────
STAT_KEYS = ["forca", "tecnica", "passe", "velocidade", "finalizacao", "defesa", "fisico"]

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

def gen_stats(overall, pos, seed):
    r = random.Random(seed)
    deltas = DELTAS.get(pos, {})
    stats = {}
    for k in STAT_KEYS:
        base = overall + deltas.get(k, 0) + r.randint(-2, 2)
        stats[k] = max(1, min(99, base))
    return stats

def gen_salario_valor(overall, seed):
    r = random.Random(seed + 7777)
    if overall >= 88:
        sal = (500_000_00, 1_200_000_00); val = (100_000_000_00, 200_000_000_00)
    elif overall >= 84:
        sal = (280_000_00, 550_000_00);   val = (55_000_000_00, 120_000_000_00)
    elif overall >= 80:
        sal = (160_000_00, 320_000_00);   val = (28_000_000_00, 65_000_000_00)
    elif overall >= 75:
        sal = (90_000_00, 180_000_00);    val = (12_000_000_00, 32_000_000_00)
    elif overall >= 70:
        sal = (50_000_00, 110_000_00);    val = (5_500_000_00, 16_000_000_00)
    elif overall >= 65:
        sal = (28_000_00, 65_000_00);     val = (2_500_000_00, 8_000_000_00)
    else:
        sal = (15_000_00, 40_000_00);     val = (800_000_00, 3_500_000_00)
    return r.randint(*sal), r.randint(*val)

def abrev(nome):
    parts = nome.split()
    if len(parts) >= 2:
        return f"{parts[0][0]}. {parts[-1]}"[:15]
    return nome[:15]

# ─────────────────── Elencos reais 2025 ───────────────────────────────────────
# Formato: (nome, posicao, idade, nacionalidade, overall)
ROSTERS = {
601: [  # Inter Miami (MLS 2025)
    ("Oscar Ustari",        "GOLEIRO",          39, "Argentina",      76),
    ("Drake Callender",     "GOLEIRO",          27, "Estados Unidos", 74),
    ("Rocco Rios Novo",     "GOLEIRO",          22, "Argentina",      67),
    ("Jordi Alba",          "LATERAL_ESQUERDO", 36, "Espanha",        82),
    ("Marcelo Weigandt",    "LATERAL_DIREITO",  25, "Argentina",      73),
    ("Maximiliano Falcon",  "ZAGUEIRO",         28, "Uruguai",        74),
    ("Tomas Aviles",        "ZAGUEIRO",         21, "Argentina",      74),
    ("Gonzalo Lujan",       "LATERAL_ESQUERDO", 24, "Argentina",      70),
    ("Noah Allen",          "LATERAL_ESQUERDO", 21, "Estados Unidos", 69),
    ("Ian Fray",            "LATERAL_DIREITO",  23, "Estados Unidos", 68),
    ("Ryan Sailor",         "ZAGUEIRO",         25, "Estados Unidos", 67),
    ("Sergio Busquets",     "VOLANTE",          37, "Espanha",        82),
    ("Federico Redondo",    "VOLANTE",          22, "Argentina",      75),
    ("Telasco Segovia",     "MEIA_CENTRAL",     22, "Venezuela",      74),
    ("Benjamin Cremaschi",  "MEIA_CENTRAL",     20, "Estados Unidos", 72),
    ("Baltasar Rodriguez",  "MEIA_ATACANTE",    22, "Argentina",      73),
    ("Yannick Bright",      "VOLANTE",          23, "Italia",         69),
    ("Lionel Messi",        "SEGUNDA_ATACANTE", 38, "Argentina",      90),
    ("Luis Suarez",         "CENTROAVANTE",     38, "Uruguai",        79),
    ("Tadeo Allende",       "PONTA_ESQUERDA",   26, "Argentina",      74),
    ("Robert Taylor",       "PONTA_DIREITA",    30, "Finlandia",      72),
    ("Leonardo Campana",    "CENTROAVANTE",     25, "Equador",        73),
    ("Fafa Picault",        "PONTA_ESQUERDA",   34, "Haiti",          70),
],
}

# ─────────────────── Validação e monta seed ───────────────────────────────────
VALID_POS = set(DELTAS.keys())
data = {"times": [], "jogadores": []}
jogador_id = 13000

for (tid, tnome, tcidade, testado, tdiv, tpais, tnivel, tsaldo, testadio, tcap, tpreco, ttatica, testilo, trep) in TIMES:
    data["times"].append({
        "id": tid, "nome": tnome, "cidade": tcidade, "estado": testado,
        "divisao": tdiv, "pais": tpais, "nivel": tnivel, "saldo": tsaldo,
        "estadio_nome": testadio, "estadio_capacidade": tcap, "preco_ingresso": tpreco,
        "tatica": ttatica, "estilo": testilo, "reputacao": trep, "escudo_res": "",
    })

    roster = ROSTERS.get(tid, [])
    if not roster:
        raise SystemExit(f"Roster ausente para time id {tid} ({tnome})")

    for (nome, pos, idade, nac, overall) in roster:
        if pos not in VALID_POS:
            raise SystemExit(f"Posicao invalida ({pos}) em {tnome}: {nome}")
        attrs = gen_stats(overall, pos, jogador_id)
        sal, val = gen_salario_valor(overall, jogador_id)
        r = random.Random(jogador_id + 31)
        contrato = r.randint(2, 5) if overall >= 74 else r.randint(1, 4)
        data["jogadores"].append({
            "id": jogador_id, "time_id": tid, "nome": nome,
            "nome_abrev": abrev(nome), "idade": int(idade), "posicao": pos,
            "nacionalidade": nac,
            "forca": attrs["forca"], "tecnica": attrs["tecnica"], "passe": attrs["passe"],
            "velocidade": attrs["velocidade"], "finalizacao": attrs["finalizacao"],
            "defesa": attrs["defesa"], "fisico": attrs["fisico"],
            "salario": sal, "valor_mercado": val, "contrato": contrato, "nivel": tnivel,
        })
        jogador_id += 1

# ─────────────────── Escreve o arquivo ────────────────────────────────────────
out_path = os.path.join("app", "src", "main", "assets", "seed_outros.json")
os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

por_time = {}
for j in data["jogadores"]:
    por_time[j["time_id"]] = por_time.get(j["time_id"], 0) + 1
print(f"Escrito {len(data['times'])} clubes e {len(data['jogadores'])} jogadores em {out_path}")
menores = [tid for tid, n in por_time.items() if n < 16]
if menores:
    print(f"  AVISO: clubes com <16 jogadores: {menores}")
