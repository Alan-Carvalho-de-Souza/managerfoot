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

    # ── Pool europeu (divisao 91) — base da UEFA Champions League ─────────────
    # As reputacoes ordenam os potes do sorteio (ver regras_championsleague.md) e
    # alimentam o coeficiente historico. Elas convivem com as das ligas jogaveis:
    # Premier (401-420), La Liga (501-520) e Serie A (521-540). Ao mexer nelas,
    # rode SeedChampionsTest para conferir que nenhum pote concentra 5 clubes de
    # um mesmo pais (o limite por pote e 4).

    # Alemanha — 4 vagas diretas
    (602, "Bayern de Munique", "Munique", "BAY", 91, "Alemanha", 10, 140_000_000_00,
     "Allianz Arena", 75024, 8500, "4-2-3-1", "OFENSIVO", 94),
    (603, "Bayer Leverkusen", "Leverkusen", "NRW", 91, "Alemanha", 9, 70_000_000_00,
     "BayArena", 30210, 7000, "3-4-3", "OFENSIVO", 87),
    (604, "Borussia Dortmund", "Dortmund", "NRW", 91, "Alemanha", 9, 75_000_000_00,
     "Signal Iduna Park", 81365, 7000, "4-2-3-1", "OFENSIVO", 86),
    (605, "RB Leipzig", "Leipzig", "SAX", 91, "Alemanha", 8, 60_000_000_00,
     "Red Bull Arena", 47069, 6500, "4-2-3-1", "OFENSIVO", 83),

    # Franca — 3 vagas diretas
    (606, "Paris Saint-Germain", "Paris", "IDF", 91, "Franca", 10, 130_000_000_00,
     "Parc des Princes", 47929, 8500, "4-3-3", "OFENSIVO", 93),
    (607, "Olympique de Marselha", "Marselha", "PAC", 91, "Franca", 8, 50_000_000_00,
     "Stade Velodrome", 67394, 6500, "4-2-3-1", "OFENSIVO", 81),
    (608, "AS Monaco", "Monaco", "MON", 91, "Franca", 8, 48_000_000_00,
     "Stade Louis II", 18523, 6500, "4-2-3-1", "EQUILIBRADO", 80),

    # Holanda — 2 vagas diretas
    (609, "PSV Eindhoven", "Eindhoven", "NBR", 91, "Holanda", 8, 42_000_000_00,
     "Philips Stadion", 35000, 6000, "4-3-3", "OFENSIVO", 79),
    (610, "Ajax", "Amsterda", "NHL", 91, "Holanda", 8, 40_000_000_00,
     "Johan Cruijff Arena", 54990, 6000, "4-3-3", "OFENSIVO", 78),

    # Belgica, Tchequia, Turquia, Portugal, Ucrania — 1 vaga direta cada
    (611, "Club Brugge", "Bruges", "WVL", 91, "Belgica", 7, 30_000_000_00,
     "Jan Breydelstadion", 29042, 5500, "4-3-3", "EQUILIBRADO", 76),
    (612, "Slavia Praga", "Praga", "PRG", 91, "Tchequia", 7, 20_000_000_00,
     "Fortuna Arena", 19370, 4500, "4-2-3-1", "EQUILIBRADO", 73),
    (613, "Galatasaray", "Istambul", "IST", 91, "Turquia", 8, 45_000_000_00,
     "Rams Park", 52223, 6000, "4-2-3-1", "OFENSIVO", 80),
    (614, "Sporting CP", "Lisboa", "LIS", 91, "Portugal", 9, 55_000_000_00,
     "Estadio Jose Alvalade", 50095, 6000, "3-4-3", "OFENSIVO", 84),
    (615, "Shakhtar Donetsk", "Donetsk", "DON", 91, "Ucrania", 7, 22_000_000_00,
     "Arena Lviv", 34915, 4000, "4-2-3-1", "OFENSIVO", 74),

    # Associacoes menores — disputam as 8 vagas da qualificacao
    (616, "Celtic", "Glasgow", "GLA", 91, "Escocia", 7, 30_000_000_00,
     "Celtic Park", 60411, 5000, "4-3-3", "OFENSIVO", 76),
    (617, "Rangers", "Glasgow", "GLA", 91, "Escocia", 7, 24_000_000_00,
     "Ibrox Stadium", 50817, 5000, "4-3-3", "EQUILIBRADO", 72),
    (618, "Olympiacos", "Pireu", "ATT", 91, "Grecia", 7, 26_000_000_00,
     "Karaiskakis", 32115, 4500, "4-2-3-1", "EQUILIBRADO", 75),
    (619, "PAOK", "Salonica", "CMC", 91, "Grecia", 7, 20_000_000_00,
     "Toumba", 28701, 4200, "4-2-3-1", "EQUILIBRADO", 71),
    (620, "Red Bull Salzburg", "Salzburgo", "SBG", 91, "Austria", 7, 22_000_000_00,
     "Red Bull Arena Salzburg", 30188, 4500, "4-4-2", "OFENSIVO", 73),
    (621, "Sturm Graz", "Graz", "STY", 91, "Austria", 6, 14_000_000_00,
     "Merkur Arena", 16364, 3800, "4-4-2", "EQUILIBRADO", 68),
    (622, "Young Boys", "Berna", "BER", 91, "Suica", 7, 18_000_000_00,
     "Wankdorf", 31783, 4200, "4-4-2", "EQUILIBRADO", 70),
    (623, "Basel", "Basileia", "BSL", 91, "Suica", 6, 16_000_000_00,
     "St. Jakob-Park", 38512, 4200, "4-2-3-1", "EQUILIBRADO", 69),
    (624, "Copenhague", "Copenhague", "HOV", 91, "Dinamarca", 7, 20_000_000_00,
     "Parken", 38065, 4500, "4-3-3", "EQUILIBRADO", 72),
    (625, "Midtjylland", "Herning", "MIJ", 91, "Dinamarca", 6, 15_000_000_00,
     "MCH Arena", 11800, 3800, "4-2-3-1", "OFENSIVO", 69),
    (626, "Bodo Glimt", "Bodo", "NOR", 91, "Noruega", 7, 16_000_000_00,
     "Aspmyra Stadion", 8270, 3800, "4-3-3", "OFENSIVO", 71),
    (627, "Dinamo Zagreb", "Zagreb", "ZAG", 91, "Croacia", 7, 18_000_000_00,
     "Maksimir", 24851, 4000, "4-2-3-1", "EQUILIBRADO", 72),
    (628, "Estrela Vermelha", "Belgrado", "BEO", 91, "Servia", 7, 16_000_000_00,
     "Rajko Mitic", 51755, 3800, "4-2-3-1", "OFENSIVO", 71),
    (629, "Legia Varsovia", "Varsovia", "MAZ", 91, "Polonia", 6, 13_000_000_00,
     "Stadion Legii", 31103, 3500, "4-2-3-1", "EQUILIBRADO", 68),
    (630, "Malmo FF", "Malmo", "SKA", 91, "Suecia", 6, 12_000_000_00,
     "Eleda Stadion", 22500, 3500, "4-4-2", "EQUILIBRADO", 67),
    (631, "FCSB", "Bucareste", "BUC", 91, "Romenia", 6, 11_000_000_00,
     "Arena Nationala", 55634, 3200, "4-2-3-1", "EQUILIBRADO", 66),
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

