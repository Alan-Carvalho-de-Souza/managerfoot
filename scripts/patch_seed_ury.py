import json

with open('app/src/main/assets/seed_data.json', 'r', encoding='utf-8') as f:
    seed = json.load(f)

with open('app/src/main/assets/seed_uruguay.json', 'r', encoding='utf-8') as f:
    ury = json.load(f)

times_br_ar = [t for t in seed['times'] if t['id'] < 141]
jogadores_br_ar = [j for j in seed['jogadores'] if j.get('time_id') is not None and j['time_id'] < 141]

print(f'Kept {len(times_br_ar)} BR+AR teams, {len(jogadores_br_ar)} BR+AR players')
print(f'Adding {len(ury["times"])} UY teams, {len(ury["jogadores"])} UY players')

seed['times'] = times_br_ar + ury['times']
seed['jogadores'] = jogadores_br_ar + ury['jogadores']

print(f'Total: {len(seed["times"])} teams, {len(seed["jogadores"])} players')

with open('app/src/main/assets/seed_data.json', 'w', encoding='utf-8') as f:
    json.dump(seed, f, ensure_ascii=False, separators=(',', ':'))

print('Done.')
