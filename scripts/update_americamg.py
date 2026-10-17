# -*- coding: utf-8 -*-
"""
Atualiza o elenco do América-MG (time_id=24) no seed_data.json com os
jogadores reais do clube (fonte: Transfermarkt + ogol.com.br).

Regras:
- 20 jogadores principais ocupam os IDs existentes 561-580 (substituicao)
- 18 jogadores adicionais sao acrescentados com IDs 3858-3875
- Posicoes "Meia Ofensivo" do Transfermarkt sao gravadas como MEIA_ATACANTE
- Valores em centavos (escala compativel com o resto do seed)
"""

import json
import io

PATH = r"C:\Dev\ManagerFoot\app\src\main\assets\seed_data.json"

# (id, nome, nome_abrev, idade, posicao, forca, tecnica, passe, velocidade,
#  finalizacao, defesa, fisico, salario, contrato, valor_mercado)
# Substituicoes (IDs existentes 561-580):
SUBSTITUICOES = [
    (561, "Gustavo",          "Gustavo",        33, "GOLEIRO",          67, 60, 60, 50, 50, 72, 60, 234500, 2, 6800000),
    (562, "Cassio Meneses",   "C. Meneses",     24, "GOLEIRO",          60, 56, 58, 53, 50, 65, 58, 210000, 3, 6200000),
    (563, "Nathan Pelae",     "N. Pelae",       30, "ZAGUEIRO",         64, 58, 62, 55, 50, 70, 65, 224000, 2, 7000000),
    (564, "Ricardo Silva",    "R. Silva",       33, "ZAGUEIRO",         65, 56, 60, 50, 50, 72, 64, 227500, 1, 6500000),
    (565, "Emerson Santos",   "E. Santos",      31, "ZAGUEIRO",         63, 57, 60, 54, 51, 69, 64, 220500, 2, 6800000),
    (566, "William",          "William",        28, "LATERAL_DIREITO",  62, 60, 60, 65, 53, 65, 62, 217000, 3, 7200000),
    (567, "Dalbert",          "Dalbert",        32, "LATERAL_ESQUERDO", 70, 65, 68, 70, 55, 68, 64, 245000, 2, 13500000),
    (568, "Artur",            "Artur",          31, "LATERAL_ESQUERDO", 63, 60, 62, 67, 54, 64, 62, 220500, 2, 7500000),
    (569, "Kappel",           "Kappel",         19, "VOLANTE",          53, 55, 60, 60, 50, 60, 58, 185500, 3, 4500000),
    (570, "Rafa Barcelos",    "R. Barcelos",    22, "VOLANTE",          58, 60, 65, 58, 53, 64, 62, 203000, 3, 6000000),
    (571, "Val Soares",       "V. Soares",      29, "MEIA_CENTRAL",     62, 65, 67, 58, 60, 60, 60, 217000, 2, 7000000),
    (572, "Fernando Elizari", "F. Elizari",     35, "MEIA_CENTRAL",     60, 65, 70, 50, 58, 58, 55, 210000, 1, 5500000),
    (573, "Ale",              "Ale",            35, "MEIA_ATACANTE",    64, 70, 68, 56, 65, 55, 56, 224000, 1, 6500000),
    (574, "Yago Souza",       "Y. Souza",       20, "MEIA_ATACANTE",    54, 62, 60, 64, 58, 50, 58, 189000, 3, 5000000),
    (575, "Wesley",           "Wesley",         26, "PONTA_DIREITA",    63, 65, 60, 73, 60, 50, 60, 220500, 3, 8500000),
    (576, "Paulinho",         "Paulinho",       21, "PONTA_DIREITA",    56, 62, 58, 70, 58, 48, 56, 196000, 3, 5500000),
    (577, "Gonzalo Mastriani","G. Mastriani",   33, "CENTROAVANTE",     68, 62, 60, 65, 75, 45, 68, 238000, 2, 11000000),
    (578, "Paulo Victor",     "P. Victor",      25, "CENTROAVANTE",     62, 60, 58, 65, 70, 45, 62, 217000, 2, 7800000),
    (579, "Willian Bigode",   "W. Bigode",      39, "CENTROAVANTE",     64, 70, 65, 50, 75, 45, 55, 224000, 1, 6800000),
    (580, "Yarlen",           "Yarlen",         20, "CENTROAVANTE",     53, 58, 55, 65, 65, 42, 58, 185500, 3, 5000000),
]