# ───────────────────────── Alemanha ──────────────────────────────────────────
602: [  # Bayern de Munique
    ("Manuel Neuer",         "GOLEIRO",          39, "Alemanha",      85),
    ("Jonas Urbig",          "GOLEIRO",          22, "Alemanha",      73),
    ("Dayot Upamecano",      "ZAGUEIRO",         27, "Franca",        85),
    ("Jonathan Tah",         "ZAGUEIRO",         29, "Alemanha",      84),
    ("Min-jae Kim",          "ZAGUEIRO",         29, "Coreia do Sul", 81),
    ("Hiroki Ito",           "ZAGUEIRO",         26, "Japao",         78),
    ("Josip Stanisic",       "LATERAL_DIREITO",  25, "Croacia",       78),
    ("Sacha Boey",           "LATERAL_DIREITO",  25, "Franca",        75),
    ("Alphonso Davies",      "LATERAL_ESQUERDO", 25, "Canada",        85),
    ("Raphael Guerreiro",    "LATERAL_ESQUERDO", 32, "Portugal",      79),
    ("Joshua Kimmich",       "VOLANTE",          30, "Alemanha",      87),
    ("Leon Goretzka",        "VOLANTE",          31, "Alemanha",      80),
    ("Joao Palhinha",        "MEIA_CENTRAL",     30, "Portugal",      80),
    ("Aleksandar Pavlovic",  "MEIA_CENTRAL",     21, "Alemanha",      79),
    ("Jamal Musiala",        "MEIA_ATACANTE",    22, "Alemanha",      86),
    ("Michael Olise",        "PONTA_DIREITA",    24, "Franca",        86),
    ("Serge Gnabry",         "PONTA_DIREITA",    30, "Alemanha",      81),
    ("Luis Diaz",            "PONTA_ESQUERDA",   28, "Colombia",      85),
    ("Harry Kane",           "CENTROAVANTE",     32, "Inglaterra",    89),
    ("Nicolas Jackson",      "CENTROAVANTE",     24, "Senegal",       78),
],
603: [  # Bayer Leverkusen
    ("Mark Flekken",         "GOLEIRO",          32, "Holanda",       78),
    ("Niklas Lomb",          "GOLEIRO",          32, "Alemanha",      68),
    ("Edmond Tapsoba",       "ZAGUEIRO",         26, "Burkina Faso",  81),
    ("Loic Bade",            "ZAGUEIRO",         25, "Franca",        79),
    ("Jarell Quansah",       "ZAGUEIRO",         22, "Inglaterra",    78),
    ("Axel Tape",            "ZAGUEIRO",         20, "Franca",        70),
    ("Lucas Vazquez",        "LATERAL_DIREITO",  34, "Espanha",       78),
    ("Arthur Augusto",       "LATERAL_DIREITO",  22, "Brasil",        73),
    ("Alejandro Grimaldo",   "LATERAL_ESQUERDO", 30, "Espanha",       84),
    ("Piero Hincapie",       "LATERAL_ESQUERDO", 24, "Equador",       81),
    ("Robert Andrich",       "VOLANTE",          31, "Alemanha",      79),
    ("Exequiel Palacios",    "VOLANTE",          27, "Argentina",     80),
    ("Aleix Garcia",         "MEIA_CENTRAL",     28, "Espanha",       78),
    ("Ibrahim Maza",         "MEIA_CENTRAL",     20, "Argelia",       74),
    ("Malik Tillman",        "MEIA_ATACANTE",    23, "Estados Unidos",79),
    ("Nathan Tella",         "PONTA_DIREITA",    26, "Nigeria",       77),
    ("Ernest Poku",          "PONTA_DIREITA",    21, "Holanda",       72),
    ("Eliesse Ben Seghir",   "PONTA_ESQUERDA",   20, "Marrocos",      77),
    ("Patrik Schick",        "CENTROAVANTE",     29, "Tchequia",      82),
    ("Christian Kofane",     "CENTROAVANTE",     19, "Camaroes",      71),
],
604: [  # Borussia Dortmund
    ("Gregor Kobel",         "GOLEIRO",          28, "Suica",         84),
    ("Alexander Meyer",      "GOLEIRO",          34, "Alemanha",      69),
    ("Nico Schlotterbeck",   "ZAGUEIRO",         26, "Alemanha",      83),
    ("Waldemar Anton",       "ZAGUEIRO",         29, "Alemanha",      79),
    ("Niklas Sule",          "ZAGUEIRO",         30, "Alemanha",      79),
    ("Filippo Mane",         "ZAGUEIRO",         21, "Italia",        68),
    ("Yan Couto",            "LATERAL_DIREITO",  23, "Brasil",        78),
    ("Julian Ryerson",       "LATERAL_DIREITO",  28, "Noruega",       78),
    ("Ramy Bensebaini",      "LATERAL_ESQUERDO", 30, "Argelia",       77),
    ("Daniel Svensson",      "LATERAL_ESQUERDO", 23, "Suecia",        74),
    ("Pascal Gross",         "VOLANTE",          34, "Alemanha",      78),
    ("Marcel Sabitzer",      "VOLANTE",          31, "Austria",       79),
    ("Felix Nmecha",         "MEIA_CENTRAL",     25, "Alemanha",      78),
    ("Carney Chukwuemeka",   "MEIA_CENTRAL",     22, "Inglaterra",    74),
    ("Julian Brandt",        "MEIA_ATACANTE",    29, "Alemanha",      80),
    ("Karim Adeyemi",        "PONTA_DIREITA",    24, "Alemanha",      79),
    ("Jobe Bellingham",      "PONTA_DIREITA",    20, "Inglaterra",    76),
    ("Maximilian Beier",     "PONTA_ESQUERDA",   23, "Alemanha",      78),
    ("Serhou Guirassy",      "CENTROAVANTE",     29, "Guine",         84),
    ("Fabio Silva",          "CENTROAVANTE",     23, "Portugal",      75),
],
605: [  # RB Leipzig
    ("Peter Gulacsi",        "GOLEIRO",          35, "Hungria",       79),
    ("Maarten Vandevoordt",  "GOLEIRO",          23, "Belgica",       73),
    ("Castello Lukeba",      "ZAGUEIRO",         23, "Franca",        80),
    ("Willi Orban",          "ZAGUEIRO",         33, "Hungria",       79),
    ("Lutsharel Geertruida", "ZAGUEIRO",         25, "Holanda",       77),
    ("El Chadaille Bitshiabu","ZAGUEIRO",        20, "Franca",        75),
    ("Ridle Baku",           "LATERAL_DIREITO",  27, "Alemanha",      76),
    ("Benjamin Henrichs",    "LATERAL_DIREITO",  28, "Alemanha",      76),
    ("David Raum",           "LATERAL_ESQUERDO", 27, "Alemanha",      79),
    ("Kosta Nedeljkovic",    "LATERAL_ESQUERDO", 20, "Servia",        73),
    ("Nicolas Seiwald",      "VOLANTE",          24, "Austria",       77),
    ("Xaver Schlager",       "VOLANTE",          28, "Austria",       78),
    ("Christoph Baumgartner","MEIA_CENTRAL",     26, "Austria",       78),
    ("Ezechiel Banzuzi",     "MEIA_CENTRAL",     20, "Belgica",       72),
    ("Romulo Cardoso",       "MEIA_ATACANTE",    22, "Brasil",        75),
    ("Antonio Nusa",         "PONTA_DIREITA",    20, "Noruega",       78),
    ("Assan Ouedraogo",      "PONTA_DIREITA",    19, "Alemanha",      74),
    ("Yan Diomande",         "PONTA_ESQUERDA",   19, "Costa do Marfim",75),
    ("Conrad Harder",        "CENTROAVANTE",     20, "Dinamarca",     75),
    ("Timo Werner",          "CENTROAVANTE",     29, "Alemanha",      74),
],

