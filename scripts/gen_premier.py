"""
gen_premier.py
Gera seed_premier.json com os 20 clubes da Premier League 2026/27 (divisao=11,
pais="Inglaterra") e seus elencos REAIS (plantel ~20/08/2026).

Os elencos (nome, posicao, idade, nacionalidade, overall) vêm dos arquivos
premier_group_{A..E}.json pesquisados na web. Este script converte o "overall"
de cada jogador nos 7 atributos do jogo (forca/tecnica/passe/velocidade/
finalizacao/defesa/fisico) aplicando ajustes por posição, e deriva salário,
valor de mercado e abreviação de nome.

Uso: python scripts/gen_premier.py
(os arquivos premier_group_*.json devem estar no diretório passado em GROUPS_DIR)
"""
import json
import os
import random

# ─────────────────── Localização dos elencos pesquisados ──────────────────────
GROUPS_DIR = os.environ.get(
    "PREMIER_GROUPS_DIR",
    r"C:\Users\Alan Carvalho\AppData\Local\Temp\claude\C--Dev-ManagerFoot\62384b41-d963-49c2-a07a-a38bceadfefc\scratchpad",
)
GROUP_FILES = [f"premier_group_{g}.json" for g in ("A", "B", "C", "D", "E")]

# ─────────────────── Metadados dos 20 clubes (id 401-420) ─────────────────────
# (id, nome, cidade, estado, nivel, saldo(centavos), estadio, cap, preco, tatica, estilo, reputacao)
TIMES = [
    (401, "Manchester City",         "Manchester",          "ENG", 10, 150_000_000_00, "Etihad Stadium",                    61038, 9000, "4-3-3",   "OFENSIVO",     95),
    (402, "Liverpool",               "Liverpool",           "ENG", 10, 140_000_000_00, "Anfield",                           61276, 9000, "4-3-3",   "OFENSIVO",     94),
    (403, "Arsenal",                 "Londres",             "ENG", 10, 130_000_000_00, "Emirates Stadium",                  60704, 9000, "4-3-3",   "EQUILIBRADO",  93),
    (404, "Chelsea",                 "Londres",             "ENG",  9, 120_000_000_00, "Stamford Bridge",                   40044, 8500, "4-2-3-1", "OFENSIVO",     88),
    (405, "Manchester United",       "Manchester",          "ENG",  9, 120_000_000_00, "Old Trafford",                      74158, 8500, "4-2-3-1", "EQUILIBRADO",  88),
    (406, "Newcastle United",        "Newcastle upon Tyne", "ENG",  9, 100_000_000_00, "St James' Park",                    52729, 8000, "4-3-3",   "EQUILIBRADO",  86),
    (407, "Tottenham Hotspur",       "Londres",             "ENG",  9,  95_000_000_00, "Tottenham Hotspur Stadium",         62850, 8500, "4-3-3",   "OFENSIVO",     85),
    (408, "Aston Villa",             "Birmingham",          "ENG",  8,  70_000_000_00, "Villa Park",                        36887, 7000, "4-4-2",   "EQUILIBRADO",  83),
    (409, "Brighton & Hove Albion",  "Brighton",            "ENG",  8,  55_000_000_00, "Falmer Stadium",                    32176, 6500, "4-2-3-1", "OFENSIVO",     79),
    (410, "Nottingham Forest",       "Nottingham",          "ENG",  8,  50_000_000_00, "City Ground",                       31212, 6500, "4-3-3",   "CONTRA_ATAQUE",77),
    (411, "Crystal Palace",          "Londres",             "ENG",  8,  48_000_000_00, "Selhurst Park",                     25194, 6000, "4-3-3",   "EQUILIBRADO",  77),
    (412, "Bournemouth",             "Bournemouth",         "ENG",  7,  40_000_000_00, "Vitality Stadium",                  12357, 5500, "4-4-2",   "OFENSIVO",     75),
    (413, "Everton",                 "Liverpool",           "ENG",  7,  42_000_000_00, "Hill Dickinson Stadium",            52769, 6000, "4-4-2",   "DEFENSIVO",    75),
    (414, "Fulham",                  "Londres",             "ENG",  7,  38_000_000_00, "Craven Cottage",                    28107, 6000, "4-4-2",   "EQUILIBRADO",  74),
    (415, "Brentford",               "Londres",             "ENG",  7,  35_000_000_00, "Gtech Community Stadium",           17250, 5500, "4-3-3",   "CONTRA_ATAQUE",73),
    (416, "Leeds United",            "Leeds",               "ENG",  7,  32_000_000_00, "Elland Road",                       37633, 5500, "4-2-3-1", "OFENSIVO",     72),
    (417, "Sunderland",              "Sunderland",          "ENG",  6,  28_000_000_00, "Stadium of Light",                  48095, 5000, "4-4-2",   "EQUILIBRADO",  70),
    (418, "Coventry City",           "Coventry",            "ENG",  6,  24_000_000_00, "Coventry Building Society Arena",   32609, 4500, "4-4-2",   "EQUILIBRADO",  66),
    (419, "Ipswich Town",            "Ipswich",             "ENG",  6,  22_000_000_00, "Portman Road",                      30056, 4500, "4-4-2",   "DEFENSIVO",    66),
    (420, "Hull City",               "Kingston upon Hull",  "ENG",  6,  20_000_000_00, "MKM Stadium",                       24983, 4000, "4-4-2",   "DEFENSIVO",    64),
]