# Adicoes (IDs novos a partir de 3858):
ADICOES = [
    (3858, "Matheus Simoes",   "M. Simoes",      18, "GOLEIRO",          47, 50, 50, 50, 45, 55, 52, 164500, 3, 3200000),
    (3859, "Thallyson",        "Thallyson",      20, "ZAGUEIRO",         53, 52, 55, 55, 45, 60, 60, 185500, 3, 4500000),
    (3860, "Biel Silva",       "B. Silva",       18, "ZAGUEIRO",         48, 50, 52, 55, 45, 56, 58, 168000, 4, 3500000),
    (3861, "Leo Izidoro",      "L. Izidoro",     19, "ZAGUEIRO",         50, 52, 53, 55, 45, 58, 58, 175000, 3, 4000000),
    (3862, "Rian Henrique",    "R. Henrique",    18, "ZAGUEIRO",         47, 50, 50, 55, 45, 55, 56, 164500, 4, 3200000),
    (3863, "Italo Brito",      "I. Brito",       19, "LATERAL_DIREITO",  50, 55, 55, 65, 50, 56, 56, 175000, 3, 4500000),
    (3864, "Heitor Barcelos",  "H. Barcelos",    22, "LATERAL_DIREITO",  56, 58, 58, 64, 50, 60, 58, 196000, 3, 5500000),
    (3865, "Rua Victor",       "R. Victor",      19, "LATERAL_ESQUERDO", 50, 55, 55, 65, 48, 55, 55, 175000, 3, 4500000),
    (3866, "Felipe Amaral",    "F. Amaral",      22, "MEIA_ATACANTE",    56, 62, 60, 60, 60, 48, 55, 196000, 3, 5800000),
    (3867, "Julio Cesar",      "J. Cesar",       20, "MEIA_ATACANTE",    52, 58, 58, 60, 56, 45, 55, 182000, 3, 4800000),
    (3868, "Otavio Goncalves", "O. Goncalves",   20, "MEIA_ATACANTE",    52, 58, 58, 58, 56, 45, 55, 182000, 3, 4800000),
    (3869, "Gabriel Santos",   "G. Santos",      19, "MEIA_ATACANTE",    50, 56, 56, 60, 55, 45, 53, 175000, 3, 4500000),
    (3870, "Yago Santos",      "Y. Santos",      23, "MEIA_ATACANTE",    58, 62, 62, 60, 60, 48, 56, 203000, 2, 6200000),
    (3871, "Jhonatan Lima",    "J. Lima",        20, "MEIA_ATACANTE",    53, 58, 58, 58, 56, 45, 55, 185500, 3, 5000000),
    (3872, "Matias Segovia",   "M. Segovia",     23, "CENTROAVANTE",     60, 60, 56, 64, 65, 42, 60, 210000, 2, 7000000),
    (3873, "Thauan",           "Thauan",         22, "CENTROAVANTE",     56, 58, 55, 65, 62, 42, 58, 196000, 3, 5800000),
    (3874, "Everton Brito",    "E. Brito",       31, "CENTROAVANTE",     60, 60, 56, 60, 65, 45, 60, 210000, 2, 6500000),
    (3875, "Kauan Cristtyan",  "K. Cristtyan",   20, "CENTROAVANTE",     52, 56, 55, 62, 60, 42, 56, 182000, 3, 5000000),
]

def jogador_dict(t):
    (id_, nome, nome_abrev, idade, posicao, forca, tecnica, passe, velocidade,
     finalizacao, defesa, fisico, salario, contrato, valor_mercado) = t
    return {
        "id": id_,
        "time_id": 24,
        "nome": nome,
        "nome_abrev": nome_abrev,
        "idade": idade,
        "posicao": posicao,
        "forca": forca,
        "tecnica": tecnica,
        "passe": passe,
        "velocidade": velocidade,
        "finalizacao": finalizacao,
        "defesa": defesa,
        "fisico": fisico,
        "salario": salario,
        "contrato": contrato,
        "valor_mercado": valor_mercado
    }

def main():
    with io.open(PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    jogadores = data["jogadores"]

    # Mapa por id para substituicao
    sub_map = {t[0]: jogador_dict(t) for t in SUBSTITUICOES}

    # 1) Substitui as entradas existentes 561-580
    for i, j in enumerate(jogadores):
        if j.get("id") in sub_map:
            jogadores[i] = sub_map[j["id"]]

    # 2) Encontra a ultima entrada com time_id=24 (deve ser id 580 apos a substituicao)
    last_idx = -1
    for i, j in enumerate(jogadores):
        if j.get("time_id") == 24:
            last_idx = i

    if last_idx < 0:
        raise RuntimeError("Nao encontrei entradas time_id=24")

    # 3) Confere se IDs novos nao colidem com existentes
    existing_ids = {j["id"] for j in jogadores}
    novos = [jogador_dict(t) for t in ADICOES]
    for n in novos:
        if n["id"] in existing_ids:
            raise RuntimeError(f"ID {n['id']} ja existe — escolha outro")

    # 4) Insere os novos jogadores logo apos o ultimo America-MG existente
    jogadores[last_idx + 1 : last_idx + 1] = novos

    # Persiste mantendo o formato existente (indent=2 — confirmar)
    with io.open(PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

    # Valida
    with io.open(PATH, "r", encoding="utf-8") as f:
        check = json.load(f)
    am_count = sum(1 for j in check["jogadores"] if j.get("time_id") == 24)
    print(f"OK. Total America-MG: {am_count}")
    print(f"Substituidos: {len(SUBSTITUICOES)} | Adicionados: {len(ADICOES)}")

if __name__ == "__main__":
    main()
