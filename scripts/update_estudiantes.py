#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Update Estudiantes LP (time_id=86) players in seed_data.json with real Transfermarkt 2026 data."""

import json

FILE_PATH = r"c:\Dev\ManagerFoot\app\src\main\assets\seed_data.json"

def euro_to_game(euros):
    """Convert EUR market value to game currency (EUR x 10)."""
    return int(euros * 10)

def vm_to_salario(vm):
    """Salary ~15% of market value, minimum 75000."""
    return max(75_000, int(vm * 0.15))

# ─── Updates for existing Estudiantes IDs (1840–1861) ───────────────────────
# Only nome, nome_abrev, idade, posicao, valor_mercado and salario are changed.
# Existing stats (forca, tecnica, passe, etc.) are preserved.
UPDATES = {
    1840: {"nome": "Fernando Muslera",      "nome_abrev": "F. Muslera",      "idade": 39, "posicao": "GOLEIRO",          "valor_mercado": euro_to_game(750_000)},
    1841: {"nome": "Fabricio Iacovich",      "nome_abrev": "F. Iacovich",     "idade": 24, "posicao": "GOLEIRO",          "valor_mercado": euro_to_game(500_000)},
    1842: {"nome": "Rodrigo Borzone",        "nome_abrev": "R. Borzone",      "idade": 21, "posicao": "GOLEIRO",          "valor_mercado": euro_to_game(50_000)},
    1843: {"nome": "Tomás Palacios",         "nome_abrev": "T. Palacios",     "idade": 22, "posicao": "ZAGUEIRO",         "valor_mercado": euro_to_game(4_500_000)},
    1844: {"nome": "Santiago Núñez",         "nome_abrev": "S. Núñez",        "idade": 25, "posicao": "ZAGUEIRO",         "valor_mercado": euro_to_game(2_000_000)},
    1845: {"nome": "Leandro González Pírez", "nome_abrev": "L. González",     "idade": 34, "posicao": "ZAGUEIRO",         "valor_mercado": euro_to_game(375_000)},
    1846: {"nome": "Valente Pierani",        "nome_abrev": "V. Pierani",      "idade": 20, "posicao": "ZAGUEIRO",         "valor_mercado": euro_to_game(200_000)},
    1847: {"nome": "Ramiro Funes Mori",      "nome_abrev": "R. Funes Mori",   "idade": 35, "posicao": "ZAGUEIRO",         "valor_mercado": euro_to_game(75_000)},
    1848: {"nome": "Eros Mancuso",           "nome_abrev": "E. Mancuso",      "idade": 27, "posicao": "LATERAL_DIREITO",  "valor_mercado": euro_to_game(3_200_000)},
    1849: {"nome": "Eric Meza",              "nome_abrev": "E. Meza",         "idade": 27, "posicao": "LATERAL_DIREITO",  "valor_mercado": euro_to_game(2_200_000)},
    1850: {"nome": "Santiago Arzamendia",    "nome_abrev": "S. Arzamendia",   "idade": 27, "posicao": "LATERAL_ESQUERDO", "valor_mercado": euro_to_game(2_500_000)},
    1851: {"nome": "Gastón Benedetti",       "nome_abrev": "G. Benedetti",    "idade": 25, "posicao": "LATERAL_ESQUERDO", "valor_mercado": euro_to_game(2_000_000)},
    1852: {"nome": "Gabriel Neves",          "nome_abrev": "G. Neves",        "idade": 28, "posicao": "VOLANTE",          "valor_mercado": euro_to_game(1_300_000)},
    1853: {"nome": "Ezequiel Piovi",         "nome_abrev": "E. Piovi",        "idade": 33, "posicao": "VOLANTE",          "valor_mercado": euro_to_game(900_000)},
    1854: {"nome": "Mikel Amondarain",       "nome_abrev": "M. Amondarain",   "idade": 21, "posicao": "MEIA_CENTRAL",     "valor_mercado": euro_to_game(1_500_000)},
    1855: {"nome": "Alexis Castro",          "nome_abrev": "A. Castro",       "idade": 31, "posicao": "MEIA_CENTRAL",     "valor_mercado": euro_to_game(1_200_000)},
    1856: {"nome": "Lucas Cornejo",          "nome_abrev": "L. Cornejo",      "idade": 21, "posicao": "MEIA_CENTRAL",     "valor_mercado": euro_to_game(50_000)},
    1857: {"nome": "Facundo Farías",         "nome_abrev": "F. Farías",       "idade": 23, "posicao": "MEIA_ATACANTE",    "valor_mercado": euro_to_game(2_500_000)},
    1858: {"nome": "José Sosa",              "nome_abrev": "J. Sosa",         "idade": 40, "posicao": "MEIA_ATACANTE",    "valor_mercado": euro_to_game(75_000)},
    1859: {"nome": "Brian Aguirre",          "nome_abrev": "B. Aguirre",      "idade": 23, "posicao": "PONTA_ESQUERDA",   "valor_mercado": euro_to_game(3_800_000)},
    1860: {"nome": "Edwuin Cetré",           "nome_abrev": "E. Cetré",        "idade": 28, "posicao": "PONTA_ESQUERDA",   "valor_mercado": euro_to_game(3_000_000)},
    1861: {"nome": "Joaquín Tobio Burgos",   "nome_abrev": "J. Tobio Burgos", "idade": 21, "posicao": "PONTA_ESQUERDA",   "valor_mercado": euro_to_game(1_800_000)},
}

