def peso(setor, fin, vel):
    if setor == 'ATQ': return fin * 5 + vel + 50
    if setor == 'MEIO': return fin
    return max(fin // 7, 1)

squads = {
    '4-4-2': {
        'ATQ':  [('CA1', 80, 75), ('CA2', 75, 80)],
        'MEIO': [('MC1', 55, 60), ('MC2', 50, 55), ('MLD', 60, 70), ('MLE', 55, 65)],
        'DEF':  [('ZAG1', 35, 40), ('ZAG2', 35, 40), ('LAT1', 40, 70), ('LAT2', 40, 70)],
    },
    '4-3-3': {
        'ATQ':  [('CA1', 85, 70), ('PE1', 75, 85), ('PD1', 75, 85)],
        'MEIO': [('VOL', 45, 55), ('MC1', 55, 60), ('MC2', 58, 65)],
        'DEF':  [('ZAG1', 35, 40), ('ZAG2', 35, 40), ('LAT1', 40, 70), ('LAT2', 40, 70)],
    },
    '4-5-1': {
        'ATQ':  [('CA1', 80, 70)],
        'MEIO': [('MC1', 65, 60), ('MC2', 60, 55), ('MLD', 65, 70), ('MLE', 60, 65), ('VOL', 50, 55)],
        'DEF':  [('ZAG1', 35, 40), ('ZAG2', 35, 40), ('LAT1', 40, 70), ('LAT2', 40, 70)],
    },
}

for form, squad in squads.items():
    total = 0
    pesos = {}
    for setor, jogadores in squad.items():
        for nome, fin, vel in jogadores:
            p = peso(setor, fin, vel)
            pesos[nome] = (setor, p)
            total += p
    print(f'\n=== {form} ===')
    print(f'  {"Jogador":<8} {"Setor":<6} {"Peso":>7}  {"% Gol":>7}')
    print('  ' + '-'*36)
    totais = {'ATQ': 0, 'MEIO': 0, 'DEF': 0}
    for nome, (setor, p) in pesos.items():
        print(f'  {nome:<8} {setor:<6} {p:>7}   {p/total*100:>6.1f}%')
        totais[setor] += p
    print('  ' + '-'*36)
    for setor, t in totais.items():
        print(f'  {"Total "+setor:<15} {t:>7}   {t/total*100:>6.1f}%')