# Mapeia a chave usada nos arquivos de grupo → id do time
NOME_PARA_ID = {
    "Arsenal": 403, "Aston Villa": 408, "Bournemouth": 412, "Brentford": 415,
    "Brighton": 409, "Chelsea": 404, "Coventry City": 418, "Crystal Palace": 411,
    "Everton": 413, "Fulham": 414, "Hull City": 420, "Ipswich Town": 419,
    "Leeds United": 416, "Liverpool": 402, "Manchester City": 401, "Manchester United": 405,
    "Newcastle United": 406, "Nottingham Forest": 410, "Sunderland": 417, "Tottenham Hotspur": 407,
}

# ─────────────────── Derivação dos 7 atributos a partir do overall ────────────
STAT_KEYS = ["forca", "tecnica", "passe", "velocidade", "finalizacao", "defesa", "fisico"]

# Deltas absolutos aplicados ao overall por posição (defesa = componente defensivo;
# para GOLEIRO, "defesa" representa a habilidade de defender)
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
    if overall >= 85:
        sal = (300_000_00, 600_000_00); val = (60_000_000_00, 150_000_000_00)
    elif overall >= 80:
        sal = (180_000_00, 350_000_00); val = (35_000_000_00, 80_000_000_00)
    elif overall >= 75:
        sal = (110_000_00, 220_000_00); val = (18_000_000_00, 45_000_000_00)
    elif overall >= 70:
        sal = (60_000_00, 140_000_00);  val = (9_000_000_00, 25_000_000_00)
    elif overall >= 65:
        sal = (35_000_00, 90_000_00);   val = (4_000_000_00, 14_000_000_00)
    else:
        sal = (18_000_00, 50_000_00);   val = (1_500_000_00, 7_000_000_00)
    return r.randint(*sal), r.randint(*val)


def abrev(nome):
    parts = nome.split()
    if len(parts) >= 2:
        return f"{parts[0][0]}. {parts[-1]}"[:15]
    return nome[:15]


# ─────────────────── Carrega elencos pesquisados ──────────────────────────────
elencos = {}
for fname in GROUP_FILES:
    path = os.path.join(GROUPS_DIR, fname)
    with open(path, encoding="utf-8") as f:
        grupo = json.load(f)
    for clube, jogadores in grupo.items():
        if clube not in NOME_PARA_ID:
            raise SystemExit(f"Clube desconhecido no {fname}: {clube!r}")
        elencos[clube] = jogadores

faltando = [n for n in NOME_PARA_ID if n not in elencos]
if faltando:
    raise SystemExit(f"Faltam elencos para: {faltando}")

# ─────────────────── Monta o seed ─────────────────────────────────────────────
data = {"times": [], "jogadores": []}
jogador_id = 8000
VALID_POS = set(DELTAS.keys())

for (tid, tnome, tcidade, testado, tnivel, tsaldo, testadio, tcap, tpreco, ttatica, testilo, trep) in TIMES:
    data["times"].append({
        "id": tid, "nome": tnome, "cidade": tcidade, "estado": testado,
        "divisao": 11, "pais": "Inglaterra", "nivel": tnivel, "saldo": tsaldo,
        "estadio_nome": testadio, "estadio_capacidade": tcap, "preco_ingresso": tpreco,
        "tatica": ttatica, "estilo": testilo, "reputacao": trep, "escudo_res": "",
    })

    # localiza a chave de elenco correspondente a este id
    clube_key = next(k for k, v in NOME_PARA_ID.items() if v == tid)
    for j in elencos[clube_key]:
        pos = j["posicao"]
        if pos not in VALID_POS:
            raise SystemExit(f"Posição inválida ({pos}) em {clube_key}: {j.get('nome')}")
        overall = int(j["overall"])
        attrs = gen_stats(overall, pos, jogador_id)
        sal, val = gen_salario_valor(overall, jogador_id)
        r = random.Random(jogador_id + 31)
        contrato = r.randint(2, 5) if overall >= 72 else r.randint(1, 4)
        nome = j["nome"]
        registro = {
            "id": jogador_id, "time_id": tid, "nome": nome,
            "nome_abrev": abrev(nome), "idade": int(j["idade"]), "posicao": pos,
            "nacionalidade": j.get("nacionalidade", "Inglaterra"),
            "forca": attrs["forca"], "tecnica": attrs["tecnica"], "passe": attrs["passe"],
            "velocidade": attrs["velocidade"], "finalizacao": attrs["finalizacao"],
            "defesa": attrs["defesa"], "fisico": attrs["fisico"],
            "salario": sal, "valor_mercado": val, "contrato": contrato, "nivel": tnivel,
        }
        sec = j.get("posicao_sec")
        if sec and sec in VALID_POS:
            registro["posicao_sec"] = sec
        data["jogadores"].append(registro)
        jogador_id += 1

# ─────────────────── Escreve o arquivo ────────────────────────────────────────
out_path = os.path.join("app", "src", "main", "assets", "seed_premier.json")
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
