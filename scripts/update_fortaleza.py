#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Update Fortaleza EC (time_id=13) players in seed_data.json with real Transfermarkt 2026 data."""

import json

FILE_PATH = r"c:\Dev\ManagerFoot\app\src\main\assets\seed_data.json"

def euro_to_game(euros):
    return int(euros * 10)

def vm_to_salario(vm):
    return max(75_000, int(vm * 0.15))

# ─── Updates for all 25 existing IDs (301–325) ───────────────────────────────
# nome, nome_abrev, idade, posicao e valor_mercado atualizados.
# Stats (forca, tecnica, etc.) são preservados.
# IDs 324 e 325 eram SEGUNDA_ATACANTE → remapeados para GOLEIRO (Magrão, Vinícius)
UPDATES = {
    # Goleiros
    301: {"nome": "Brenno",           "nome_abrev": "Brenno",        "idade": 27, "posicao": "GOLEIRO",          "valor_mercado": euro_to_game(700_000)},
    302: {"nome": "João Ricardo",     "nome_abrev": "J. Ricardo",    "idade": 37, "posicao": "GOLEIRO",          "valor_mercado": euro_to_game(400_000)},
    # Zagueiros
    303: {"nome": "Tomás Cardona",    "nome_abrev": "T. Cardona",    "idade": 30, "posicao": "ZAGUEIRO",         "valor_mercado": euro_to_game(1_200_000)},
    304: {"nome": "Lucas Gazal",      "nome_abrev": "L. Gazal",      "idade": 26, "posicao": "ZAGUEIRO",         "valor_mercado": euro_to_game(850_000)},
    305: {"nome": "Emanuel Brítez",   "nome_abrev": "E. Brítez",     "idade": 34, "posicao": "ZAGUEIRO",         "valor_mercado": euro_to_game(600_000)},
    306: {"nome": "Luan Freitas",     "nome_abrev": "L. Freitas",    "idade": 25, "posicao": "ZAGUEIRO",         "valor_mercado": euro_to_game(400_000)},
    # Laterais Direitos
    307: {"nome": "Maílton",          "nome_abrev": "Maílton",       "idade": 27, "posicao": "LATERAL_DIREITO",  "valor_mercado": euro_to_game(2_000_000)},
    308: {"nome": "Paulinho",         "nome_abrev": "Paulinho",      "idade": 20, "posicao": "LATERAL_DIREITO",  "valor_mercado": euro_to_game(400_000)},
    # Laterais Esquerdos
    309: {"nome": "Gabriel Fuentes",  "nome_abrev": "G. Fuentes",    "idade": 29, "posicao": "LATERAL_ESQUERDO", "valor_mercado": euro_to_game(1_700_000)},
    310: {"nome": "Maurício Mucuri",  "nome_abrev": "M. Mucuri",     "idade": 24, "posicao": "LATERAL_ESQUERDO", "valor_mercado": euro_to_game(50_000)},
    # Volantes
    311: {"nome": "Ronald",           "nome_abrev": "Ronald",        "idade": 23, "posicao": "VOLANTE",          "valor_mercado": euro_to_game(3_000_000)},
    312: {"nome": "Ryan",             "nome_abrev": "Ryan",          "idade": 22, "posicao": "VOLANTE",          "valor_mercado": euro_to_game(2_500_000)},
    313: {"nome": "Lucas Sasha",      "nome_abrev": "L. Sasha",      "idade": 36, "posicao": "VOLANTE",          "valor_mercado": euro_to_game(200_000)},
    # Meias Centrais
    314: {"nome": "Matheus Rossetto", "nome_abrev": "M. Rossetto",   "idade": 29, "posicao": "MEIA_CENTRAL",     "valor_mercado": euro_to_game(1_500_000)},
    315: {"nome": "Pierre",           "nome_abrev": "Pierre",        "idade": 24, "posicao": "MEIA_CENTRAL",     "valor_mercado": euro_to_game(750_000)},
    # Meias Ofensivos → MEIA_ATACANTE
    316: {"nome": "Tomás Pochettino", "nome_abrev": "T. Pochettino", "idade": 30, "posicao": "MEIA_ATACANTE",    "valor_mercado": euro_to_game(4_000_000)},
    317: {"nome": "Lucas Crispim",    "nome_abrev": "L. Crispim",    "idade": 31, "posicao": "MEIA_ATACANTE",    "valor_mercado": euro_to_game(800_000)},
    # Pontas Direitas
    318: {"nome": "Vitinho",          "nome_abrev": "Vitinho",       "idade": 24, "posicao": "PONTA_DIREITA",    "valor_mercado": euro_to_game(2_800_000)},
    319: {"nome": "Welliton",         "nome_abrev": "Welliton",      "idade": 25, "posicao": "PONTA_DIREITA",    "valor_mercado": euro_to_game(600_000)},
    # Pontas Esquerdas
    320: {"nome": "Luiz Fernando",    "nome_abrev": "L. Fernando",   "idade": 29, "posicao": "PONTA_ESQUERDA",   "valor_mercado": euro_to_game(1_800_000)},
    321: {"nome": "Rodriguinho",      "nome_abrev": "Rodriguinho",   "idade": 22, "posicao": "PONTA_ESQUERDA",   "valor_mercado": euro_to_game(600_000)},
    # Centroavantes
    322: {"nome": "GB",               "nome_abrev": "GB",            "idade": 21, "posicao": "CENTROAVANTE",     "valor_mercado": euro_to_game(3_000_000)},
    323: {"nome": "Juan Miritello",   "nome_abrev": "J. Miritello",  "idade": 27, "posicao": "CENTROAVANTE",     "valor_mercado": euro_to_game(2_000_000)},
    # Remapeados de SEGUNDA_ATACANTE → GOLEIRO (Magrão e Vinícius)
    324: {"nome": "Magrão",           "nome_abrev": "Magrão",        "idade": 25, "posicao": "GOLEIRO",          "valor_mercado": euro_to_game(300_000)},
    325: {"nome": "Vinícius",         "nome_abrev": "Vinícius",      "idade": 32, "posicao": "GOLEIRO",          "valor_mercado": euro_to_game(250_000)},
}

