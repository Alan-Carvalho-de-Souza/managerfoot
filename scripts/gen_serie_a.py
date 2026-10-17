# -*- coding: utf-8 -*-
"""
gen_serie_a.py
Gera seed_serie_a.json com os 20 clubes da Serie A italiana 2025/26
(divisao=14, pais="Italia") e seus elencos REAIS (plantel ~ago/2025 — inicio da
temporada). Mesmo pipeline do gen_laliga.py: converte o "overall" de cada
jogador nos 7 atributos do jogo aplicando ajustes por posicao.

Uso: python scripts/gen_serie_a.py
"""
import json
import os
import random

# ─────────────────── Metadados dos 20 clubes (id 521-540) ─────────────────────
# (id, nome, cidade, estado, nivel, saldo(centavos), estadio, cap, preco, tatica, estilo, reputacao)
TIMES = [
    (521, "Inter de Milao",   "Milao",     "LOM", 10, 90_000_000_00, "Giuseppe Meazza",          75817, 7500, "3-5-2",   "OFENSIVO",     90),
    (522, "Napoli",           "Napoles",   "CAM", 10, 85_000_000_00, "Diego Armando Maradona",   54726, 7200, "4-3-3",   "EQUILIBRADO",  89),
    (523, "Juventus",         "Turim",     "PIE",  9, 80_000_000_00, "Allianz Stadium",          41507, 7000, "4-2-3-1", "EQUILIBRADO",  88),
    (524, "Milan",            "Milao",     "LOM",  9, 75_000_000_00, "Giuseppe Meazza",          75817, 7000, "4-2-3-1", "OFENSIVO",     87),
    (525, "Atalanta",         "Bergamo",   "LOM",  9, 60_000_000_00, "Gewiss Stadium",           19146, 6000, "3-4-1-2", "OFENSIVO",     85),
    (526, "Roma",             "Roma",      "LAZ",  9, 55_000_000_00, "Stadio Olimpico",          70634, 6500, "3-4-2-1", "EQUILIBRADO",  84),
    (527, "Lazio",            "Roma",      "LAZ",  8, 45_000_000_00, "Stadio Olimpico",          70634, 6200, "4-3-3",   "EQUILIBRADO",  82),
    (528, "Fiorentina",       "Florenca",  "TOS",  8, 42_000_000_00, "Artemio Franchi",          43147, 5800, "3-5-2",   "EQUILIBRADO",  80),
    (529, "Bologna",          "Bolonha",   "EMI",  8, 40_000_000_00, "Renato Dall'Ara",          36462, 5600, "4-2-3-1", "OFENSIVO",     80),
    (530, "Como",             "Como",      "LOM",  7, 38_000_000_00, "Giuseppe Sinigaglia",      13602, 5000, "4-2-3-1", "OFENSIVO",     74),
    (531, "Torino",           "Turim",     "PIE",  7, 26_000_000_00, "Olimpico Grande Torino",   27958, 5000, "3-5-2",   "DEFENSIVO",    75),
    (532, "Udinese",          "Udine",     "FVG",  7, 24_000_000_00, "Bluenergy Stadium",        25144, 4800, "3-5-2",   "EQUILIBRADO",  73),
    (533, "Genoa",            "Genova",    "LIG",  7, 22_000_000_00, "Luigi Ferraris",           36599, 4800, "3-5-2",   "DEFENSIVO",    73),
    (534, "Cagliari",         "Cagliari",  "SAR",  7, 17_000_000_00, "Unipol Domus",             16416, 4400, "4-4-2",   "DEFENSIVO",    69),
    (535, "Parma",            "Parma",     "EMI",  7, 18_000_000_00, "Ennio Tardini",            22885, 4400, "4-3-3",   "EQUILIBRADO",  69),
    (536, "Lecce",            "Lecce",     "PUG",  7, 14_000_000_00, "Via del Mare",             31533, 4200, "4-3-3",   "DEFENSIVO",    66),
    (537, "Sassuolo",         "Sassuolo",  "EMI",  7, 16_000_000_00, "Mapei Stadium",            21584, 4300, "4-3-3",   "OFENSIVO",     68),
    (538, "Hellas Verona",    "Verona",    "VEN",  7, 13_000_000_00, "Marcantonio Bentegodi",    39211, 4200, "3-4-2-1", "DEFENSIVO",    65),
    (539, "Cremonese",        "Cremona",   "LOM",  7, 12_000_000_00, "Giovanni Zini",            16003, 4000, "3-5-2",   "DEFENSIVO",    63),
    (540, "Pisa",             "Pisa",      "TOS",  7, 11_000_000_00, "Arena Garibaldi",          25000, 4000, "3-4-2-1", "DEFENSIVO",    62),
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

# ─────────────────── Elencos reais 2025/26 ────────────────────────────────────
# Formato: (nome, posicao, idade, nacionalidade, overall)
ROSTERS = {
521: [  # Inter de Milao
    ("Yann Sommer",         "GOLEIRO",          37, "Suica",     84),
    ("Josep Martinez",      "GOLEIRO",          27, "Espanha",   76),
    ("Benjamin Pavard",     "ZAGUEIRO",         29, "Franca",    83),
    ("Alessandro Bastoni",  "ZAGUEIRO",         26, "Italia",    86),
    ("Francesco Acerbi",    "ZAGUEIRO",         37, "Italia",    80),
    ("Stefan de Vrij",      "ZAGUEIRO",         33, "Holanda",   79),
    ("Yann Bisseck",        "ZAGUEIRO",         25, "Alemanha",  79),
    ("Manuel Akanji",       "ZAGUEIRO",         30, "Suica",     83),
    ("Denzel Dumfries",     "LATERAL_DIREITO",  29, "Holanda",   82),
    ("Matteo Darmian",      "LATERAL_DIREITO",  36, "Italia",    76),
    ("Federico Dimarco",    "LATERAL_ESQUERDO", 28, "Italia",    84),
    ("Carlos Augusto",      "LATERAL_ESQUERDO", 26, "Brasil",    79),
    ("Nicolo Barella",      "VOLANTE",          28, "Italia",    87),
    ("Hakan Calhanoglu",    "VOLANTE",          31, "Turquia",   85),
    ("Henrikh Mkhitaryan",  "MEIA_CENTRAL",     36, "Armenia",   80),
    ("Piotr Zielinski",     "MEIA_CENTRAL",     31, "Polonia",   82),
    ("Davide Frattesi",     "MEIA_CENTRAL",     26, "Italia",    81),
    ("Petar Sucic",         "VOLANTE",          22, "Croacia",   76),
    ("Kristjan Asllani",    "VOLANTE",          23, "Albania",   76),
    ("Marcus Thuram",       "CENTROAVANTE",     28, "Franca",    86),
    ("Lautaro Martinez",    "CENTROAVANTE",     28, "Argentina", 89),
    ("Ange-Yoan Bonny",     "CENTROAVANTE",     22, "Franca",    76),
    ("Francesco Pio Esposito", "CENTROAVANTE",  20, "Italia",    74),
    ("Luis Henrique",       "PONTA_DIREITA",    24, "Brasil",    77),
],
522: [  # Napoli
    ("Alex Meret",          "GOLEIRO",          28, "Italia",    82),
    ("Vanja Milinkovic-Savic", "GOLEIRO",       28, "Servia",    80),
    ("Amir Rrahmani",       "ZAGUEIRO",         31, "Kosovo",    81),
    ("Alessandro Buongiorno", "ZAGUEIRO",       26, "Italia",    83),
    ("Sam Beukema",         "ZAGUEIRO",         27, "Holanda",   79),
    ("Juan Jesus",          "ZAGUEIRO",         34, "Brasil",    74),
    ("Giovanni Di Lorenzo", "LATERAL_DIREITO",  32, "Italia",    83),
    ("Pasquale Mazzocchi",  "LATERAL_DIREITO",  30, "Italia",    73),
    ("Leonardo Spinazzola", "LATERAL_ESQUERDO", 32, "Italia",    77),
    ("Mathias Olivera",     "LATERAL_ESQUERDO", 28, "Uruguai",   79),
    ("Stanislav Lobotka",   "VOLANTE",          31, "Eslovaquia",84),
    ("Frank Anguissa",      "VOLANTE",          30, "Camaroes",  84),
    ("Billy Gilmour",       "VOLANTE",           24, "Escocia",  78),
    ("Scott McTominay",     "MEIA_CENTRAL",     29, "Escocia",   85),
    ("Kevin De Bruyne",     "MEIA_ATACANTE",    34, "Belgica",   88),
    ("Matteo Politano",     "PONTA_DIREITA",    32, "Italia",    80),
    ("David Neres",         "PONTA_ESQUERDA",   28, "Brasil",    80),
    ("Noa Lang",            "PONTA_ESQUERDA",   26, "Holanda",   78),
    ("Romelu Lukaku",       "CENTROAVANTE",     32, "Belgica",   84),
    ("Rasmus Hojlund",      "CENTROAVANTE",     22, "Dinamarca", 80),
    ("Lorenzo Lucca",       "CENTROAVANTE",     25, "Italia",    77),
],
523: [  # Juventus
    ("Michele Di Gregorio", "GOLEIRO",          28, "Italia",    82),
    ("Mattia Perin",        "GOLEIRO",          33, "Italia",    75),
    ("Gleison Bremer",      "ZAGUEIRO",         28, "Brasil",    85),
    ("Federico Gatti",      "ZAGUEIRO",         27, "Italia",    80),
    ("Pierre Kalulu",       "ZAGUEIRO",         25, "Franca",    80),
    ("Lloyd Kelly",         "ZAGUEIRO",         27, "Inglaterra",77),
    ("Daniele Rugani",      "ZAGUEIRO",         31, "Italia",    74),
    ("Juan Cabal",          "ZAGUEIRO",         24, "Colombia",  77),
    ("Andrea Cambiaso",     "LATERAL_ESQUERDO", 25, "Italia",    82),
    ("Joao Mario",          "LATERAL_DIREITO",  25, "Portugal",  75),
    ("Manuel Locatelli",    "VOLANTE",          27, "Italia",    82),
    ("Khephren Thuram",     "VOLANTE",          24, "Franca",    82),
    ("Weston McKennie",     "MEIA_CENTRAL",     27, "EUA",       79),
    ("Teun Koopmeiners",    "MEIA_ATACANTE",    27, "Holanda",   82),
    ("Fabio Miretti",       "MEIA_CENTRAL",     22, "Italia",    76),
    ("Kenan Yildiz",        "MEIA_ATACANTE",    20, "Turquia",   82),
    ("Francisco Conceicao", "PONTA_DIREITA",    23, "Portugal",  81),
    ("Nico Gonzalez",       "PONTA_DIREITA",    27, "Argentina", 80),
    ("Jonathan David",      "CENTROAVANTE",     25, "Canada",    82),
    ("Dusan Vlahovic",      "CENTROAVANTE",     25, "Servia",    84),
    ("Randal Kolo Muani",   "CENTROAVANTE",     27, "Franca",    82),
    ("Lois Openda",         "CENTROAVANTE",     25, "Belgica",   80),
    ("Vasilije Adzic",      "MEIA_ATACANTE",    19, "Montenegro",72),
],
524: [  # Milan
    ("Mike Maignan",        "GOLEIRO",          30, "Franca",    86),
    ("Pietro Terracciano",  "GOLEIRO",          35, "Italia",    72),
    ("Fikayo Tomori",       "ZAGUEIRO",         27, "Inglaterra",81),
    ("Matteo Gabbia",       "ZAGUEIRO",         26, "Italia",    79),
    ("Strahinja Pavlovic",  "ZAGUEIRO",         24, "Servia",    80),
    ("Pervis Estupinan",    "LATERAL_ESQUERDO", 27, "Equador",   80),
    ("Alessandro Florenzi", "LATERAL_DIREITO",  34, "Italia",    73),
    ("Kyle Walker",         "LATERAL_DIREITO",  35, "Inglaterra",79),
    ("Zachary Athekame",    "LATERAL_DIREITO",  20, "Suica",     73),
    ("Youssouf Fofana",     "VOLANTE",          26, "Franca",    82),
    ("Ruben Loftus-Cheek",  "MEIA_CENTRAL",     29, "Inglaterra",79),
    ("Samuele Ricci",       "VOLANTE",          24, "Italia",    79),
    ("Luka Modric",         "MEIA_CENTRAL",     40, "Croacia",   82),
    ("Ardon Jashari",       "VOLANTE",          23, "Suica",     78),
    ("Christian Pulisic",   "PONTA_DIREITA",    27, "EUA",       84),
    ("Rafael Leao",         "PONTA_ESQUERDA",   26, "Portugal",  85),
    ("Samu Chukwueze",      "PONTA_DIREITA",    26, "Nigeria",   78),
    ("Christopher Nkunku",  "SEGUNDA_ATACANTE", 27, "Franca",    82),
    ("Santiago Gimenez",    "CENTROAVANTE",     24, "Mexico",    80),
],
525: [  # Atalanta
    ("Marco Carnesecchi",   "GOLEIRO",          25, "Italia",    81),
    ("Francesco Rossi",     "GOLEIRO",          34, "Italia",    68),
    ("Berat Djimsiti",      "ZAGUEIRO",         32, "Albania",   79),
    ("Isak Hien",           "ZAGUEIRO",         26, "Suecia",    81),
    ("Odilon Kossounou",    "ZAGUEIRO",         24, "Costa do Marfim", 78),
    ("Giorgio Scalvini",    "ZAGUEIRO",         22, "Italia",    80),
    ("Sead Kolasinac",      "ZAGUEIRO",         32, "Bosnia",    78),
    ("Honest Ahanor",       "ZAGUEIRO",         17, "Italia",    70),
    ("Davide Zappacosta",   "LATERAL_DIREITO",  33, "Italia",    78),
    ("Raoul Bellanova",     "LATERAL_DIREITO",  25, "Italia",    80),
    ("Marten de Roon",      "VOLANTE",          34, "Holanda",   78),
    ("Ederson",             "VOLANTE",          26, "Brasil",    83),
    ("Marco Brescianini",   "VOLANTE",          25, "Italia",    76),
    ("Mario Pasalic",       "MEIA_CENTRAL",     30, "Croacia",   79),
    ("Charles De Ketelaere","MEIA_ATACANTE",    24, "Belgica",   82),
    ("Lazar Samardzic",     "MEIA_ATACANTE",    23, "Servia",    79),
    ("Daniel Maldini",      "MEIA_ATACANTE",    24, "Italia",    77),
    ("Ademola Lookman",     "PONTA_ESQUERDA",   28, "Nigeria",   84),
    ("Kamaldeen Sulemana",  "PONTA_ESQUERDA",   23, "Gana",      77),
    ("Gianluca Scamacca",   "CENTROAVANTE",     26, "Italia",    80),
    ("Nikola Krstovic",     "CENTROAVANTE",     25, "Montenegro",78),
],
526: [  # Roma
    ("Mile Svilar",         "GOLEIRO",          26, "Servia",    82),
    ("Pierluigi Gollini",   "GOLEIRO",          30, "Italia",    73),
    ("Gianluca Mancini",    "ZAGUEIRO",         29, "Italia",    80),
    ("Evan Ndicka",         "ZAGUEIRO",         26, "Costa do Marfim", 81),
    ("Mario Hermoso",       "ZAGUEIRO",         30, "Espanha",   78),
    ("Zeki Celik",          "LATERAL_DIREITO",  28, "Turquia",   76),
    ("Wesley",              "LATERAL_DIREITO",  21, "Brasil",    77),
    ("Angelino",            "LATERAL_ESQUERDO", 28, "Espanha",   80),
    ("Anass Salah-Eddine",  "LATERAL_ESQUERDO", 23, "Holanda",   74),
    ("Manu Kone",           "VOLANTE",          24, "Franca",    82),
    ("Bryan Cristante",     "VOLANTE",          30, "Italia",    79),
    ("Neil El Aynaoui",     "VOLANTE",          24, "Marrocos",  76),
    ("Lorenzo Pellegrini",  "MEIA_ATACANTE",    29, "Italia",    80),
    ("Tommaso Baldanzi",    "MEIA_ATACANTE",    22, "Italia",    76),
    ("Matias Soule",        "PONTA_DIREITA",    22, "Argentina", 80),
    ("Stephan El Shaarawy", "PONTA_ESQUERDA",   33, "Italia",    76),
    ("Paulo Dybala",        "SEGUNDA_ATACANTE", 32, "Argentina", 83),
    ("Artem Dovbyk",        "CENTROAVANTE",     28, "Ucrania",   80),
    ("Evan Ferguson",       "CENTROAVANTE",     21, "Irlanda",   77),
],
527: [  # Lazio
    ("Ivan Provedel",       "GOLEIRO",          31, "Italia",    80),
    ("Christos Mandas",     "GOLEIRO",          24, "Grecia",    74),
    ("Alessio Romagnoli",   "ZAGUEIRO",         30, "Italia",    80),
    ("Mario Gila",          "ZAGUEIRO",         25, "Espanha",   79),
    ("Patric",              "ZAGUEIRO",         32, "Espanha",   74),
    ("Nuno Tavares",        "LATERAL_ESQUERDO", 25, "Portugal",  79),
    ("Manuel Lazzari",      "LATERAL_DIREITO",  32, "Italia",    76),
    ("Adam Marusic",        "LATERAL_DIREITO",  33, "Montenegro",76),
    ("Elseid Hysaj",        "LATERAL_DIREITO",  31, "Albania",   73),
    ("Nicolo Rovella",      "VOLANTE",          24, "Italia",    80),
    ("Danilo Cataldi",      "VOLANTE",          31, "Italia",    76),
    ("Matteo Guendouzi",    "VOLANTE",          26, "Franca",    81),
    ("Mattia Vecino",       "MEIA_CENTRAL",     34, "Uruguai",   74),
    ("Reda Belahyane",      "MEIA_CENTRAL",     21, "Marrocos",  73),
    ("Gustav Isaksen",      "PONTA_DIREITA",    24, "Dinamarca", 77),
    ("Mattia Zaccagni",     "PONTA_ESQUERDA",   30, "Italia",    81),
    ("Matteo Cancellieri",  "PONTA_DIREITA",    23, "Italia",    74),
    ("Pedro",               "SEGUNDA_ATACANTE", 38, "Espanha",   77),
    ("Taty Castellanos",    "CENTROAVANTE",     27, "Argentina", 79),
    ("Boulaye Dia",         "CENTROAVANTE",     29, "Senegal",   77),
],
528: [  # Fiorentina
    ("David de Gea",        "GOLEIRO",          35, "Espanha",   81),
    ("Tommaso Martinelli",  "GOLEIRO",          19, "Italia",    68),
    ("Pietro Comuzzo",      "ZAGUEIRO",         20, "Italia",    78),
    ("Luca Ranieri",        "ZAGUEIRO",         26, "Italia",    77),
    ("Marin Pongracic",     "ZAGUEIRO",         28, "Croacia",   78),
    ("Pablo Mari",          "ZAGUEIRO",         32, "Espanha",   76),
    ("Dodo",                "LATERAL_DIREITO",  27, "Brasil",    80),
    ("Robin Gosens",        "LATERAL_ESQUERDO", 31, "Alemanha",  78),
    ("Fabiano Parisi",      "LATERAL_ESQUERDO", 25, "Italia",    74),
    ("Rolando Mandragora",  "VOLANTE",          28, "Italia",    77),
    ("Nicolo Fagioli",      "MEIA_CENTRAL",     24, "Italia",    78),
    ("Simon Sohm",          "VOLANTE",          24, "Suica",     76),
    ("Amir Richardson",     "MEIA_CENTRAL",     23, "Marrocos",  74),
    ("Jacopo Fazzini",      "MEIA_ATACANTE",    22, "Italia",    75),
    ("Albert Gudmundsson",  "SEGUNDA_ATACANTE", 28, "Islandia",  80),
    ("Moise Kean",          "CENTROAVANTE",     25, "Italia",    82),
    ("Roberto Piccoli",     "CENTROAVANTE",     24, "Italia",    76),
    ("Edin Dzeko",          "CENTROAVANTE",     39, "Bosnia",    76),
],
529: [  # Bologna
    ("Lukasz Skorupski",    "GOLEIRO",          34, "Polonia",   78),
    ("Federico Ravaglia",   "GOLEIRO",          26, "Italia",    70),
    ("Jhon Lucumi",         "ZAGUEIRO",         27, "Colombia",  80),
    ("Torbjorn Heggem",     "ZAGUEIRO",         26, "Noruega",   76),
    ("Martin Vitik",        "ZAGUEIRO",         22, "Chequia",   75),
    ("Nicolo Casale",       "ZAGUEIRO",         27, "Italia",    76),
    ("Emil Holm",           "LATERAL_DIREITO",  25, "Suecia",    75),
    ("Lorenzo De Silvestri","LATERAL_DIREITO",  37, "Italia",    72),
    ("Charalampos Lykogiannis", "LATERAL_ESQUERDO", 32, "Grecia", 74),
    ("Juan Miranda",        "LATERAL_ESQUERDO", 25, "Espanha",   75),
    ("Remo Freuler",        "VOLANTE",          33, "Suica",     78),
    ("Lewis Ferguson",      "VOLANTE",          26, "Escocia",   80),
    ("Nikola Moro",         "VOLANTE",          27, "Croacia",   74),
    ("Tommaso Pobega",      "MEIA_CENTRAL",     26, "Italia",    76),
    ("Riccardo Orsolini",   "PONTA_DIREITA",    28, "Italia",    81),
    ("Jens Odgaard",        "MEIA_ATACANTE",    26, "Dinamarca", 76),
    ("Nicolo Cambiaghi",    "PONTA_ESQUERDA",   24, "Italia",    74),
    ("Benjamin Dominguez",  "PONTA_ESQUERDA",   22, "Argentina", 74),
    ("Santiago Castro",     "CENTROAVANTE",     21, "Argentina", 79),
    ("Thijs Dallinga",      "CENTROAVANTE",     25, "Holanda",   76),
],
530: [  # Como
    ("Pepe Reina",          "GOLEIRO",          43, "Espanha",   72),
    ("Jean Butez",          "GOLEIRO",          30, "Franca",    75),
    ("Marc-Oliver Kempf",   "ZAGUEIRO",         30, "Alemanha",  74),
    ("Edoardo Goldaniga",   "ZAGUEIRO",         32, "Italia",    73),
    ("Alberto Dossena",     "ZAGUEIRO",         27, "Italia",    75),
    ("Jacobo Ramon",        "ZAGUEIRO",         20, "Espanha",   74),
    ("Ivan Smolcic",        "ZAGUEIRO",         25, "Croacia",   73),
    ("Alex Valle",          "LATERAL_ESQUERDO", 21, "Espanha",   74),
    ("Maximo Perrone",      "VOLANTE",          22, "Argentina", 76),
    ("Maxence Caqueret",    "VOLANTE",          25, "Franca",    78),
    ("Sergi Roberto",       "MEIA_CENTRAL",     33, "Espanha",   76),
    ("Lucas Da Cunha",      "MEIA_CENTRAL",     24, "Franca",    74),
    ("Nicolas Paz",         "MEIA_ATACANTE",    21, "Argentina", 80),
    ("Assane Diao",         "PONTA_ESQUERDA",   20, "Espanha",   77),
    ("Jesus Rodriguez",     "PONTA_ESQUERDA",   20, "Espanha",   75),
    ("Jayden Addai",        "PONTA_DIREITA",    20, "Holanda",   74),
    ("Gabriel Strefezza",   "PONTA_DIREITA",    28, "Brasil",    75),
    ("Alvaro Morata",       "CENTROAVANTE",     33, "Espanha",   81),
    ("Patrick Cutrone",     "CENTROAVANTE",     27, "Italia",    74),
    ("Anastasios Douvikas", "CENTROAVANTE",     26, "Grecia",    75),
],
531: [  # Torino
    ("Franco Israel",       "GOLEIRO",          25, "Uruguai",   74),
    ("Alberto Paleari",     "GOLEIRO",          33, "Italia",    70),
    ("Saul Coco",           "ZAGUEIRO",         26, "Guine Equatorial", 76),
    ("Guillermo Maripan",   "ZAGUEIRO",         31, "Chile",     77),
    ("Sebastian Walukiewicz","ZAGUEIRO",        25, "Polonia",   74),
    ("Perr Schuurs",        "ZAGUEIRO",         26, "Holanda",   77),
    ("Adam Masina",         "ZAGUEIRO",         31, "Marrocos",  73),
    ("Marcus Pedersen",     "LATERAL_DIREITO",  25, "Noruega",   74),
    ("Valentino Lazaro",    "LATERAL_DIREITO",  29, "Austria",   74),
    ("Cristiano Biraghi",   "LATERAL_ESQUERDO", 33, "Italia",    74),
    ("Ivan Ilic",           "VOLANTE",          24, "Servia",    76),
    ("Cesare Casadei",      "MEIA_CENTRAL",     22, "Italia",    77),
    ("Gvidas Gineitis",     "VOLANTE",          21, "Lituania",  73),
    ("Nikola Vlasic",       "MEIA_ATACANTE",    28, "Croacia",   77),
    ("Cyril Ngonge",        "PONTA_DIREITA",    25, "Belgica",   75),
    ("Giovanni Simeone",    "CENTROAVANTE",     30, "Argentina", 78),
    ("Che Adams",           "CENTROAVANTE",     29, "Escocia",   76),
    ("Antonio Sanabria",    "CENTROAVANTE",     29, "Paraguai",  75),
],
532: [  # Udinese
    ("Razvan Sava",         "GOLEIRO",          23, "Romenia",   74),
    ("Daniele Padelli",     "GOLEIRO",          40, "Italia",    66),
    ("Oumar Solet",         "ZAGUEIRO",         25, "Franca",    78),
    ("Thomas Kristensen",   "ZAGUEIRO",         28, "Dinamarca", 75),
    ("Lautaro Giannetti",   "ZAGUEIRO",         31, "Argentina", 74),
    ("Christian Kabasele",  "ZAGUEIRO",         34, "Belgica",   73),
    ("Kingsley Ehizibue",   "LATERAL_DIREITO",  30, "Nigeria",   72),
    ("Alessandro Zanoli",   "LATERAL_DIREITO",  25, "Italia",    74),
    ("Jordan Zemura",       "LATERAL_ESQUERDO", 26, "Zimbabue",  73),
    ("Hassane Kamara",      "LATERAL_ESQUERDO", 31, "Costa do Marfim", 74),
    ("Jesper Karlstrom",    "VOLANTE",          30, "Suecia",    73),
    ("Sandi Lovric",        "VOLANTE",          27, "Eslovenia", 76),
    ("Arthur Atta",         "MEIA_CENTRAL",     22, "Franca",    74),
    ("Jurgen Ekkelenkamp",  "MEIA_ATACANTE",    25, "Holanda",   74),
    ("Nicolo Zaniolo",      "MEIA_ATACANTE",    26, "Italia",    76),
    ("Iker Bravo",          "SEGUNDA_ATACANTE", 20, "Espanha",   73),
    ("Vakoun Bayo",         "CENTROAVANTE",     28, "Costa do Marfim", 74),
    ("Keinan Davis",        "CENTROAVANTE",     27, "Inglaterra",75),
],
533: [  # Genoa
    ("Nicola Leali",        "GOLEIRO",          32, "Italia",    75),
    ("Daniele Sommariva",   "GOLEIRO",          26, "Italia",    66),
    ("Johan Vasquez",       "ZAGUEIRO",         27, "Mexico",    78),
    ("Leo Ostigard",        "ZAGUEIRO",         26, "Noruega",   76),
    ("Sebastian Otoa",      "ZAGUEIRO",         21, "Dinamarca", 72),
    ("Brooke Norton-Cuffy", "LATERAL_DIREITO",  21, "Inglaterra",74),
    ("Stefano Sabelli",     "LATERAL_DIREITO",  32, "Italia",    73),
    ("Aaron Martin",        "LATERAL_ESQUERDO", 28, "Espanha",   74),
    ("Morten Frendrup",     "VOLANTE",          24, "Dinamarca", 78),
    ("Patrizio Masini",     "MEIA_CENTRAL",     22, "Italia",    72),
    ("Mikael Ellertsson",   "VOLANTE",          23, "Islandia",  73),
    ("Nicolae Stanciu",     "MEIA_ATACANTE",    32, "Romenia",   76),
    ("Ruslan Malinovskyi",  "MEIA_ATACANTE",    32, "Ucrania",   77),
    ("Valentin Carboni",    "MEIA_ATACANTE",    20, "Argentina", 76),
    ("Vitinha",             "CENTROAVANTE",     25, "Portugal",  75),
    ("Andrea Pinamonti",    "CENTROAVANTE",     26, "Italia",    77),
    ("Lorenzo Colombo",     "CENTROAVANTE",     23, "Italia",    75),
    ("Caleb Ekuban",        "CENTROAVANTE",     31, "Gana",      73),
],
534: [  # Cagliari
    ("Elia Caprile",        "GOLEIRO",          24, "Italia",    76),
    ("Simone Scuffet",      "GOLEIRO",          29, "Italia",    73),
    ("Sebastiano Luperto",  "ZAGUEIRO",         29, "Italia",    75),
    ("Yerry Mina",          "ZAGUEIRO",         31, "Colombia",  77),
    ("Adam Obert",          "ZAGUEIRO",         23, "Eslovaquia",73),
    ("Gabriele Zappa",      "LATERAL_DIREITO",  26, "Italia",    73),
    ("Nadir Zortea",        "LATERAL_DIREITO",  26, "Italia",    73),
    ("Tommaso Augello",     "LATERAL_ESQUERDO", 31, "Italia",    73),
    ("Antoine Makoumbou",   "VOLANTE",          27, "Congo",     73),
    ("Matteo Prati",        "VOLANTE",          22, "Italia",    73),
    ("Razvan Marin",        "VOLANTE",          29, "Romenia",   76),
    ("Michael Folorunsho",  "MEIA_CENTRAL",     27, "Italia",    76),
    ("Alessandro Deiola",   "VOLANTE",          30, "Italia",    72),
    ("Gianluca Gaetano",    "MEIA_ATACANTE",    25, "Italia",    75),
    ("Zito Luvumbo",        "PONTA_ESQUERDA",   23, "Angola",    75),
    ("Mattia Felici",       "PONTA_ESQUERDA",   24, "Italia",    72),
    ("Sebastiano Esposito", "SEGUNDA_ATACANTE", 23, "Italia",    76),
    ("Andrea Belotti",      "CENTROAVANTE",     32, "Italia",    76),
    ("Leonardo Pavoletti",  "CENTROAVANTE",     37, "Italia",    72),
    ("Gennaro Borrelli",    "CENTROAVANTE",     25, "Italia",    72),
],
535: [  # Parma
    ("Zion Suzuki",         "GOLEIRO",          23, "Japao",     78),
    ("Edoardo Corvi",       "GOLEIRO",          24, "Italia",    66),
    ("Lautaro Valenti",     "ZAGUEIRO",         26, "Argentina", 74),
    ("Yordan Osorio",       "ZAGUEIRO",         31, "Venezuela", 73),
    ("Alessandro Circati",  "ZAGUEIRO",         21, "Australia", 75),
    ("Botond Balogh",       "ZAGUEIRO",         23, "Hungria",   73),
    ("Enrico Delprato",     "LATERAL_DIREITO",  26, "Italia",    74),
    ("Woyo Coulibaly",      "LATERAL_DIREITO",  26, "Franca",    72),
    ("Emanuele Valeri",     "LATERAL_ESQUERDO", 27, "Italia",    73),
    ("Adrian Bernabe",      "MEIA_CENTRAL",     24, "Espanha",   77),
    ("Mandela Keita",       "VOLANTE",          23, "Belgica",   73),
    ("Hernani",             "VOLANTE",          31, "Brasil",    72),
    ("Pontus Almqvist",     "PONTA_DIREITA",    26, "Suecia",    73),
    ("Adrien Tanko",        "PONTA_ESQUERDA",   22, "Gana",      72),
    ("Mateo Pellegrino",    "CENTROAVANTE",     24, "Argentina", 74),
    ("Milan Djuric",        "CENTROAVANTE",     35, "Bosnia",    73),
    ("Nikola Sekulov",      "SEGUNDA_ATACANTE", 23, "Italia",    72),
    ("Filippo Terracciano", "ZAGUEIRO",         22, "Italia",    73),
],
536: [  # Lecce
    ("Wladimiro Falcone",   "GOLEIRO",          30, "Italia",    76),
    ("Christian Fruchtl",   "GOLEIRO",          25, "Alemanha",  71),
    ("Federico Baschirotto","ZAGUEIRO",         29, "Italia",    75),
    ("Kialonda Gaspar",     "ZAGUEIRO",         25, "Angola",    73),
    ("Jean Gallo",          "ZAGUEIRO",         24, "Italia",    72),
    ("Antonino Gallo",      "LATERAL_ESQUERDO", 25, "Italia",    74),
    ("Valentin Gendrey",    "LATERAL_DIREITO",  25, "Franca",    73),
    ("Danilo Veiga",        "LATERAL_DIREITO",  22, "Portugal",  72),
    ("Lassana Coulibaly",   "VOLANTE",          29, "Mali",      74),
    ("Ylber Ramadani",      "VOLANTE",          29, "Albania",   74),
    ("Balthazar Pierret",   "VOLANTE",          24, "Franca",    72),
    ("Medon Berisha",       "MEIA_CENTRAL",     22, "Albania",   72),
    ("Rares Ilie",          "MEIA_ATACANTE",    22, "Romenia",   72),
    ("Santiago Pierotti",   "PONTA_DIREITA",    24, "Argentina", 73),
    ("Lameck Banda",        "PONTA_ESQUERDA",   24, "Zambia",    74),
    ("Riccardo Sottil",     "PONTA_ESQUERDA",   26, "Italia",    74),
    ("Nicola Sansone",      "SEGUNDA_ATACANTE", 34, "Italia",    72),
    ("Francesco Camarda",   "CENTROAVANTE",     17, "Italia",    73),
    ("Tete Morente",        "PONTA_ESQUERDA",   28, "Espanha",   73),
],
537: [  # Sassuolo
    ("Arijanet Muric",      "GOLEIRO",          27, "Kosovo",    76),
    ("Stefano Turati",      "GOLEIRO",          24, "Italia",    72),
    ("Tarik Muharemovic",   "ZAGUEIRO",         22, "Bosnia",    74),
    ("Jay Idzes",           "ZAGUEIRO",         25, "Indonesia", 74),
    ("Filippo Romagna",     "ZAGUEIRO",         28, "Italia",    72),
    ("Fali Cande",          "ZAGUEIRO",         25, "Guine-Bissau", 72),
    ("Josh Doig",           "LATERAL_ESQUERDO", 23, "Escocia",   74),
    ("Matias Vina",         "LATERAL_ESQUERDO", 28, "Uruguai",   74),
    ("Daniel Boloca",       "VOLANTE",          27, "Romenia",   74),
    ("Kristian Thorstvedt", "MEIA_CENTRAL",     26, "Noruega",   76),
    ("Ismael Kone",         "VOLANTE",          23, "Canada",    73),
    ("Nemanja Matic",       "VOLANTE",          37, "Servia",    76),
    ("Cristian Volpato",    "MEIA_ATACANTE",    22, "Australia", 73),
    ("Domenico Berardi",    "PONTA_DIREITA",    31, "Italia",    79),
    ("Armand Lauriente",    "PONTA_ESQUERDA",   27, "Franca",    77),
    ("Andrea Pinamonti",    "CENTROAVANTE",     26, "Italia",    77),
    ("Walid Cheddira",      "CENTROAVANTE",     27, "Marrocos",  74),
    ("Laurentiu Pirvu",     "CENTROAVANTE",     22, "Romenia",   70),
],
538: [  # Hellas Verona
    ("Lorenzo Montipo",     "GOLEIRO",          29, "Italia",    74),
    ("Simone Perilli",      "GOLEIRO",          31, "Italia",    68),
    ("Giangiacomo Magnani", "ZAGUEIRO",         30, "Italia",    72),
    ("Martin Frese",        "ZAGUEIRO",         27, "Dinamarca", 72),
    ("Daniele Ghilardi",    "ZAGUEIRO",         22, "Italia",    73),
    ("Victor Nelsson",      "ZAGUEIRO",         27, "Dinamarca", 75),
    ("Jackson Tchatchoua",  "LATERAL_DIREITO",  23, "Camaroes",  74),
    ("Fabio Depaoli",       "LATERAL_DIREITO",  28, "Italia",    72),
    ("Domagoj Bradaric",    "LATERAL_ESQUERDO", 26, "Croacia",   73),
    ("Suat Serdar",         "VOLANTE",          28, "Alemanha",  74),
    ("Grigoris Kastanos",   "MEIA_CENTRAL",     27, "Chipre",    72),
    ("Antoine Bernede",     "VOLANTE",          26, "Franca",    72),
    ("Ondrej Duda",         "MEIA_ATACANTE",    30, "Eslovaquia",74),
    ("Tomas Suslov",        "MEIA_ATACANTE",    23, "Eslovaquia",74),
    ("Gift Orban",          "CENTROAVANTE",     23, "Nigeria",   75),
    ("Casper Tengstedt",    "CENTROAVANTE",     25, "Dinamarca", 73),
    ("Amin Sarr",           "CENTROAVANTE",     24, "Suecia",    73),
    ("Daniel Mosquera",     "CENTROAVANTE",     23, "Colombia",  72),
],
539: [  # Cremonese
    ("Emil Audero",         "GOLEIRO",          28, "Italia",    75),
    ("Marco Silvestri",     "GOLEIRO",          34, "Italia",    73),
    ("Matteo Bianchetti",   "ZAGUEIRO",         32, "Italia",    72),
    ("Federico Ceccherini", "ZAGUEIRO",         33, "Italia",    72),
    ("Filippo Terracciano", "ZAGUEIRO",         22, "Italia",    73),
    ("Leonardo Sernicola",  "LATERAL_DIREITO",  28, "Italia",    72),
    ("Romano Floriani Mussolini", "LATERAL_DIREITO", 22, "Italia", 72),
    ("Charles Pickel",      "VOLANTE",          28, "Suica",     72),
    ("Michele Collocolo",   "VOLANTE",          26, "Italia",    72),
    ("Warren Bondo",        "VOLANTE",          22, "Franca",    73),
    ("Martin Payero",       "MEIA_CENTRAL",     27, "Argentina", 73),
    ("Jari Vandeputte",     "MEIA_ATACANTE",    29, "Belgica",   72),
    ("Franco Vazquez",      "MEIA_ATACANTE",    36, "Argentina", 74),
    ("Alessio Zerbin",      "PONTA_ESQUERDA",   26, "Italia",    73),
    ("Federico Bonazzoli",  "CENTROAVANTE",     28, "Italia",    74),
    ("Jamie Vardy",         "CENTROAVANTE",     38, "Inglaterra",75),
    ("Manuel De Luca",      "CENTROAVANTE",     27, "Italia",    72),
    ("Faris Moumbagna",     "CENTROAVANTE",     25, "Camaroes",  73),
],
540: [  # Pisa
    ("Adrian Semper",       "GOLEIRO",          27, "Croacia",   73),
    ("Louis Thomas Buffon", "GOLEIRO",          22, "Italia",    68),
    ("Antonio Caracciolo",  "ZAGUEIRO",         34, "Italia",    71),
    ("Simone Canestrelli",  "ZAGUEIRO",         25, "Italia",    74),
    ("Arturo Calabresi",    "ZAGUEIRO",         29, "Italia",    72),
    ("Giovanni Bonfanti",   "ZAGUEIRO",         22, "Italia",    72),
    ("Samuele Angori",      "LATERAL_ESQUERDO", 22, "Italia",    72),
    ("Idrissa Toure",       "LATERAL_DIREITO",  27, "Alemanha",  72),
    ("Marius Marin",        "VOLANTE",          27, "Romenia",   74),
    ("Michel Aebischer",    "VOLANTE",          28, "Suica",     76),
    ("Ebenezer Akinsanmiro","VOLANTE",          20, "Nigeria",   73),
    ("Matteo Tramoni",      "MEIA_ATACANTE",    25, "Franca",    74),
    ("Juan Cuadrado",       "PONTA_DIREITA",    37, "Colombia",  73),
    ("Stefano Moreo",       "SEGUNDA_ATACANTE", 32, "Italia",    72),
    ("M'Bala Nzola",        "CENTROAVANTE",     29, "Angola",    75),
    ("Henrik Meister",      "CENTROAVANTE",     22, "Dinamarca", 73),
    ("Mefail Osmani",       "ZAGUEIRO",         23, "Kosovo",    70),
],
}

# ─────────────────── Validação e monta seed ───────────────────────────────────
VALID_POS = set(DELTAS.keys())
data = {"times": [], "jogadores": []}
jogador_id = 12000

for (tid, tnome, tcidade, testado, tnivel, tsaldo, testadio, tcap, tpreco, ttatica, testilo, trep) in TIMES:
    data["times"].append({
        "id": tid, "nome": tnome, "cidade": tcidade, "estado": testado,
        "divisao": 14, "pais": "Italia", "nivel": tnivel, "saldo": tsaldo,
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
out_path = os.path.join("app", "src", "main", "assets", "seed_serie_a.json")
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
