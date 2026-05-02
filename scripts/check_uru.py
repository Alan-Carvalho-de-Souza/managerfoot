import json
with open('app/src/main/assets/seed_data.json','r',encoding='utf-8-sig') as f:
    d=json.load(f)
uru = [t for t in d['times'] if t.get('pais','')=='Uruguai']
for t in uru:
    print(f"id={t['id']} {t['nome']} div={t['divisao']}")