# ───────────────────────── Franca ────────────────────────────────────────────
606: [  # Paris Saint-Germain
    ("Lucas Chevalier",      "GOLEIRO",          24, "Franca",        81),
    ("Matvey Safonov",       "GOLEIRO",          26, "Russia",        77),
    ("Marquinhos",           "ZAGUEIRO",         31, "Brasil",        84),
    ("Willian Pacho",        "ZAGUEIRO",         24, "Equador",       84),
    ("Illya Zabarnyi",       "ZAGUEIRO",         23, "Ucrania",       81),
    ("Lucas Beraldo",        "ZAGUEIRO",         22, "Brasil",        77),
    ("Achraf Hakimi",        "LATERAL_DIREITO",  27, "Marrocos",      86),
    ("Yoram Zague",          "LATERAL_DIREITO",  19, "Franca",        70),
    ("Nuno Mendes",          "LATERAL_ESQUERDO", 27, "Portugal",      85),
    ("Lucas Hernandez",      "LATERAL_ESQUERDO", 29, "Franca",        78),
    ("Vitinha",              "VOLANTE",          25, "Portugal",      86),
    ("Joao Neves",           "VOLANTE",          21, "Portugal",      85),
    ("Fabian Ruiz",          "MEIA_CENTRAL",     29, "Espanha",       83),
    ("Warren Zaire-Emery",   "MEIA_CENTRAL",     19, "Franca",        82),
    ("Senny Mayulu",         "MEIA_ATACANTE",    19, "Franca",        75),
    ("Ousmane Dembele",      "PONTA_DIREITA",    28, "Franca",        88),
    ("Desire Doue",          "PONTA_DIREITA",    20, "Franca",        83),
    ("Khvicha Kvaratskhelia","PONTA_ESQUERDA",   24, "Georgia",       87),
    ("Bradley Barcola",      "CENTROAVANTE",     23, "Franca",        83),
    ("Goncalo Ramos",        "CENTROAVANTE",     24, "Portugal",      81),
],
607: [  # Olympique de Marselha
    ("Geronimo Rulli",       "GOLEIRO",          33, "Argentina",     79),
    ("Ruben Blanco",         "GOLEIRO",          30, "Espanha",       71),
    ("Leonardo Balerdi",     "ZAGUEIRO",         26, "Argentina",     78),
    ("Nayef Aguerd",         "ZAGUEIRO",         29, "Marrocos",      78),
    ("Facundo Medina",       "ZAGUEIRO",         26, "Argentina",     77),
    ("CJ Egan-Riley",        "ZAGUEIRO",         22, "Inglaterra",    73),
    ("Timothy Weah",         "LATERAL_DIREITO",  25, "Estados Unidos",75),
    ("Amir Murillo",         "LATERAL_DIREITO",  29, "Panama",        74),
    ("Emerson Palmieri",     "LATERAL_ESQUERDO", 31, "Italia",        76),
    ("Ulisses Garcia",       "LATERAL_ESQUERDO", 29, "Suica",         72),
    ("Geoffrey Kondogbia",   "VOLANTE",          32, "Republica Centro-Africana", 77),
    ("Arthur Vermeeren",     "VOLANTE",          20, "Belgica",       75),
    ("Pierre-Emile Hojbjerg","MEIA_CENTRAL",     30, "Dinamarca",     80),
    ("Angel Gomes",          "MEIA_CENTRAL",     25, "Inglaterra",    75),
    ("Matt O'Riley",         "MEIA_ATACANTE",    25, "Dinamarca",     76),
    ("Mason Greenwood",      "PONTA_DIREITA",    24, "Inglaterra",    80),
    ("Amine Gouiri",         "PONTA_DIREITA",    25, "Argelia",       78),
    ("Igor Paixao",          "PONTA_ESQUERDA",   25, "Brasil",        78),
    ("Pierre-Emerick Aubameyang","CENTROAVANTE", 36, "Gabao",         78),
    ("Robinio Vaz",          "CENTROAVANTE",     18, "Franca",        70),
],
608: [  # AS Monaco
    ("Philipp Kohn",         "GOLEIRO",          27, "Suica",         74),
    ("Radoslaw Majecki",     "GOLEIRO",          26, "Polonia",       74),
    ("Thilo Kehrer",         "ZAGUEIRO",         29, "Alemanha",      77),
    ("Mohammed Salisu",      "ZAGUEIRO",         26, "Gana",          76),
    ("Jordan Teze",          "ZAGUEIRO",         26, "Holanda",       76),
    ("Christian Mawissa",    "ZAGUEIRO",         20, "Franca",        73),
    ("Vanderson",            "LATERAL_DIREITO",  24, "Brasil",        78),
    ("Krepin Diatta",        "LATERAL_DIREITO",  26, "Senegal",       75),
    ("Caio Henrique",        "LATERAL_ESQUERDO", 28, "Brasil",        76),
    ("Mamadou Coulibaly",    "LATERAL_ESQUERDO", 20, "Franca",        70),
    ("Denis Zakaria",        "VOLANTE",          28, "Suica",         79),
    ("Lamine Camara",        "VOLANTE",          21, "Senegal",       76),
    ("Maghnes Akliouche",    "MEIA_CENTRAL",     23, "Franca",        80),
    ("Aleksandr Golovin",    "MEIA_CENTRAL",     29, "Russia",        79),
    ("Takumi Minamino",      "MEIA_ATACANTE",    30, "Japao",         76),
    ("Ansu Fati",            "PONTA_DIREITA",    22, "Espanha",       76),
    ("George Ilenikhena",    "PONTA_DIREITA",    19, "Nigeria",       74),
    ("Kassoum Ouattara",     "PONTA_ESQUERDA",   21, "Franca",        71),
    ("Mika Biereth",         "CENTROAVANTE",     22, "Dinamarca",     79),
    ("Folarin Balogun",      "CENTROAVANTE",     24, "Estados Unidos",76),
],

# ───────────────────────── Holanda ───────────────────────────────────────────
609: [  # PSV Eindhoven
    ("Walter Benitez",       "GOLEIRO",          32, "Argentina",     78),
    ("Nick Olij",            "GOLEIRO",          30, "Holanda",       72),
    ("Olivier Boscagli",     "ZAGUEIRO",         28, "Franca",        77),
    ("Ryan Flamingo",        "ZAGUEIRO",         22, "Holanda",       76),
    ("Armando Obispo",       "ZAGUEIRO",         26, "Holanda",       74),
    ("Yarek Gasiorowski",    "ZAGUEIRO",         20, "Espanha",       73),
    ("Sergino Dest",         "LATERAL_DIREITO",  25, "Estados Unidos",76),
    ("Richard Ledezma",      "LATERAL_DIREITO",  25, "Estados Unidos",72),
    ("Mauro Junior",         "LATERAL_ESQUERDO", 26, "Brasil",        74),
    ("Anass Salah-Eddine",   "LATERAL_ESQUERDO", 23, "Holanda",       73),
    ("Jerdy Schouten",       "VOLANTE",          29, "Holanda",       79),
    ("Joey Veerman",         "VOLANTE",          27, "Holanda",       78),
    ("Ismael Saibari",       "MEIA_CENTRAL",     24, "Marrocos",      79),
    ("Guus Til",             "MEIA_CENTRAL",     28, "Holanda",       76),
    ("Dennis Man",           "MEIA_ATACANTE",    27, "Romenia",       76),
    ("Ivan Perisic",         "PONTA_DIREITA",    36, "Croacia",       77),
    ("Esmir Bajraktarevic",  "PONTA_DIREITA",    20, "Estados Unidos",72),
    ("Couhaib Driouech",     "PONTA_ESQUERDA",   23, "Marrocos",      74),
    ("Ricardo Pepi",         "CENTROAVANTE",     22, "Estados Unidos",77),
    ("Alassane Plea",        "CENTROAVANTE",     32, "Franca",        76),
],
610: [  # Ajax
    ("Vitezslav Jaros",      "GOLEIRO",          24, "Tchequia",      74),
    ("Remko Pasveer",        "GOLEIRO",          42, "Holanda",       73),
    ("Ko Itakura",           "ZAGUEIRO",         28, "Japao",         77),
    ("Josip Sutalo",         "ZAGUEIRO",         25, "Croacia",       76),
    ("Youri Baas",           "ZAGUEIRO",         22, "Holanda",       75),
    ("Aaron Bouwman",        "ZAGUEIRO",         19, "Holanda",       69),
    ("Anton Gaaei",          "LATERAL_DIREITO",  23, "Dinamarca",     72),
    ("Lucas Rosa",           "LATERAL_DIREITO",  25, "Brasil",        71),
    ("Owen Wijndal",         "LATERAL_ESQUERDO", 26, "Holanda",       73),
    ("Youri Regeer",         "LATERAL_ESQUERDO", 22, "Holanda",       73),
    ("Kenneth Taylor",       "VOLANTE",          23, "Holanda",       78),
    ("Branco van den Boomen","VOLANTE",          30, "Holanda",       74),
    ("Davy Klaassen",        "MEIA_CENTRAL",     32, "Holanda",       76),
    ("Sivert Mannsverk",     "MEIA_CENTRAL",     23, "Noruega",       73),
    ("Mika Godts",           "MEIA_ATACANTE",    20, "Belgica",       76),
    ("Steven Berghuis",      "PONTA_DIREITA",    34, "Holanda",       77),
    ("Oscar Gloukh",         "PONTA_DIREITA",    21, "Israel",        77),
    ("Raul Moro",            "PONTA_ESQUERDA",   23, "Espanha",       73),
    ("Wout Weghorst",        "CENTROAVANTE",     33, "Holanda",       76),
    ("Kasper Dolberg",       "CENTROAVANTE",     28, "Dinamarca",     75),
],

