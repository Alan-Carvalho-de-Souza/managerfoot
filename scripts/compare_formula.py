import math, random

def pois_sim(lC, lF, N=120000, seed=42):
    rng = random.Random(seed)
    def pois(l):
        L = math.exp(-l); k = 0; p = 1.0
        while True:
            k += 1; p *= rng.random()
            if p <= L: return k - 1
    vc = ve = vf = 0
    for _ in range(N):
        gc = pois(lC); gf = pois(lF)
        if gc > gf:    vc += 1
        elif gc == gf: ve += 1
        else:          vf += 1
    return vc/N*100, ve/N*100, vf/N*100

def calcular(fC, fF, formula):
    fC2 = fC*fC; fF2 = fF*fF
    dom = (fC2 - fF2) / (fC2 + fF2)
    domC = max(dom, 0); domF = max(-dom, 0)
    tot = fC + fF; probC = fC/tot; probF = fF/tot; MEDIA = 2.7
    if formula == 'atual':
        mC = 1 + domC * 2.0
        mF = 1 + domF * 2.0
    elif formula == 'pow15':   # dom^1.5 * 3.5
        mC = 1 + (domC ** 1.5) * 3.5
        mF = 1 + (domF ** 1.5) * 3.5
    elif formula == 'pow2':    # dom^2 * 6.0  (ainda mais convexa, controla extremos)
        mC = 1 + (domC ** 2) * 6.0
        mF = 1 + (domF ** 2) * 6.0
    lC = MEDIA * probC * mC
    lF = MEDIA * probF * mF
    return lC, lF

cenarios = [(70,70),(85,80),(75,65),(80,60),(90,60),(100,60),(100,50),(110,50),(120,40)]

print("=" * 122)
print(f"  {'Cenario':8}  {'Ratio':5}  ─── ATUAL (dom×2.0) ────────────────  ─── NOVO A (dom^1.5×3.5) ─────────  ─── NOVO B (dom²×6.0) ───────────")
print(f"  {'':8}  {'':5}  {'lC':6} {'lF':6}  {'%Casa':6} {'%Emp':5} {'%Fora':6}   {'lC':6} {'lF':6}  {'%Casa':6} {'%Emp':5} {'%Fora':6}   {'lC':6} {'lF':6}  {'%Casa':6} {'%Emp':5} {'%Fora':6}")
print("-" * 122)
for fC, fF in cenarios:
    lC_at, lF_at = calcular(fC, fF, 'atual')
    lC_p,  lF_p  = calcular(fC, fF, 'pow15')
    lC_q,  lF_q  = calcular(fC, fF, 'pow2')
    vc_at, e_at, vf_at = pois_sim(lC_at, lF_at)
    vc_p,  e_p,  vf_p  = pois_sim(lC_p,  lF_p)
    vc_q,  e_q,  vf_q  = pois_sim(lC_q,  lF_q)
    tag = " ◄" if fC == 85 and fF == 80 else ""
    print(f"  {str(fC)+'x'+str(fF):8}  {fC/fF:5.2f}"
          f"  {lC_at:6.3f} {lF_at:6.3f}  {vc_at:5.1f}% {e_at:4.1f}% {vf_at:5.1f}%"
          f"   {lC_p:6.3f} {lF_p:6.3f}  {vc_p:5.1f}% {e_p:4.1f}% {vf_p:5.1f}%"
          f"   {lC_q:6.3f} {lF_q:6.3f}  {vc_q:5.1f}% {e_q:4.1f}% {vf_q:5.1f}%{tag}")
print("=" * 122)