# ─── New players (IDs 3842–3847): Ponta Direita x2 + Centroavante x4 ───────
def new_player(pid, nome, abrev, idade, pos, forca, tecnica, passe, velocidade,
               finalizacao, defesa, fisico, contrato, vm_euros):
    vm = euro_to_game(vm_euros)
    return {
        "id": pid, "time_id": 86,
        "nome": nome, "nome_abrev": abrev, "idade": idade,
        "posicao": pos,
        "forca": forca, "tecnica": tecnica, "passe": passe, "velocidade": velocidade,
        "finalizacao": finalizacao, "defesa": defesa, "fisico": fisico,
        "salario": vm_to_salario(vm), "contrato": contrato,
        "valor_mercado": vm,
    }

NEW_PLAYERS = [
    new_player(3842, "Tiago Palacios",   "T. Palacios",  25, "PONTA_DIREITA",
               forca=76, tecnica=82, passe=74, velocidade=87, finalizacao=76, defesa=60, fisico=72,
               contrato=3, vm_euros=4_500_000),
    new_player(3843, "Fabricio Pérez",   "F. Pérez",     20, "PONTA_DIREITA",
               forca=67, tecnica=76, passe=70, velocidade=82, finalizacao=68, defesa=54, fisico=68,
               contrato=3, vm_euros=2_000_000),
    new_player(3844, "Adolfo Gaich",     "A. Gaich",     27, "CENTROAVANTE",
               forca=74, tecnica=69, passe=68, velocidade=72, finalizacao=78, defesa=58, fisico=80,
               contrato=4, vm_euros=800_000),
    new_player(3845, "Guido Carrillo",   "G. Carrillo",  34, "CENTROAVANTE",
               forca=72, tecnica=67, passe=66, velocidade=66, finalizacao=74, defesa=56, fisico=76,
               contrato=2, vm_euros=350_000),
    new_player(3846, "Lucas Alario",     "L. Alario",    33, "CENTROAVANTE",
               forca=71, tecnica=68, passe=67, velocidade=65, finalizacao=73, defesa=55, fisico=74,
               contrato=2, vm_euros=350_000),
    new_player(3847, "Franco Domínguez", "F. Domínguez", 18, "CENTROAVANTE",
               forca=60, tecnica=64, passe=63, velocidade=70, finalizacao=65, defesa=53, fisico=66,
               contrato=4, vm_euros=50_000),
]

# ─── Apply updates ───────────────────────────────────────────────────────────
with open(FILE_PATH, encoding="utf-8-sig") as f:
    data = json.load(f)

jogadores = data["jogadores"]
updated = 0
for j in jogadores:
    if j["id"] in UPDATES:
        patch = UPDATES[j["id"]]
        vm = patch["valor_mercado"]
        j.update(patch)
        j["salario"] = vm_to_salario(vm)
        updated += 1

# Append new players right after the last Estudiantes player (id=1861)
insert_after_id = 1861
insert_idx = next((i for i, j in enumerate(jogadores) if j["id"] == insert_after_id), None)
if insert_idx is not None:
    for offset, np in enumerate(NEW_PLAYERS):
        jogadores.insert(insert_idx + 1 + offset, np)

with open(FILE_PATH, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=3)

print(f"Updated {updated} existing players.")
print(f"Added {len(NEW_PLAYERS)} new players (IDs {NEW_PLAYERS[0]['id']}–{NEW_PLAYERS[-1]['id']}).")
print("Done.")
