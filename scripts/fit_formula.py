import math, random

def pois_sim(lC, lF, N=200000, seed=42):
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

def calcular(fC, fF, alpha, k):
    fC2 = fC*fC; fF2 = fF*fF
    dom = (fC2 - fF2) / (fC2 + fF2)
    domC = max(dom, 0); domF = max(-dom, 0)
    tot = fC + fF; probC = fC/tot; probF = fF/tot; MEDIA = 2.7
    mC = 1 + (domC ** alpha) * k
    mF = 1 + (domF ** alpha) * k
    lC = MEDIA * probC * mC
    lF = MEDIA * probF * mF
    return lC, lF

# Targets: (fC, fF, VC_alvo, E_alvo, VF_alvo)
targets = [
    (85, 80,  50, 25, 25),
    (75, 65,  60, 20, 20),
    (80, 60,  75, 15, 10),
    (90, 60,  82, 10,  8),
    (100, 60, 87,  8,  5),
]

print("Buscando melhor (alpha, k)...\n")
best_err = 1e9
best_params = None

for alpha_i in range(5, 40, 1):   # alpha 0.50 a 1.95 step 0.05
    alpha = alpha_i / 20.0
    for k_i in range(20, 120, 2):  # k 2.0 a 12.0 step 0.2
        k = k_i / 10.0
        err = 0
        for fC, fF, vc_t, e_t, vf_t in targets:
            lC, lF = calcular(fC, fF, alpha, k)
            vc, e, vf = pois_sim(lC, lF, N=60000)
            err += (vc - vc_t)**2 + (vf - vf_t)**2
        if err < best_err:
            best_err = err
            best_params = (alpha, k)

print(f"Melhor alpha={best_params[0]:.2f}, k={best_params[1]:.1f}  (erro={best_err:.1f})\n")
alpha, k = best_params

print("=" * 85)
print(f"  {'Cenario':8}  {'Ratio':5}   ── ALVO ───────   ── RESULTADO ───────   ok?")
print(f"  {'':8}  {'':5}   {'%Casa':5} {'%Emp':5} {'%Fora':5}  {'%Casa':6} {'%Emp':5} {'%Fora':6}")
print("-" * 85)
all_scenarios = [(70,70,None,None,None),(85,80,50,25,25),(75,65,60,20,20),(80,60,75,15,10),
                 (90,60,82,10,8),(100,60,87,8,5),(100,50,None,None,None),(120,40,None,None,None)]
for row in all_scenarios:
    fC, fF = row[0], row[1]
    vc_t, e_t, vf_t = row[2], row[3], row[4]
    lC, lF = calcular(fC, fF, alpha, k)
    vc, e, vf = pois_sim(lC, lF, N=200000)
    alvo_str = f"{vc_t:4}% {e_t:4}% {vf_t:4}%" if vc_t else "  -      -      -  "
    ok = "✓" if vc_t and abs(vc-vc_t)<=3 and abs(vf-vf_t)<=3 else ("" if not vc_t else "~")
    print(f"  {str(fC)+'x'+str(fF):8}  {fC/fF:5.2f}   {alvo_str}   {vc:5.1f}% {e:4.1f}% {vf:5.1f}%   {ok}")
print("=" * 85)
print(f"\nFormula: mC = 1 + dom^{alpha:.2f} × {k:.1f}   (dom = (fC²-fF²)/(fC²+fF²))")
