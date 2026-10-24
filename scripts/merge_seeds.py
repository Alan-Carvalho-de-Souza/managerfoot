"""
merge_seeds.py
Sincroniza os seeds auxiliares por liga dentro do seed_data.json.

Contexto: o seed_data.json e a base autoritativa carregada pelo app
(SeedDataSource le SOMENTE esse arquivo). Ligas como brasileirao/argentina
vivem apenas dentro dele (nao ha mais arquivo-fonte separado). Outras ligas
mantem um arquivo-fonte editavel (seed_premier.json, seed_uruguay.json) que
serve como fonte da verdade daquela liga.

Este script pega o seed_data.json como base e, para cada arquivo auxiliar
existente, faz UPSERT (casando por id) de times e jogadores:
  - id ja presente  -> substitui o registro pelo do arquivo auxiliar
  - id novo         -> adiciona ao final
Ids que nao aparecem em nenhum auxiliar (ex.: brasileirao) sao preservados.

A chave 'tecnicos' do seed_data.json e PRESERVADA (e sofre upsert a partir dos
auxiliares, caso algum passe a trazer tecnicos). Nunca a descarte: os tecnicos
sao gerados por scripts/gen_tecnicos.py e alimentam o Ranking de Tecnicos.
Fluxo recomendado ao adicionar/alterar uma liga:
    python scripts/merge_seeds.py     # sincroniza times/jogadores/escudos
    python scripts/gen_tecnicos.py    # (re)gera 1 tecnico por time (inclui novos)

Ou seja: edite seed_premier.json / seed_uruguay.json e rode
    python scripts/merge_seeds.py
para propagar as mudancas (escudo_res, nome, saldo, etc.) ao seed_data.json.
"""
import json
import os

ASSETS = os.path.join("app", "src", "main", "assets")
BASE = os.path.join(ASSETS, "seed_data.json")

# Arquivos-fonte por liga que ainda existem separados. Sao a fonte da verdade
# dos ids que contem. Ligas sem arquivo aqui vivem apenas no seed_data.json.
AUX_FILES = [
    "seed_premier.json",
    "seed_championship.json",
    "seed_laliga.json",
    "seed_serie_a.json",
    "seed_uruguay.json",
    "seed_outros.json",
]


def upsert(base_list, aux_list, key="id"):
    """Substitui itens de base_list cujo id aparece em aux_list (preservando a
    ordem original) e adiciona ao final os ids novos que so existem no auxiliar."""
    aux_by_id = {item[key]: item for item in aux_list}
    used = set()
    result = []
    for item in base_list:
        i = item.get(key)
        if i in aux_by_id:
            result.append(aux_by_id[i])
            used.add(i)
        else:
            result.append(item)
    added = 0
    for item in aux_list:
        if item[key] not in used:
            result.append(item)
            added += 1
    return result, len(used), added


with open(BASE, encoding="utf-8") as f:
    data = json.load(f)

times = data.get("times", [])
jogadores = data.get("jogadores", [])
tecnicos = data.get("tecnicos", [])
print(
    f"Base seed_data.json: {len(times)} times, {len(jogadores)} jogadores, "
    f"{len(tecnicos)} tecnicos"
)

for fname in AUX_FILES:
    path = os.path.join(ASSETS, fname)
    if not os.path.exists(path):
        print(f"  (pulando {fname}: nao encontrado)")
        continue
    with open(path, encoding="utf-8") as f:
        aux = json.load(f)
    times, ut, at = upsert(times, aux.get("times", []))
    jogadores, uj, aj = upsert(jogadores, aux.get("jogadores", []))
    tecnicos, uc, ac = upsert(tecnicos, aux.get("tecnicos", []))
    print(
        f"  {fname}: times {ut} atualizados / {at} novos"
        f" | jogadores {uj} atualizados / {aj} novos"
        f" | tecnicos {uc} atualizados / {ac} novos"
    )

# Preserva a chave 'tecnicos' apenas se houver algum — evita gravar [] e mascarar
# a necessidade de rodar gen_tecnicos.py.
merged = {"times": times, "jogadores": jogadores}
if tecnicos:
    merged["tecnicos"] = tecnicos

with open(BASE, "w", encoding="utf-8") as f:
    # indent=2 e o formato em que o gen_tecnicos.py (ultimo passo do pipeline)
    # deixa o arquivo. Gravar compacto aqui reescrevia as 127 mil linhas do
    # seed a cada merge, enterrando a mudanca real num diff do arquivo inteiro.
    json.dump(merged, f, ensure_ascii=False, indent=2)
    f.write(chr(10))

size_kb = os.path.getsize(BASE) / 1024
print(f"\n[OK] {BASE}")
print(
    f"  Times: {len(times)} | Jogadores: {len(jogadores)}"
    f" | Tecnicos: {len(tecnicos)} | Tamanho: {size_kb:.1f} KB"
)
if not tecnicos:
    print("  [ATENCAO] Nenhum tecnico no seed! Rode: python scripts/gen_tecnicos.py")
