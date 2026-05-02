import json

with open('app/src/main/assets/seed_data.json', encoding='utf-8') as f:
    data = json.load(f)
with open('app/src/main/assets/seed_uruguay.json', encoding='utf-8') as f:
    uru = json.load(f)

existing_jog_ids = {j['id'] for j in data['jogadores']}
uru_jog_ids = {j['id'] for j in uru['jogadores']}
collisions = existing_jog_ids & uru_jog_ids
print('Collisions:', collisions)
if not collisions:
    data['times'].extend(uru['times'])
    data['jogadores'].extend(uru['jogadores'])
    with open('app/src/main/assets/seed_data.json', 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
    print('Merged:', len(data['times']), 'times,', len(data['jogadores']), 'jogadores')
