import math, random

def simular(fC, fF, MULT, MEDIA=2.7, N=100000, seed=42):
    rng = random.Random(seed)
    def pois(l):
        L = math.exp(-l); k = 0; p = 1.0
        while True:
            k += 1; p *= rng.random()
            if p <= L: return k - 1
    tot = fC + fF
    dom = (fC - fF) / tot
    lC = MEDIA * (fC / tot) * (1 + max(dom, 0) * MULT)
    lF = MEDIA * (fF / tot) * (1 + max(-dom, 0) * MULT)
    vc = ve = vf = 0
    for _ in range(N):
        gc = pois(lC); gf = pois(lF)
        if gc > gf:   vc += 1
        elif gc == gf: ve += 1
        else:          vf += 1
    return lC, lF, vc/N*100, ve/N*100, vf/N*100

cenarios = [(70,70),(75,65),(80,60),(90,60),(100,60),(100,50),(110,50),(120,40)]
header = f"{'fC':>5} {'fF':>5} {'Ratio':>6}  {'lCasa':>6} {'lFora':>6}  {'VitCasa':>8} {'Empate':>7} {'VitFora':>8}"

for label, MULT in [("ATUAL (MULT=1.10)", 1.10), ("OPCAO D (MULT=1.50)", 1.50), ("OPCAO E (MULT=2.00)", 2.00), ("OPCAO F (MULT=2.50)", 2.50)]:
    MEDIA = 2.7
    print(f"\n=== {label} ===")
    print(header)
    for fC, fF in cenarios:
        lC, lF, vc, ve, vf = simular(fC, fF, MULT, MEDIA=MEDIA)
        print(f"{fC:>5} {fF:>5} {fC/fF:>6.2f}  {lC:>6.3f} {lF:>6.3f}  {vc:>7.1f}% {ve:>6.1f}% {vf:>7.1f}%")