# ─── Novos jogadores (IDs 3848–3852) ─────────────────────────────────────────
def new_player(pid, nome, abrev, idade, pos, forca, tecnica, passe, velocidade,
               finalizacao, defesa, fisico, contrato, vm_euros):
    vm = euro_to_game(vm_euros)
    return {
        "id": pid, "time_id": 13,
        "nome": nome, "nome_abrev": abrev, "idade": idade,
        "posicao": pos,
        "forca": forca, "tecnica": tecnica, "passe": passe, "velocidade": velocidade,
        "finalizacao": finalizacao, "defesa": defesa, "fisico": fisico,
        "salario": vm_to_salario(vm), "contrato": contrato,
        "valor_mercado": vm,
    }

NEW_PLAYERS = [
    new_player(3848, "Rodrigo Santos", "R. Santos",  25, "VOLANTE",
               forca=66, tecnica=65, passe=74, velocidade=68, finalizacao=62, defesa=70, fisico=72,
               contrato=3, vm_euros=50_000),
    new_player(3849, "Lucca Prior",    "L. Prior",   22, "MEIA_ATACANTE",
               forca=67, tecnica=74, passe=72, velocidade=74, finalizacao=71, defesa=60, fisico=67,
               contrato=3, vm_euros=500_000),
    new_player(3850, "Lucas Emanoel",  "L. Emanoel", 19, "MEIA_ATACANTE",
               forca=60, tecnica=68, passe=67, velocidade=72, finalizacao=65, defesa=55, fisico=63,
               contrato=4, vm_euros=50_000),
    new_player(3851, "Paulo Baya",     "P. Baya",    26, "PONTA_ESQUERDA",
               forca=68, tecnica=73, passe=70, velocidade=76, finalizacao=70, defesa=58, fisico=68,
               contrato=2, vm_euros=550_000),
    new_player(3852, "Kayke",          "Kayke",      19, "CENTROAVANTE",
               forca=67, tecnica=70, passe=66, velocidade=74, finalizacao=74, defesa=55, fisico=70,
               contrato=3, vm_euros=1_000_000),
]

# ─── Apply ───────────────────────────────────────────────────────────────────
with open(FILE_PATH, encoding="utf-8") as f:
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

# Insere novos jogadores após o último jogador do Fortaleza (id=325)
insert_after_id = 325
insert_idx = next((i for i, j in enumerate(jogadores) if j["id"] == insert_after_id), None)
if insert_idx is not None:
    for offset, np in enumerate(NEW_PLAYERS):
        jogadores.insert(insert_idx + 1 + offset, np)

with open(FILE_PATH, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=3)

print(f"Updated {updated} existing players.")
print(f"Added {len(NEW_PLAYERS)} new players (IDs {NEW_PLAYERS[0]['id']}–{NEW_PLAYERS[-1]['id']}).")
print("Done.")