# ───────────────────────── Belgica ───────────────────────────────────────────
611: [  # Club Brugge
    ("Simon Mignolet",       "GOLEIRO",          38, "Belgica",       77),
    ("Nordin Jackers",       "GOLEIRO",          28, "Belgica",       72),
    ("Joel Ordonez",         "ZAGUEIRO",         21, "Equador",       76),
    ("Brandon Mechele",      "ZAGUEIRO",         32, "Belgica",       74),
    ("Zaid Romero",          "ZAGUEIRO",         24, "Argentina",     73),
    ("Jorne Spileers",       "ZAGUEIRO",         20, "Belgica",       71),
    ("Kyriani Sabbe",        "LATERAL_DIREITO",  21, "Belgica",       74),
    ("Joaquin Seys",         "LATERAL_DIREITO",  20, "Belgica",       73),
    ("Bjorn Meijer",         "LATERAL_ESQUERDO", 22, "Holanda",       72),
    ("Joris Kayembe",        "LATERAL_ESQUERDO", 31, "RD Congo",      72),
    ("Hans Vanaken",         "VOLANTE",          33, "Belgica",       78),
    ("Raphael Onyedika",     "VOLANTE",          24, "Nigeria",       77),
    ("Aleksandar Stankovic", "MEIA_CENTRAL",     20, "Servia",        75),
    ("Hugo Vetlesen",        "MEIA_CENTRAL",     25, "Noruega",       74),
    ("Christos Tzolis",      "MEIA_ATACANTE",    24, "Grecia",        78),
    ("Carlos Forbs",         "PONTA_DIREITA",    22, "Portugal",      75),
    ("Chemsdine Talbi",      "PONTA_DIREITA",    20, "Marrocos",      75),
    ("Mamadou Diakhon",      "PONTA_ESQUERDA",   19, "Franca",        71),
    ("Nicolo Tresoldi",      "CENTROAVANTE",     21, "Italia",        74),
    ("Romeo Vermant",        "CENTROAVANTE",     21, "Belgica",       73),
],

# ───────────────────────── Tchequia ──────────────────────────────────────────
612: [  # Slavia Praga
    ("Jindrich Stanek",      "GOLEIRO",          29, "Tchequia",      76),
    ("Ales Mandous",         "GOLEIRO",          33, "Tchequia",      70),
    ("Igoh Ogbu",            "ZAGUEIRO",         26, "Nigeria",       75),
    ("David Zima",           "ZAGUEIRO",         25, "Tchequia",      74),
    ("Tomas Holes",          "ZAGUEIRO",         32, "Tchequia",      73),
    ("Stephan Chaloupek",    "ZAGUEIRO",         23, "Austria",       69),
    ("David Doudera",        "LATERAL_DIREITO",  27, "Tchequia",      72),
    ("Lukas Masopust",       "LATERAL_DIREITO",  32, "Tchequia",      72),
    ("Dominik Javorcek",     "LATERAL_ESQUERDO", 23, "Tchequia",      71),
    ("Jan Boril",            "LATERAL_ESQUERDO", 34, "Tchequia",      70),
    ("Christos Zafeiris",    "VOLANTE",          22, "Grecia",        75),
    ("Oscar Dorley",         "VOLANTE",          27, "Liberia",       73),
    ("Lukas Provod",         "MEIA_CENTRAL",     29, "Tchequia",      76),
    ("Michal Sadilek",       "MEIA_CENTRAL",     26, "Tchequia",      72),
    ("Ivan Schranz",         "MEIA_ATACANTE",    32, "Eslovaquia",    74),
    ("Matej Jurasek",        "PONTA_DIREITA",    22, "Tchequia",      73),
    ("Youssoupha Mbodji",    "PONTA_DIREITA",    22, "Senegal",       70),
    ("Vasil Kusej",          "PONTA_ESQUERDA",   25, "Tchequia",      74),
    ("Tomas Chory",          "CENTROAVANTE",     30, "Tchequia",      75),
    ("Mojmir Chytil",        "CENTROAVANTE",     27, "Tchequia",      74),
],

# ───────────────────────── Turquia ───────────────────────────────────────────
613: [  # Galatasaray
    ("Ugurcan Cakir",        "GOLEIRO",          29, "Turquia",       79),
    ("Gunay Guvenc",         "GOLEIRO",          34, "Turquia",       70),
    ("Davinson Sanchez",     "ZAGUEIRO",         29, "Colombia",      79),
    ("Abdulkerim Bardakci",  "ZAGUEIRO",         31, "Turquia",       76),
    ("Ismail Jakobs",        "ZAGUEIRO",         26, "Senegal",       75),
    ("Metehan Baltaci",      "ZAGUEIRO",         22, "Turquia",       71),
    ("Wilfried Singo",       "LATERAL_DIREITO",  25, "Costa do Marfim",78),
    ("Roland Sallai",        "LATERAL_DIREITO",  28, "Hungria",       76),
    ("Eren Elmali",          "LATERAL_ESQUERDO", 25, "Turquia",       74),
    ("Kazimcan Karatas",     "LATERAL_ESQUERDO", 22, "Turquia",       72),
    ("Lucas Torreira",       "VOLANTE",          29, "Uruguai",       79),
    ("Mario Lemina",         "VOLANTE",          32, "Gabao",         76),
    ("Ilkay Gundogan",       "MEIA_CENTRAL",     35, "Alemanha",      80),
    ("Gabriel Sara",         "MEIA_CENTRAL",     26, "Brasil",        78),
    ("Yunus Akgun",          "MEIA_ATACANTE",    25, "Turquia",       76),
    ("Leroy Sane",           "PONTA_DIREITA",    29, "Alemanha",      82),
    ("Baris Alper Yilmaz",   "PONTA_DIREITA",    25, "Turquia",       77),
    ("Ahmed Kutucu",         "PONTA_ESQUERDA",   25, "Turquia",       72),
    ("Victor Osimhen",       "CENTROAVANTE",     27, "Nigeria",       87),
    ("Mauro Icardi",         "CENTROAVANTE",     32, "Argentina",     79),
],

# ───────────────────────── Portugal ──────────────────────────────────────────
614: [  # Sporting CP
    ("Rui Silva",            "GOLEIRO",          31, "Portugal",      77),
    ("Joao Virginia",        "GOLEIRO",          26, "Portugal",      72),
    ("Goncalo Inacio",       "ZAGUEIRO",         24, "Portugal",      82),
    ("Ousmane Diomande",     "ZAGUEIRO",         22, "Costa do Marfim",81),
    ("Zeno Debast",          "ZAGUEIRO",         22, "Belgica",       77),
    ("Jerry St. Juste",      "ZAGUEIRO",         29, "Holanda",       74),
    ("Geovany Quenda",       "LATERAL_DIREITO",  18, "Portugal",      78),
    ("Ivan Fresneda",        "LATERAL_DIREITO",  21, "Espanha",       75),
    ("Maxi Araujo",          "LATERAL_ESQUERDO", 25, "Uruguai",       76),
    ("Matheus Reis",         "LATERAL_ESQUERDO", 30, "Brasil",        74),
    ("Morten Hjulmand",      "VOLANTE",          26, "Dinamarca",     81),
    ("Hidemasa Morita",      "VOLANTE",          30, "Japao",         77),
    ("Pedro Goncalves",      "MEIA_CENTRAL",     27, "Portugal",      81),
    ("Joao Simoes",          "MEIA_CENTRAL",     20, "Portugal",      73),
    ("Francisco Trincao",    "MEIA_ATACANTE",    26, "Portugal",      79),
    ("Luis Suarez",          "PONTA_DIREITA",    28, "Colombia",      77),
    ("Geny Catamo",          "PONTA_DIREITA",    24, "Mocambique",    75),
    ("Nuno Santos",          "PONTA_ESQUERDA",   30, "Portugal",      75),
    ("Fotis Ioannidis",      "CENTROAVANTE",     25, "Grecia",        78),
    ("Alisson Santos",       "CENTROAVANTE",     26, "Brasil",        74),
],

