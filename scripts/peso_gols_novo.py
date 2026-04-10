# Compara formula ATUAL vs NOVA para escolha do marcador dentro do ataque

def peso_atual_full(fin, vel): return fin * 5 + vel + 50
def peso_atual_periodo(fin, vel): return fin * 3 + vel + 50
def peso_novo(fin, vel, tec): return fin * fin // 10 + vel + tec

casos = [
    ("Artilheiro (fin=90,vel=85,tec=80)", 90, 85, 80),
    ("Titular bom (fin=78,vel=78,tec=72)", 78, 78, 72),
    ("Atacante médio (fin=70,vel=75,tec=68)", 70, 75, 68),
    ("Reserva fraco (fin=55,vel=70,tec=60)", 55, 70, 60),
]

print("=== Pesos individuais ===")
print(f"{'Atacante':<35} {'Atual-full':>12} {'Atual-per':>12} {'Novo (fin²/10+vel+tec)':>22}")
print("-"*85)
for nome, fin, vel, tec in casos:
    print(f"{nome:<35} {peso_atual_full(fin,vel):>12} {peso_atual_periodo(fin,vel):>12} {peso_novo(fin,vel,tec):>22}")

print("\n=== Probabilidade de marcar em time 4-4-2 com 2 atacantes ===")
# Caso: artilheiro (90) + companheiro médio (70)
pairs = [
    ("Artilheiro fin=90 + fin=70", (90,85,80), (70,75,68)),
    ("Bom fin=82 + médio fin=72",  (82,80,75), (72,74,70)),
    ("Iguais fin=78 + fin=76",     (78,78,72), (76,76,71)),
]
for desc, a1, a2 in pairs:
    f1, v1, t1 = a1; f2, v2, t2 = a2
    wa_old = peso_atual_full(f1,v1); wb_old = peso_atual_full(f2,v2)
    wa_new = peso_novo(f1,v1,t1);   wb_new = peso_novo(f2,v2,t2)
    tot_old = wa_old+wb_old; tot_new = wa_new+wb_new
    print(f"\n  {desc}:")
    print(f"    ATUAL:  {wa_old}/{wa_old+wb_old} = {wa_old/tot_old*100:.1f}% vs {wb_old/tot_old*100:.1f}%  (ratio {wa_old/wb_old:.2f}x)")
    print(f"    NOVO:   {wa_new}/{wa_new+wb_new} = {wa_new/tot_new*100:.1f}% vs {wb_new/tot_new*100:.1f}%  (ratio {wa_new/wb_new:.2f}x)")
