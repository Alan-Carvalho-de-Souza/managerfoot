#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Update Chapecoense players in seed_data.json with real Transfermarkt data."""

import json
import re

FILE_PATH = r"c:\Dev\ManagerFoot\app\src\main\assets\seed_data.json"

# Real Chapecoense players from Transfermarkt 2025 season
# (market values in EUR x10 = game currency)
NEW_PLAYERS = [
    # Goleiros
    {"id": 641, "time_id": 28, "nome": "Anderson", "nome_abrev": "Anderson", "idade": 28,
     "posicao": "GOLEIRO", "forca": 57, "tecnica": 53, "passe": 55, "velocidade": 53,
     "finalizacao": 42, "defesa": 63, "fisico": 62, "salario": 675000, "contrato": 2, "valor_mercado": 4500000},

    {"id": 642, "time_id": 28, "nome": "Rafael Santos", "nome_abrev": "R. Santos", "idade": 37,
     "posicao": "GOLEIRO", "forca": 51, "tecnica": 51, "passe": 52, "velocidade": 46,
     "finalizacao": 38, "defesa": 57, "fisico": 55, "salario": 150000, "contrato": 1, "valor_mercado": 1000000},

    {"id": 643, "time_id": 28, "nome": "Matheus Aurelio", "nome_abrev": "M. Aurelio", "idade": 26,
     "posicao": "GOLEIRO", "forca": 50, "tecnica": 50, "passe": 50, "velocidade": 50,
     "finalizacao": 38, "defesa": 55, "fisico": 55, "salario": 75000, "contrato": 3, "valor_mercado": 500000},

    # Zagueiros
    {"id": 644, "time_id": 28, "nome": "Doma", "nome_abrev": "Doma", "idade": 27,
     "posicao": "ZAGUEIRO", "forca": 62, "tecnica": 59, "passe": 58, "velocidade": 60,
     "finalizacao": 45, "defesa": 66, "fisico": 64, "salario": 1350000, "contrato": 2, "valor_mercado": 9000000},

    {"id": 645, "time_id": 28, "nome": "Joao Paulo", "nome_abrev": "J. Paulo", "idade": 28,
     "posicao": "ZAGUEIRO", "forca": 61, "tecnica": 58, "passe": 57, "velocidade": 59,
     "finalizacao": 44, "defesa": 65, "fisico": 63, "salario": 1200000, "contrato": 4, "valor_mercado": 8000000},

    {"id": 646, "time_id": 28, "nome": "Victor Caetano", "nome_abrev": "V. Caetano", "idade": 28,
     "posicao": "ZAGUEIRO", "forca": 60, "tecnica": 57, "passe": 56, "velocidade": 58,
     "finalizacao": 43, "defesa": 64, "fisico": 62, "salario": 1050000, "contrato": 1, "valor_mercado": 7000000},

    {"id": 647, "time_id": 28, "nome": "Bruno Leonardo", "nome_abrev": "B. Leonardo", "idade": 29,
     "posicao": "ZAGUEIRO", "forca": 57, "tecnica": 54, "passe": 53, "velocidade": 55,
     "finalizacao": 40, "defesa": 61, "fisico": 59, "salario": 600000, "contrato": 3, "valor_mercado": 4000000},

    {"id": 648, "time_id": 28, "nome": "Rafael Thyere", "nome_abrev": "R. Thyere", "idade": 32,
     "posicao": "ZAGUEIRO", "forca": 55, "tecnica": 53, "passe": 52, "velocidade": 52,
     "finalizacao": 38, "defesa": 59, "fisico": 57, "salario": 450000, "contrato": 2, "valor_mercado": 3000000},

    {"id": 649, "time_id": 28, "nome": "Kauan Faria", "nome_abrev": "K. Faria", "idade": 23,
     "posicao": "ZAGUEIRO", "forca": 50, "tecnica": 49, "passe": 48, "velocidade": 52,
     "finalizacao": 37, "defesa": 55, "fisico": 54, "salario": 75000, "contrato": 4, "valor_mercado": 500000},

    {"id": 650, "time_id": 28, "nome": "Vinicius Eduardo", "nome_abrev": "V. Eduardo", "idade": 21,
     "posicao": "ZAGUEIRO", "forca": 49, "tecnica": 48, "passe": 47, "velocidade": 51,
     "finalizacao": 36, "defesa": 54, "fisico": 53, "salario": 75000, "contrato": 1, "valor_mercado": 500000},

    # Laterais Esquerdo
    {"id": 651, "time_id": 28, "nome": "Mancha", "nome_abrev": "Mancha", "idade": 25,
     "posicao": "LATERAL_ESQUERDO", "forca": 58, "tecnica": 56, "passe": 57, "velocidade": 62,
     "finalizacao": 43, "defesa": 60, "fisico": 61, "salario": 825000, "contrato": 2, "valor_mercado": 5500000},

    {"id": 652, "time_id": 28, "nome": "Walter Clar", "nome_abrev": "W. Clar", "idade": 31,
     "posicao": "LATERAL_ESQUERDO", "forca": 57, "tecnica": 55, "passe": 56, "velocidade": 59,
     "finalizacao": 42, "defesa": 60, "fisico": 60, "salario": 750000, "contrato": 3, "valor_mercado": 5000000},

    {"id": 653, "time_id": 28, "nome": "Bruno Pacheco", "nome_abrev": "B. Pacheco", "idade": 34,
     "posicao": "LATERAL_ESQUERDO", "forca": 54, "tecnica": 52, "passe": 53, "velocidade": 55,
     "finalizacao": 40, "defesa": 57, "fisico": 57, "salario": 375000, "contrato": 1, "valor_mercado": 2500000},

    {"id": 654, "time_id": 28, "nome": "Da Silva", "nome_abrev": "Da Silva", "idade": 26,
     "posicao": "LATERAL_ESQUERDO", "forca": 49, "tecnica": 48, "passe": 50, "velocidade": 54,
     "finalizacao": 38, "defesa": 53, "fisico": 53, "salario": 75000, "contrato": 4, "valor_mercado": 500000},

    # Laterais Direito
    {"id": 655, "time_id": 28, "nome": "Marcos Vinicius", "nome_abrev": "M. Vinicius", "idade": 29,
     "posicao": "LATERAL_DIREITO", "forca": 57, "tecnica": 55, "passe": 56, "velocidade": 61,
     "finalizacao": 43, "defesa": 59, "fisico": 60, "salario": 600000, "contrato": 2, "valor_mercado": 4000000},

    {"id": 656, "time_id": 28, "nome": "Everton", "nome_abrev": "Everton", "idade": 31,
     "posicao": "LATERAL_DIREITO", "forca": 55, "tecnica": 54, "passe": 55, "velocidade": 59,
     "finalizacao": 42, "defesa": 57, "fisico": 58, "salario": 450000, "contrato": 3, "valor_mercado": 3000000},

    {"id": 657, "time_id": 28, "nome": "Gustavo Talles", "nome_abrev": "G. Talles", "idade": 22,
     "posicao": "LATERAL_DIREITO", "forca": 49, "tecnica": 48, "passe": 50, "velocidade": 56,
     "finalizacao": 39, "defesa": 51, "fisico": 52, "salario": 75000, "contrato": 1, "valor_mercado": 500000},

    # Volantes
    {"id": 658, "time_id": 28, "nome": "Carvalheira", "nome_abrev": "Carvalheira", "idade": 26,
     "posicao": "VOLANTE", "forca": 65, "tecnica": 63, "passe": 64, "velocidade": 62,
     "finalizacao": 52, "defesa": 66, "fisico": 65, "salario": 3000000, "contrato": 4, "valor_mercado": 20000000},

    {"id": 659, "time_id": 28, "nome": "Camilo", "nome_abrev": "Camilo", "idade": 27,
     "posicao": "VOLANTE", "forca": 63, "tecnica": 61, "passe": 62, "velocidade": 60,
     "finalizacao": 50, "defesa": 64, "fisico": 63, "salario": 1800000, "contrato": 2, "valor_mercado": 12000000},

    {"id": 660, "time_id": 28, "nome": "Higor Meritao", "nome_abrev": "H. Meritao", "idade": 31,
     "posicao": "VOLANTE", "forca": 62, "tecnica": 59, "passe": 60, "velocidade": 57,
     "finalizacao": 47, "defesa": 63, "fisico": 62, "salario": 1350000, "contrato": 3, "valor_mercado": 9000000},

    # New players (IDs 3825-3841)
    {"id": 3825, "time_id": 28, "nome": "Vinicius Balieiro", "nome_abrev": "V. Balieiro", "idade": 26,
     "posicao": "VOLANTE", "forca": 57, "tecnica": 55, "passe": 57, "velocidade": 56,
     "finalizacao": 44, "defesa": 60, "fisico": 58, "salario": 600000, "contrato": 1, "valor_mercado": 4000000},

    {"id": 3826, "time_id": 28, "nome": "Bruno Matias", "nome_abrev": "B. Matias", "idade": 27,
     "posicao": "MEIA_CENTRAL", "forca": 57, "tecnica": 60, "passe": 63, "velocidade": 57,
     "finalizacao": 50, "defesa": 57, "fisico": 57, "salario": 900000, "contrato": 4, "valor_mercado": 6000000},

    {"id": 3827, "time_id": 28, "nome": "Joao Vitor", "nome_abrev": "J. Vitor", "idade": 28,
     "posicao": "MEIA_CENTRAL", "forca": 55, "tecnica": 58, "passe": 61, "velocidade": 55,
     "finalizacao": 48, "defesa": 55, "fisico": 55, "salario": 600000, "contrato": 2, "valor_mercado": 4000000},

    {"id": 3828, "time_id": 28, "nome": "David Antunes", "nome_abrev": "D. Antunes", "idade": 20,
     "posicao": "MEIA_CENTRAL", "forca": 51, "tecnica": 53, "passe": 55, "velocidade": 53,
     "finalizacao": 44, "defesa": 50, "fisico": 51, "salario": 300000, "contrato": 3, "valor_mercado": 2000000},

    # Meias Ofensivos -> MEIA_ATACANTE
    {"id": 3829, "time_id": 28, "nome": "Robert Santos", "nome_abrev": "Rob. Santos", "idade": 22,
     "posicao": "MEIA_ATACANTE", "forca": 58, "tecnica": 63, "passe": 61, "velocidade": 60,
     "finalizacao": 62, "defesa": 48, "fisico": 57, "salario": 1500000, "contrato": 1, "valor_mercado": 10000000},

    {"id": 3830, "time_id": 28, "nome": "Jean Carlos", "nome_abrev": "J. Carlos", "idade": 34,
     "posicao": "MEIA_ATACANTE", "forca": 55, "tecnica": 60, "passe": 59, "velocidade": 54,
     "finalizacao": 59, "defesa": 46, "fisico": 55, "salario": 600000, "contrato": 2, "valor_mercado": 4000000},

    {"id": 3831, "time_id": 28, "nome": "Giovanni Augusto", "nome_abrev": "G. Augusto", "idade": 36,
     "posicao": "MEIA_ATACANTE", "forca": 52, "tecnica": 57, "passe": 57, "velocidade": 50,
     "finalizacao": 56, "defesa": 43, "fisico": 52, "salario": 300000, "contrato": 4, "valor_mercado": 2000000},

    {"id": 3832, "time_id": 28, "nome": "Wermeson", "nome_abrev": "Wermeson", "idade": 25,
     "posicao": "MEIA_ATACANTE", "forca": 49, "tecnica": 52, "passe": 52, "velocidade": 53,
     "finalizacao": 51, "defesa": 41, "fisico": 50, "salario": 75000, "contrato": 3, "valor_mercado": 500000},

    # Ponta Esquerda
    {"id": 3833, "time_id": 28, "nome": "Mauricio Garcez", "nome_abrev": "M. Garcez", "idade": 29,
     "posicao": "PONTA_ESQUERDA", "forca": 59, "tecnica": 62, "passe": 59, "velocidade": 65,
     "finalizacao": 63, "defesa": 46, "fisico": 60, "salario": 1500000, "contrato": 1, "valor_mercado": 10000000},

    {"id": 3834, "time_id": 28, "nome": "Italo Vargas", "nome_abrev": "I. Vargas", "idade": 23,
     "posicao": "PONTA_ESQUERDA", "forca": 59, "tecnica": 62, "passe": 58, "velocidade": 66,
     "finalizacao": 63, "defesa": 45, "fisico": 60, "salario": 1500000, "contrato": 2, "valor_mercado": 10000000},

    {"id": 3835, "time_id": 28, "nome": "Enio", "nome_abrev": "Enio", "idade": 25,
     "posicao": "PONTA_ESQUERDA", "forca": 58, "tecnica": 61, "passe": 57, "velocidade": 65,
     "finalizacao": 62, "defesa": 44, "fisico": 59, "salario": 1200000, "contrato": 4, "valor_mercado": 8000000},

    {"id": 3836, "time_id": 28, "nome": "Kevin Ramirez", "nome_abrev": "K. Ramirez", "idade": 32,
     "posicao": "PONTA_ESQUERDA", "forca": 56, "tecnica": 59, "passe": 55, "velocidade": 62,
     "finalizacao": 60, "defesa": 43, "fisico": 57, "salario": 750000, "contrato": 3, "valor_mercado": 5000000},

    # Ponta Direita
    {"id": 3837, "time_id": 28, "nome": "Marcinho", "nome_abrev": "Marcinho", "idade": 30,
     "posicao": "PONTA_DIREITA", "forca": 58, "tecnica": 61, "passe": 57, "velocidade": 64,
     "finalizacao": 62, "defesa": 44, "fisico": 59, "salario": 1125000, "contrato": 1, "valor_mercado": 7500000},

    {"id": 3838, "time_id": 28, "nome": "Rubens", "nome_abrev": "Rubens", "idade": 23,
     "posicao": "PONTA_DIREITA", "forca": 50, "tecnica": 53, "passe": 50, "velocidade": 58,
     "finalizacao": 53, "defesa": 39, "fisico": 52, "salario": 150000, "contrato": 2, "valor_mercado": 1000000},

    # Centroavantes
    {"id": 3839, "time_id": 28, "nome": "Neto Pessoa", "nome_abrev": "N. Pessoa", "idade": 31,
     "posicao": "CENTROAVANTE", "forca": 60, "tecnica": 56, "passe": 52, "velocidade": 57,
     "finalizacao": 62, "defesa": 42, "fisico": 62, "salario": 525000, "contrato": 4, "valor_mercado": 3500000},

    {"id": 3840, "time_id": 28, "nome": "Yannick Bolasie", "nome_abrev": "Y. Bolasie", "idade": 36,
     "posicao": "CENTROAVANTE", "forca": 57, "tecnica": 55, "passe": 50, "velocidade": 54,
     "finalizacao": 59, "defesa": 40, "fisico": 58, "salario": 300000, "contrato": 3, "valor_mercado": 2000000},

    {"id": 3841, "time_id": 28, "nome": "Joao Bom", "nome_abrev": "J. Bom", "idade": 20,
     "posicao": "CENTROAVANTE", "forca": 49, "tecnica": 48, "passe": 47, "velocidade": 52,
     "finalizacao": 53, "defesa": 38, "fisico": 51, "salario": 75000, "contrato": 1, "valor_mercado": 500000},
]