# ───────────────────────── Ucrania ───────────────────────────────────────────
615: [  # Shakhtar Donetsk
    ("Dmytro Riznyk",        "GOLEIRO",          26, "Ucrania",       76),
    ("Kyrylo Fesiun",        "GOLEIRO",          24, "Ucrania",       69),
    ("Mykola Matviyenko",    "ZAGUEIRO",         29, "Ucrania",       77),
    ("Valeriy Bondar",       "ZAGUEIRO",         26, "Ucrania",       74),
    ("Marlon Santos",        "ZAGUEIRO",         30, "Brasil",        73),
    ("Pedro Henrique",       "ZAGUEIRO",         25, "Brasil",        72),
    ("Yukhym Konoplia",      "LATERAL_DIREITO",  26, "Ucrania",       74),
    ("Vinicius Tobias",      "LATERAL_DIREITO",  22, "Brasil",        72),
    ("Irakli Azarovi",       "LATERAL_ESQUERDO", 23, "Georgia",       73),
    ("Hryhoriy Pedko",       "LATERAL_ESQUERDO", 21, "Ucrania",       68),
    ("Oleh Ocheretko",       "VOLANTE",          21, "Ucrania",       74),
    ("Julio Romao",          "VOLANTE",          24, "Portugal",      74),
    ("Dmytro Kryskiv",       "MEIA_CENTRAL",     23, "Ucrania",       73),
    ("Yegor Nazaryna",       "MEIA_CENTRAL",     27, "Ucrania",       73),
    ("Georgiy Sudakov",      "MEIA_ATACANTE",    23, "Ucrania",       81),
    ("Pedrinho",             "PONTA_DIREITA",    27, "Brasil",        75),
    ("Eguinaldo",            "PONTA_DIREITA",    21, "Brasil",        73),
    ("Marian Shved",         "PONTA_ESQUERDA",   28, "Ucrania",       73),
    ("Lassina Traore",       "CENTROAVANTE",     25, "Burkina Faso",  75),
    ("Luca Meirelles",       "CENTROAVANTE",     20, "Brasil",        72),
],

# ───────────────────────── Escocia ───────────────────────────────────────────
616: [  # Celtic
    ("Kasper Schmeichel",    "GOLEIRO",          39, "Dinamarca",     76),
    ("Viljami Sinisalo",     "GOLEIRO",          24, "Finlandia",     72),
    ("Cameron Carter-Vickers","ZAGUEIRO",        28, "Estados Unidos",76),
    ("Auston Trusty",        "ZAGUEIRO",         27, "Estados Unidos",74),
    ("Liam Scales",          "ZAGUEIRO",         27, "Irlanda",       73),
    ("Dane Murray",          "ZAGUEIRO",         23, "Escocia",       68),
    ("Alistair Johnston",    "LATERAL_DIREITO",  27, "Canada",        75),
    ("Anthony Ralston",      "LATERAL_DIREITO",  27, "Escocia",       71),
    ("Kieran Tierney",       "LATERAL_ESQUERDO", 28, "Escocia",       76),
    ("Marcelo Saracchi",     "LATERAL_ESQUERDO", 27, "Uruguai",       72),
    ("Callum McGregor",      "VOLANTE",          32, "Escocia",       76),
    ("Reo Hatate",           "VOLANTE",          28, "Japao",         76),
    ("Arne Engels",          "MEIA_CENTRAL",     22, "Belgica",       75),
    ("Paulo Bernardo",       "MEIA_CENTRAL",     23, "Portugal",      73),
    ("Benjamin Nygren",      "MEIA_ATACANTE",    24, "Suecia",        75),
    ("Daizen Maeda",         "PONTA_DIREITA",    28, "Japao",         78),
    ("James Forrest",        "PONTA_DIREITA",    34, "Escocia",       71),
    ("Yang Hyun-jun",        "PONTA_ESQUERDA",   23, "Coreia do Sul", 71),
    ("Adam Idah",            "CENTROAVANTE",     24, "Irlanda",       73),
    ("Johnny Kenny",         "CENTROAVANTE",     22, "Irlanda",       70),
],
617: [  # Rangers
    ("Jack Butland",         "GOLEIRO",          32, "Inglaterra",    75),
    ("Liam Kelly",           "GOLEIRO",          29, "Escocia",       70),
    ("John Souttar",         "ZAGUEIRO",         29, "Escocia",       74),
    ("Nasser Djiga",         "ZAGUEIRO",         23, "Burkina Faso",  73),
    ("Robin Propper",        "ZAGUEIRO",         32, "Holanda",       72),
    ("Emmanuel Fernandez",   "ZAGUEIRO",         24, "Espanha",       71),
    ("Max Aarons",           "LATERAL_DIREITO",  25, "Inglaterra",    74),
    ("James Tavernier",      "LATERAL_DIREITO",  34, "Inglaterra",    73),
    ("Jefte",                "LATERAL_ESQUERDO", 22, "Brasil",        72),
    ("Ridvan Yilmaz",        "LATERAL_ESQUERDO", 24, "Turquia",       71),
    ("Nicolas Raskin",       "VOLANTE",          24, "Belgica",       75),
    ("Mohamed Diomande",     "VOLANTE",          24, "Costa do Marfim",73),
    ("Thelo Aasgaard",       "MEIA_CENTRAL",     23, "Noruega",       73),
    ("Connor Barron",        "MEIA_CENTRAL",     23, "Escocia",       71),
    ("Joe Rothwell",         "MEIA_ATACANTE",    30, "Inglaterra",    72),
    ("Oliver Antman",        "PONTA_DIREITA",    24, "Finlandia",     73),
    ("Djeidi Gassama",       "PONTA_DIREITA",    22, "Franca",        73),
    ("Vaclav Cerny",         "PONTA_ESQUERDA",   28, "Tchequia",      74),
    ("Cyriel Dessers",       "CENTROAVANTE",     31, "Nigeria",       74),
    ("Hamza Igamane",        "CENTROAVANTE",     23, "Marrocos",      73),
],

# ───────────────────────── Grecia ────────────────────────────────────────────
618: [  # Olympiacos
    ("Konstantinos Tzolakis","GOLEIRO",          23, "Grecia",        75),
    ("Alexandros Paschalakis","GOLEIRO",         36, "Grecia",        70),
    ("Lorenzo Pirola",       "ZAGUEIRO",         23, "Italia",        75),
    ("Panagiotis Retsos",    "ZAGUEIRO",         27, "Grecia",        74),
    ("David Carmo",          "ZAGUEIRO",         26, "Portugal",      74),
    ("Nikolas Michelis",     "ZAGUEIRO",         22, "Grecia",        69),
    ("Rodinei",              "LATERAL_DIREITO",  33, "Brasil",        74),
    ("Costinha",             "LATERAL_DIREITO",  26, "Portugal",      72),
    ("Francisco Ortega",     "LATERAL_ESQUERDO", 26, "Argentina",     74),
    ("Bruno Onyemaechi",     "LATERAL_ESQUERDO", 26, "Nigeria",       72),
    ("Santiago Hezze",       "VOLANTE",          24, "Argentina",     75),
    ("Dani Garcia",          "VOLANTE",          35, "Espanha",       72),
    ("Chiquinho",            "MEIA_CENTRAL",     30, "Portugal",      75),
    ("Christos Mouzakitis",  "MEIA_CENTRAL",     19, "Grecia",        74),
    ("Daniel Podence",       "MEIA_ATACANTE",    30, "Portugal",      76),
    ("Gelson Martins",       "PONTA_DIREITA",    30, "Portugal",      74),
    ("Stevan Jovetic",       "PONTA_DIREITA",    36, "Montenegro",    72),
    ("Yusuf Yazici",         "PONTA_ESQUERDA",   28, "Turquia",       75),
    ("Ayoub El Kaabi",       "CENTROAVANTE",     32, "Marrocos",      79),
    ("Roman Yaremchuk",      "CENTROAVANTE",     30, "Ucrania",       74),
],
619: [  # PAOK
    ("Dominik Kotarski",     "GOLEIRO",          25, "Croacia",       74),
    ("Antonis Tsiftsis",     "GOLEIRO",          25, "Grecia",        68),
    ("Giannis Michailidis",  "ZAGUEIRO",         25, "Grecia",        74),
    ("Abdul Mumin",          "ZAGUEIRO",         27, "Gana",          74),
    ("Dimitrios Kourbelis",  "ZAGUEIRO",         31, "Grecia",        72),
    ("Antonis Martis",       "ZAGUEIRO",         22, "Grecia",        68),
    ("Lazaros Rota",         "LATERAL_DIREITO",  28, "Grecia",        72),
    ("Kenny Lala",           "LATERAL_DIREITO",  34, "Franca",        71),
    ("Jonny Otto",           "LATERAL_ESQUERDO", 31, "Espanha",       73),
    ("Rafa Soares",          "LATERAL_ESQUERDO", 29, "Portugal",      72),
    ("Soualiho Meite",       "VOLANTE",          31, "Franca",        74),
    ("Mady Camara",          "VOLANTE",          28, "Guine",         73),
    ("Giannis Konstantelias","MEIA_CENTRAL",     22, "Grecia",        76),
    ("Magomed Ozdoev",       "MEIA_CENTRAL",     33, "Russia",        72),
    ("Andrija Zivkovic",     "MEIA_ATACANTE",    29, "Servia",        74),
    ("Kiril Despodov",       "PONTA_DIREITA",    29, "Bulgaria",      75),
    ("Taison",               "PONTA_DIREITA",    37, "Brasil",        71),
    ("Luis Palma",           "PONTA_ESQUERDA",   26, "Honduras",      73),
    ("Chuba Akpom",          "CENTROAVANTE",     30, "Inglaterra",    74),
    ("Fedor Chalov",         "CENTROAVANTE",     27, "Russia",        73),
],

