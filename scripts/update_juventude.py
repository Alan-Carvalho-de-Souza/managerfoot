import json

with open('app/src/main/assets/seed_data.json', encoding='utf-8') as f:
    data = json.load(f)

# 30 jogadores reais do EC Juventude (time_id=17) baseados no Transfermarkt 2026
# Meia Ofensivo → MEIA_ATACANTE conforme solicitado
players = [
    # ── Goleiros ──────────────────────────────────────────────────────────
    {"id": 401, "time_id": 17, "nome": "Jandrei", "nome_abrev": "Jandrei",
     "idade": 33, "posicao": "GOLEIRO",
     "forca": 63, "tecnica": 55, "passe": 58, "velocidade": 54, "finalizacao": 40, "defesa": 70, "fisico": 64,
     "salario": 1500000, "contrato": 2, "valor_mercado": 1200000},
    {"id": 402, "time_id": 17, "nome": "Pedro Rocha", "nome_abrev": "P. Rocha",
     "idade": 27, "posicao": "GOLEIRO",
     "forca": 54, "tecnica": 47, "passe": 49, "velocidade": 45, "finalizacao": 36, "defesa": 60, "fisico": 54,
     "salario": 600000, "contrato": 1, "valor_mercado": 500000},
    {"id": 3853, "time_id": 17, "nome": "Ruan Carneiro", "nome_abrev": "R. Carneiro",
     "idade": 36, "posicao": "GOLEIRO",
     "forca": 51, "tecnica": 44, "passe": 46, "velocidade": 42, "finalizacao": 34, "defesa": 56, "fisico": 51,
     "salario": 300000, "contrato": 1, "valor_mercado": 400000},
    {"id": 3854, "time_id": 17, "nome": "Zé Henrique", "nome_abrev": "Z. Henrique",
     "idade": 21, "posicao": "GOLEIRO",
     "forca": 51, "tecnica": 44, "passe": 46, "velocidade": 42, "finalizacao": 34, "defesa": 56, "fisico": 51,
     "salario": 300000, "contrato": 3, "valor_mercado": 400000},
    # ── Zagueiros ─────────────────────────────────────────────────────────
    {"id": 403, "time_id": 17, "nome": "Messias", "nome_abrev": "Messias",
     "idade": 31, "posicao": "ZAGUEIRO",
     "forca": 63, "tecnica": 62, "passe": 64, "velocidade": 62, "finalizacao": 48, "defesa": 71, "fisico": 70,
     "salario": 1800000, "contrato": 2, "valor_mercado": 3000000},
    {"id": 404, "time_id": 17, "nome": "Rodrigo Sam", "nome_abrev": "R. Sam",
     "idade": 30, "posicao": "ZAGUEIRO",
     "forca": 60, "tecnica": 60, "passe": 62, "velocidade": 60, "finalizacao": 46, "defesa": 68, "fisico": 67,
     "salario": 1300000, "contrato": 3, "valor_mercado": 2100000},
    {"id": 405, "time_id": 17, "nome": "Marcos Paulo", "nome_abrev": "M. Paulo",
     "idade": 23, "posicao": "ZAGUEIRO",
     "forca": 57, "tecnica": 57, "passe": 59, "velocidade": 57, "finalizacao": 43, "defesa": 64, "fisico": 63,
     "salario": 800000, "contrato": 2, "valor_mercado": 1800000},
    {"id": 406, "time_id": 17, "nome": "Gabriel Pinheiro", "nome_abrev": "G. Pinheiro",
     "idade": 29, "posicao": "ZAGUEIRO",
     "forca": 57, "tecnica": 57, "passe": 59, "velocidade": 57, "finalizacao": 43, "defesa": 64, "fisico": 63,
     "salario": 700000, "contrato": 1, "valor_mercado": 1500000},
    {"id": 424, "time_id": 17, "nome": "Titi", "nome_abrev": "Titi",
     "idade": 38, "posicao": "ZAGUEIRO",
     "forca": 54, "tecnica": 53, "passe": 55, "velocidade": 53, "finalizacao": 40, "defesa": 61, "fisico": 60,
     "salario": 400000, "contrato": 1, "valor_mercado": 700000},
    # ── Laterais Direitos ─────────────────────────────────────────────────
    {"id": 407, "time_id": 17, "nome": "Nathan", "nome_abrev": "Nathan",
     "idade": 24, "posicao": "LATERAL_DIREITO",
     "forca": 67, "tecnica": 68, "passe": 70, "velocidade": 73, "finalizacao": 57, "defesa": 68, "fisico": 68,
     "salario": 2500000, "contrato": 1, "valor_mercado": 9000000},
    {"id": 408, "time_id": 17, "nome": "Raí Ramos", "nome_abrev": "Raí Ramos",
     "idade": 31, "posicao": "LATERAL_DIREITO",
     "forca": 60, "tecnica": 62, "passe": 64, "velocidade": 67, "finalizacao": 50, "defesa": 62, "fisico": 62,
     "salario": 1200000, "contrato": 2, "valor_mercado": 2100000},
    # ── Laterais Esquerdos ────────────────────────────────────────────────
    {"id": 409, "time_id": 17, "nome": "Patryck", "nome_abrev": "Patryck",
     "idade": 23, "posicao": "LATERAL_ESQUERDO",
     "forca": 67, "tecnica": 68, "passe": 70, "velocidade": 73, "finalizacao": 57, "defesa": 68, "fisico": 68,
     "salario": 2500000, "contrato": 1, "valor_mercado": 9000000},
    {"id": 410, "time_id": 17, "nome": "Diogo Barbosa", "nome_abrev": "D. Barbosa",
     "idade": 33, "posicao": "LATERAL_ESQUERDO",
     "forca": 57, "tecnica": 59, "passe": 61, "velocidade": 64, "finalizacao": 47, "defesa": 59, "fisico": 59,
     "salario": 700000, "contrato": 1, "valor_mercado": 1500000},
    {"id": 425, "time_id": 17, "nome": "Alan Ruschel", "nome_abrev": "A. Ruschel",
     "idade": 36, "posicao": "LATERAL_ESQUERDO",
     "forca": 54, "tecnica": 55, "passe": 57, "velocidade": 60, "finalizacao": 43, "defesa": 55, "fisico": 55,
     "salario": 400000, "contrato": 1, "valor_mercado": 700000},
    {"id": 3855, "time_id": 17, "nome": "Wadson", "nome_abrev": "Wadson",
     "idade": 25, "posicao": "LATERAL_ESQUERDO",
     "forca": 51, "tecnica": 52, "passe": 54, "velocidade": 56, "finalizacao": 40, "defesa": 52, "fisico": 52,
     "salario": 400000, "contrato": 2, "valor_mercado": 400000},
    # ── Volantes ──────────────────────────────────────────────────────────
    {"id": 411, "time_id": 17, "nome": "Lucas Mineiro", "nome_abrev": "L. Mineiro",
     "idade": 30, "posicao": "VOLANTE",
     "forca": 64, "tecnica": 67, "passe": 69, "velocidade": 65, "finalizacao": 59, "defesa": 70, "fisico": 70,
     "salario": 2000000, "contrato": 1, "valor_mercado": 6000000},
    {"id": 412, "time_id": 17, "nome": "Luan Martins", "nome_abrev": "L. Martins",
     "idade": 27, "posicao": "VOLANTE",
     "forca": 57, "tecnica": 62, "passe": 64, "velocidade": 60, "finalizacao": 53, "defesa": 64, "fisico": 64,
     "salario": 700000, "contrato": 2, "valor_mercado": 1500000},
    {"id": 413, "time_id": 17, "nome": "Davi Góes", "nome_abrev": "D. Góes",
     "idade": 20, "posicao": "VOLANTE",
     "forca": 57, "tecnica": 62, "passe": 64, "velocidade": 60, "finalizacao": 53, "defesa": 64, "fisico": 64,
     "salario": 700000, "contrato": 3, "valor_mercado": 1500000},
    {"id": 3856, "time_id": 17, "nome": "Léo Oliveira", "nome_abrev": "L. Oliveira",
     "idade": 29, "posicao": "VOLANTE",
     "forca": 54, "tecnica": 58, "passe": 60, "velocidade": 56, "finalizacao": 49, "defesa": 60, "fisico": 60,
     "salario": 500000, "contrato": 1, "valor_mercado": 500000},
    {"id": 3857, "time_id": 17, "nome": "Iba Ly", "nome_abrev": "Iba Ly",
     "idade": 23, "posicao": "VOLANTE",
     "forca": 54, "tecnica": 58, "passe": 60, "velocidade": 56, "finalizacao": 49, "defesa": 60, "fisico": 60,
     "salario": 500000, "contrato": 2, "valor_mercado": 500000},
    # ── Meias Centrais ────────────────────────────────────────────────────
    {"id": 414, "time_id": 17, "nome": "Mandaca", "nome_abrev": "Mandaca",
     "idade": 24, "posicao": "MEIA_CENTRAL",
     "forca": 67, "tecnica": 72, "passe": 74, "velocidade": 68, "finalizacao": 64, "defesa": 63, "fisico": 65,
     "salario": 2800000, "contrato": 3, "valor_mercado": 11500000},
    {"id": 415, "time_id": 17, "nome": "Pablo Roberto", "nome_abrev": "P. Roberto",
     "idade": 26, "posicao": "MEIA_CENTRAL",
     "forca": 63, "tecnica": 69, "passe": 71, "velocidade": 65, "finalizacao": 60, "defesa": 60, "fisico": 62,
     "salario": 1800000, "contrato": 2, "valor_mercado": 3000000},
    # ── Meias Atacantes (Meia Ofensivo → MEIA_ATACANTE) ──────────────────
    {"id": 416, "time_id": 17, "nome": "Raí", "nome_abrev": "Raí",
     "idade": 24, "posicao": "MEIA_ATACANTE",
     "forca": 63, "tecnica": 70, "passe": 67, "velocidade": 67, "finalizacao": 68, "defesa": 57, "fisico": 61,
     "salario": 1500000, "contrato": 2, "valor_mercado": 4200000},
    {"id": 417, "time_id": 17, "nome": "Ray Breno", "nome_abrev": "Ray Breno",
     "idade": 21, "posicao": "MEIA_ATACANTE",
     "forca": 54, "tecnica": 61, "passe": 58, "velocidade": 58, "finalizacao": 59, "defesa": 47, "fisico": 52,
     "salario": 500000, "contrato": 2, "valor_mercado": 500000},
    # ── Pontas Esquerdas ──────────────────────────────────────────────────
    {"id": 419, "time_id": 17, "nome": "Allanzinho", "nome_abrev": "Allanzinho",
     "idade": 26, "posicao": "PONTA_ESQUERDA",
     "forca": 61, "tecnica": 68, "passe": 62, "velocidade": 72, "finalizacao": 64, "defesa": 52, "fisico": 60,
     "salario": 1000000, "contrato": 1, "valor_mercado": 2400000},
    {"id": 420, "time_id": 17, "nome": "Marcos Paulo", "nome_abrev": "M. Paulo",
     "idade": 25, "posicao": "PONTA_ESQUERDA",
     "forca": 61, "tecnica": 68, "passe": 62, "velocidade": 72, "finalizacao": 64, "defesa": 52, "fisico": 60,
     "salario": 1000000, "contrato": 2, "valor_mercado": 2700000},
    {"id": 421, "time_id": 17, "nome": "Fábio Lima", "nome_abrev": "F. Lima",
     "idade": 29, "posicao": "PONTA_ESQUERDA",
     "forca": 61, "tecnica": 68, "passe": 62, "velocidade": 72, "finalizacao": 64, "defesa": 52, "fisico": 60,
     "salario": 1000000, "contrato": 2, "valor_mercado": 2400000},
    # ── Ponta Direita ─────────────────────────────────────────────────────
    {"id": 418, "time_id": 17, "nome": "Manuel Castro", "nome_abrev": "M. Castro",
     "idade": 30, "posicao": "PONTA_DIREITA",
     "forca": 67, "tecnica": 73, "passe": 68, "velocidade": 78, "finalizacao": 70, "defesa": 58, "fisico": 66,
     "salario": 2500000, "contrato": 2, "valor_mercado": 7200000},
    # ── Centroavantes ─────────────────────────────────────────────────────
    {"id": 422, "time_id": 17, "nome": "Alisson Safira", "nome_abrev": "A. Safira",
     "idade": 31, "posicao": "CENTROAVANTE",
     "forca": 63, "tecnica": 64, "passe": 61, "velocidade": 64, "finalizacao": 74, "defesa": 48, "fisico": 68,
     "salario": 1800000, "contrato": 2, "valor_mercado": 4500000},
    {"id": 423, "time_id": 17, "nome": "Alan Kardec", "nome_abrev": "A. Kardec",
     "idade": 37, "posicao": "CENTROAVANTE",
     "forca": 57, "tecnica": 58, "passe": 55, "velocidade": 58, "finalizacao": 67, "defesa": 42, "fisico": 62,
     "salario": 500000, "contrato": 1, "valor_mercado": 1000000},
]

# Índice dos jogadores por ID para atualização rápida
idx_por_id = {j['id']: i for i, j in enumerate(data['jogadores'])}

updated = 0
added = 0
for p in players:
    pid = p['id']
    if pid in idx_por_id:
        data['jogadores'][idx_por_id[pid]] = p
        updated += 1
    else:
        data['jogadores'].append(p)
        added += 1

with open('app/src/main/assets/seed_data.json', 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

total_juv = len([j for j in data['jogadores'] if j['time_id'] == 17])
print(f"Atualizados: {updated} | Adicionados: {added} | Total Juventude: {total_juv}")
print(f"Max ID: {max(j['id'] for j in data['jogadores'])}")
