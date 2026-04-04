import json, math, random
from collections import defaultdict

with open('app/src/main/assets/seed_brasileirao.json', encoding='utf-8') as f:
    data = json.load(f)

times_map = {t['id']: t for t in data['times']}
por_time = defaultdict(list)
for j in data['jogadores']:
    if j.get('time_id'):
        por_time[j['time_id']].append(j['forca'])

medias = {tid: sum(v)/len(v) for tid,v in por_time.items()}
div = {tid: times_map[tid]['divisao'] for tid in medias if tid in times_map}
div_media = defaultdict(list)
for tid, m in medias.items():
    div_media[div.get(tid,'?')].append(m)

print('=== FORCA MEDIA REAL POR DIVISAO ===')
for d in ['A','B','C','D']:
    vs = div_media[d]
    print('  Serie %s: min=%.1f  max=%.1f  media=%.1f' % (d, min(vs), max(vs), sum(vs)/len(vs)))

rows = [
    ('A-top vs A-mid',   81.5, 70.0),
    ('A-top vs A-bot',   81.5, 56.0),
    ('A-avg vs B-avg',   68.8, 55.6),
    ('A-avg vs C-avg',   68.8, 47.1),
    ('A-avg vs D-avg',   68.8, 43.3),
    ('A-top vs C-bot',   81.5, 37.4),
    ('A-top vs D-bot',   81.5, 37.0),
]

print()
print('  %-24s  ratio   dom_lin  dom_pow1.5  dom_pow2   multC_lin  multC_pow2' % 'Cenario')
for label, fC, fF in rows:
    r = fC/fF
    d_lin = (fC - fF)/(fC + fF)
    d_p15 = (fC**1.5 - fF**1.5)/(fC**1.5 + fF**1.5)
    d_p2  = (fC**2.0 - fF**2.0)/(fC**2.0 + fF**2.0)
    print('  %-24s  %5.2fx  %7.3f  %10.3f  %9.3f  %9.3f  %10.3f' % (
        label, r, d_lin, d_p15, d_p2, 1+d_lin*2.0, 1+d_p2*2.0))

# Monte Carlo para comparar win% linear vs pow2
def simular_win(fC, fF, MULT, MEDIA=2.7, N=80000, POW=1.0, seed=42):
    rng = random.Random(seed)
    def pois(l):
        L = math.exp(-l); k = 0; p = 1.0
        while True:
            k += 1; p *= rng.random()
            if p <= L: return k - 1
    tot = fC + fF
    probC = fC/tot
    probF = fF/tot
    if POW != 1.0:
        fCp = fC**POW; fFp = fF**POW; totp = fCp + fFp
        dom = (fCp - fFp)/totp
    else:
        dom = (fC - fF)/tot
    lC = MEDIA * probC * (1 + max(dom,0)*MULT)
    lF = MEDIA * probF * (1 + max(-dom,0)*MULT)
    vc=ve=vf=0
    for _ in range(N):
        gc=pois(lC); gf=pois(lF)
        if gc>gf: vc+=1
        elif gc==gf: ve+=1
        else: vf+=1
    return vc/N*100, ve/N*100, vf/N*100

print()
print('=== WIN% ATUAL (linear dom, MULT=2.00) vs POW=2.0 (MULT=2.00) ===')
print('  %-24s  ratio   LINEAR(atual)           POW=2.0' % 'Cenario')
for label, fC, fF in rows:
    r = fC/fF
    vc1,ve1,vf1 = simular_win(fC,fF,2.00,POW=1.0)
    vc2,ve2,vf2 = simular_win(fC,fF,2.00,POW=2.0)
    print('  %-24s  %5.2fx  %4.1f%%/%4.1f%%/%4.1f%%  ->  %4.1f%%/%4.1f%%/%4.1f%%' % (
        label, r, vc1,ve1,vf1, vc2,ve2,vf2))