# ───────────────────────── Austria ───────────────────────────────────────────
620: [  # Red Bull Salzburg
    ("Alexander Schlager",   "GOLEIRO",          29, "Austria",       74),
    ("Janis Blaswich",       "GOLEIRO",          34, "Alemanha",      73),
    ("Hendry Blank",         "ZAGUEIRO",         21, "Alemanha",      74),
    ("Jacob Rasmussen",      "ZAGUEIRO",         28, "Dinamarca",     73),
    ("Samson Baidoo",        "ZAGUEIRO",         21, "Austria",       73),
    ("Joane Gadou",          "ZAGUEIRO",         19, "Franca",        71),
    ("Amar Dedic",           "LATERAL_DIREITO",  23, "Bosnia",        75),
    ("Stefan Lainer",        "LATERAL_DIREITO",  33, "Austria",       71),
    ("Aleksa Terzic",        "LATERAL_ESQUERDO", 26, "Servia",        72),
    ("Frans Kratzig",        "LATERAL_ESQUERDO", 22, "Alemanha",      72),
    ("Mamady Diambou",       "VOLANTE",          23, "Mali",          73),
    ("Soumaila Diabate",     "VOLANTE",          22, "Costa do Marfim",72),
    ("Maurits Kjaergaard",   "MEIA_CENTRAL",     22, "Dinamarca",     75),
    ("Bobby Clark",          "MEIA_CENTRAL",     20, "Inglaterra",    73),
    ("Takumu Kawamura",      "MEIA_ATACANTE",    24, "Japao",         72),
    ("Yorbe Vertessen",      "PONTA_DIREITA",    24, "Belgica",       73),
    ("Edmund Baidoo",        "PONTA_DIREITA",    21, "Gana",          71),
    ("Dorgeles Nene",        "PONTA_ESQUERDA",   22, "Mali",          75),
    ("Karim Konate",         "CENTROAVANTE",     21, "Costa do Marfim",76),
    ("Petar Ratkov",         "CENTROAVANTE",     22, "Servia",        74),
],
621: [  # Sturm Graz
    ("Kjell Scherpen",       "GOLEIRO",          25, "Holanda",       73),
    ("Christian Zieglbauer", "GOLEIRO",          22, "Austria",       66),
    ("Gregory Wuthrich",     "ZAGUEIRO",         30, "Suica",         71),
    ("Jon Gorenc Stankovic", "ZAGUEIRO",         29, "Eslovenia",     71),
    ("Emanuel Aiwu",         "ZAGUEIRO",         25, "Austria",       70),
    ("Niklas Geyrhofer",     "ZAGUEIRO",         25, "Austria",       69),
    ("Dimitri Lavalee",      "LATERAL_DIREITO",  28, "Belgica",       70),
    ("Jusuf Gazibegovic",    "LATERAL_DIREITO",  25, "Bosnia",        70),
    ("Max Johnston",         "LATERAL_ESQUERDO", 22, "Escocia",       71),
    ("Stefan Hierlander",    "LATERAL_ESQUERDO", 34, "Austria",       68),
    ("Tochi Chukwuani",      "VOLANTE",          22, "Dinamarca",     70),
    ("Arjan Malic",          "VOLANTE",          22, "Austria",       68),
    ("Otar Kiteishvili",     "MEIA_CENTRAL",     29, "Georgia",       73),
    ("Tomi Horvat",          "MEIA_CENTRAL",     26, "Eslovenia",     71),
    ("Leon Grgic",           "MEIA_ATACANTE",    19, "Austria",       70),
    ("William Boving",       "PONTA_DIREITA",    22, "Dinamarca",     72),
    ("Dimitri Oberlin",      "PONTA_DIREITA",    28, "Suica",         69),
    ("Seedy Jatta",          "PONTA_ESQUERDA",   22, "Gambia",        69),
    ("Maurice Malone",       "CENTROAVANTE",     25, "Alemanha",      71),
    ("Jacob Horn",           "CENTROAVANTE",     23, "Alemanha",      68),
],

# ───────────────────────── Suica ─────────────────────────────────────────────
622: [  # Young Boys
    ("Marvin Keller",        "GOLEIRO",          23, "Suica",         72),
    ("David von Ballmoos",   "GOLEIRO",          30, "Suica",         71),
    ("Mohamed Ali Camara",   "ZAGUEIRO",         28, "Guine",         73),
    ("Loris Benito",         "ZAGUEIRO",         33, "Suica",         71),
    ("Marco Burch",          "ZAGUEIRO",         25, "Suica",         70),
    ("Noah Persson",         "ZAGUEIRO",         23, "Suecia",        69),
    ("Lewin Blum",           "LATERAL_DIREITO",  24, "Suica",         71),
    ("Facinet Conte",        "LATERAL_DIREITO",  21, "Guine",         68),
    ("Jaouen Hadjam",        "LATERAL_ESQUERDO", 23, "Argelia",       73),
    ("Saidy Janko",          "LATERAL_ESQUERDO", 30, "Gambia",        70),
    ("Sandro Lauper",        "VOLANTE",          29, "Suica",         71),
    ("Armin Gigovic",        "VOLANTE",          23, "Suecia",        72),
    ("Alvyn Sanches",        "MEIA_CENTRAL",     22, "Suica",         74),
    ("Filip Ugrinic",        "MEIA_CENTRAL",     26, "Suica",         73),
    ("Darian Males",         "MEIA_ATACANTE",    24, "Suica",         72),
    ("Edimilson Fernandes",  "PONTA_DIREITA",    29, "Suica",         72),
    ("Joel Monteiro",        "PONTA_DIREITA",    26, "Suica",         71),
    ("Ebrima Colley",        "PONTA_ESQUERDA",   25, "Gambia",        71),
    ("Chris Bedia",          "CENTROAVANTE",     29, "Costa do Marfim",72),
    ("Alan Virginius",       "CENTROAVANTE",     22, "Franca",        70),
],
623: [  # Basel
    ("Marwin Hitz",          "GOLEIRO",          38, "Suica",         72),
    ("Mirko Salvi",          "GOLEIRO",          32, "Suica",         67),
    ("Jonas Adjetey",        "ZAGUEIRO",         21, "Gana",          72),
    ("Adrian Barisic",       "ZAGUEIRO",         24, "Croacia",       71),
    ("Finn van Breemen",     "ZAGUEIRO",         22, "Holanda",       71),
    ("Nicolas Vouilloz",     "ZAGUEIRO",         22, "Suica",         68),
    ("Flavius Daniliuc",     "LATERAL_DIREITO",  24, "Austria",       71),
    ("Metinho",              "LATERAL_DIREITO",  22, "Brasil",        70),
    ("Dominik Schmid",       "LATERAL_ESQUERDO", 27, "Suica",         71),
    ("Keigo Tsunemoto",      "LATERAL_ESQUERDO", 24, "Japao",         70),
    ("Leon Avdullahu",       "VOLANTE",          21, "Kosovo",        73),
    ("Taulant Xhaka",        "VOLANTE",          34, "Albania",       68),
    ("Xherdan Shaqiri",      "MEIA_CENTRAL",     34, "Suica",         77),
    ("Koba Koindredi",       "MEIA_CENTRAL",     24, "Franca",        71),
    ("Ibrahim Salah",        "MEIA_ATACANTE",    24, "Marrocos",      72),
    ("Philip Otele",         "PONTA_DIREITA",    26, "Nigeria",       72),
    ("Marin Soticek",        "PONTA_DIREITA",    24, "Croacia",       71),
    ("Jonathan Dubasin",     "PONTA_ESQUERDA",   25, "Espanha",       72),
    ("Albian Ajeti",         "CENTROAVANTE",     28, "Suica",         71),
    ("Bradley Fink",         "CENTROAVANTE",     22, "Alemanha",      70),
],