print(f"Loading {FILE_PATH}...")
with open(FILE_PATH, 'r', encoding='utf-8-sig') as f:
    data = json.load(f)

players = data['jogadores']

# Remove all existing Chapecoense players (time_id=28)
original_count = len(players)
players_other = [p for p in players if p.get('time_id') != 28]
removed_count = original_count - len(players_other)
print(f"Removed {removed_count} existing Chapecoense players")

# Find insert position - right before first player of time_id=29 (Ponte Preta)
insert_idx = None
for i, p in enumerate(players_other):
    if p.get('time_id') == 29:
        insert_idx = i
        break

if insert_idx is None:
    # Append at end of players section
    insert_idx = len(players_other)
    print("No time_id=29 found, appending at end")
else:
    print(f"Inserting before index {insert_idx} (first Ponte Preta player)")

# Insert new Chapecoense players
for i, player in enumerate(NEW_PLAYERS):
    players_other.insert(insert_idx + i, player)

data['jogadores'] = players_other
new_count = len(data['jogadores'])
print(f"Total players: {original_count} -> {new_count} (+{new_count - original_count})")

# Write back with same formatting style
print("Writing file...")
with open(FILE_PATH, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=3, separators=(',', ':'))

print("Done!")

# Verify
with open(FILE_PATH, 'r', encoding='utf-8') as f:
    verify = json.load(f)

chape_players = [p for p in verify['jogadores'] if p.get('time_id') == 28]
print(f"\nVerification - Chapecoense players: {len(chape_players)}")
for p in chape_players:
    print(f"  ID={p['id']}: {p['nome']} ({p['posicao']}) - {p['idade']} anos - VM={p['valor_mercado']}")
