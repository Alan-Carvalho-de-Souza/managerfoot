"""
Diagnostico completo do seed: IDs duplicados, times sem jogadores,
jogadores com time_id invalido, times com elenco menor que 16.
"""
import json, collections, sys

path = r'app/src/main/assets/seed_brasileirao.json'
data = json.loads(open(path, encoding='utf-8').read())
times = data['times']
jogadores = data['jogadores']

erros = 0

print(f'=== Times: {len(times)} | Jogadores: {len(jogadores)} ===\n')

# IDs duplicados de times
time_ids = [t['id'] for t in times]
dup_times = [i for i,c in collections.Counter(time_ids).items() if c > 1]
if dup_times:
    print(f'[ERRO] Time IDs duplicados: {dup_times}')
    erros += len(dup_times)

# IDs duplicados de jogadores
jog_ids = [j['id'] for j in jogadores]
dup_jog = [i for i,c in collections.Counter(jog_ids).items() if c > 1]
if dup_jog:
    print(f'[ERRO] Jogador IDs duplicados ({len(dup_jog)}): {dup_jog[:20]}')
    erros += len(dup_jog)

# Jogadores com time_id que nao existe
time_id_set = set(time_ids)
jogs_invalidos = [j for j in jogadores if j.get('time_id') is not None and j['time_id'] not in time_id_set]
if jogs_invalidos:
    print(f'[ERRO] Jogadores com time_id inexistente ({len(jogs_invalidos)}):')
    for j in jogs_invalidos[:10]:
        print(f'  jogador id={j["id"]} time_id={j["time_id"]}')
    erros += len(jogs_invalidos)

# Divisao por time
div_por_time = {t['id']: t['divisao'] for t in times}

# Jogadores por time
jog_por_time = collections.Counter(
    j['time_id'] for j in jogadores if j.get('time_id') is not None
)

# Times sem jogadores
times_sem = [t for t in times if t['id'] not in jog_por_time]
if times_sem:
    print(f'[ERRO] Times sem nenhum jogador ({len(times_sem)}):')
    for t in times_sem:
        print(f'  id={t["id"]:3d} div={t["divisao"]} {t["nome"]}')
    erros += len(times_sem)

# Times com elenco abaixo de 16
print('\n=== Elenco por time ===')
abaixo = []
for t in sorted(times, key=lambda x: (x['divisao'], x['id'])):
    qtd = jog_por_time.get(t['id'], 0)
    flag = ' <-- BAIXO' if qtd < 16 else ''
    if qtd < 16:
        abaixo.append((t['id'], t['divisao'], t['nome'], qtd))
    print(f'  id={t["id"]:3d} div={t["divisao"]} {t["nome"]:<35} jogadores={qtd}{flag}')

if abaixo:
    print(f'\n[ERRO] {len(abaixo)} time(s) com menos de 16 jogadores:')
    for id, div, nome, qtd in abaixo:
        print(f'  id={id} div={div} {nome} -> {qtd} jogadores')
    erros += len(abaixo)

print(f'\n=== Resultado: {"OK" if erros == 0 else f"{erros} problema(s) encontrado(s)"} ===')
sys.exit(0 if erros == 0 else 1)