# ───────────────────────── Dinamarca ─────────────────────────────────────────
624: [  # Copenhague
    ("Theo Sander",          "GOLEIRO",          21, "Dinamarca",     70),
    ("Jacob Rinne",          "GOLEIRO",          32, "Suecia",        70),
    ("Pantelis Hatzidiakos", "ZAGUEIRO",         29, "Grecia",        74),
    ("Denis Vavro",          "ZAGUEIRO",         29, "Eslovaquia",    74),
    ("Gabriel Pereira",      "ZAGUEIRO",         26, "Brasil",        73),
    ("Villads Nielsen",      "ZAGUEIRO",         20, "Dinamarca",     69),
    ("Rodrigo Huescas",      "LATERAL_DIREITO",  22, "Mexico",        73),
    ("Tobias Christensen",   "LATERAL_DIREITO",  24, "Noruega",       70),
    ("Birger Meling",        "LATERAL_ESQUERDO", 31, "Noruega",       74),
    ("Marcos Lopez",         "LATERAL_ESQUERDO", 26, "Peru",          73),
    ("William Clem",         "VOLANTE",          24, "Dinamarca",     73),
    ("Robert Silva",         "VOLANTE",          23, "Uruguai",       71),
    ("Hakon Arnar Haraldsson","MEIA_CENTRAL",    22, "Islandia",      76),
    ("Oscar Hojlund",        "MEIA_CENTRAL",     20, "Dinamarca",     72),
    ("Mohamed Elyounoussi",  "MEIA_ATACANTE",    31, "Noruega",       74),
    ("Elias Achouri",        "PONTA_DIREITA",    26, "Tunisia",       75),
    ("Jordan Larsson",       "PONTA_DIREITA",    28, "Suecia",        73),
    ("Viktor Dadason",       "PONTA_ESQUERDA",   20, "Islandia",      70),
    ("Youssoufa Moukoko",    "CENTROAVANTE",     21, "Alemanha",      75),
    ("Filip Bundgaard",      "CENTROAVANTE",     20, "Dinamarca",     72),
],
625: [  # Midtjylland
    ("Elias Olafsson",       "GOLEIRO",          25, "Islandia",      72),
    ("Jonas Lossl",          "GOLEIRO",          36, "Dinamarca",     70),
    ("Mads Bech Sorensen",   "ZAGUEIRO",         26, "Dinamarca",     72),
    ("Stefan Gartenmann",    "ZAGUEIRO",         28, "Dinamarca",     71),
    ("Juninho",              "ZAGUEIRO",         25, "Brasil",        71),
    ("Valdemar Byskov",      "ZAGUEIRO",         22, "Dinamarca",     69),
    ("Oliver Sorensen",      "LATERAL_DIREITO",  23, "Dinamarca",     70),
    ("Daniel Gaarde",        "LATERAL_DIREITO",  22, "Dinamarca",     69),
    ("Nikolas Dyhr",         "LATERAL_ESQUERDO", 24, "Dinamarca",     71),
    ("Mikel Gogorza",        "LATERAL_ESQUERDO", 27, "Espanha",       70),
    ("Mads Bidstrup",        "VOLANTE",          24, "Dinamarca",     73),
    ("Aral Simsir",          "VOLANTE",          24, "Dinamarca",     72),
    ("Charles",              "MEIA_CENTRAL",     24, "Brasil",        72),
    ("Paulinho",             "MEIA_CENTRAL",     25, "Brasil",        71),
    ("Dario Osorio",         "MEIA_ATACANTE",    22, "Chile",         74),
    ("Philip Billing",       "PONTA_DIREITA",    29, "Dinamarca",     74),
    ("Ousmane Diao",         "PONTA_DIREITA",    22, "Senegal",       70),
    ("Simon Hedlund",        "PONTA_ESQUERDA",   32, "Suecia",        70),
    ("Franculino Dju",       "CENTROAVANTE",     21, "Guine-Bissau",  77),
    ("Adam Buksa",           "CENTROAVANTE",     29, "Polonia",       73),
],

# ───────────────────────── Noruega ───────────────────────────────────────────
626: [  # Bodo Glimt
    ("Nikita Haikin",        "GOLEIRO",          30, "Russia",        73),
    ("Julian Faye Lund",     "GOLEIRO",          25, "Noruega",       69),
    ("Odin Bjortuft",        "ZAGUEIRO",         25, "Noruega",       73),
    ("Jostein Gundersen",    "ZAGUEIRO",         29, "Noruega",       72),
    ("Brede Moe",            "ZAGUEIRO",         34, "Noruega",       71),
    ("Isak Maattaa",         "ZAGUEIRO",         22, "Suecia",        68),
    ("Fredrik Sjovold",      "LATERAL_DIREITO",  22, "Noruega",       70),
    ("Brice Wembangomo",     "LATERAL_DIREITO",  28, "Noruega",       70),
    ("Fredrik Bjorkan",      "LATERAL_ESQUERDO", 27, "Noruega",       73),
    ("Gaute Vetti",          "LATERAL_ESQUERDO", 22, "Noruega",       68),
    ("Patrick Berg",         "VOLANTE",          28, "Noruega",       76),
    ("Ulrik Saltnes",        "VOLANTE",          33, "Noruega",       72),
    ("Hakon Evjen",          "MEIA_CENTRAL",     25, "Noruega",       74),
    ("Sondre Brunstad Fet",  "MEIA_CENTRAL",     27, "Noruega",       71),
    ("Jens Petter Hauge",    "MEIA_ATACANTE",    26, "Noruega",       75),
    ("Ole Didrik Blomberg",  "PONTA_DIREITA",    24, "Noruega",       72),
    ("Daniel Bassi",         "PONTA_DIREITA",    24, "Noruega",       72),
    ("Sondre Sorli",         "PONTA_ESQUERDA",   25, "Noruega",       71),
    ("Kasper Hogh",          "CENTROAVANTE",     25, "Dinamarca",     74),
    ("Andreas Helmersen",    "CENTROAVANTE",     26, "Noruega",       71),
],

# ───────────────────────── Croacia ───────────────────────────────────────────
627: [  # Dinamo Zagreb
    ("Ivan Nevistic",        "GOLEIRO",          27, "Croacia",       73),
    ("Danijel Zagorac",      "GOLEIRO",          38, "Croacia",       67),
    ("Scott McKenna",        "ZAGUEIRO",         29, "Escocia",       74),
    ("Maximilian Hjalde",    "ZAGUEIRO",         24, "Suecia",        72),
    ("Sergi Dominguez",      "ZAGUEIRO",         20, "Espanha",       71),
    ("Mauro Perkovic",       "ZAGUEIRO",         22, "Croacia",       69),
    ("Sadegh Moharrami",     "LATERAL_DIREITO",  29, "Ira",           72),
    ("Luka Stojkovic",       "LATERAL_DIREITO",  22, "Croacia",       70),
    ("Niko Galesic",         "LATERAL_ESQUERDO", 25, "Croacia",       71),
    ("Raul Torrente",        "LATERAL_ESQUERDO", 23, "Espanha",       71),
    ("Josip Misic",          "VOLANTE",          31, "Croacia",       74),
    ("Juan Cordoba",         "VOLANTE",          22, "Colombia",      71),
    ("Martin Baturina",      "MEIA_CENTRAL",     22, "Croacia",       77),
    ("Marko Soldo",          "MEIA_CENTRAL",     22, "Croacia",       71),
    ("Dario Spikic",         "MEIA_ATACANTE",    26, "Croacia",       72),
    ("Arber Hoxha",          "PONTA_DIREITA",    26, "Albania",       73),
    ("Takuya Ogiwara",       "PONTA_DIREITA",    24, "Japao",         71),
    ("Monsef Bakrar",        "PONTA_ESQUERDA",   24, "Argelia",       73),
    ("Sandro Kulenovic",     "CENTROAVANTE",     26, "Croacia",       74),
    ("Dion Drena Beljo",     "CENTROAVANTE",     23, "Croacia",       73),
],

# ───────────────────────── Servia ────────────────────────────────────────────
628: [  # Estrela Vermelha
    ("Omri Glazer",          "GOLEIRO",          29, "Israel",        74),
    ("Ivan Gutesa",          "GOLEIRO",          23, "Servia",        68),
    ("Uros Spajic",          "ZAGUEIRO",         32, "Servia",        73),
    ("Aleksandar Dragovic",  "ZAGUEIRO",         34, "Austria",       72),
    ("Rodrigao",             "ZAGUEIRO",         30, "Brasil",        72),
    ("Veljko Milosavljevic", "ZAGUEIRO",         18, "Servia",        71),
    ("Ognjen Mimovic",       "LATERAL_DIREITO",  21, "Servia",        70),
    ("Seol Young-woo",       "LATERAL_DIREITO",  27, "Coreia do Sul", 71),
    ("Milan Rodic",          "LATERAL_ESQUERDO", 34, "Servia",        70),
    ("Nikola Stankovic",     "LATERAL_ESQUERDO", 22, "Servia",        69),
    ("Mirko Ivanic",         "VOLANTE",          32, "Montenegro",    74),
    ("Timi Elsnik",          "VOLANTE",          27, "Eslovenia",     72),
    ("Rade Krunic",          "MEIA_CENTRAL",     32, "Bosnia",        74),
    ("Vasilije Kostov",      "MEIA_CENTRAL",     20, "Servia",        70),
    ("Andrija Maksimovic",   "MEIA_ATACANTE",    18, "Servia",        73),
    ("Silas Katompa",        "PONTA_DIREITA",    27, "RD Congo",      75),
    ("Nemanja Radonjic",     "PONTA_DIREITA",    29, "Servia",        73),
    ("Felicio Milson",       "PONTA_ESQUERDA",   24, "Angola",        72),
    ("Cherif Ndiaye",        "CENTROAVANTE",     29, "Senegal",       74),
    ("Marko Arnautovic",     "CENTROAVANTE",     36, "Austria",       74),
],

# ───────────────────────── Polonia ───────────────────────────────────────────
629: [  # Legia Varsovia
    ("Kacper Tobiasz",       "GOLEIRO",          23, "Polonia",       71),
    ("Dominik Hladun",       "GOLEIRO",          30, "Polonia",       68),
    ("Steve Kapuadi",        "ZAGUEIRO",         27, "Franca",        72),
    ("Radovan Pankov",       "ZAGUEIRO",         31, "Servia",        71),
    ("Jan Ziolkowski",       "ZAGUEIRO",         20, "Polonia",       71),
    ("Wojciech Urbanski",    "ZAGUEIRO",         22, "Polonia",       68),
    ("Pawel Wszolek",        "LATERAL_DIREITO",  33, "Polonia",       71),
    ("Ruben Vinagre",        "LATERAL_DIREITO",  26, "Portugal",      72),
    ("Patryk Kun",           "LATERAL_ESQUERDO", 30, "Polonia",       70),
    ("Arkadiusz Reca",       "LATERAL_ESQUERDO", 30, "Polonia",       70),
    ("Juergen Elitim",       "VOLANTE",          26, "Colombia",      72),
    ("Rafal Augustyniak",    "VOLANTE",          32, "Polonia",       71),
    ("Bartosz Kapustka",     "MEIA_CENTRAL",     29, "Polonia",       72),
    ("Claude Goncalves",     "MEIA_CENTRAL",     31, "Portugal",      70),
    ("Kacper Chodyna",       "MEIA_ATACANTE",    27, "Polonia",       70),
    ("Luquinhas",            "PONTA_DIREITA",    29, "Brasil",        72),
    ("Petar Stojanovic",     "PONTA_DIREITA",    30, "Eslovenia",     71),
    ("Migouel Alfarela",     "PONTA_ESQUERDA",   27, "Franca",        70),
    ("Mileta Rajovic",       "CENTROAVANTE",     26, "Servia",        72),
    ("Jean-Pierre Nsame",    "CENTROAVANTE",     32, "Camaroes",      71),
],

# ───────────────────────── Suecia ────────────────────────────────────────────
630: [  # Malmo FF
    ("Johan Dahlin",         "GOLEIRO",          39, "Suecia",        70),
    ("Ismael Diawara",       "GOLEIRO",          24, "Suecia",        67),
    ("Dennis Hadzikadunic",  "ZAGUEIRO",         27, "Bosnia",        71),
    ("Pontus Jansson",       "ZAGUEIRO",         34, "Suecia",        71),
    ("Colin Rosler",         "ZAGUEIRO",         25, "Alemanha",      69),
    ("Hugo Bolin",           "ZAGUEIRO",         22, "Suecia",        68),
    ("Jens Stryger Larsen",  "LATERAL_DIREITO",  34, "Dinamarca",     71),
    ("Noel Tornqvist",       "LATERAL_DIREITO",  22, "Suecia",        68),
    ("Gabriel Busanello",    "LATERAL_ESQUERDO", 27, "Brasil",        70),
    ("Martin Sviberg",       "LATERAL_ESQUERDO", 22, "Suecia",        67),
    ("Lasse Berg Johnsen",   "VOLANTE",          26, "Noruega",       71),
    ("Adi Nalic",            "VOLANTE",          26, "Bosnia",        70),
    ("Sergio Pena",          "MEIA_CENTRAL",     30, "Peru",          72),
    ("Otto Rosengren",       "MEIA_CENTRAL",     23, "Suecia",        69),
    ("Taha Ali",             "MEIA_ATACANTE",    27, "Noruega",       71),
    ("Arnor Sigurdsson",     "PONTA_DIREITA",    26, "Islandia",      72),
    ("Oliver Berg",          "PONTA_DIREITA",    32, "Suecia",        70),
    ("Sead Haksabanovic",    "PONTA_ESQUERDA",   26, "Montenegro",    72),
    ("Erik Botheim",         "CENTROAVANTE",     25, "Noruega",       73),
    ("Mohamed Buya Turay",   "CENTROAVANTE",     30, "Serra Leoa",    71),
],

# ───────────────────────── Romenia ───────────────────────────────────────────
631: [  # FCSB
    ("Stefan Tarnovanu",     "GOLEIRO",          25, "Romenia",       71),
    ("Lukas Zima",           "GOLEIRO",          31, "Tchequia",      68),
    ("Siyabonga Ngezana",    "ZAGUEIRO",         28, "Africa do Sul", 71),
    ("Mihai Popescu",        "ZAGUEIRO",         32, "Romenia",       70),
    ("Joyskim Dawa",         "ZAGUEIRO",         29, "Camaroes",      70),
    ("Daniel Graovac",       "ZAGUEIRO",         32, "Bosnia",        69),
    ("Alexandru Pantea",     "LATERAL_DIREITO",  24, "Romenia",       69),
    ("Valentin Cretu",       "LATERAL_DIREITO",  36, "Romenia",       68),
    ("Risto Radunovic",      "LATERAL_ESQUERDO", 33, "Montenegro",    70),
    ("Vlad Mihalcea",        "LATERAL_ESQUERDO", 21, "Romenia",       66),
    ("Marius Marin",         "VOLANTE",          27, "Romenia",       71),
    ("Adrian Sut",           "VOLANTE",          27, "Romenia",       70),
    ("Darius Olaru",         "MEIA_CENTRAL",     27, "Romenia",       73),
    ("Mihai Lixandru",       "MEIA_CENTRAL",     23, "Romenia",       69),
    ("Florin Tanase",        "MEIA_ATACANTE",    31, "Romenia",       73),
    ("Dennis Politic",       "PONTA_DIREITA",    25, "Romenia",       71),
    ("David Miculescu",      "PONTA_DIREITA",    24, "Romenia",       70),
    ("Juri Cisotti",         "PONTA_ESQUERDA",   32, "Italia",        69),
    ("Daniel Birligea",      "CENTROAVANTE",     25, "Romenia",       72),
    ("Alexandru Stoian",     "CENTROAVANTE",     19, "Romenia",       67),
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
